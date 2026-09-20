import json
from pathlib import Path
import sys

from support import SupervisorCase
from schedule import waves
from state import load_plan, overlaps


class SchedulingTests(SupervisorCase):
    def test_fork_join_and_quiet_verification(self):
        self.plan([self.task("root"), self.task("a", ["root"], duration=0.6),
                   self.task("b", ["root"], duration=0.6), self.task("join", ["a", "b"])])
        child = self.start()
        self.wait_for(lambda: (self.root / "running-a").exists() and (self.root / "running-b").exists())
        self.assertFalse((self.root / "started-join").exists())
        self.assertEqual(child.wait(timeout=8), 0)
        self.assertEqual(self.state()["mode"], "finished")
        for record in self.state()["tasks"].values():
            self.assertEqual(record["status"], "passed")
            self.assertIsNotNone(record["worker_pid"])
            self.assertIsNotNone(record["verified_at"])
            self.assertEqual([item["phase"] for item in record["evidence"]], ["worker", "check"])

    def test_shared_file_and_directory_reservations_serialize(self):
        plan = self.plan([self.task("a", files=["src/"], duration=0.3),
                          self.task("b", files=["src/shared.kt"]), self.task("c")])
        self.assertEqual(waves(plan), [["a", "c"], ["b"]])
        child = self.start()
        self.wait_for(lambda: (self.root / "started-a").exists())
        self.assertFalse((self.root / "started-b").exists())
        self.assertEqual(child.wait(timeout=8), 0)

    def test_failed_check_blocks_join_but_independent_branch_finishes(self):
        bad = self.task("bad")
        bad["check"] = [sys.executable, "-c", "raise SystemExit(7)"]
        self.plan([bad, self.task("independent"), self.task("join", ["bad"])])
        self.assertEqual(self.start().wait(timeout=8), 1)
        self.assertEqual(self.state()["tasks"]["bad"]["status"], "failed")
        self.assertEqual(self.state()["tasks"]["independent"]["status"], "passed")
        self.assertFalse((self.root / "started-join").exists())

    def test_invalid_plan_never_starts_workers(self):
        self.plan([self.task("a", ["b"]), self.task("b", ["a"])])
        self.assertEqual(self.command("plan").returncode, 2)
        self.plan([self.task("a", ["missing"])])
        self.assertEqual(self.command("start").returncode, 2)
        self.assertFalse(self.state_path.exists())

    def test_scope_traversal_and_symlink_escape_rejected(self):
        self.plan([self.task("a", files=["../escape"])])
        self.assertEqual(self.command("plan").returncode, 2)
        self.root.joinpath("outside").symlink_to(self.root.parent)
        self.plan([self.task("a", files=["outside/escape"])])
        self.assertEqual(self.command("plan").returncode, 2)
        self.assertTrue(overlaps(["src/foo"], ["src/foo/bar"]))
        self.assertFalse(overlaps(["src/foo"], ["src/foobar"]))

    def test_worker_limit_is_respected(self):
        self.plan([self.task("a", duration=0.3), self.task("b", duration=0.3)], max_workers=1)
        child = self.start()
        self.wait_for(lambda: (self.root / "started-a").exists())
        self.assertFalse((self.root / "started-b").exists())
        self.assertEqual(child.wait(timeout=8), 0)

    def test_missing_worker_fails_without_model_call(self):
        self.plan([self.task("a")], worker=[])
        self.assertEqual(self.command("start").returncode, 2)
        self.assertFalse((self.root / "started-a").exists())

    def test_long_prompt_does_not_block_controls(self):
        self.plan([self.task("a", duration=5)])
        # JSON accepts trailing whitespace; make stdin exceed a pipe buffer.
        self.root.joinpath("prompt.md").write_text("{{TASK}}" + " " * 200000)
        child = self.start()
        self.wait_for(lambda: (self.root / "started-a").exists())
        self.assert_ok(self.command("cancel"))
        self.assertEqual(child.wait(timeout=8), 1)

    def test_internal_symlink_and_dot_aliases_cannot_run_together(self):
        self.root.joinpath("shared.kt").touch()
        self.root.joinpath("alias.kt").symlink_to("shared.kt")
        self.plan([self.task("a", files=["shared.kt"]),
                   self.task("b", files=["./alias.kt"])])
        plan, _, _ = load_plan(self.root, self.root / "plan.json")
        self.assertEqual(waves(plan), [["a"], ["b"]])
