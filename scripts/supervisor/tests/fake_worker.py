"""Ordinary local process used by tests; never calls an agent or model API."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time

task = json.loads(sys.stdin.read())
task_id = task["id"]
print(f"worker {task_id}: stdout recorded", flush=True)
print(f"worker {task_id}: stderr recorded", file=sys.stderr, flush=True)
Path(f"started-{task_id}").write_text(str(os.getpid()))
Path(f"running-{task_id}").touch()
child = None
if task.get("child"):
    child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
    Path(f"child-{task_id}").write_text(str(child.pid))
deadline = time.monotonic() + task.get("duration", 0.1)
while time.monotonic() < deadline:
    Path(f"heartbeat-{task_id}").write_text(str(time.monotonic_ns()))
    time.sleep(0.02)
Path(f"running-{task_id}").unlink()
Path(f"finished-{task_id}").write_text(str(time.monotonic_ns()))
sys.exit(task.get("exit_code", 0))
