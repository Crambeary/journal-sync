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
