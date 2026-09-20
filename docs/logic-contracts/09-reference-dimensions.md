---
id: N09
type: work
spec_status: ready
priority: medium
finding: F9
build_status: built
build_agent: "dimensions09/1"
build_pid: 226642
build_pid_note: recorded
build_attempt: 1
build_started_at: "2026-09-19T13:45:10Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T13:51:50Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/dimensions09-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
tags: [logic-contracts]
---

# Reference dimensions

## Problem and evidence

`LengthInput.parseToMeters` rejects values less than or equal to zero, but not
NaN or infinity. `NaN`, `Infinity`, and `1e309` were accepted in the review;
using the resulting reference length caused measurement exceptions. Settings
persistence does not independently validate its inputs.

## Required changes

- Require finite positive numbers before and after conversion to metres.
  Reject overflow, underflow to zero, non-numeric text, zero, and negatives.
- Apply the same invariant at persistence and engine/profile boundaries;
  keyboard restrictions are not validation, and stored values can be invalid.
- Keep invalid text in the editor with a correction message. Do not persist it
  or silently use a different length while presenting it as accepted.
- Handle existing invalid persisted values explicitly: flag settings as requiring
  correction and prevent measurement until the user supplies valid dimensions.
  Do not replace an invalid known reference with a plausible default invisibly.
- Retain the intentional engine-only `width = 0` meaning "unknown width" for
  length-only callers. The app's current width field requires a positive finite
  value; this exception must not let invalid UI width text bypass validation.
- Keep metre storage independent of display units, preserving normal valid
  conversions between metres, centimetres, and decimal feet.

## Code and test touchpoints

- [LengthInput.kt](../../app/src/main/java/com/cocode/measureapp/core/LengthInput.kt)
- [LengthField.kt](../../app/src/main/java/com/cocode/measureapp/ui/LengthField.kt)
- [SettingsRepository.kt](../../app/src/main/java/com/cocode/measureapp/data/SettingsRepository.kt)
- [Model.kt](../../app/src/main/java/com/cocode/measureapp/geometry/Model.kt)
- [LengthInputTest.kt](../../app/src/test/java/com/cocode/measureapp/core/LengthInputTest.kt)
- [StickScaleTest.kt](../../app/src/test/java/com/cocode/measureapp/stick/StickScaleTest.kt)

## Incoming contracts

None. This node validates user-entered or persisted reference dimensions before
they become measurement inputs.

## Outgoing contracts

### C09 Valid settings

**Consumer:** [[05-measurement-failure-handling]].

Supply validated app settings, or an explicit requirement to correct the length
or width. The consumer must prevent measurement with invalid settings while
preserving the current image and marks. Invalid values are never formatted as
accepted settings or sent through a successful measurement path.

**Edge assertion:** enter or load each invalid value, then attempt measurement.
The app explains which dimension needs correction and does not crash. Replace
the value with a valid one and retry using the same image.

### C12 Metric profile

**Consumer:** [[03-solver-eligibility-confidence]].

Provide a finite strictly positive reference length in metres and either a
finite strictly positive known width, or the explicitly supported engine-only
unknown-width value zero. Negative, NaN, and infinite widths are always invalid.
The consumer must still guard arithmetic overflow in scale/measurement output;
valid finite inputs alone do not guarantee finite intermediate calculations.

**Edge assertion:** equivalent valid profiles entered in different units yield
the same metric results. Direct engine callers with invalid dimensions receive
a rejected outcome; a supported length-only caller with zero width still works.

## Acceptance checks

- Reject `NaN`, `Infinity`, `-Infinity`, `1e309`, `0`, `-1`, empty text, and a
  positive decimal that underflows to zero in metres.
- Accept 1 m, 100 cm, and 1 ft (0.3048 m) with conversion tolerance `1e-12` m.
- Test invalid persisted values for both length and width; no silent default
  substitution and no loss of valid unrelated settings.
- Test repository setters and the public engine entry point independently of
  the text field. Check a very large but finite input for controlled arithmetic
  failure rather than an exception or an infinite successful measurement.
- Verify positive known-width profiles and the explicit engine-only zero-width
  path; do not weaken the app's four-corner marker validation.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| dimensions09/1 | 226642 | 2026-09-19T13:45:10Z | 2026-09-19T14:01:42Z | passed |
