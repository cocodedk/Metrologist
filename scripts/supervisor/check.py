"""Project checks: require new regressions, execute them, inspect actual JUnit results."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def main():
    plan = json.loads(Path("docs/supervisor/tasks.json").read_text())
    task = next(t for t in plan["tasks"] if t["id"] == sys.argv[1])
    expected = []
    for name in task["required_tests"]:
        path = Path(name)
        if not path.is_file():
            raise ValueError(f"Missing required regression test: {name}")
        package = re.search(r"^package\s+([\w.]+)", path.read_text(), re.M)
        if not package:
            raise ValueError(f"Missing Kotlin package: {name}")
        expected.append(package[1] + "." + path.stem)
    # Keep this run's single-use daemon out of another project's global `gradle --stop`.
    registry = Path(".supervisor/gradle-daemons").resolve()
    command = ["./gradlew", "--no-daemon", f"-Dorg.gradle.daemon.registry.base={registry}",
               "-Pkotlin.compiler.execution.strategy=in-process", "--rerun-tasks"]
    environment = os.environ.copy()
    if task["id"] == "integration":
        target = Path(".supervisor/android-serial")
        serial = target.read_text().strip() if target.exists() else environment.get("ANDROID_SERIAL", "")
        if not re.fullmatch(r"[A-Za-z0-9_.:-]+", serial):
            raise ValueError("Select one Android test device in .supervisor/android-serial or ANDROID_SERIAL")
        environment["ANDROID_SERIAL"] = serial
        print(f"Android integration target: {serial}", flush=True)
        command += ["buildSmoke", ":app:connectedDebugAndroidTest"]
        report_root = Path("app/build/outputs/androidTest-results/connected")
    else:
        command += [":app:testDebugUnitTest"]
        for name in expected:
            command += ["--tests", name]
        command += [":app:assembleDebug"]
        report_root = Path("app/build/test-results/testDebugUnitTest")
    # Remove only reports of this command so stale XML cannot pass the gate.
    for report in report_root.rglob("*.xml"):
        report.unlink()
    subprocess.run(command, check=True, env=environment)
    cases = [case for file in report_root.rglob("*.xml")
             for case in ET.parse(file).iter("testcase")
             if all(case.find(tag) is None for tag in ("failure", "error", "skipped"))]
    for name in expected:
        if not any(case.get("classname") == name for case in cases):
            raise ValueError(f"No passing, non-skipped tests actually ran for {name}")
    if task["id"] == "integration":
        for edge in range(1, 16):
            if not any(re.match(f"C{edge:02d}(?:_|\\b)", case.get("name", "")) for case in cases):
                raise ValueError(f"Missing passing Android flow assertion for C{edge:02d}")
    print(f"Verified {task['id']}: required regressions executed successfully")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, StopIteration, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
