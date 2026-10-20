# 03 — CI on a public repo

**What to build:** Every push runs the same two gates the local agent loop uses — the unit test task and the production client game-test task under a virtual framebuffer — on GitHub Actions, with game-test screenshots uploaded as artifacts even when tests fail. The repository is made public so Actions minutes are free.

**Blocked by:** 02 — Walking skeleton + test-harness spike.

**Status:** ready-for-agent

- [ ] User explicitly confirms making the repository public before visibility is changed (hard to reverse; check nothing private is committed first)
- [ ] Repository visibility switched to public
- [ ] Workflow runs on every push on Linux with the Java version 26.2 requires
- [ ] Workflow runs the unit test task and the production client game-test task; a failing test fails the run
- [ ] Screenshots folder uploaded as an artifact on success and failure
- [ ] A green run is visible on the default branch

## Notes

Done locally (not pushed, repository still private):

- `.github/workflows/ci.yml` added: runs on every push and pull_request on `ubuntu-latest` with Temurin JDK 25 (`build.gradle` compiles with `options.release = 25`, and Gradle 9.7.1 needs JDK 25 to run), Gradle cached via `gradle/actions/setup-gradle`, `./gradlew build` (unit tests), then `./gradlew runProductionClientGameTest`. Both steps fail the run on test failure.
- `build.gradle` sets `useXVFB = true` on the game-test task, so Loom wraps the game in `xvfb-run` itself; CI only installs `xvfb` and `xauth` via apt and does not wrap the command.
- `run/screenshots/` is uploaded as artifact `gametest-screenshots` with `if: always()` and `if-no-files-found: warn`.
- Workflow was reviewed by hand and not run on GitHub; no `actionlint` available locally. Confirm the action major versions (checkout v5, setup-java v5, setup-gradle v5, upload-artifact v5) against the latest releases when pushing.
- Pre-publication audit of all commits and tracked files found no secrets, tokens, or personal emails beyond commit author metadata. Two minor items: commit `96a6a90` contains a `.pyc` embedding a local home path, and `refs/t3/orchestration-v2/checkpoints/*` refs hold extra commits that must not be pushed (push branches only).

Remaining (needs user confirmation and outward-facing actions): confirm and switch visibility to public, push, and confirm a green run on `main`.
