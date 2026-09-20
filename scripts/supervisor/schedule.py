"""Deterministic task selection in manifest order."""
import signal
import time

from runner import Runner
from state import acknowledge, locked, now, overlaps, read, relative, save


def eligible(plan, state):
    records = state["tasks"]
    return [t for t in plan["tasks"] if records[t["id"]]["status"] == "pending"
            and all(records[d]["status"] == "passed" for d in t["depends"])]


def candidates(plan, state):
    reserved = [t for t in plan["tasks"]
                if state["tasks"][t["id"]]["status"] in ("running", "checking", "awaiting-check")]
    result = []
    for task in eligible(plan, state):
        if len(reserved) >= plan["max_workers"]:
            break
        if not any(overlaps(task["files"], t["files"]) for t in reserved):
            result.append(task)
            reserved.append(task)
    return result


def advance(root, plan, state, runner):
    waiting = [t for t in plan["tasks"] if state["tasks"][t["id"]]["status"] == "awaiting-check"]
    # Checks see a quiet checkout. Gradle/build outputs are shared even for disjoint edits.
    if waiting or any(r["checking"] for r in runner.active.values()):
        if not runner.active:
            runner.launch(waiting[0], state, checking=True)
        return
    for task in candidates(plan, state):
        for value in task["files"]:
            if str(relative(root, value).relative_to(root)) != value:
                raise ValueError(f"Reserved path changed through a symlink: {value}")
        runner.launch(task, state)
    if not runner.active:
        state["mode"] = "finished" if all(r["status"] == "passed" for r in state["tasks"].values()) else "blocked"


def run(root, plan, template, state_path):
    runner = Runner(root, plan, template)
    stopping = False

    def stop(_signum, _frame):
        nonlocal stopping
        stopping = True

    previous = {sig: signal.signal(sig, stop) for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)}
    tick = time.monotonic()
    try:
        while not stopping:
            with locked(state_path.with_suffix(".lock")):
                state = read(state_path)
                current = time.monotonic()
                elapsed, tick = current - tick, current
                pending = [r for r in state["requests"] if r["status"] == "pending"]
                if pending and runner.control(state, pending[-1]["action"]):
                    acknowledge(state)
                if not pending and state["mode"] == "running":
                    runner.collect(state, elapsed)
                    advance(root, plan, state, runner)
                state["heartbeat_at"] = now()
                save(state_path, state)
                if state["mode"] in ("finished", "blocked", "cancelled"):
                    return 0 if state["mode"] == "finished" else 1
            time.sleep(plan["poll_seconds"])
        return 1
    finally:
        # Reconcile while holding the state lock: no control command can be lost.
        with locked(state_path.with_suffix(".lock")):
            state = read(state_path)
            runner.close(state)
            if state["mode"] not in ("finished", "blocked", "cancelled"):
                state["mode"] = "blocked"
            for request in state["requests"]:
                if request["status"] == "pending":
                    request.update(status="superseded", at=now(), reason="Run ended before performance")
            state.update(controller=None, heartbeat_at=now())
            save(state_path, state)
        for sig, handler in previous.items():
            signal.signal(sig, handler)


def waves(plan):
    state = {"tasks": {t["id"]: {"status": "pending"} for t in plan["tasks"]}}
    result = []
    while ready := candidates(plan, state):
        result.append([t["id"] for t in ready])
        for task in ready:
            state["tasks"][task["id"]]["status"] = "passed"
    return result
