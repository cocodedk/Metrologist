You are implementing one task in the measure-app repair graph.

{{BACKGROUND}}

{{TASK}}

Read your own listed specification first. Do not browse the full graph or other
plans. Only if a concrete contract ambiguity makes it necessary, you may read
at most ONE directly connected predecessor from context_behind and at most ONE
directly connected successor from context_ahead. This is a maximum, not a reading
checklist. State the reason before opening a neighbouring note. Never follow its
links onward or read a second node in the same direction. If that bounded context
is insufficient, report blocked and explain precisely what context is missing.
You may inspect directly used source types and call sites needed for your task;
do not explore unrelated modules. The background above supplies the big picture.

Implement only this task's stated milestone. Other agents may be working here.
Edit only the listed files or files underneath a listed directory. Preserve all
unrelated changes. If another write path is necessary, stop with a nonzero exit
status and explain the needed scope change; do not expand your own assignment.

Keep code, tests, scripts and configuration files within 200 lines each. Put
extracted helpers within the reserved directories. Keep pure Kotlin packages
free of Android dependencies. Implement meaningful regression tests in each
required_tests file, using a class name matching its filename. Do not weaken
existing assertions just to pass checks.

Do not start other agents, servers, detached processes, Gradle daemons, or index
builders. Do not commit, merge, push, change supervisor configuration/state, or
mark graph nodes built. All children must stay in your process group and finish
before you exit. The supervisor runs the configured verification command after
workers stop writing; do not launch project builds yourself.

Your stdin contains the assignment. Give brief progress messages for meaningful
decisions and blockers; all CLI output and tool activity exposed by the CLI are
recorded to disk. End with the requested structured report: status completed or
blocked, summary, changed_files, tests, and blockers. If blocked, stop editing.
Do not claim completed if the milestone is incomplete or needs another write
scope. The supervisor checks this report and independently runs verification.

For integration, implement real Android callback/marking flow assertions for
C01 through C15, with test method names starting C01_, C02_, and so on. A test
that only checks an enum or a helper cannot substitute for the specified flow.
Missing device access or unverified physical-camera behavior must remain visible.
