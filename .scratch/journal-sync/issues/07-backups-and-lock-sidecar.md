# 07 — Backups and lock sidecar

**What to build:** A bad sync can always be undone: before every overwrite of the journal file a backup of the old file is written, and on every load a backup of the book's previous contents is written. Backups live in the game directory (not the vault) and rotate, keeping the most recent 20. While a screen is bound, a `.lock` sidecar exists next to the journal file and is removed when the screen closes; it is informational only (a stale lock never blocks loading).

**Blocked by:** 04 — Tracer bullet.

**Status:** ready-for-agent

- [ ] Backup written before every file overwrite (including keep-book conflict resolution once 06 exists)
- [ ] Backup of prior book contents written on every load
- [ ] Rotation keeps exactly the newest 20; tested through core's interface
- [ ] Lock sidecar created on bind, removed on close; a pre-existing stale lock does not prevent loading
- [ ] Client game test: after load and close, a backup exists and no lock remains
- [ ] Core test: a stale lock left by a crash does not block loading and is replaced by the new lock
- [ ] Core test: backup and lock behaviour with a missing, read-only, or unwritable journal directory reports an error and never crashes
