# 08 — Config, path templates, keybind, and feedback

**What to build:** All settings live in one JSON config file (`journal-sync.json`) in the game's config folder: global journal path, optional per-world and per-server path templates (`{world-name}`, `{server-address}`), and backup count. A missing journal file is created on first save. A rebindable keybind does the same as the Journal button. The player gets clear in-game feedback for success and for every failure (missing or unreadable path, invalid config, cap exceeded).

**Blocked by:** 04 — Tracer bullet.

**Status:** done

Branch-local implementation complete; ticket 05/07 integration remains explicitly listed below. The cap checkbox stays open until that integration is verified.

- [x] Config parsed with sensible defaults when missing; invalid config reported, not crashed on — `JournalConfigTest.missingConfigCreatesDefaultsAndUsesGlobalPath`, `malformedAndInvalidSettingsAreReportedWithoutReplacingConfig`; real-client `feedbackAndReboundKey` invalid-config scenario
- [x] Global path used by default; per-world and per-server templates resolve correctly (tested through core) — `JournalConfigTest.templatesAndOverrideResolveThroughCore` (absolute/relative paths, context precedence, sanitized substitutions, JVM override)
- [x] Missing journal file created on first save — `JournalConfigTest.missingJournalFeedbackPreservesBookAndCreatesOnlyOnSave`; real-client scenario asserts existing book text survives, file absent before close, exact contents after close
- [x] Keybind appears in controls, is rebindable, and triggers the same action as the button — Fabric `KeyMappingHelper` registration in `JournalSyncClient`; game test asserts inclusion in Controls, rebinds J → K through vanilla Controls handler, loads exact same pages
- [x] Success and error feedback shown in-game for load and save (screenshot of at least one error case) — `feedbackAndReboundKey` asserts latest actual chat feedback for loaded, missing, saved, unreadable, invalid config, save failure; screenshots in `docs/evidence/08-config-feedback/` (see `docs/evidence/08-config-feedback/verification.md`). Cap-specific mapping awaits 05.
- [x] Client game test presses the rebindable Journal keybind (via the game-test input API) and gets the same result as the button — `context.getInput().pressKey(JournalSyncClient.journalKey)` in `feedbackAndReboundKey`, in singleplayer and dedicated-server connections; also holds the key and sends its character event to assert no shortcut text leaks into the book
- [ ] Core test: a 120-page note and a missing or unreadable journal path produce the in-game error feedback without touching the file — missing/unreadable portions pass in `JournalConfigTest.missingJournalFeedbackPreservesBookAndCreatesOnlyOnSave` and `unreadableJournalProducesFeedbackWithoutChangingFiles`; 120-page refusal depends on ticket 05 (not present at branch base).


## How it works

- **Core config**: `src/main/java/com/crambeary/journalsync/JournalConfig.java` reads strict JSON from `config/journal-sync.json`, writes sensible defaults when absent, validates all path templates and a nonnegative integer `backupCount` (default 20), and resolves paths against the game directory. Templates substitute filesystem-safe world names/server addresses; an active server template wins over a world template. `-Djournal_sync.path` remains the final path override. Config is reread on every Journal action; failures preserve the book and binding and report a message.
- **Core feedback**: `JournalFeedback` exposes a typed load result and localized feedback key, preserving the public `Journal.load/save` APIs. A missing journal keeps current book pages, returns the absent token and a warning, and creates no file until save. Unreadable paths return failure without changing pages or files. `CAP_EXCEEDED` is available for ticket 05; `JournalStatus.show` is the reusable client sink for other tickets.
- **Client**: both the button and registered/rebindable key mapping call `BoundBook.press`. Screen keyboard events use the current mapping, consume the shortcut, and suppress its separate GLFW character event. The button reads **Bound** for the screen's remaining lifetime. An eight-second toast reports each result; local-only client chat retains the path and reason. Save feedback is emitted after the close handler completes. Invalid config and load failures leave the existing binding untouched.
- **Tests**: five new core tests (18 total), plus expanded real-client scenarios run in both integrated and dedicated-server worlds. Tests inspect actual client chat feedback, file bytes, book pages and server-side book persistence; screenshots capture displayed feedback. README documents the config, templates, controls and missing-file behavior.

## Integration still required after 05 / 07 merge

1. **05**: adapt its cap-exceeded load/save outcome to `JournalFeedback.Kind.CAP_EXCEEDED`; add the public-core 120-page refusal test (unchanged bytes/mtime and failure feedback), plus real-client cap screenshot and unbound/unchanged-book assertions. Do not treat the enum/translation alone as implemented cap enforcement. Tick the final checkbox only after these pass.
2. **07**: feed `JournalConfig.backupCount()` into its backup rotation policy for both prior-book and overwritten-file backups; add a nondefault-count integration test. The setting is parsed/tested now, but no rotation implementation exists at this branch base.
3. Reconcile any new conflict/save outcomes from 06 with `JournalFeedback.saved` so a conflict is never reported as a successful save; keep conflict prompt ownership in 06.
4. Rerun locked build and production game test after rebasing onto those tickets. No merge or push performed here.

## Human check

The repository-wide Prism smoke test (singleplayer, LAN, own dedicated server) remains for Marc after integration. No launcher instance or real journal was modified by this task. Existing Escape behavior remains: closing a bound book writes the file even if vanilla discards its item edits.


## Validation result

- Baseline locked wrapper build: `BUILD SUCCESSFUL in 12s`.
- Final locked wrapper `build runProductionClientGameTest`: `BUILD SUCCESSFUL in 1m 3s`; `15 actionable tasks: 11 executed, 4 up-to-date`.
- 18 unit tests, zero failures/errors. Client scenarios and screenshots cover both singleplayer and dedicated-server connections. Evidence manifest: `docs/evidence/08-config-feedback/verification.md`.
- Visual evidence examples: `docs/evidence/08-config-feedback/0000_singleplayer-rebound-key-loaded.png`, `0001_singleplayer-saved-feedback.png`, `0002_singleplayer-unreadable-error.png`, `0003_singleplayer-invalid-config.png`, `0004_singleplayer-missing-warning.png`, `0005_singleplayer-save-error.png` (all under that evidence directory).
