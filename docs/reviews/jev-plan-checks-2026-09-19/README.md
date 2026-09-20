# Plan corrections and Jev evaluation — 2026-09-19

The nine implementation plans and their map have been revised. All remain `not-built`,
with no assigned build agent/PID or invented start/end times. No app source or tests changed.
The graph now has 15 contractual edges; C14 and C15 make shared ownership explicit.

The preceding single Fable High review reported no P0/P1. No second Fable pass was run.
It used one built-in advisor consultation within that session. This evaluation uses Jev directly.

## Review dispositions

| Finding | Plan change |
| --- | --- |
| Shared-file conflicts | Coordinator reserves actual write paths, including extracted files; overlapping writes are prohibited across nodes. |
| Shared outcome/result ownership | Node 05 owns the outcome schema (C14), result invalidation (C15/C11), and the MeasureApp state boundary. |
| Landscape/zero-vector tests | Node 07 explicitly replaces fixtures that encode old signs or a fake upright fallback. Existing pose-based requirements were retained. |
| Stick winding | Node 06 accepts both cyclic windings with preserved edge pairs; detector-style, reversed, shifted and crossed fixtures are specified. |
| Surface eligibility/noise floor | Nodes 03/04 require rectangle-normal consistency with selected surface when gravity is usable; explicit half-pixel and focal-error fixtures added. |
| Crashed owner | Contact interval, timeout, identity verification, partial-edit inspection and confirmed termination precede reclamation. |
| Lost control requests | Monotonic request/acknowledgement IDs and one metadata writer; workers cannot clear a newer request. |
| Delayed pause/cancel | Observe within 15 seconds, including command waits; acknowledge only when the requested stopping condition is achieved. |
| Blocked integration/retry | Coordinator assigns edge verification; a closed blocked attempt gets a new verification attempt and fresh evidence. |
| Timestamp clock domains | C02 declares elapsedRealtimeNanos; C01 preserves camera timestamp provenance; node 02 rejects incompatible/unknown timebases. |
| Calibration provenance | C03/C04 carry calibration quality/source, preserved through C07/C10 into diagnostics. |
| Old evidence on retry | Archive prior evidence, clear current evidence and revalidate against the integrated revision. |
| Read-only clock objection | A read-only clock or coordinator can supply real timestamps; being read-only does not prevent reading time. |

New frontmatter fields are `build_request_id`, `build_acknowledged_request_id`,
`build_heartbeat_at`, and `build_files`. Existing progress values were preserved.
This is still a manual coordination protocol, not implemented build automation.
While workers run, control requests go through the coordinator; direct concurrent YAML edits
are unsupported and require stopping/reconciling the workers.

The 5-degree surface-consistency tolerance and 100 ms maximum sample age are explicit
initial engineering choices, not claims of measured handset accuracy. Noisy positive
fixtures use a 2% dimension bound; exact synthetic oracles retain their strict tolerances.
They must be implemented and tested before a work node can become built.

## Is Jev useful here?

**Yes, for focused checks against stated rules. It is not an independent graph reviewer.**
It matched all 36 expected labels in this small evaluation, catching the seeded broken
rules and accepting the repairs. It also recognized two requirements already present
in the original plans, avoiding blanket rejection of the old text.

I selected the questions, evidence excerpts and expected labels before calling Jev.
The criteria and deliberate defects are authored by the same person who changed the plans.
These are 12 curated criteria across three related variants, not 36 independent real-world
samples or a held-out benchmark. Some checks could also be implemented as text assertions.
The result supports using Jev to triage phrasing and policy consistency; it does not measure
its ability to discover unknown bugs, prove geometry, or approve implementations.

## Method

- Direct local `jev.py` client from the jev-decisions skill; no Prime Agent adapter.
- Requested `~typesafe/jev-latest`; all responses resolved to `typesafe/jev-1.13-20260917`.
- Choice labels: `covered`, `gap`, `unclear`, with one atomic criterion per question.
- The old, revised and deliberately broken texts were submitted without variant labels
  or expected answers inside the API request. Question wording remained the same.
- Broken variants deliberately permit a prohibited behavior or remove a required guarantee;
  some also contradict another retained clause. They are evidence artifacts only, not plans.
- Requests and expected labels were frozen before the calls; no prompt tuning or retries.
- A preselected confidence threshold of 0.8 would send uncertain judgments for manual review.
  No model answer authorized an edit, build or irreversible action.

## Results

| Measure | Observed |
| --- | --- |
| Requests / judgments | 27 / 36 |
| Correct against authored expectations | 36 / 36 |
| Expected covered / gap | 14 / 22 |
| Confidence at least 0.8 | 30 / 36 |
| Below 0.8, requiring review | 6 / 36 |
| Confident incorrect answers | 0 in this sample |
| Lowest reported confidence | 0.21, old surface-policy ambiguity |
| Median client request time | 0.486 seconds |
| Total client time | 13.474 seconds |
| Largest input / input plus output | 2,371 / 2,523 tokens |
| Aggregate input / output | 26,029 / 1,437 tokens |
| OpenRouter reported cost | $0.001093218 |

An always-gap classifier would match 22/36 labels. Jev also classified all 14 covered
cases correctly. Six correct answers still fell below the confidence threshold: ambiguity
should therefore trigger inspection, not be treated as proof that a check failed.
Confidence here is the model's reported value, not a validated probability of correctness.

| Criterion | Old text | Revised text | Broken variant | Minimum confidence |
| --- | --- | --- | --- | --- |
| shared_files | gap | covered | gap | 0.68 |
| newer_request | gap | covered | gap | 0.60 |
| dead_worker | gap | covered | gap | 0.95 |
| poll_interval | gap | covered | gap | 0.94 |
| schema_owner | gap | covered | gap | 0.70 |
| both_windings | gap | covered | gap | 0.76 |
| provenance | gap | covered | gap | 0.85 |
| same_clock | gap | covered | gap | 0.96 |
| noise_fixture | gap | covered | gap | 0.84 |
| rectangle_surface | gap | covered | gap | 0.21 |
| pose_ground_truth | covered | covered | gap | 0.95 |
| downstream_dimensions | covered | covered | gap | 0.98 |

In the earlier broad Jev review, C03's edge assertion was flagged as missing consumer
coverage. This narrower question correctly recognized its recovered-dimensions assertion
in both versions. That is evidence of sensitivity to question framing, not a universal
improvement in the model. I have not concealed the earlier false flag.

## Deterministic verification

- Parsed YAML: all ten nodes have the same control fields and retain their initial build state.
- Graph validation: 15 unique contract definitions match the index and Mermaid edges;
  every consumer links to its producer definition; all 54 wiki links and local source links resolve.
- Hash comparison: all tracked repository files, including app source/tests, remain unchanged.
- An independent temporary geometric calculation checked feasibility of the half-pixel
  fixture: width error about 0.12%, height error about 0.23%, wall-normal discrepancy below
  0.13 degrees for either offset sign, using homogeneous directions and length-based scale.
  This is a mathematical sanity check, not an app implementation test or camera guarantee.
- No Android build, device test, implementation run or second Fable review was performed.

## Evidence

- [Exact inputs and predeclared expected labels](requests.jsonl)
- [Raw API responses and per-request timing](responses.jsonl)
- [Every scored judgment](judgments.jsonl)
- [Machine-readable summary](summary.json)
- [Frozen evaluation design and plan hashes](design.json)
- [Updated graph](../../logic-contracts/00-logic-map.md)

All inputs are plan excerpts. They contain no credentials or machine-specific paths.
The deliberately broken variants must not be copied into the live specifications.

## Primary references

The clock-domain rules were verified against Android's [sensor timestamp contract](https://developer.android.com/reference/android/hardware/SensorEvent#timestamp)
and [camera timestamp source contract](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_TIMESTAMP_SOURCE).
TypeSafe recommends focused inputs and warns about reasoning, numeric and context limitations
in its [Jev limitations](https://docs.typesafe.ai/model-jaggedness/jev-1.13).
