import json
import os
import signal
import sys
import time

from support import SupervisorCase
from state import locked, members, read, save


class ControlTests(SupervisorCase):
    def test_pause_resume_preserves_attempt_and_stops_writes(self):
        self.plan([self.task("a", duration=1.2)], timeout_seconds=2)
        child = self.start()
        self.wait_for(lambda: (self.root / "heartbeat-a").exists())
        self.assert_ok(self.command("pause"))
        self.wait_for(lambda: self.state()["mode"] == "paused")
        first = self.root.joinpath("heartbeat-a").read_text()
        time.sleep(2.1)
        self.assertEqual(self.root.joinpath("heartbeat-a").read_text(), first)
        self.assert_ok(self.command("resume"))
        self.assertEqual(child.wait(timeout=8), 0)
        self.assertEqual(self.state()["tasks"]["a"]["attempt"], 1)

    def test_pause_then_cancel_does_not_lose_newer_request(self):
        self.plan([self.task("a", duration=30, child=True)])
        child = self.start()
        self.wait_for(lambda: (self.root / "child-a").exists())
        # Queue both while holding the same lock used by the actual control commands.
        with locked(self.state_path.with_suffix(".lock")):
            state = self.state()
            state["requests"] += [{"id": 1, "action": "pause", "status": "pending"},
                                   {"id": 2, "action": "cancel", "status": "pending"}]
            save(self.state_path, state)
        self.assertEqual(child.wait(timeout=8), 1)
        self.assertEqual([r["status"] for r in self.state()["requests"]], ["superseded", "done"])
        self.assertEqual(self.state()["tasks"]["a"]["status"], "cancelled")
        self.assertFalse(members(self.state()["tasks"]["a"]["pid"]))

    def test_two_supervisors_cannot_own_checkout(self):
        self.plan([self.task("a", duration=30)])
        child = self.start()
        self.wait_for(lambda: (self.root / "started-a").exists())
        self.assertEqual(self.command("start").returncode, 2)
        self.assert_ok(self.command("cancel"))
        self.assertEqual(child.wait(timeout=8), 1)

    def test_timeout_kills_children_and_blocks_dependents(self):
        self.plan([self.task("a", duration=30, child=True), self.task("b", ["a"])], timeout_seconds=0.3)
        self.assertEqual(self.start().wait(timeout=8), 1)
        self.assertEqual(self.state()["tasks"]["a"]["error"], "Process timeout")
        self.assertFalse(members(self.state()["tasks"]["a"]["pid"]))
        self.assertFalse((self.root / "started-b").exists())

    def test_orphan_child_is_killed_and_not_counted_as_success(self):
        self.plan([self.task("a", child=True)])
        self.assertEqual(self.start().wait(timeout=8), 1)
        self.assertIn("child processes", self.state()["tasks"]["a"]["error"])
        self.assertFalse(members(self.state()["tasks"]["a"]["pid"]))

    def test_retry_clears_evidence_and_keeps_attempt_history(self):
        task = self.task("a")
        task["check"] = [sys.executable, "-c", "from pathlib import Path; assert Path('allow').exists()"]
        self.plan([task])
        self.assertEqual(self.start().wait(timeout=8), 1)
        # A real CLI attempt adds identity/report fields that must remain only in history.
        stale = {"worker_pid": 123, "agent_pid": 456, "agent": "a/1",
                 "process_start": "old-start", "cli_identity": {"session_id": "old-session"},
                 "report": {"status": "completed"}, "command": ["old-command"]}
        with locked(self.state_path.with_suffix(".lock")):
            state = self.state()
            state["tasks"]["a"].update(stale)
            save(self.state_path, state)
        self.root.joinpath("allow").touch()
        self.assert_ok(self.command("retry", "a"))
        pending = self.state()["tasks"]["a"]
        self.assertEqual(pending["evidence"], [])
        self.assertIsNone(pending["verified_at"])
        self.assertEqual(pending["history"][0]["status"], "failed")
        for key, value in stale.items():
            self.assertNotIn(key, pending)
            self.assertEqual(pending["history"][0][key], value)
        self.assertNotIn("log", pending)
        self.assertNotIn("stderr", pending)
        self.assertEqual(self.start().wait(timeout=8), 0)
        self.assertEqual(self.state()["tasks"]["a"]["attempt"], 2)
        self.assertNotIn("agent_pid", self.state()["tasks"]["a"])
        self.assertNotIn("cli_identity", self.state()["tasks"]["a"])
        self.assertEqual(len(self.state()["tasks"]["a"]["evidence"]), 2)

    def test_hard_crash_cannot_reclaim_a_live_worker(self):
        self.plan([self.task("a", duration=30)])
        child = self.start()
        self.wait_for(lambda: (self.root / "started-a").exists())
        pid = self.state()["tasks"]["a"]["pid"]
        child.kill()
        child.wait()
        self.assertEqual(self.command("start").returncode, 2)
        self.assertEqual(self.command("recover").returncode, 2)
        os.killpg(pid, signal.SIGKILL)
        self.wait_for(lambda: not members(pid))
        self.assert_ok(self.command("recover"))
        self.assertEqual(self.state()["tasks"]["a"]["status"], "failed")
        self.assert_ok(self.command("retry", "a"))

    def test_normal_termination_cleans_up_workers(self):
        self.plan([self.task("a", duration=30, child=True)])
        child = self.start()
        self.wait_for(lambda: (self.root / "child-a").exists())
        child.terminate()
        self.assertEqual(child.wait(timeout=8), 1)
        self.assertFalse(members(self.state()["tasks"]["a"]["pid"]))

    def test_check_process_is_cancellable(self):
        task = self.task("a")
        task["check"] = [sys.executable, "-c", "import time; time.sleep(30)"]
        self.plan([task])
        child = self.start()
        self.wait_for(lambda: self.state_path.exists() and self.state()["tasks"]["a"]["status"] == "checking")
        self.assert_ok(self.command("cancel"))
        self.assertEqual(child.wait(timeout=8), 1)
        self.assertEqual(self.state()["tasks"]["a"]["status"], "cancelled")

    def test_aborted_run_cannot_carry_pending_control_into_retry(self):
        self.plan([self.task("a", duration=30)], poll_seconds=1)
        child = self.start()
        self.wait_for(lambda: (self.root / "started-a").exists())
        # The controller is sleeping between polls; queue a request and terminate it.
        with locked(self.state_path.with_suffix(".lock")):
            state = self.state()
            state["requests"].append({"id": 1, "action": "pause", "status": "pending"})
            save(self.state_path, state)
            child.terminate()
        self.assertEqual(child.wait(timeout=8), 1)
        self.assertEqual(self.state()["requests"][0]["status"], "superseded")
        self.assert_ok(self.command("retry", "a"))
        self.assertFalse(any(r["status"] == "pending" for r in self.state()["requests"]))
