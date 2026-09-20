# One batched Jev check of the supervisor

One call asked six questions about the same source snapshot: 6,844 input tokens,
254 output tokens, reported cost $0.000287448. The request selected
`~typesafe/jev-latest`; the response identified `typesafe/jev-1.13-20260917`.
No second call was made. The 32k context limit was not approached.

The [request](request.json) preserves the exact pre-fix code and questions.
[Expected labels](expected.json) were written before the call; the
[response](response.json) and [client result](result.txt) preserve its output.

| Question | Inspection/test expectation | Jev answer | Confidence |
| --- | --- | --- | --- |
| Old pending requests cannot affect a retry | gap | gap | 0.31 |
| File aliases cannot bypass reservations | gap | satisfied | 0.60 |
| Worker exit alone cannot pass a task | satisfied | satisfied | 0.93 |
| Verification waits for a quiet checkout | satisfied | satisfied | 0.99 |
| Recovery refuses a live recorded group | satisfied | satisfied | 0.92 |
| Paused time is excluded from timeout | satisfied | satisfied | 0.27 |

Jev matched five of six inspection labels. Three answers fell below the
preselected 0.8 confidence gate, including its incorrect alias answer. The client
therefore exited 3. This was not treated as approval or as a transport failure.

Both gaps had already been identified by source inspection. Dedicated regression
tests subsequently reproduced them on this snapshot, then passed after fixing
path canonicalization and superseding unfinished requests when a run ends.
The other control requirements are covered by executable process tests.

This is a targeted check against human-selected requirements, not an independent
bug-discovery benchmark. Jev missed a real bug even with the relevant source in
context. It can provide a second opinion; source inspection and actual tests
remain authoritative. Missing context invalidates an answer regardless of its
confidence, and broader context alone does not guarantee a correct decision.
