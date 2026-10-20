# 03 — CI on a public repo

**What to build:** Every push runs the same two gates the local agent loop uses — the unit test task and the production client game-test task under a virtual framebuffer — on GitHub Actions, with game-test screenshots uploaded as artifacts even when tests fail. The repository is made public so Actions minutes are free.

**Blocked by:** 02 — Walking skeleton + test-harness spike.

**Status:** done

- [x] User explicitly confirms making the repository public before visibility is changed (hard to reverse; check nothing private is committed first) — Marc confirmed 2026-10-20 after the audit below; asked to rewrite history to drop the `.pyc` files and keep `.scratch/book2.md`
- [x] Repository visibility switched to public — `gh repo view` reports `PUBLIC` (2026-10-20)
- [x] Workflow runs on every push on Linux with the Java version 26.2 requires — `on: push` + `pull_request`, `ubuntu-latest`, Temurin 25
- [x] Workflow runs the unit test task and the production client game-test task; a failing test fails the run — separate `./gradlew build` and `./gradlew runProductionClientGameTest` steps; a non-zero Gradle exit fails the step (not yet observed with a deliberately failing test)
- [x] Screenshots folder uploaded as an artifact on success and failure — `if: always()`; run 39420083739 uploaded `gametest-screenshots` (532 KB). Failure-path upload is guaranteed by `if: always()` but not yet observed
- [x] A green run is visible on the default branch — https://github.com/Crambeary/journal-sync/actions/runs/39420083739 (all steps green, 3m30s)

## Notes

Implementation:

- `.github/workflows/ci.yml` added: runs on every push and pull_request on `ubuntu-latest` with Temurin JDK 25 (`build.gradle` compiles with `options.release = 25`, and Gradle 9.7.1 needs JDK 25 to run), Gradle cached via `gradle/actions/setup-gradle`, `./gradlew build` (unit tests), then `./gradlew runProductionClientGameTest`. Both steps fail the run on test failure.
- `build.gradle` sets `useXVFB = true` on the game-test task, so Loom wraps the game in `xvfb-run` itself; CI only installs `xvfb` and `xauth` via apt and does not wrap the command.
- `run/screenshots/` is uploaded as artifact `gametest-screenshots` with `if: always()` and `if-no-files-found: warn`.
- First run passed on GitHub. `upload-artifact` bumped v5 → v6 afterwards because v5 targets the deprecated Node 20 runtime.
- Pre-publication audit of all commits and tracked files found no secrets, tokens, or personal emails beyond commit author metadata. Two minor items: commit `96a6a90` contained `tools/__pycache__/*.pyc` embedding a local home path — removed from all history with `git filter-branch` before the first push (tree contents otherwise unchanged); `refs/t3/orchestration-v2/checkpoints/*` refs hold extra commits and must not be pushed (only `main` was pushed).
