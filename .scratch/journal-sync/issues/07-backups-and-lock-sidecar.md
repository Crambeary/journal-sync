# 07 — Backups and lock sidecar

**What to build:** A bad sync can always be undone: before every overwrite of the journal file a backup of the old file is written, and on every load a backup of the book's previous contents is written. Backups live in the game directory (not the vault) and rotate, keeping the most recent 20. While a screen is bound, a `.lock` sidecar exists next to the journal file and is removed when the screen closes; it is informational only (a stale lock never blocks loading).

**Blocked by:** 04 — Tracer bullet.

**Status:** done

- [x] Backup written before every file overwrite (including keep-book conflict resolution once 06 exists) — `Journal.save` funnels all writes through its backup-first implementation; `JournalTest.saveBacksUpTheOldFileBeforeEveryOverwriteAndRotatesToRequestedCount` verifies prior bytes. Keep-book is satisfied structurally through this single write path and will be re-verified after ticket 06 merges.
- [x] Backup of prior book contents written on every load — `Journal.load(file, previousBookPages, gameDirectory, backupCount)` returns the backup path; `JournalTest.loadBacksUpThePreviousBookAndReplacesAStaleLock` asserts exact prior pages.
- [x] Rotation keeps exactly the newest 20; tested through core's interface — `JournalTest.saveBacksUpTheOldFileBeforeEveryOverwriteAndRotatesToRequestedCount` performs 22 overwrites, verifies 20 remain, and checks oldest/newest retained content.
- [x] Lock sidecar created on bind, removed on close; a pre-existing stale lock does not prevent loading — `Journal.createLock` replaces existing content, `Journal.removeLock` is idempotent, and `JournalTest.loadBacksUpThePreviousBookAndReplacesAStaleLock` verifies stale-lock replacement.
- [x] Client game test: after load and close, a backup exists and no lock remains — `JournalSyncClientGameTest` asserts both for single-player and dedicated-server flows; screenshots: `docs/evidence/07-backups-lock-sidecar/0001_singleplayer-journal-loaded.png`, `0002_singleplayer-journal-edited.png`, `0004_dedicated-server-journal-loaded.png`, `0005_dedicated-server-journal-edited.png`.
- [x] Core test: a stale lock left by a crash does not block loading and is replaced by the new lock — `JournalTest.loadBacksUpThePreviousBookAndReplacesAStaleLock`.
- [x] Core test: backup and lock behaviour with a missing, read-only, or unwritable journal directory reports an error and never crashes — `JournalTest.loadReportsBackupAndLockFilesystemFailuresWithoutThrowing` and `saveReportsMissingReadOnlyAndUnwritableJournalTargetsWithoutThrowing` cover missing/unwritable path shapes and read-only POSIX permissions; APIs return `Failed` outcomes.

## How it works

- Core creates `journal-sync-backups/` inside the game directory and writes both prior-book snapshots and pre-overwrite journal snapshots there. Backups are `.md` files and rotate together, newest first, to the caller's requested count (20 by default).
- `Journal.load`'s backup-aware overload records the current book pages, replaces/creates `<journal>.lock`, and loads the file. An existing lock is informational and is overwritten. Failures are returned as `LoadOutcome.Failed` or `LockOutcome.Failed`.
- `Journal.save` routes overwrites through one backup-first write path. The legacy overload defaults to the game directory and 20 backups; the client passes the game directory explicitly. `BoundBook` removes the lock as the bound screen closes, even if save reports an error.
- Ticket 08 can pass the configured retention count to the overload. Ticket 06's keep-book overwrite uses the same core save path; re-verify that integration after 06 merges.

## Verification

- Initial build: `/tmp/journal-sync-codex-gradle.sh build` — `BUILD SUCCESSFUL in 11s`.
- Final combined build and production client game test under the required lock: `/tmp/journal-sync-codex-gradle.sh build runProductionClientGameTest` — `BUILD SUCCESSFUL in 50s` (single-player and dedicated-server scenarios completed).
