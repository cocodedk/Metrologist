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

---
id: 2026-06-14-splash-screen
title: "Add a branded splash screen"
status: completed
finished: 2026-06-14T11:50:09+02:00
priority: medium
area: ux
created: 2026-06-14
source: "user request"
---

## Context

The app cold-starts straight into the capture screen with no branded moment. A
clean splash screen should show the Metrologist mark briefly on launch, reusing
the stick/logo motif from task `2026-06-14-app-logo-icon` so launch, icon, and
site all share one identity. Use the AndroidX `core-splashscreen` API (native on
Android 12+, backported below). Keep it fast — a splash that lingers is worse
than none.

## Acceptance

- Splash implemented with `androidx.core:core-splashscreen` (`installSplashScreen`
  in `MainActivity`), themed with the brand colors and the stick/logo motif.
- Native on Android 12+ and graceful on minSdk 24 via the compat library.
- No artificial delay — dismisses as soon as the first frame is ready (optional
  brief hold only while content loads).
- Visual identity matches the launcher icon and the website (shared stick mark).

---
id: 2026-06-14-in-app-user-manual
title: "Add a built-in user manual / help"
status: completed
finished: 2026-06-14T11:50:09+02:00
priority: medium
area: ux
created: 2026-06-14
source: "user request"
---

## Context

A first-time user has no in-app guidance and won't know the essentials: that a
known-length red-white-red-white stick is required, that it must lie on the same
flat plane as the surface, how to mark the corners and stick ends, or how to read
the confidence/accuracy. A built-in, offline user manual (a Help screen reachable
from the app, plus an optional first-run walkthrough) should explain the workflow
with simple diagrams. Keep it concise and consistent with the "How it works"
section on the website so the app and site tell the same story.

## Acceptance

- A Help / Manual screen reachable in-app (e.g. a "?" in the top bar or from
  Settings), covering: the stick (known length, flat on the same surface),
  capturing at a moderate angle, marking 4 corners + 2 stick ends with
  zoom/magnifier, entering stick length + units, and reading
  width/height/area/diagonal + the confidence/caveats.
- Content is bundled (offline), concise, with at least simple illustrative
  diagrams or annotated screenshots.
- Optional: a short first-run walkthrough that links into the manual.
- Mirrors the website's "How it works" content for consistency.

---
id: 2026-06-14-wcag-accessibility-audit
title: "Run a WCAG accessibility audit on the website"
status: completed
finished: 2026-06-14T12:09:14+02:00
priority: medium
area: frontend
created: 2026-06-14
source: "user request"
---

## Context

The GitHub Pages site (EN + FA) hasn't been checked for accessibility. Run the
`/wcag-accessibility-audit` skill against the website pages and fix the findings.
Watch the areas that were set up but not verified: colour contrast (dark theme +
amber accents), tap-target sizes, focus states, keyboard navigation, semantic
landmarks, alt text, language attributes, and the Persian RTL layout.

## Acceptance

- `/wcag-accessibility-audit` run against the EN and FA Pages (and the
  printable / calibration pages), producing a findings report with WCAG
  criteria, severity, and conformance level (A / AA).
- High and important findings fixed — contrast, visible focus, semantic
  structure, alt text, keyboard access, `lang`/`dir`, and RTL.
- Site re-checked to confirm WCAG 2.1 AA where practical; any remaining gaps
  documented.

## Resolution

Audited the EN+FA pages, the print-stick pages, and the calibration tool against WCAG 2.2 AA. Fixed the high/important findings: raised low-contrast `--text-dim` text (hero eyebrow, footer, accuracy + dims labels) to `--text-secondary` and lightened `--green-read` to #55C98A; gave the lang switch a 3:1 border and a real 44px height; added an explicit `h2` type rule and promoted feature `h4`→`h3` for correct heading order; added a `prefers-reduced-motion` block (incl. revealing `.reveal` content); removed redundant `role=contentinfo` and labelled the footer Download link. Calibration tool: removed `maximum-scale=1`, added `aria-live` to the live readout, guarded the `h` shortcut against form fields, made the panel title an `h2`, gave the card an `img` role + label, relabelled the range input, and moved focus after calibration.

---
id: 2026-06-14-design-for-ai-exam
title: "Run the design-for-ai design exam on the UI"
status: completed
finished: 2026-06-14T12:09:14+02:00
priority: medium
area: frontend
created: 2026-06-14
source: "user request"
---

## Context

Run the `/design-for-ai:exam` skill — a theory-backed visual-design audit that
finds what's wrong and explains why — against the project's user-facing surfaces:
the GitHub Pages site (EN + FA) and the app's Compose screens. Use it to catch
design problems (typography scale, colour/contrast, spacing rhythm, visual
hierarchy, consistency, brand) and then act on the findings.

## Acceptance

- `/design-for-ai:exam` run against the website and the app UI, producing a
  critique that names the design principle each issue violates and why.
- Actionable findings addressed across typography, colour, spacing, hierarchy,
  and consistency.
- Changes keep the stick / measurement identity coherent across the app, the
  launcher icon, and the site.

## Resolution

Ran the design-for-ai exam on the site and the Compose app. Headline fix: the app shipped the default Material 3 purple theme with zero brand coherence — replaced `Color.kt`/`Theme.kt` with the brand palette (StaffRed primary, Amber secondary, dark instrument surfaces), disabled dynamic colour, and expanded `Type.kt` into a full monospace-for-data scale. ResultsScreen now reads like an instrument (amber `headlineMedium` values over dim labels, confidence coloured by band). CameraScreen got a real button hierarchy (prominent Capture vs outlined Settings/Help). MarkScreen overlays use brand colours with on-canvas Object/Stick labels. Website: explicit `h2` scale, GitHub CTA demoted to a `.btn-tertiary` text link, and the contrast fixes above — keeping the red-white stick identity coherent across site, icon, and app.

---
id: 2026-06-14-nielsen-heuristics-audit
title: "Run a Nielsen heuristics usability audit"
status: completed
finished: 2026-06-14T12:09:14+02:00
priority: medium
area: ux
created: 2026-06-14
source: "user request"
---

## Context

Run the `/nielsen-heuristics-audit` skill (Jakob Nielsen's 10 usability
heuristics) against the app flow (capture → mark → results → settings) and the
website, then act on the findings. The marking flow is the highest-stakes
surface: watch learnability, visibility of system status, error
prevention/recovery (overlapping corners, low confidence, missing stick length),
recognition over recall, and consistency.

## Acceptance

- `/nielsen-heuristics-audit` run against the app screens and the website,
  producing a report mapped to the 10 heuristics with severity ratings.
- High-severity usability issues fixed — status visibility, error
  prevention/recovery, consistency, recognition over recall, and help.
- Findings re-checked; remaining gaps documented.

## Resolution

Ran the 10-heuristic audit over capture→mark→results→settings and the site, then fixed the high-severity issues. Visibility/feedback: Capture disables with a 'Capturing…' state; MarkScreen shows 'Detecting stick…' while auto-detect runs. Recognition: Object/Stick labels drawn on the two boxes. Error prevention/control: a ~60dp hit-test threshold means a single finger away from a handle pans instead of grabbing a corner; a Re-mark path returns from Results to the marked photo; the Method row shows only for the tilt-sensor fallback. Consistency/recovery: Settings gained a back-nav icon and human unit names (Metres/Centimetres/Feet & inches), and the permission-denied state offers Grant/Open-settings. Site copy now describes the actual drag-box interaction, notes the stick width must be entered, and states one consistent accuracy figure.
