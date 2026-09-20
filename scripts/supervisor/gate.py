"""Do not execute a worker until its PID has been persisted by the supervisor."""
import os
from pathlib import Path
import sys
import time


def main():
    permit, owner, *command = sys.argv[1:]
    deadline = time.monotonic() + 30
    while os.getppid() == int(owner) and time.monotonic() < deadline:
        if Path(permit).exists():
            os.execvp(command[0], command)  # Same PID and process group as the recorded gate.
        time.sleep(0.02)
    raise SystemExit("Supervisor disappeared before authorizing this worker")


if __name__ == "__main__":
    main()
