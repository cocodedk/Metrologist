import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPTS = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(SCRIPTS / "supervisor"))
from state import members, read, signal_group


class SupervisorCase(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="measure-supervisor-test-")
        self.root = Path(self.temp.name)
        self.state_path = self.root / ".supervisor/state.json"
        self.root.joinpath("prompt.md").write_text("{{TASK}}")
        self.root.joinpath("spec.md").write_text("Dummy test task")
        self.processes = []

    def tearDown(self):
        for child in self.processes:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=6)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()
        if self.state_path.exists():
            for task in read(self.state_path)["tasks"].values():
                if task.get("pid") and members(task["pid"]):
                    signal_group(task["pid"], signal.SIGKILL)
        self.temp.cleanup()

    def task(self, name, depends=(), files=None, **options):
        check = ("from pathlib import Path; "
                 f"assert Path('finished-{name}').exists(); "
                 "assert not list(Path('.').glob('running-*')), 'concurrent edit during check'")
        return dict(id=name, depends=list(depends), files=files or [f"{name}.kt"],
                    specs=["spec.md"], check=[sys.executable, "-c", check], **options)

    def plan(self, tasks, **options):
        value = dict(worker=[sys.executable, str(Path(__file__).with_name("fake_worker.py"))],
                     max_workers=3, poll_seconds=0.05, timeout_seconds=5,
                     prompt="prompt.md", tasks=tasks)
        value.update(options)
        self.root.joinpath("plan.json").write_text(json.dumps(value))
        return value

    def command(self, *args):
        return subprocess.run([sys.executable, str(SCRIPTS / "supervise.py"), *args,
                               "--plan", "plan.json"], cwd=self.root, capture_output=True,
                              text=True, timeout=8)

    def start(self):
        with self.root.joinpath("supervisor.log").open("a") as log:
            child = subprocess.Popen([sys.executable, str(SCRIPTS / "supervise.py"),
                                      "start", "--plan", "plan.json"], cwd=self.root,
                                     stdout=log, stderr=log)
        self.processes.append(child)
        return child

    def wait_for(self, condition, seconds=6):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if condition():
                return
            time.sleep(0.02)
        log = self.root.joinpath("supervisor.log")
        self.fail("Timed out; " + (log.read_text() if log.exists() else "no supervisor log"))

    def state(self):
        return read(self.state_path)

    def assert_ok(self, result):
        self.assertEqual(result.returncode, 0, result.stderr)
