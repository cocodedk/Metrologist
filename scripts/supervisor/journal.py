"""Append-only event history and attempt snapshots, stored locally with the run."""
from datetime import datetime, timezone
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path


def event(directory, kind, **details):
    directory.mkdir(parents=True, exist_ok=True)
    item = {"at": datetime.now(timezone.utc).isoformat(), "event": kind, **details}
    with (directory / "events.jsonl").open("a") as handle:
        fcntl.flock(handle, fcntl.LOCK_EX)
        handle.write(json.dumps(item, sort_keys=True) + "\n")
        handle.flush()
        os.fsync(handle.fileno())


def snapshot(root, task, destination):
    files = {}
    for value in task["files"]:
        path = root / value
        for file in sorted(path.rglob("*")) if path.is_dir() else [path]:
            if file.is_file() and not file.is_symlink():
                data = file.read_bytes()
                files[str(file.relative_to(root))] = {"sha256": hashlib.sha256(data).hexdigest(),
                                                      "base64": base64.b64encode(data).decode()}
    with destination.open("x") as handle:
        json.dump(files, handle, indent=2)
        handle.flush()
        os.fsync(handle.fileno())


def report(path):
    """Require a real structured completion from a Claude streaming transcript."""
    last = None
    for line in path.read_text().splitlines():
        try:
            item = json.loads(line)
        except ValueError:
            continue
        if item.get("type") == "result":
            last = item
    if not last or last.get("is_error"):
        raise ValueError("Agent transcript has no successful final result")
    value = last.get("structured_output", {})
    if value.get("status") != "completed" or value.get("blockers"):
        raise ValueError("Agent reported blocked/incomplete: " + str(value or last.get("result", "")))
    return value


def identity(path):
    with path.open() as handle:
        beginning = handle.read(65536)
    for line in beginning.splitlines():
        try:
            item = json.loads(line)
        except ValueError:
            continue
        if item.get("type") == "system" and item.get("subtype") == "init":
            return {"model": item.get("model"), "session_id": item.get("session_id")}
    return None
