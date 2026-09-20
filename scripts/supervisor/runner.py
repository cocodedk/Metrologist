"""Own worker process groups and keep verification separate from worker exit codes."""
import os
import json
import signal
import subprocess
import sys
import time
from pathlib import Path

from state import members, now, process, save, signal_group
from journal import event, identity, report, snapshot


class Runner:
    def __init__(self, root, plan, template):
        self.root, self.plan, self.template = root, plan, template
        self.active = {}
        self.tasks = {task["id"]: task for task in plan["tasks"]}

    def launch(self, task, state, checking=False):
        task_id = task["id"]
        record = state["tasks"][task_id]
        if not checking:
            record.pop("verification_only", None)
            record.update(attempt=record["attempt"] + 1, started_at=now(), ended_at=None,
                          verified_at=None, error=None, evidence=[], worker_pid=None)
        phase = "check" if checking else "worker"
        log = self.root / ".supervisor" / f"{task_id}-{record['attempt']}-{phase}.log"
        command = task["check"] if checking else self.plan["worker"]
        prompt = self.template.replace("{{TASK}}", json.dumps(task, indent=2))
        prompt_path = log.with_suffix(".prompt.md")
        if not checking:
            with prompt_path.open("x") as output:
                output.write(prompt)
            context = {name: (self.root / name).read_text() for name in task["specs"]}
            with log.with_suffix(".context.json").open("x") as output:
                json.dump(context, output, indent=2)
            snapshot(self.root, task, log.with_suffix(".before.json"))
        permit = log.with_suffix(".go")
        permit.unlink(missing_ok=True)
        gated = [sys.executable, str(Path(__file__).with_name("gate.py")),
                 str(permit), str(os.getpid()), *command]
        # A file on stdin avoids blocking the control loop on a full pipe.
        stderr_path = log.with_suffix(".stderr.log")
        with log.open("x") as output, stderr_path.open("x") as errors, open(os.devnull if checking else prompt_path) as stdin:
            child = subprocess.Popen(gated, cwd=self.root, stdin=stdin, stdout=output,
                                     stderr=errors, start_new_session=True)
        record.update(status="checking" if checking else "running", pid=child.pid,
                      process_start=(process(child.pid) or {}).get("start"),
                      log=str(log.relative_to(self.root)), stderr=str(stderr_path.relative_to(self.root)), command=command)
        if not checking:
            record.update(worker_pid=child.pid, agent=f"{task_id}/{record['attempt']}")
        self.active[task_id] = {"child": child, "elapsed": 0.0, "checking": checking}
        save(self.root / ".supervisor/state.json", state)
        event(log.parent, "launch", task=task_id, attempt=record["attempt"], phase=phase,
              pid=child.pid, command=command, stdout=record["log"], stderr=record["stderr"])
        permit.touch()

    def collect(self, state, elapsed):
        for task_id, run in list(self.active.items()):
            child, record = run["child"], state["tasks"][task_id]
            if not run["checking"] and self.plan.get("worker_protocol") == "claude-stream-json":
                if not record.get("cli_identity"):
                    record["cli_identity"] = identity(self.root / record["log"])
                if not record.get("agent_pid"):
                    for pid in members(child.pid):
                        try:
                            if Path(f"/proc/{pid}/comm").read_text().startswith("claude"):
                                record["agent_pid"] = pid
                        except FileNotFoundError:
                            pass
            run["elapsed"] += elapsed
            code = child.poll()
            if code is None and run["elapsed"] > self.plan["timeout_seconds"]:
                record["error"] = "Process timeout"
                signal_group(child.pid, signal.SIGKILL)
                continue
            if code is None:
                continue
            if members(child.pid):
                record["error"] = record["error"] or "Worker left child processes running"
                signal_group(child.pid, signal.SIGKILL)
                continue
            if not run["checking"]:
                snapshot(self.root, self.tasks[task_id], (self.root / record["log"]).with_suffix(".after.json"))
                if code == 0 and self.plan.get("worker_protocol") == "claude-stream-json":
                    try:
                        record["report"] = report(self.root / record["log"])
                    except ValueError as error:
                        record["error"] = str(error)
            record["evidence"].append({"phase": "check" if run["checking"] else "worker",
                                       "exit_code": code, "log": record["log"], "stderr": record["stderr"], "at": now()})
            passed = code == 0 and not record["error"]
            record["status"] = ("passed" if run["checking"] else "awaiting-check") if passed else "failed"
            if record["status"] in ("passed", "failed"):
                record["ended_at"] = now()
            if record["status"] == "passed":
                record["verified_at"] = now()
            if not passed:
                record["error"] = record["error"] or f"{record['status']}: process exited {code}"
            event(self.root / ".supervisor", "exit", task=task_id, attempt=record["attempt"],
                  pid=child.pid, exit_code=code, status=record["status"], error=record["error"])
            del self.active[task_id]

    def control(self, state, action):
        sig = {"pause": signal.SIGSTOP, "resume": signal.SIGCONT, "cancel": signal.SIGKILL}[action]
        for run in self.active.values():
            signal_group(run["child"].pid, sig)
        groups = [members(run["child"].pid) for run in self.active.values()]
        if action == "pause" and any(p["state"] not in ("T", "t") for g in groups for p in g.values()):
            return False
        if action == "cancel":
            if any(groups):
                return False
            for task_id, run in self.active.items():
                code = run["child"].wait()
                record = state["tasks"][task_id]
                if not run["checking"]:
                    snapshot(self.root, self.tasks[task_id], (self.root / record["log"]).with_suffix(".after.json"))
                event(self.root / ".supervisor", "exit", task=task_id, pid=run["child"].pid,
                      exit_code=code, status="cancelled")
            self.active.clear()
            for record in state["tasks"].values():
                if record["status"] not in ("passed", "failed", "cancelled"):
                    record.update(status="cancelled", ended_at=now(), error="Cancelled by user")
        state["mode"] = {"pause": "paused", "resume": "running", "cancel": "cancelled"}[action]
        return True

    def close(self, state):
        for run in self.active.values():
            signal_group(run["child"].pid, signal.SIGKILL)
        deadline = time.monotonic() + 5
        while self.active and time.monotonic() < deadline:
            self.collect(state, 0)
            time.sleep(0.05)
        for record in state["tasks"].values():
            if record["status"] == "awaiting-check":
                record.update(status="failed", ended_at=now(), error="Interrupted before verification")
