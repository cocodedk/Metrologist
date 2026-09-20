"""Exercise the project verification gate with a local fake Gradle executable."""
import json
from pathlib import Path
import subprocess
import sys

from support import SCRIPTS, SupervisorCase


class VerificationTests(SupervisorCase):
    def setup_check(self, task_id="example", xml=None, test_exists=True):
        self.root.joinpath("docs/supervisor").mkdir(parents=True)
        task = {"id": task_id, "required_tests": ["ContractTest.kt"]}
        self.root.joinpath("docs/supervisor/tasks.json").write_text(json.dumps({"tasks": [task]}))
        if task_id == "integration":
            self.root.joinpath(".supervisor").mkdir(exist_ok=True)
            self.root.joinpath(".supervisor/android-serial").write_text("fake-device\n")
        if test_exists:
            self.root.joinpath("ContractTest.kt").write_text("package example\nclass ContractTest\n")
        reports = ("app/build/outputs/androidTest-results/connected" if task_id == "integration"
                   else "app/build/test-results/testDebugUnitTest")
        script = [f"#!{sys.executable}", "from pathlib import Path", "Path('gradle-ran').touch()"]
        if xml is not None:
            script += [f"p = Path({reports!r})", "p.mkdir(parents=True, exist_ok=True)",
                       f"(p / 'TEST-contract.xml').write_text({xml!r})"]
        self.root.joinpath("gradlew").write_text("\n".join(script) + "\n")
        self.root.joinpath("gradlew").chmod(0o755)
        return self.root / reports

    def check(self, task_id="example"):
        return subprocess.run([sys.executable, str(SCRIPTS / "supervisor/check.py"), task_id],
                              cwd=self.root, capture_output=True, text=True, timeout=5)

    def test_missing_regression_stops_before_gradle(self):
        self.setup_check(test_exists=False)
        self.assertEqual(self.check().returncode, 1)
        self.assertFalse(self.root.joinpath("gradle-ran").exists())

    def test_stale_report_cannot_pass_a_zero_test_run(self):
        reports = self.setup_check()
        reports.mkdir(parents=True)
        reports.joinpath("stale.xml").write_text('<testsuite><testcase classname="example.ContractTest"/></testsuite>')
        self.assertEqual(self.check().returncode, 1)
        self.assertFalse(reports.joinpath("stale.xml").exists())

    def test_skipped_test_cannot_pass(self):
        self.setup_check(xml='<testsuite><testcase classname="example.ContractTest"><skipped/></testcase></testsuite>')
        self.assertEqual(self.check().returncode, 1)

    def test_passing_regression_can_include_captured_output(self):
        self.setup_check(xml='<testsuite><testcase classname="example.ContractTest"><system-out>ok</system-out></testcase></testsuite>')
        self.assert_ok(self.check())

    def test_integration_requires_all_fifteen_edges(self):
        cases = ''.join(f'<testcase classname="example.ContractTest" name="C{i:02d}_flow"/>'
                        for i in range(1, 15))
        self.setup_check(task_id="integration", xml='<testsuite>' + cases + '</testsuite>')
        result = self.check("integration")
        self.assertEqual(result.returncode, 1)
        self.assertIn("C15", result.stderr)

    def test_integration_accepts_all_executed_edges(self):
        cases = ''.join(f'<testcase classname="example.ContractTest" name="C{i:02d}_flow"/>'
                        for i in range(1, 16))
        self.setup_check(task_id="integration", xml='<testsuite>' + cases + '</testsuite>')
        self.assert_ok(self.check("integration"))
