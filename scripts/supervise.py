#!/usr/bin/env python3
"""One entry point for the project's small, foreground agent supervisor."""
import argparse
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent / "supervisor"))
from schedule import run, waves
from state import fresh, load_plan, locked, now, process, read, recover, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("plan", "start", "status", "monitor", "validate", "pause", "resume", "cancel", "retry", "recheck", "recover"))
    parser.add_argument("task", nargs="?", help="task ID for retry or recheck")
    parser.add_argument("--plan", default="docs/supervisor/tasks.json")
    args = parser.parse_args()
    if args.task and args.command not in ("retry", "recheck"):
        parser.error("A task ID is only accepted with retry or recheck")
    root = Path.cwd().resolve()
    path = root / ".supervisor/state.json"
    if args.command == "monitor":
        from monitor import inspect
        return inspect(root, root / args.plan)
    if args.command == "validate":
        from validate import validate
        return validate(root, root / args.plan)
    if args.command == "status":
        if not path.exists():
            print("Not started. Run 'plan' to inspect execution waves.")
        else:
            print(json.dumps(read(path), indent=2))
        return 0
    if args.command in ("pause", "resume", "cancel"):
        with locked(path.with_suffix(".lock")):
            state = read(path)
            owner = state.get("controller")
            info = process(owner["pid"]) if owner else None
            if not info or info["start"] != owner["start"] or state["mode"] not in ("running", "paused"):
                raise ValueError("No active supervisor; inspect status and use recover after its workers stop")
            request_id = len(state["requests"]) + 1
            state["requests"].append({"id": request_id, "action": args.command,
                                      "received_at": now(), "status": "pending"})
            save(path, state)
            print(f"Request {request_id}: {args.command}; use status to see acknowledgement")
        return 0
    plan, template, digest = load_plan(root, root / args.plan)
    if args.command == "plan":
        for number, batch in enumerate(waves(plan), 1):
            print(f"Wave {number}: {', '.join(batch)}")
        print(f"Maximum workers: {plan['max_workers']}; no processes started")
        return 0
    with locked(root / ".supervisor/run.lock", wait=False):
        with locked(path.with_suffix(".lock")):
            state = read(path) if path.exists() else fresh(plan, digest)
            if state["plan_hash"] != digest:
                raise ValueError("Plan changed: retain the old state as history before starting a new plan")
            if args.command == "recover":
                recover(state)
                save(path, state)
                return 0
            if args.command == "recheck":
                if any(t["status"] in ("running", "checking", "awaiting-check") for t in state["tasks"].values()):
                    raise ValueError("Recover the interrupted run first")
                record = state["tasks"].get(args.task)
                evidence = record.get("evidence", []) if record else []
                if not record or record["status"] != "failed" or not evidence or evidence[-1]["phase"] != "check":
                    raise ValueError("recheck requires a failed verification after a completed worker")
                worker = [e for e in evidence if e["phase"] == "worker" and e["exit_code"] == 0]
                if not worker:
                    raise ValueError("No completed worker evidence to verify")
                task = next(t for t in plan["tasks"] if t["id"] == args.task)
                if not all(state["tasks"][d]["status"] == "passed" for d in task["depends"]):
                    raise ValueError("Prerequisites must pass before rechecking")
                record["history"].append({k: v for k, v in record.items() if k != "history"})
                record.update(attempt=record["attempt"] + 1, status="awaiting-check", pid=None,
                              started_at=now(), ended_at=None, verified_at=None, error=None,
                              evidence=worker, verification_only=True)
                state.update(mode="running", controller={"pid": os.getpid(), "start": process(os.getpid())["start"]})
            elif args.command == "retry":
                if any(t["status"] in ("running", "checking", "awaiting-check") for t in state["tasks"].values()):
                    raise ValueError("Recover the interrupted run first")
                record = state["tasks"].get(args.task)
                if not record or record["status"] not in ("failed", "cancelled"):
                    raise ValueError("retry requires a failed or cancelled task ID")
                record["history"].append({k: v for k, v in record.items() if k != "history"})
                for key in ("worker_pid", "agent_pid", "agent", "process_start", "cli_identity",
                            "report", "log", "stderr", "command"):
                    record.pop(key, None)
                record.update(status="pending", pid=None, started_at=None, ended_at=None,
                              verified_at=None, error=None, evidence=[])
                state["mode"] = "idle"
            else:
                if not plan["worker"]:
                    raise ValueError("Set the plan's worker argument array before starting agents")
                if any(t["status"] in ("running", "checking", "awaiting-check") for t in state["tasks"].values()):
                    raise ValueError("Interrupted run: inspect worker groups and recover before restart")
                if state["mode"] == "finished":
                    raise ValueError("All tasks already passed; nothing to start")
                state.update(mode="running", controller={"pid": os.getpid(), "start": process(os.getpid())["start"]})
            save(path, state)
        return run(root, plan, template, path) if args.command in ("start", "recheck") else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"supervisor: {error}", file=sys.stderr)
        sys.exit(2)
