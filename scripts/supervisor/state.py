"""Small Linux process and JSON-state helpers; no third-party dependencies."""
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
from datetime import datetime, timezone
from journal import event


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def process(pid):
    try:
        fields = Path(f"/proc/{pid}/stat").read_text().rsplit(") ", 1)[1].split()
        return {"state": fields[0], "group": int(fields[2]), "start": fields[19]}
    except (FileNotFoundError, ProcessLookupError, PermissionError):
        return None


def members(group):
    return {int(p.name): info for p in Path("/proc").iterdir()
            if p.name.isdigit() and (info := process(p.name))
            and info["group"] == group and info["state"] not in ("Z", "X")}


def signal_group(pid, sig):
    try:
        os.killpg(pid, sig)
    except ProcessLookupError:
        pass


@contextlib.contextmanager
def locked(path, wait=True):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a") as handle:
        fcntl.flock(handle, fcntl.LOCK_EX | (0 if wait else fcntl.LOCK_NB))
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def read(path):
    return json.loads(path.read_text())


def save(path, state):
    old = read(path) if path.exists() else {}
    temp = path.with_suffix(".tmp")
    with temp.open("w") as handle:
        json.dump(state, handle, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temp, path)
    stable = lambda value: {k: v for k, v in value.items() if k != "heartbeat_at"}
    if stable(old) != stable(state):
        event(path.parent, "state", state=state)


def overlaps(left, right):
    return any(a.rstrip("/") == b.rstrip("/")
               or a.startswith(b.rstrip("/") + "/")
               or b.startswith(a.rstrip("/") + "/") for a in left for b in right)


def relative(root, value):
    path = Path(value)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError(f"Expected project-relative path: {value}")
    target = (root / path).resolve()
    if not target.is_relative_to(root) or path.parts[0] in (".git", ".supervisor"):
        raise ValueError(f"Path outside permitted project scope: {value}")
    return target


def load_plan(root, path):
    plan = read(path)
    tasks = plan["tasks"]
    ids = [task["id"] for task in tasks]
    if not tasks or len(set(ids)) != len(ids):
        raise ValueError("Task IDs must be unique and the plan nonempty")
    if any(not isinstance(i, str) or not i.replace("-", "").isalnum() for i in ids):
        raise ValueError("Task IDs must contain only letters, numbers and hyphens")
    workers = plan.get("max_workers", 3)
    interval = plan.get("poll_seconds", 2)
    timeout = plan.get("timeout_seconds", 1800)
    if type(workers) is not int or workers < 1:
        raise ValueError("max_workers must be a positive integer")
    if not 0.05 <= interval <= 15 or not 0 < timeout < float("inf"):
        raise ValueError("Invalid polling interval or timeout")
    plan.update(max_workers=workers, poll_seconds=interval, timeout_seconds=timeout)
    template = relative(root, plan["prompt"]).read_text()
    if plan.get("background"):
        template = template.replace("{{BACKGROUND}}", relative(root, plan["background"]).read_text())
    if template.count("{{TASK}}") != 1:
        raise ValueError("Prompt must contain exactly one {{TASK}} placeholder")
    for command in [plan["worker"], *(t["check"] for t in tasks)]:
        if not isinstance(command, list) or any(not isinstance(a, str) for a in command):
            raise ValueError("Commands must be argument arrays, never shell strings")
    for task in tasks:
        if not task["check"] or not task["files"]:
            raise ValueError(f"{task['id']} needs files and a verification command")
        if not set(task["depends"]) <= set(ids):
            raise ValueError(f"Unknown prerequisite for {task['id']}")
        for value in task["files"] + task.get("required_tests", []):
            relative(root, value)
        # Compare actual paths: 'src/./file' and an internal symlink are aliases.
        task["files"] = [str(relative(root, value).relative_to(root)) for value in task["files"]]
        for value in task["specs"]:
            relative(root, value).read_text()
        for value in task.get("context_behind", []) + task.get("context_ahead", []):
            relative(root, value).read_text()
    done = set()
    while len(done) < len(tasks):
        ready = {t["id"] for t in tasks if set(t["depends"]) <= done} - done
        if not ready:
            raise ValueError("Execution dependencies contain a cycle")
        done |= ready
    digest = hashlib.sha256((json.dumps(plan, sort_keys=True) + template).encode()).hexdigest()
    return plan, template, digest


def fresh(plan, digest):
    return {"plan_hash": digest, "mode": "idle", "controller": None,
            "requests": [], "heartbeat_at": None,
            "tasks": {t["id"]: {"status": "pending", "attempt": 0, "history": [], "files": t["files"],
                                  "node": t.get("node"), "depends": t["depends"],
                                  "pid": None, "started_at": None, "ended_at": None,
                                  "verified_at": None, "error": None, "evidence": []}
                      for t in plan["tasks"]}}


def acknowledge(state):
    pending = [r for r in state["requests"] if r["status"] == "pending"]
    for request in pending:
        request.update(status="done" if request is pending[-1] else "superseded", at=now())


def recover(state):
    active = [t for t in state["tasks"].values() if t["status"] in ("running", "checking", "awaiting-check")]
    for task in active:
        if task.get("pid") and members(task["pid"]):
            raise ValueError(f"Worker group {task['pid']} still exists; inspect and stop it first")
    for task in active:
        task.update(status="failed", ended_at=now(), error="Supervisor stopped; inspect partial work")
    state.update(mode="blocked", controller=None, heartbeat_at=now())
    for request in state["requests"]:
        if request["status"] == "pending":
            request["status"] = "superseded"
