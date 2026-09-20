"""Inspect recorded worker activity and scope without launching or controlling processes."""
import json
from pathlib import Path

from journal import event
from state import overlaps, read


def activity(root, task, log):
    reads, writes, messages, calls = set(), set(), [], []
    provider_tools = {}
    partial_tool, partial_chars = None, 0
    if not log.exists():
        return reads, writes, messages, calls, partial_tool, partial_chars, provider_tools
    with log.open() as stream:
        for line in stream:
            try:
                row = json.loads(line)
            except ValueError:
                continue  # The final streaming line may still be arriving.
            item = row.get("event", {})
            if item.get("type") == "content_block_start":
                partial_tool = item.get("content_block", {}).get("name")
                partial_chars = 0
            elif item.get("type") == "content_block_stop":
                partial_tool = None
            elif item.get("type") == "content_block_delta":
                partial_chars += len(item.get("delta", {}).get("partial_json", ""))
            if row.get("type") != "assistant":
                continue
            for part in row.get("message", {}).get("content", []):
                if part.get("type") == "server_tool_use":
                    provider_tools[part["id"]] = dict(name=part.get("name"),
                        started_at=row.get("timestamp"), status="waiting", pid=None,
                        visibility="provider-managed; no local PID or model supplied")
                elif part.get("tool_use_id") in provider_tools:
                    entry = provider_tools[part["tool_use_id"]]
                    content = part.get("content", {})
                    entry.update(status="returned", ended_at=row.get("timestamp"),
                        result_type=content.get("type") if isinstance(content, dict) else part.get("type"))
                if part.get("type") == "text":
                    messages.append(part["text"])
                if part.get("type") != "tool_use":
                    continue
                name = part.get("name")
                value = part.get("input", {}).get("file_path")
                if not value:
                    continue
                path = (root / value).resolve()
                rel = str(path.relative_to(root)) if path.is_relative_to(root) else str(path)
                calls.append((name, rel))
                if name == "Read" and rel.startswith("docs/logic-contracts/"):
                    reads.add(rel)
                if name in ("Write", "Edit"):
                    writes.add(rel)
    return reads, writes, messages, calls, partial_tool, partial_chars, provider_tools


def inspect(root, plan_path):
    state = read(root / ".supervisor/state.json")
    plan = read(plan_path)
    violations, details = [], []
    print("mode:", state["mode"], "heartbeat:", state.get("heartbeat_at"))
    passed = [name for name, record in state["tasks"].items() if record["status"] == "passed"]
    print(f"passed ({len(passed)}/{len(state['tasks'])}):", ", ".join(passed))
    for task in plan["tasks"]:
        record = state["tasks"][task["id"]]
        worker = root / ".supervisor" / f"{task['id']}-{record['attempt']}-worker.log"
        worker_evidence = [e for e in record.get("evidence", []) if e["phase"] == "worker"]
        if record.get("verification_only") and worker_evidence:
            worker = root / worker_evidence[-1]["log"]
        reads, writes, messages, calls, partial_tool, partial_chars, provider_tools = activity(root, task, worker)
        paths = [str((root / p).resolve().relative_to(root)) for p in task["files"]]
        for name in writes:
            if not overlaps([name], paths):
                violations.append(task["id"] + ": undeclared write " + name)
            file = root / name
            if file.is_file() and file.suffix in (".kt", ".py", ".ts", ".js", ".json", ".kts"):
                if len(file.read_text().splitlines()) > 200:
                    violations.append(task["id"] + ": file exceeds 200 lines " + name)
        for direction in ("context_behind", "context_ahead"):
            if len(reads.intersection(task.get(direction, []))) > 1:
                violations.append(task["id"] + ": too many " + direction + " notes")
        allowed = set(task["specs"] + task.get("context_behind", []) + task.get("context_ahead", []))
        for name in sorted(reads - allowed):
            violations.append(task["id"] + ": unrelated note " + name)
        detail = dict(task=task["id"], status=record["status"], attempt=record["attempt"],
                      agent_pid=record.get("agent_pid"), started_at=record.get("started_at"),
                      ended_at=record.get("ended_at"), reads=sorted(reads), writes=sorted(writes),
                      provider_tools=provider_tools, process_pid=record.get("pid"),
                      verification_only=bool(record.get("verification_only")))
        details.append(detail)
        if record["status"] in ("passed", "pending"):
            continue
        print(task["id"], record["status"], "attempt", record["attempt"], "agent PID", record.get("agent_pid"))
        if record["status"] == "checking":
            print("  worker finished; current check PID:", record.get("pid"),
                  "verification-only retry:", bool(record.get("verification_only")))
        print("  started:", record.get("started_at"), "model:", record.get("cli_identity", {}).get("model"))
        if record.get("error"):
            print("  error:", record["error"][:260])
        print("  notes:", sorted(reads), "edited files:", len(writes))
        for message in messages[-2:]:
            print("  progress:", message[:400])
        print("  recent tools:", calls[-3:])
        for entry in provider_tools.values():
            print("  provider tool:", entry)
        if partial_tool:
            print("  receiving tool:", partial_tool, "argument characters:", partial_chars)
        evidence = record.get("evidence", [])
        failed_check = record["status"] == "failed" and bool(evidence) and evidence[-1].get("phase") == "check"
        if record["status"] == "checking" or failed_check:
            for label in ("log", "stderr"):
                file = root / record[label]
                if file.exists():
                    print("  " + label + ":", file.read_text()[-2000:])
    event(root / ".supervisor", "monitor", observer="supervisor-monitor", mode=state["mode"],
          tasks=details, violations=violations)
    print("Violations:", violations)
    return 1 if violations else 0
