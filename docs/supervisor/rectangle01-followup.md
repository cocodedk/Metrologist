# Rectangle retry: verified baseline and remaining defects

The coordinator ran the existing `rectangle01` check after attempt 1. Its required
regression class and Android assembly passed. This does not complete the live
engine integration, which is explicitly owned by the dependent `eligibility03`.

Review the existing candidate implementation and preserve the valid-infinity,
half-pixel and calibration cases. Make these focused corrections:

- `CameraIntrinsics(1e-7, 1e-7, 1000.0, 750.0)` is considered usable, but
  `inverseMatrix()` throws because the determinant is below `NORM_EPS`.
  `RectangleSolver.candidate` must return an explicit rejection for unusable
  numeric projection inputs. Cover this case and finite overflow cases with
  regression assertions. Handle expected numeric/domain failures narrowly;
  do not catch every throwable or turn coding defects into success.
- Add the missing fixture for `PROJECTION_UNUSABLE` (corner rays on incompatible
  depth sides). Keep exact valid geometry and existing rejection tests intact.
- The half-pixel test currently packages an accepted candidate as a scored
  `PlaneSolution` and passes the old selector. This is not evidence of the final
  eligibility rules. Keep the candidate-accuracy assertions, but name and document
  the test according to what it actually proves. Node 03 owns live selection.

Your milestone is the candidate producer, not its downstream consumer. Keep
consumer handoff notes in `summary`; use `blockers` only for something that
prevents this assigned milestone from completing. A completed report must have
an empty blockers array. Do not edit logs, state, or the previous report. The
supervisor will run the real check after you finish; report tests as not run by
you. You may extract supporting tests under the newly reserved rectangle test
directory, but invoke them from the required regression class so they execute.
