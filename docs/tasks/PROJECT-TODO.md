# Project TODO

Ad hoc inbox of pending work for Metrologist. One YAML-frontmatter block per
task. Completed tasks move to `PROJECT-DONE.md` (do not delete them here).

---
id: 2026-06-14-wcag-accessibility-audit
title: "Run a WCAG accessibility audit on the website"
status: pending
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

---
id: 2026-06-14-design-for-ai-exam
title: "Run the design-for-ai design exam on the UI"
status: pending
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

---
id: 2026-06-14-nielsen-heuristics-audit
title: "Run a Nielsen heuristics usability audit"
status: pending
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







