# Contributing to Metrologist

Thank you for considering a contribution. This document covers everything you need to set up locally, understand the conventions, and open a clean pull request.

## Local Setup

**Requirements:**

- JDK 17 (any distribution — Eclipse Temurin works well)
- Android SDK with build-tools matching AGP 9.1.1 (set `ANDROID_HOME` or `local.properties`)
- Git

```bash
git clone https://github.com/cocodedk/Metrologist.git
cd Metrologist
./scripts/install-hooks.sh
```

The hook installer sets `core.hooksPath` to `.githooks`, which activates pre-commit, commit-msg, and pre-push hooks for the repo. See the [Git hooks caveats](#git-hooks-caveats) section below.

## Build, Test, and Lint

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests (~219 tests, JVM only — no device needed)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Build + test + lint in one shot (CI gate)
./gradlew buildSmoke

# JaCoCo coverage report (HTML in app/build/reports/jacoco/)
./gradlew :app:jacocoTestReport
```

## Coding Style

- **File size** — keep every file under 200 lines. Split composables, helpers, and data classes into their own files rather than stacking them.
- **Immutable models** — use `data class` with `val` fields; avoid mutable state outside the UI layer.
- **Pure functions** — the `geometry`, `core`, and `stick` packages must stay free of Android imports. All business logic goes there; Android classes stay in `capture`, `detect`, `ui`, `export`, and `data`.
- **Strict typing** — avoid `Any`, unchecked casts, and nullable types where a non-null contract can be enforced; use `require`/`check` to guard invariants at the boundary.
- **Commit messages** — follow [Conventional Commits](https://www.conventionalcommits.org/):
  `type(scope): short description`, e.g. `feat(geometry): add Vec2 scalar multiply`.

## Local Git Setup

Run these once after cloning:

```bash
git config pull.rebase true
git config branch.autosetuprebase always
git config core.hooksPath .githooks   # done automatically by install-hooks.sh
```

## Branch Naming

| Prefix | Commit type | Use for |
|--------|-------------|---------|
| `feature/` | `feat` | New capabilities |
| `fix/` | `fix` | Bug fixes |
| `chore/` | `chore` | Dependency bumps, build config |
| `docs/` | `docs` | Documentation only |
| `refactor/` | `refactor` | Code restructuring with no behavior change |
| `ci/` | `ci` | CI / workflow changes |

Use kebab-case: `feature/gravity-solver-fallback`, not `feature/GravitySolverFallback`. Never push directly to `main`.

## Git Hooks Caveats

`core.hooksPath` is a local Git config key — it is not committed to the repo, so every person who clones the repo must run `./scripts/install-hooks.sh` to activate the hooks. Running `git commit --no-verify` bypasses all hooks; only use that flag to recover from a broken hook, not to skip legitimate checks.

## Pull Request Checklist

Before requesting a review, confirm:

- [ ] `./gradlew buildSmoke` passes locally (build + tests + lint).
- [ ] New pure-Kotlin logic in `geometry`, `core`, or `stick` has JVM unit tests; coverage stays at 100 % line/branch/method for those packages.
- [ ] No file exceeds 200 lines.
- [ ] No Android imports in `geometry`, `core`, or `stick`.
- [ ] Commit messages follow Conventional Commits.
- [ ] Branch is rebased onto the current `main`, not merged.
- [ ] PR description explains *what* changes and *why*.
