# 06 — Conflict detection and prompt

**What to build:** If the journal file changed on disk while a bound book was open (for example, edited in Obsidian), closing the book does not silently overwrite it. Instead the player is prompted: keep book, keep file, or save book as a conflict copy — with conflict copy as the default. Without a conflict, saving stays last-write-wins with no prompt.

**Blocked by:** 04 — Tracer bullet.

**Status:** done

- [x] Core detects conflict by comparing the file's current hash to the load token's hash — `Journal.save` compares SHA-256 of raw bytes (including missing/created/deleted files); `JournalTest.anExternalEditIsReportedAsAConflictAndNothingIsWritten`, `aLineEndingOnlyChangeByAnotherToolIsStillAConflict`, `aFileCreatedWhileTheBookWasOpenIsAConflict`, `aFileDeletedWhileTheBookWasOpenIsAConflictAndKeepBookRecreatesIt`.
- [x] Each resolution tested through core's interface: keep book overwrites the file, keep file leaves it untouched, conflict copy writes a timestamped sibling file and leaves the original untouched — `JournalTest.keepBookOverwritesTheExternallyEditedFile`, `keepFileLeavesTheExternallyEditedFileUntouched`, `conflictCopyWritesATimestampedSiblingAndLeavesTheOriginalUntouched`; collision-safe copies covered by `twoConflictCopiesInTheSameSecondDoNotOverwriteEachOther`.
- [x] No prompt and a normal save when the file is unchanged — `JournalTest.anUnchangedFileSavesWithoutAConflict`; `JournalSyncClientGameTest.journalLoadsAndSavesOnClose` asserts the saved bytes, server book, and absence of a screen, in single-player and on a dedicated server.
- [x] Prompt defaults to conflict copy — `Journal.Resolution.DEFAULT`, `ConflictScreen.setInitialFocus`; `conflictsOfferAllResolutions` asserts the focused button and presses Enter to create exactly one copy. Visible focus: `docs/evidence/06-conflict-prompt/0001_singleplayer-conflict-default.png` and `0008_dedicated-server-conflict-default.png`.
- [x] Client game test (scenario 3): edit file while bound book is open, close → prompt shown (screenshot), default choice produces a conflict copy — `JournalSyncClientGameTest.conflictsOfferAllResolutions` runs all three choices plus prompt Escape in both world types; screenshots `docs/evidence/06-conflict-prompt/0001_singleplayer-conflict-default.png` through `0004_singleplayer-conflict-escape.png`, and `0008_dedicated-server-conflict-default.png` through `0011_dedicated-server-conflict-escape.png`.
- [x] Core test (and the client game test) covers the file being edited externally while the book is open, including CRLF line endings and frontmatter written by Obsidian — `JournalTest.anObsidianEditWithCrlfAndFrontmatterIsAConflictAndEveryResolutionTreatsItByteForByte` and `conflictsOfferAllResolutions` use an external YAML/frontmatter + CRLF edit. Keep-file/copy preserve the exact text bytes and file modified time; the copy contains the in-game pages.

## How it works

- **Core** (`src/main/java/com/crambeary/journalsync/Journal.java`): the existing three-argument `save` now returns `SaveOutcome.Conflict` without writing when the file hash differs from the load token. The additive four-argument overload accepts `Resolution.KEEP_BOOK`, `KEEP_FILE`, or `CONFLICT_COPY`. A missing file has a distinct absent token. Read/write failures remain `Failed` outcomes. With an unchanged file, save stays last-write-wins without a prompt (the optional resolution is ignored).
- **Copies:** `journal.conflict-yyyyMMdd-HHmmss.md` lives beside `journal.md`. `CREATE_NEW` prevents overwriting another copy; same-second collisions get numbered suffixes. Core returns the copy path in `SavedAsConflictCopy`. Keep-file returns `KeptFile` and touches nothing.
- **Client** (`BoundBook`, `ConflictScreen`): after a bound book closes, core either saves or asks for a choice. Prompt creation is scheduled after screen removal so vanilla's close cannot replace it. The screen has vanilla confirm styling with three buttons. Conflict copy explicitly receives initial focus; Enter accepts it. Escape in the prompt or removal without choosing also resolves to conflict copy once. Client code only presents choices and passes them to core; the pages are snapshotted before the prompt opens.
- **Vanilla book:** Done sends the edited book to the server independently of the journal resolution. Keep-file leaves the external journal untouched; it does not undo vanilla's saved item. The game test checks that distinction for every choice and retains the existing reconnect assertions.

## Escape decision

**Changed to match vanilla discard.** Escape from the bound book cancels the journal save, ends the binding, and shows no conflict prompt. Vanilla discards book edits on Escape; saving those same edits to disk would leave the two sides unexpectedly different. A before-key event marks this close as discarded before the removal handler runs. `JournalSyncClientGameTest.escapeDiscardsBoundEdits` verifies unchanged external CRLF/frontmatter bytes and modified time, no prompt, ended binding, and an empty server-side book, in both world types. Escape from the separate conflict prompt means safe conflict copy: Done already committed the book, so preserving that text is appropriate.

## Validation (2026-10-20)

All Gradle invocations used the supplied JDK-25 wrapper under the shared game-test lock. Initial `build`: **BUILD SUCCESSFUL in 13s**. Final `build runProductionClientGameTest`: **BUILD SUCCESSFUL in 51s**, `15 actionable tasks: 9 executed, 6 up-to-date` (exit 0). The final run compiled both client and game tests, ran 22 `JournalTest` + 3 `BookLimitsTest` cases (zero failures/errors/skips), and ran the real production client under Xvfb in single-player and on a dedicated server.

Evidence: `docs/evidence/06-conflict-prompt/validation.txt`, the two JUnit XML reports in that folder, and eight prompt screenshots there. The default screenshot was visually inspected: all three choices are readable and conflict copy has the visible focus border. Offline test-profile authentication/Realms errors, optional missing flite narrator, and JVM native-access/Unsafe warnings did not fail the tests.

## Integration and remaining human checks

- Integrated ticket 07: the single backup-aware `write` path handles both normal saves and `KEEP_BOOK`; discard, ordinary close/failure and conflict completion release the sidecar. Exact external-byte backup and lock cleanup are asserted by core and real-client integration tests. See `docs/evidence/integration-07-08/verification.md`.
- Ticket 05 should share its codec between normal saves and conflict-copy serialization (`join` here). Hash raw file bytes, not normalized text, to retain CRLF/frontmatter conflict detection.
- Integrated ticket 08: feedback distinguishes `Failed`, `Conflict`, `KeptFile`, `SavedAsConflictCopy` and `Saved`. Real-client assertions retain safe initial focus, Enter, prompt Escape and book Escape behavior. Both test path properties remain available.
- No ticket-06 implementation checkbox remains. The spec's overall real Prism/LAN/user-server smoke test remains a human release check; this run does not claim it. After parallel tickets merge, the coordinator should rebase and rerun both gates.
- Recovered core behavior/tests from prior commit `08fc386` and reviewed/copied the prior uncommitted client work; the old worktree was left untouched. Added explicit default focus, safe prompt removal, and full client scenarios here.
