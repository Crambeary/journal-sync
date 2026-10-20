# Final tickets 05–09 integration

Date: 2026-10-20. Worktree: `/home/marc/.t3/worktrees/journal-sync/t3-10b2808d`.
Branch: `t3/integrate-tickets-05-09` (initial workspace branch: `t3/10b2808d`). Base: `67e49e0` (integrated 06–09).
Merged `t3/lossless-codec-pagination-cap-1` at `177bfb5`, including `86c761c`
and `0eefa39`. No parent-main merge, push, nested delegation, real vault edits,
or old-worktree changes were performed.

## Gate and evidence

The final command was:

```sh
rtk proxy flock /tmp/claude-1000/journal-sync-gametest.lock /tmp/journal-sync-codex-gradle.sh build runProductionClientGameTest
```

Both complete runs used JDK 25, Xvfb and the shared exclusive lock. The first
compile exposed a missing Minecraft import, corrected before the first complete
run. The final repeat covers structured, localized cap feedback introduced while
the first real-client run was in progress. `validation.txt` preserves concise
actual output from both complete runs instead of duplicating large raw logs.
Sanitized JUnit XML retains suite/testcase results without environment properties
or console output. Screenshots are from the final run; `sha256.json` records them.

Final combined gate: **BUILD SUCCESSFUL in 2m 9s**, exit 0.
Initial complete combined gate: **BUILD SUCCESSFUL in 2m 12s**, exit 0.

Final unit totals: **54 tests, 0 failures, 0 errors, 0 skipped**: 38 Journal,
3 real book limits, 5 config, 8 integration. The 300 seeded codec roundtrips
are iterations within the property-style tests, not 300 separate JUnit cases.

## Verified integration requirements

- All four save overloads validate count and UTF-16 page length before any write,
  backup, directory creation or conflict-copy creation. Core tests cover unchanged
  and conflicting files, all resolutions, unchanged bytes/time and no extra files;
  missing-target refusal creates no directories. KEEP_FILE keeps bytes/time intact.
- Normal saves, conflict copies and prior-book backups share the lossless `join`
  serializer. Real book CODEC tests retain 200 seeded marked-note and 100 seeded
  marker-less roundtrips; exact limits, surrogate boundaries, long lines, Unicode,
  frontmatter/CRLF, empty pages and trailing newlines remain covered. Invalid UTF-8
  is refused. First-save markers and subsequent stable boundaries pass in core and
  the actual editor, including server-held pages and empty-page journal saves.
- Raw file bytes feed SHA-256 conflict tokens and pre-overwrite backups. No text
  normalization precedes hashing. Normal/KEEP_BOOK writes retain the single
  backup-first overwrite path; backup failure refuses overwrite. Copy/KEEP_FILE
  preserve the original file; copy naming remains collision-safe.
- Backup-aware load returns structured `LoadOutcome.OverCap` with count/limit;
  direct load retains `PageCapExceededException`. Both feedback load APIs and save
  outcomes map cap refusal to `CAP_EXCEEDED`, never generic read/write failure.
  One existing toast/chat sink displays the localized count, limit and refusal.
- Fresh 120-page refusal preserves original pages and journal bytes/time, creates
  no binding/lock, and close cannot overwrite it. Core also proves no backup
  directory is created. Refused reload retains prior pages, binding, sidecar mtime,
  backup contents and original hash; subsequent Done prompts and safe Enter creates
  a copy while external bytes/time remain unchanged. These real-client assertions
  pass in singleplayer and Fabric dedicated-server worlds.
- Missing-file and unreadable-path feedback remains covered by core and actual
  visible chat assertions; missing journals preserve book text and first save
  creates the file. Successful load still backs up prior book contents before bind.
- Each binding retains resolved path, game directory and nondefault backup count.
  Count 2 affects both load/save; changing config/path/directory while bound cannot
  redirect save. Rejected config/read reload retains a valid binding/lock. Stale
  locks remain informational; close, discard, conflict and failure paths clean up
  locks or display a specific removal error.
- Vanilla Controls J→K rebinding, key input and suppressed shortcut characters,
  conflict default focus/Enter, prompt Escape and book Escape remain verified.
  Both game-test entrypoints and path/game-directory property overrides remain.
- Full actual-client coverage retains singleplayer/Fabric reconnect, a published
  LAN host, and a separate unmodified Mojang server load/edit/Done/reconnect with
  server-owned book assertions. No server mod installation is used for vanilla.

## Visual and human checks

Final cap screenshots are visually inspected for readable count/limit and refusal.
Screenshots supplement exact assertions; they do not assert Obsidian behavior.
`docs/obsidian/README.md` ships CSS installation and Source/Live Preview/Reading
view checks. Obsidian theme/appearance verification remains human-only. Ticket 10
still owns real Prism + vault, joining another LAN host and an external dedicated
server smoke test; localhost automation does not claim these checks.

## Diagnostic triage

Synthetic offline-account authentication/Realms failures, optional missing flite
narrator, JVM native-access/Unsafe warnings and upstream anisotropic-filter warnings
are expected environment noise. Deliberately blocked backup/lock/read/write paths
produce expected visible errors. Final successful gates leave no unresolved test
failure. No secret or `.env` content was read.
