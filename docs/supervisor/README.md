# Small graph supervisor

Run from the repository root. Python 3.10+ on Linux is sufficient; no packages,
database, service or model SDK are needed. `scripts/supervise.py` is the single
entry point. Small helpers keep each code file below the project's 200-line cap.

```sh
python3 scripts/supervise.py plan
python3 scripts/supervise.py start
```

`plan` validates the task graph and prints illustrative waves without launching
anything. `start` runs the loop in the foreground. Keep its terminal open.
From another terminal in the same project:

```sh
python3 scripts/supervise.py status
python3 scripts/supervise.py monitor
python3 scripts/supervise.py recheck TASK_ID
python3 scripts/supervise.py validate
python3 scripts/supervise.py pause
python3 scripts/supervise.py resume
python3 scripts/supervise.py cancel
```

The worker is configured as the user's `cyp` Fish function with **Opus, high
effort**. The Fish launcher forwards arguments without interpolating task text.
It receives [worker-prompt.md](worker-prompt.md), the shared
[background](background.md), and the task on stdin. CLI streaming output includes
tool activity and the final structured report. Only Read, Glob, Grep, Edit and
Write tools are enabled; the supervisor owns tests and child-agent scheduling.

Workers read their own specification. Only when necessary may they read at most
one listed predecessor and one listed successor, stating why. They cannot follow
those neighbours' links further. The final integration worker has one self-contained
[integration brief](integration-brief.md), so it need not traverse the whole graph.

## What the loop does

Every two seconds it reads control requests, polls workers, checks finished work,
and launches eligible tasks up to `max_workers`. Manifest order breaks ties.
Independent branches can run together. Dependencies must pass before a join
starts. File and directory reservations prevent declared write scopes overlapping.
Reservations remain held through verification. Checks run one at a time, after
all active workers finish, because they compile a shared checkout.

The task graph splits node 05 into its shared interface and final recovery
integration. This removes the contract graph's execution cycle. The first task
runs alone; marker validation, dimensions and gravity can then run in parallel.
Later capture/frame and surface branches join before solver eligibility.
Actual scheduling depends on completion events and file reservations; printed
waves are an illustration, not a promise that all tasks finish simultaneously.

Each task has explicit dependencies, specifications, reserved paths, required
regression files, and a verification command. The project checker requires new
tests to exist and actually execute, then checks the Android build. The final
task also requires all 15 edge assertions in Android instrumentation results.
Missing tests/device access or failing commands block completion.

For Android integration, select one device by writing its ADB serial to the local
`.supervisor/android-serial` file (or set `ANDROID_SERIAL` before starting the loop).
The checker passes this selection to Gradle and refuses an unspecified target.
This prevents a connected phone or another project's emulator from accidentally
receiving the test installation. Device startup and shutdown belong to the coordinator.

To use a separate instance of an existing AVD, run
`python3 scripts/supervisor/emulator.py AVD_NAME --port 5556` in a foreground
terminal. The helper opens the AVD read-only, records its PID and full output in
`.supervisor/devices`, and selects its serial for integration. Ctrl-C stops its
owned emulator. It refuses an occupied port and does not stop other emulators.
If an existing AVD is locked by a writable instance, use `--image PACKAGE_ID`
with a new AVD name. This creates a fresh AVD under `.supervisor/devices` from
an already installed SDK image; it does not copy another emulator's live disk.
Without `avdmanager`, use `--template EXISTING_AVD_NAME`: this reuses only its
hardware configuration and installed system image, with fresh project-local data.

## Reusable tools and retained evidence

`monitor` gives a compact progress report, agent PID/model and recent tool activity.
It audits recorded file edits and node reads against each assignment, reports
unfinished streamed tool output, and appends its observations to `events.jsonl`.
Its implementation is [monitor.py](../../scripts/supervisor/monitor.py).

`recheck TASK_ID` retries a failed verification after a completed worker. It
archives the failed attempt, allocates fresh check logs and reruns the real
gate without launching another agent. It cannot bypass a failed worker or
unfinished prerequisites. The old worker identity remains historical; the new
attempt is marked `verification_only` in the state record.
Before another check overwrites Gradle's report directories, preserve their
contents with `python3 scripts/supervisor/reports.py TASK_ID`. It records copies,
source modification times and verified SHA-256 hashes in `.supervisor/reports/`.
Snapshots after an early failure can include older reports; they are not pass claims.
After reviewing a fully passing run, use
`python3 scripts/supervisor/reconcile.py --reviewed` to record it in the graph.
This requires finished tasks, fresh passing Android/JVM/lint reports and app
sources unchanged since verification began. It preserves the previous graph,
state and source hashes under `.supervisor/graph-records/`, writes a run report
and records historical worker identities in each node. It does not replace live
supervisor controls or perform the coordinator's acceptance review.
Gradle checks use a project-local daemon registry at `.supervisor/gradle-daemons/`
so a global `gradle --stop` from another project does not stop this run's
single-use daemon. Dependency caches remain shared as before.

The final integration brief also consumes the retained calibration-page PNG/JSON
fixtures in `app/src/androidTest/assets/calibration-target/`. For an independent
numeric check against the current compiled app, run
`python3 tools/calibration/check-engine.py`; its exact output stays under
`.supervisor/calibration-target/engine-probe/`. See
[the calibration guide](../../tools/calibration/README.md) for browser and
physical-screen checks and their different evidence limits.

The monitor also records provider-side tool calls, including `advisor`, with
their observed start/return timestamps. These are remote operations: the CLI
does not supply a separate PID or model, and advisor results can be encrypted
(`advisor_redacted_result`). The raw stream is retained, but encrypted content
is not a readable review. Local `--disallowedTools Advisor` did not disable this
server-side tool in the observed run. The integration brief explicitly tells
the worker not to invoke it. Independent coordinator checks remain required.

`validate` checks all node links, contract definitions, consumer backlinks, the
edge table and diagram, and execution-plan validity. It also runs the independent
half-pixel geometry oracle. Results are saved in `.supervisor/validation.json`
and the event history. Implementations: [validate.py](../../scripts/supervisor/validate.py)
and [fixture.py](../../scripts/supervisor/fixture.py). This checks the specifications;
it does not mark implementation milestones passed or substitute for app tests.

Reusable Graph-Loop tools belong in `scripts/supervisor`, with commands exposed
through `scripts/supervise.py`. Durable run evidence belongs in `.supervisor`;
it must not depend on temporary-directory files. Earlier review logs, prompts,
fixtures, adapters and one-off scripts have been preserved under
`.supervisor/archive/2026-09-19-tmp-evidence`, with a SHA-256 import manifest.
Credentials were excluded. Historical scripts are evidence, not active entry
points; the two reviewed plan amendments remain under `.supervisor/revisions`.
These local archives are ignored by Git, retained on disk, and never automatically
deleted by the supervisor. Git commits do not back up this runtime history.

## State and controls

`.supervisor/state.json` contains task status, unique agent/attempt identity,
worker PID, current process PID (worker or verifier), start/end/verification
times, evidence, request acknowledgements, and previous attempts. Prompts and
stdout/stderr logs sit beside it. This local directory is ignored by Git.
An OS lock permits only one supervisor per checkout; atomic JSON replacement
and a short state lock serialize control requests and updates.

## Recorded logs

Everything exposed by the CLI is recorded on disk, not only displayed in a
terminal. `events.jsonl` is an append-only, flushed event history of state changes,
requests, process launches and exits. Each attempt has separate stdout/stderr
logs, its exact prompt, a snapshot of its own specification, and before/after
copies of files within its reserved scope. Attempt filenames are exclusive:
retries cannot overwrite earlier evidence. The process gate starts work only
after its PID and launch event have been saved.

Worker logs use Claude's verbose stream-JSON output, including partial messages,
tool calls/results, reported model/session identity, and the final result. A zero
CLI exit code without a structured `completed` result cannot pass. Check stdout
and stderr have their own logs. Snapshots encode file bytes as base64 with SHA-256
hashes. Hidden model internals or output the provider does not expose cannot be
recorded. These are local logs and are not committed or automatically deleted.

```sh
tail -f .supervisor/events.jsonl
tail -f .supervisor/schema05-1-worker.log
```

- **Pause:** sends SIGSTOP to the owned process groups; acknowledges only when
  their remaining processes are stopped. No new tasks or checks start. File
  reservations stay held. This can leave partially written files until resumed.
- **Resume:** sends SIGCONT and continues the same attempt. Paused time does not
  count toward the task timeout.
- **Cancel:** kills the owned process groups, waits for their exit, and retains
  partial files and logs for inspection. It never rolls back your checkout.
- **Timeout/failure:** fails the task, blocks its dependents, and allows unrelated
  branches to finish. There are no automatic retries.
- **Ctrl-C/TERM/HUP:** stops the loop and cleans up its workers. A hard kill or
  machine crash requires recovery; the next start refuses to adopt old workers.

Control commands submit numbered requests. `status` shows whether each was
performed or superseded by a newer request. A pending pause cannot erase a later
cancel. The loop acknowledges only after performing the latest requested action.

After the loop exits, inspect failed work, then explicitly retry:

```sh
python3 scripts/supervise.py retry markers06
python3 scripts/supervise.py start
```

After an abnormal supervisor loss, inspect the recorded process groups and stop
any surviving workers first. `recover` refuses while their groups remain alive;
it does not signal an old PID whose identity might have changed.

```sh
python3 scripts/supervise.py recover
python3 scripts/supervise.py retry markers06
python3 scripts/supervise.py start
```

Plan/prompt edits during a run are unsupported. Stop all workers first. If the
plan needs changing, retain the old `.supervisor` directory as run history before
starting with a fresh state. Do not do this while old workers could still write.

A coordinator can preserve passed milestones for a narrowly reviewed amendment
to unfinished assignments: acquire the run/state locks, verify no owned groups
remain, archive the old plan and state, and verify passed assignments, dependencies
and checks are unchanged before recording the new hash. Archive a failed attempt
through `retry` before assigning its new file list. The first real run used this
procedure for helpers extracted into new files; `.supervisor/revisions/1/` and
the `plan-amendment` event preserve both versions and the added review context.
This is a recorded coordinator action, not an automatic scheduler decision.

## Deliberate limits

This is a cooperative local runner, not a filesystem security sandbox. Reservations
prevent conflicting assignments; worker prompts require respecting those paths.
Use an appropriately restricted CLI configuration and inspect diffs. Workers that
detach themselves from the owned process group violate the runner contract.

Runtime task state lives in JSON. The Markdown graph remains the implementation
specification; its frontmatter is not a live duplicate of the JSON. A task passing
its configured check is a milestone, not proof that an entire graph node is built.
Review the combined changes and integration evidence before updating graph nodes
to `built`. This avoids declaring local work complete while edge checks remain.

Jev is optional advice during human/agent review. Batch questions about the same
evidence into one call. Include the actual relevant source, requirements and
runtime evidence: Jev cannot inspect files or retrieve missing context. An answer
without sufficient context is unusable even with high reported confidence. It
does not schedule workers, change state, release file reservations, or replace
executable verification.

## Test the supervisor without model calls

```sh
python3 -m unittest discover -s scripts/supervisor/tests -v
```

These tests use temporary projects and ordinary Python child processes. They do
not launch agents, build the Android app, or change the graph's build status.
