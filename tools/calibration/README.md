# Calibration target

From the project root, run `python3 tools/calibration/serve.py` and open
`http://127.0.0.1:8766/calibration-target.html`.

1. Match the on-screen reference against a real bank card or 100 mm ruler.
2. Enter the displayed reference length and width in the measurement app.
3. Select Wall for an upright display. Photograph the target; mark the four
   crosshair centres and all four outside corners of the checker border.
   Reference dimensions include that entire border. Align it manually: the
   current automatic detector assumes the older band pattern.
4. Compare the app's width, height and area with the page. Save the test record.

The default target is 23 x 15 cm, area 345 cm², reference 10 x 2 cm. An angled
view means moving the phone while keeping the target flat. The nonrectangle
has independently computed edge lengths, area and angles; its width/height
labels are means of opposite edges, not its bounding box.

Photo mode preserves scale. Fitting is an explicit dimension change, including
the reference. After fitting, update both reference values in the app. Browser
zoom/display changes invalidate confirmation; recheck after moving displays.
Print landscape at Actual size / 100%, then verify the reference with a ruler.

## Retained checks and app fixtures

- `node tools/calibration/check.cjs`: independent geometry assertions.
- `python3 tools/calibration/run-browser-check.py`: calibration, photo mode,
  result comparison, invalidation and mobile checks through Playwright CLI.
- `python3 tools/calibration/run-browser-check.py --export-fixtures`: render the
  actual SVG targets into `app/src/androidTest/assets/calibration-target/`, with
  JSON ground truth and synthetic pinhole metadata for Android flow tests.
- `python3 tools/calibration/check-engine.py`: compare both fixtures with the
  already-built production engine, including dimensions, area, diagonal, angles,
  solver selection and the nonrectangle confidence cap. Requires a current app
  build and the project's cached Kotlin stdlib; refuses stale source/bytecode.
  Exact commands, bytecode/fixture hashes and results remain in
  `.supervisor/calibration-target/engine-probe/`.

Browser commands use an existing Playwright CLI session named
`measure-calibration-check` open at the local URL. Browser screenshots are kept
in `output/playwright/`; exact check output is appended to
`.supervisor/calibration-target/`. The Android assets are retained in the repo.

Browser tests simulate scale confirmation; they do not calibrate a physical
monitor. Rendered Android fixtures test measurement/marking behavior, not lens
calibration, camera distortion, gravity sensor timing or real photographic
accuracy. A real phone capture remains a separate test.
