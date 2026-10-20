# 07 — Backups and lock sidecar

**What to build:** A bad sync can always be undone: before every overwrite of the journal file a backup of the old file is written, and on every load a backup of the book's previous contents is written. Backups live in the game directory (not the vault) and rotate, keeping the most recent 20. While a screen is bound, a `.lock` sidecar exists next to the journal file and is removed when the screen closes; it is informational only (a stale lock never blocks loading).

**Blocked by:** 04 — Tracer bullet.

**Status:** done

- [x] Backup written before every file overwrite (including keep-book conflict resolution once 06 exists) — `Journal.save` funnels all writes through its backup-first implementation; `JournalTest.saveBacksUpTheOldFileBeforeEveryOverwriteAndRotatesToRequestedCount` verifies prior bytes. Integration tests now assert exact external CRLF/frontmatter bytes are backed up on KEEP_BOOK, while KEEP_FILE/copy preserve bytes and modified time.
- [x] Backup of prior book contents written on every load — `Journal.load(file, previousBookPages, gameDirectory, backupCount)` returns the backup path; `JournalTest.loadBacksUpThePreviousBookAndReplacesAStaleLock` asserts exact prior pages.
- [x] Rotation keeps exactly the newest 20; tested through core's interface — `JournalTest.saveBacksUpTheOldFileBeforeEveryOverwriteAndRotatesToRequestedCount` performs 22 overwrites, verifies 20 remain, and checks oldest/newest retained content.
- [x] Lock sidecar created on bind, removed on close; a pre-existing stale lock does not prevent loading — `Journal.createLock` replaces existing content, `Journal.removeLock` is idempotent, and `JournalTest.loadBacksUpThePreviousBookAndReplacesAStaleLock` verifies stale-lock replacement.
- [x] Client game test: after load and close, a backup exists and no lock remains — `JournalSyncClientGameTest` asserts both for single-player and dedicated-server flows; screenshots: `docs/evidence/07-backups-lock-sidecar/0001_singleplayer-journal-loaded.png`, `0002_singleplayer-journal-edited.png`, `0004_dedicated-server-journal-loaded.png`, `0005_dedicated-server-journal-edited.png`.
- [x] Core test: a stale lock left by a crash does not block loading and is replaced by the new lock — `JournalTest.loadBacksUpThePreviousBookAndReplacesAStaleLock`.
- [x] Core test: backup and lock behaviour with a missing, read-only, or unwritable journal directory reports an error and never crashes — `JournalTest.loadReportsBackupAndLockFilesystemFailuresWithoutThrowing` and `saveReportsMissingReadOnlyAndUnwritableJournalTargetsWithoutThrowing` cover missing/unwritable path shapes and read-only POSIX permissions; APIs return `Failed` outcomes.

## How it works

- Core creates `journal-sync-backups/` inside the game directory and writes both prior-book snapshots and pre-overwrite journal snapshots there. Backups are `.md` files and rotate together, newest first, to the caller's requested count (20 by default).
- `Journal.load`'s backup-aware overload reads the file first, records the current book pages, then replaces/creates `<journal>.lock` before binding. Rejected reads preserve the existing valid lock and binding. An existing lock is informational and is overwritten. Failures are returned as `LoadOutcome.Failed` or `LockOutcome.Failed`.
- `Journal.save` routes overwrites through one backup-first write path. The legacy overload defaults to the game directory and 20 backups; the client passes the game directory explicitly. `BoundBook` removes the lock as the bound screen closes, even if save reports an error.
- Integrated with tickets 06/08: `Binding` snapshots the resolved path, game directory and parsed retention count. Load and save both use that count; normal and KEEP_BOOK saves share the sole backup-aware overwrite path. Escape discard, ordinary success/failure, and every conflict completion release the lock. Lock removal failures report visible errors.

## Verification

- Initial build: `/tmp/journal-sync-codex-gradle.sh build` — `BUILD SUCCESSFUL in 11s`.
- Final combined build and production client game test under the required lock: `/tmp/journal-sync-codex-gradle.sh build runProductionClientGameTest` — `BUILD SUCCESSFUL in 50s` (single-player and dedicated-server scenarios completed).


## Integration with 06 / 08 / 09 (2026-10-20)

Merged branch `t3/backup-lock-sidecar-1` into `t3/integrate-backups-config`, then merged
`t3/config-templates-keybind-feedback` while retaining both multiplayer game-test entrypoints.
`JournalIntegrationTest` checks nondefault parsed rotation on both loads/saves, exact
KEEP_BOOK backup bytes, backup failure refusing overwrite, missing-file lock/creation,
and load/read/lock error feedback. `configuredBackupsAndBinding` runs the actual client
with config path resolution and count 2, proves the count affects both load and save,
and changes config/path/game-directory inputs while bound to prove session policy is
retained. `backupAndLockErrors` asserts visible errors and cleanup for filesystem failures.
Conflict tests assert backups and all outcomes; Escape asserts no stale sidecar.

Integration evidence and final validation: `docs/evidence/integration-07-08/verification.md`.
