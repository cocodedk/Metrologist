"""Integration checks must never silently install on every connected device."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("project_check", Path(__file__).parents[1] / "check.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class AndroidTargetTests(unittest.TestCase):
    def setUp(self):
        self.before = Path.cwd()
        self.temp = tempfile.TemporaryDirectory()
        os.chdir(self.temp.name)
        Path("docs/supervisor").mkdir(parents=True)
        Path(".supervisor").mkdir()
        Path("FlowTest.kt").write_text("package example\nclass FlowTest\n")
        Path("docs/supervisor/tasks.json").write_text(json.dumps({"tasks": [
            {"id": "integration", "required_tests": ["FlowTest.kt"]},
        ]}))
        self.args = patch.object(checker.sys, "argv", ["check.py", "integration"])
        self.args.start()

    def tearDown(self):
        self.args.stop()
        os.chdir(self.before)
        self.temp.cleanup()

    @staticmethod
    def reports(*args, **kwargs):
        directory = Path("app/build/outputs/androidTest-results/connected")
        directory.mkdir(parents=True)
        cases = ''.join(f'<testcase classname="example.FlowTest" name="C{i:02d}_flow"/>' for i in range(1, 16))
        (directory / "result.xml").write_text(f"<testsuite>{cases}</testsuite>")

    def test_missing_target_never_launches_gradle(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(checker.subprocess, "run") as run:
            with self.assertRaisesRegex(ValueError, "Select one Android"):
                checker.main()
            run.assert_not_called()

    def test_local_target_overrides_ambient_other_device(self):
        Path(".supervisor/android-serial").write_text("emulator-5556\n")
        with patch.dict(os.environ, {"ANDROID_SERIAL": "another-device"}), \
                patch.object(checker.subprocess, "run", side_effect=self.reports) as run:
            checker.main()
            self.assertEqual(run.call_args.kwargs["env"]["ANDROID_SERIAL"], "emulator-5556")

    def test_multiple_serials_are_rejected_before_install(self):
        Path(".supervisor/android-serial").write_text("emulator-5556,another-device\n")
        with patch.object(checker.subprocess, "run") as run:
            with self.assertRaisesRegex(ValueError, "Select one Android"):
                checker.main()
            run.assert_not_called()
