# 04 — Tracer bullet: Load and save-on-close against one global file

**What to build:** Pressing Journal on an open book and quill fills it with the pages of one global journal markdown file and binds that screen. Closing the bound screen writes its pages back to the file. Books whose screen was never bound are left completely untouched. This establishes core's load/save interface (load returns pages plus a load token carrying the file's hash; save takes pages and the token and returns an outcome) and the thin client layer that calls it. Path comes from a minimal hardcoded/default setting for now; full config is ticket 08.

**Blocked by:** 02 — Walking skeleton + test-harness spike.

**Status:** awaiting-manual-check — only the real single-player world check is left (see "Manual check" below)

- [x] Core load/save interface exists and is covered by Fabric Loader JUnit tests through its public interface only — `Journal.load(Path)` → `Loaded(pages, LoadToken)` and `Journal.save(Path, pages, token)` → `SaveOutcome` (`src/main/java/com/crambeary/journalsync/Journal.java`); 10 tests in `JournalTest` (bootstraps Minecraft), all green in `./gradlew build`
- [x] Simple round trip works: a multi-page note with `<!-- page -->` markers loads into the book page-for-page and saves back unchanged — `JournalTest.aMultiPageNoteSavesBackUnchanged` (frontmatter, an empty page, unicode, trailing newline) plus the game test's byte-for-byte file assertion
- [x] Client game test (scenario 1): open book and quill, press Journal → book pages match the file (screenshot) — game test asserts the screen's pages equal the three pages of the file; `docs/evidence/04-tracer-bullet/0001_singleplayer-journal-loaded.png` (and `0004_dedicated-server-journal-loaded.png`)
- [x] Client game test (scenario 2): type text, close → file contains the edited text — game test types after the loaded text, clicks Done, asserts the file bytes; `docs/evidence/04-tracer-bullet/0002_singleplayer-journal-edited.png`
- [x] Client game test: open and close a book without pressing Journal → file untouched — game test asserts the file's bytes and modified time are unchanged and that vanilla still saved the typed text to the server-side book; `0000_singleplayer-unbound-book.png`
- [ ] Works in the user's real single-player world (quick manual check noted in this ticket) — **not doable by the agent**, see "Manual check" below
- [x] Client game test opens the book the real way (`give` a book and quill, then use the item), not via `setScreen`, so the server holds the item the edit packet refers to; close, reconnect, and the server-side item has the saved pages — `give @a writable_book` then a right click; after Done the server-side `WRITABLE_BOOK_CONTENT` is asserted, then the singleplayer world is reopened and the dedicated server reconnected and the pages asserted again
- [x] Prism mod set: the production client game test also runs once with the jars from the Prism 26.2 instance's `mods/` folder (Book Copy, Fabric API) alongside this mod, and passes — `./gradlew runProductionClientGameTest -PprismModsDir=/home/marc/.local/share/PrismLauncher/instances/26.2/minecraft/mods` → BUILD SUCCESSFUL; loaded `bookcopy 0.2.0-26.2` and `fabric-api 0.161.0+26.2`; screenshots in `docs/evidence/04-tracer-bullet/prism-mods/`

## How it works

- **Core** (`src/main`): pages are joined with `\n<!-- page -->\n` (split and join on exactly that string, so any text round-trips). A missing file loads as zero pages with `LoadToken.ABSENT`; the first save creates it and its folder. `save` reports failures in its outcome instead of throwing. The token (SHA-256 of the file bytes) is carried but not yet compared — conflict detection is ticket 06.
- **Client** (`src/client`): `BoundBook` loads on Journal press, fills the screen's `pages`, binds the screen, and saves on `ScreenEvents.remove` for that screen. Access to the screen's private `pages`/`currentPage`/`updatePageContent`/`updateButtonVisibility`/`setInitialFocus` is by access widener (`src/client/resources/journal_sync.accesswidener`, namespace `official` on 26.2), not a mixin.
- **Path:** `<game dir>/journal-sync/journal.md`, overridable with `-Djournal_sync.path=...` (the tests use it). Real config is ticket 08.
- 26.2 facts verified while building this: only the Done button sends the edit packet (`setScreen(null)` first, then the packet); Escape discards in vanilla. Because the binding saves on any close, **Escape on a bound book still writes the file while vanilla discards the edit on the server** — flagging for ticket 06/08 to decide. The Sign button also closes the edit screen, which ends the binding.
- Not in this ticket and therefore not handled: a journal page over 1024 characters or more than 100 pages (ticket 05 paginates and caps), conflicts (06), backups/lock file (07), user feedback in game (08 — failures are only logged for now). **Use a short test note for the manual check.**

## Environment deviation (read before re-running the game test)

This machine has no `Xvfb` (`xvfb-run` missing); installing `xorg-server-xvfb xorg-xauth` via `sudo -A pacman` was not approved when attempted. The game test results above were produced by running the unchanged Gradle task with a throwaway `xvfb-run` shim (outside the repo) that just runs the game on the existing display (`:0`, Xwayland), so a game window appeared on the desktop during the run. Nothing in the repo depends on the shim. Install the packages from the README for true headless runs.

## Manual check (for Marc)

1. `./gradlew build` (JDK 25), then copy `build/libs/journal-sync-0.1.0.jar` into `/home/marc/.local/share/PrismLauncher/instances/26.2/minecraft/mods/` (next to Fabric API and Book Copy).
2. Create `/home/marc/.local/share/PrismLauncher/instances/26.2/minecraft/journal-sync/journal.md` containing, exactly:
   ```
   # Day 1
   Found iron.
   <!-- page -->
   Day 2
   Built a farm.
   ```
   Keep a copy somewhere to compare against afterwards.
3. Launch the 26.2 instance, open a single-player world, run `/give @s writable_book`, right-click the book. Press **Journal** (left of the book). Expect: "Page 1 of 2", page 1 is `# Day 1` / `Found iron.`, page 2 (page-forward arrow) is `Day 2` / `Built a farm.`, and the cursor is at the end of the page.
4. Type some words on page 1, click **Done**. Open `journal.md`: it should have your words on the first page, the `<!-- page -->` line unchanged, and page 2 unchanged. Reopen the book in game: the saved text is in it.
5. Untouched check: note the file's modified time (`stat journal.md`), `/give @s writable_book` a second book, open it, type something, press **Done** without pressing Journal. The file and its modified time must not change.
6. Tick the box above if all of that held.
