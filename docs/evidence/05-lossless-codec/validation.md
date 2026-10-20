# Ticket 05 validation — 2026-10-20

Base: `d5decbc`. Minecraft 26.2, Fabric Loader 0.19.5, Java 25.
All Gradle invocations used the provided JDK/cache/Xvfb wrapper, serialized by
`flock /tmp/claude-1000/journal-sync-gametest.lock` from this worktree.

- Initial wrapper `build`: **BUILD SUCCESSFUL in 25s**.
- Expanded unit suite wrapper `build`: **BUILD SUCCESSFUL in 11s**.
- Final wrapper `build`: **BUILD SUCCESSFUL in 9s**;
  **10 actionable tasks: 2 executed, 8 up-to-date** (the test task executed).
- Wrapper `runProductionClientGameTest`: **BUILD SUCCESSFUL in 48s**;
  **11 actionable tasks: 7 executed, 4 up-to-date**.
- Final unit results: **24 tests, 0 failures, 0 errors, 0 skipped**
  (`JournalTest`: 21; `BookLimitsTest`: 3). Sibling XML files preserve the actual
  testcase results without environment properties or console output.
- `git diff --check`: clean.

The production client test ran under real Xvfb. In singleplayer and against the
headless dedicated server it verified marker-less pagination, first-save markers,
server-side pages, leading/trailing empty-page preservation by the journal close
handler, the 120-page warning, unchanged journal bytes and modification time on
refusal and after Done, preserved original book text, normal bound editing, and
reconnect persistence of the final edited book. Both cap screenshots were copied
from the test output and visually inspected: the count, limit and refusal message
are visible. The test obtains each book using `give` and opens it by using the item.

The offline test account emits Mojang HTTP 401 profile/key lookup errors; the
client and dedicated-server tests still complete successfully. The JVM also
emits dependency native-access/Unsafe deprecation warnings. Neither is a test
failure.

Obsidian's UI was not run. See the ticket and `docs/obsidian/README.md` for the
manual visual check; CSS behavior is not represented as an automated result.
