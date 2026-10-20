# 02 — Walking skeleton + test-harness spike

**What to build:** A Fabric mod for Minecraft 26.2 that builds locally and loads in the user's Prism Fabric 26.2 instance, adding an inert **Journal** button to the book and quill screen. Alongside it, the two test seams from the spec are proven to work on this machine: a Fabric Loader JUnit test that bootstraps Minecraft and reads the real writable-book limits, and a headless (virtual framebuffer) client game test that opens a book and quill, clicks the Journal button, and saves a screenshot. Everything runs locally only — no CI here.

**Blocked by:** 01 — Glue prototype (requires a "go").

**Status:** done — pending the user's check that the jar loads in the Prism instance (first item)

- [x] Mod builds with a single local command (`./gradlew build`; see README for this machine's environment) and loads in a production-style Fabric client (loader 0.19.5, 26.2) without errors. **Not yet checked in the Prism instance itself** — drop `build/libs/journal-sync-0.1.0.jar` into its `mods/` folder and launch
- [x] Journal button visible on the book and quill screen (screenshot as evidence)
- [x] Unit test task passes, including one test that bootstraps Minecraft and asserts against the real page-count and page-length limits
- [x] Production client game-test task passes headless under a virtual framebuffer, leaving a named screenshot of the book screen with the button clicked
- [x] Written finding (in this ticket) on whether the client game-test API on 26.2 can: open the book and quill screen, type text into it, click a mod-added button, and start/connect to a dedicated server
- [x] The finding is reflected in ticket 09's approach

## Finding: client game-test API on 26.2 (2026-10-19)

Run with `./gradlew runProductionClientGameTest`; screenshots land in `run/screenshots/` and the ones from this spike are kept in `docs/evidence/02-walking-skeleton/`.

All four capabilities work, verified by `JournalSyncClientGameTest` (passes under Xvfb):

- **Open the book and quill screen:** yes. `context.setScreen(() -> new BookEditScreen(player, new ItemStack(Items.WRITABLE_BOOK), InteractionHand.MAIN_HAND, WritableBookContent.EMPTY))`, then `context.waitForScreen(BookEditScreen.class)`. Caveat: this opens the screen directly, not through using an item, so the server never sees a matching held item unless the test also gives one (`server.runCommand("give ...")`).
- **Type text into it:** yes. `context.getInput().typeChars("hello")`; the text shows in the screenshot.
- **Click a mod-added button:** yes. `context.clickScreenButton("journal_sync.button.journal")` matches by translation key and fires the button's handler (the test asserts one press).
- **Start/connect to a dedicated server:** yes. `try (var server = context.worldBuilder().createServer(); var connection = server.connect())` gives a real client-server connection, with `server.runCommand(...)`, `runOnServer`, `connection.getServerPlayer()` etc. This runs a Fabric server with Fabric API (not strictly a vanilla server); the dedicated server needs `eula.txt` in the run dir (the production task writes it). The same book scenario ran against it successfully.

Tooling notes that affect later tickets:

- The game test mod is a separate `gametest` source set; the production task needs it packaged (`gametestJar`) and added with `mods.from`, otherwise the client starts and exits "successfully" without running any test. 26.2 is unobfuscated, so no remapping is needed.
- Both `./gradlew test` (unit tests, 3 tests) and the game test are green.
- Anything the game test needs to observe on the button (e.g. `JOURNAL_PRESSES`) must be public in the client source set; the gametest set can see the main/client classes.

**Reflected in ticket 09:** scenario 5 can be automated — see that ticket.
