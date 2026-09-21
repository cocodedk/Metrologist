# Illustrated guide and checker reference — 2026-09-20

The English/Persian website now has a user guide and a redesigned print page.
Both coloured ends are red. A 4 mm checker margin surrounds all four sides; the
full outside rectangle stays 200 × 40 mm. The black outline is drawn inside those
bounds. Cut outside that line, retaining the checker margin.

Place the centre of each red corner handle where two outside edges of the black
outline meet. The guide uses a whole-stick diagram, an enlarged corner and a
right/wrong comparison. The two yellow mid-edge dots are explicitly not corners.
Both guide languages use short instructions and name the green Object box and red
Stick box. The current detector assumes the old band pattern, so manual alignment
of the new reference is explained; automatic support is not claimed.

The guide describes published v0.0.1 and labels newer surface-mode behavior as
conditional. The website commit contains no pending application changes.

Validation: six pages' local links/anchors; SVG/XML parsing; English/Persian
320/390/1440 px layouts; four loaded marking illustrations; no horizontal page
overflow; one-page A4 landscape print PDFs; 200 × 40 mm browser print bounds and
100 mm ruler; visual review of mobile, desktop and print output. Print calibration
still requires a physical ruler because a printer may scale its output.

The calibration lab also renders the red-ended checker reference, preserving the
same ground-truth outside corners. Both rendered PNG fixtures were regenerated.
Independent geometry assertions and compiled-engine probes passed; the two Android
calibration flow tests passed. These are synthetic tests, not photographic
accuracy measurements.

Website evidence remains in `.supervisor/site-guide/` and `output/playwright/`.
Calibration artwork/probe evidence remains in `.supervisor/calibration-target/`;
Android rerun output is `.supervisor/fdroid/calibration-android-rerun.log`.

## Publication

Published website-only commit `b460b796be256cd34b8b3b03574c71bdffcfe0f6` after
explicit user approval. [Pages deployment](https://github.com/cocodedk/Metrologist/actions/runs/35491034265)
and [CI](https://github.com/cocodedk/Metrologist/actions/runs/35491034264) passed.
All 15 changed website files returned HTTP 200 and matched the commit byte-for-byte.
Both live guides loaded all four marking illustrations without horizontal overflow.
Live checks are retained in `.supervisor/site-guide/live-verification.json` and
`live-browser-check.log`. The public guide is available at
https://measure.cocode.dk/user-guide.html.
