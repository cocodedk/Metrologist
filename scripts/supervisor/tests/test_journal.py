import json
import sys

from support import SupervisorCase
from state import load_plan


class JournalTests(SupervisorCase):
    def test_records_prompt_output_state_launch_exit_and_snapshots(self):
        self.plan([self.task("a")])
        self.root.joinpath("a.kt").write_text("original source\n")
        self.assertEqual(self.start().wait(timeout=8), 0)
        directory = self.root / ".supervisor"
        events = [json.loads(line) for line in (directory / "events.jsonl").read_text().splitlines()]
        self.assertTrue(all(event["at"] for event in events))
        self.assertEqual([e["event"] for e in events].count("launch"), 2)
        self.assertEqual([e["event"] for e in events].count("exit"), 2)
        self.assertEqual(events[-1]["state"]["mode"], "finished")
        self.assertIn("stdout recorded", (directory / "a-1-worker.log").read_text())
        self.assertIn("stderr recorded", (directory / "a-1-worker.stderr.log").read_text())
        self.assertEqual(json.loads((directory / "a-1-worker.prompt.md").read_text())["id"], "a")
        self.assertEqual(json.loads((directory / "a-1-worker.context.json").read_text())["spec.md"], "Dummy test task")
        self.assertEqual((directory / "a-1-worker.before.json").read_text(),
                         (directory / "a-1-worker.after.json").read_text())

    def test_zero_cli_exit_without_structured_completion_fails(self):
        self.plan([self.task("a"), self.task("b", ["a"])], worker_protocol="claude-stream-json")
        self.assertEqual(self.start().wait(timeout=8), 1)
        self.assertIn("no successful final result", self.state()["tasks"]["a"]["error"])
        self.assertFalse(self.root.joinpath("started-b").exists())

    def test_completed_cli_report_still_requires_verification(self):
        output = json.dumps({"type": "result", "is_error": False,
                             "structured_output": {"status": "completed", "blockers": []}})
        worker = [sys.executable, "-c", f"print({output!r})"]
        self.plan([self.task("a")], worker=worker, worker_protocol="claude-stream-json")
        self.assertEqual(self.start().wait(timeout=8), 1)  # No required finished-a artifact.
        self.assertEqual(self.state()["tasks"]["a"]["report"]["status"], "completed")
        self.assertEqual(self.state()["tasks"]["a"]["evidence"][-1]["phase"], "check")

    def test_background_is_in_prompt_and_its_hash(self):
        self.plan([self.task("a")], background="background.md")
        self.root.joinpath("prompt.md").write_text("{{BACKGROUND}}\n{{TASK}}")
        self.root.joinpath("background.md").write_text("Measure flat surfaces")
        _, template, first = load_plan(self.root, self.root / "plan.json")
        self.assertIn("Measure flat surfaces", template)
        self.root.joinpath("background.md").write_text("A corrected background")
        self.assertNotEqual(load_plan(self.root, self.root / "plan.json")[2], first)
