# Project DONE

Archive of completed Metrologist tasks. Each block is the original task with
`status: completed` and a `finished` timestamp appended, moved here from
`PROJECT-TODO.md` (see the `ptodo` skill). Newest at the bottom.

---
id: 2026-06-14-app-logo-icon
title: "Add an app logo / launcher icon (stick motif)"
status: completed
finished: 2026-06-14T11:39:54+02:00
priority: medium
area: ux
created: 2026-06-14
source: "user request"
---

## Context

The app still ships the default Android Studio launcher icon. Metrologist needs
its own logo / adaptive launcher icon that fits its measurement identity. The
red-white-red-white reference stick is the product's strongest visual motif —
it's already used as the website favicon (`website/favicon.svg`) and across the
Pages site — so a stick-based mark would be recognisable and on-brand. The
favicon can be the design source so the app and site share one identity.

## Acceptance

- A custom adaptive launcher icon (mipmap-anydpi-v26 foreground + background,
  plus density fallbacks) replaces the default, built around the red-white stick
  motif.
- Reads clearly at small sizes (48dp) and on light/dark wallpapers; a monochrome
  themed-icon variant is provided where feasible.
- Aligns with `website/favicon.svg` so the app and the site share one mark.
- App display name stays "Metrologist".

---
id: 2026-06-14-stick-length-settings
title: "Stick dimensions: adjust length & width, use both in the measurement"
status: completed
finished: 2026-06-14T11:39:54+02:00
priority: medium
area: ux
created: 2026-06-14
source: "user request"
---

## Context

The red-white-red-white stick has two known physical dimensions: its length and
its width. Today only the length is configurable and only the length is used by
the engine (it fixes scale along the stick's axis). This task makes BOTH the
length and the width adjustable in Settings (unit-aware, validated) AND uses both
in the measurement calculation. The marking flow already draws the stick as a
4-corner box (`StickBox`), so the width is available from the box's short edges —
the engine just needs to use it: the long edges calibrate against the known
length, the short edges against the known width, giving a second, perpendicular
scale reference that improves accuracy and catches skew. The design spec deferred
configurable / multi-profile sticks to "later" — saved profiles (length + width)
are a good stretch goal.

## Acceptance

- Settings let the user set BOTH stick length and stick width, each in the chosen
  unit (m / cm / ft-in) with live conversion; non-positive or unparseable values
  are rejected with a clear inline message (no crash, no silent 0).
- The measurement calculation uses BOTH dimensions — length along the stick axis
  and width across it (from the `StickBox` long/short edges) — combined into the
  scale (document how, e.g. least-squares / averaged) so width is never ignored.
- Both values persist (DataStore), are shown prominently, and feed the engine;
  the pure-Kotlin engine change keeps 100% test coverage (synthetic-scene tests
  for the two-dimension scale).
- Quick presets for common stick sizes; stretch: saved stick profiles
  (length + width) that can be switched quickly.

---
id: 2026-06-14-printable-stick
title: "Printable reference stick on the website"
status: completed
finished: 2026-06-14T11:39:54+02:00
priority: medium
area: frontend
created: 2026-06-14
source: "user request"
---

## Context

Not everyone has a physical red-white-red-white stick. The Pages site (EN + FA)
should offer a printable reference stick at a precisely known real size (length
and width) so a user can print it at actual size, mount it on something rigid,
and use it as the measurement reference. It must state its exact printed
dimensions so they match what the user enters in the app's stick settings, and
include print guidance to avoid scaling. This complements the on-screen
calibration target already in `tools/calibration-target.html`.

## Acceptance

- The website (`website/index.html` + `website/fa/index.html`) links to a
  printable stick — a print-friendly HTML page and/or a downloadable PDF/SVG —
  showing a red-white-red-white stick at exact, stated dimensions (length + width
  in cm and inches).
- Print page fits A4 and Letter, uses `@media print` CSS, instructs "print at
  100% / actual size (no fit-to-page)", and includes a short ruler/calibration
  mark to confirm the print came out at true scale.
- The stated dimensions match what the user enters in the app's stick settings
  (length + width).
- Vivid pure red / white bands for reliable detection; available on both the EN
  and FA pages.
