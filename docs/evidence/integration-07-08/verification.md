# Tickets 07 / 08 integration evidence

Date: 2026-10-20. Branch: `t3/integrate-backups-config`. Base: parent main
`1aa36a8` (includes tickets 06 and 09). This integration merges ticket 07 commits
`25bcd4f`, `2125d33` and ticket 08 commits `8a0c696`, `594c889`, `2f75c0c`, `de61cd2`.
Ticket 05's branch/worktree was neither merged nor modified. Main was not merged or pushed.

## Validation

Both full runs used the supplied JDK 25 wrapper with the shared exclusive game-test
lock, running `build runProductionClientGameTest`. The command's output was redirected
directly into this evidence directory.

- `initial-validation.log`: BUILD SUCCESSFUL in 2m 7s; 16 tasks, 13 executed / 3 up-to-date.
- `gradle-validation.log`: final BUILD SUCCESSFUL in 2m 6s; 16 tasks, 9 executed / 7 up-to-date, exit 0.
- Four copied JUnit XML reports: 40 tests (27 Journal, 3 real book limits, 5 config,
  5 integration); zero failures, errors or skips.
- Final client output explicitly records passing integration assertions in both
  singleplayer and Fabric dedicated-server worlds, then passing published LAN host
  and real unmodified vanilla-server load/edit/save/reconnect assertions.
- `vanilla-server.log`: copied console log from the final isolated Mojang process,
  including book NBT, two joins and clean server shutdown. Jar provenance and download
  verification remain in ticket 09's checked-in provenance and Gradle download task.

## Preserved contracts and integration checks

- Normal save and KEEP_BOOK call the sole backup-aware overwrite path. The core
  integration test checks exact external CRLF/frontmatter backup bytes; actual-client
  conflict scenarios check the same. Failed backup prevents overwrite. KEEP_FILE and
  conflict copy preserve original bytes and modified time and do not back up/overwrite it.
- Each successful bind backs up previous book pages before changing them. Missing-file
  load preserves book text and the absent token even though a lock creates the parent
  directory; first save creates the journal successfully.
- The binding retains resolved file, game directory and parsed backup count. Core tests
  prove exact retained content at count 2; actual client config resolution (without
  path override) proves count 2 on both load/save, then changes all inputs while bound
  and verifies the original file, backup directory and count remain in use.
- Rejected config/read reload leaves the current book and valid binding/lock intact.
  A stale lock remains informational. Ordinary save/failure, book Escape discard and
  every conflict completion remove the sidecar. Actual-client forced backup, lock
  creation and lock removal failures produce visible error feedback; forced removal
  failure ends the binding and reports why removal was impossible.
- Rebindable J → K uses vanilla Controls and Fabric input and behaves like the button;
  shortcut character events do not enter the page. All conflict outcomes map to distinct
  feedback. Default focus/Enter and prompt Escape retain safe conflict-copy behavior;
  book Escape preserves disk bytes/time and discards vanilla edits.
- Both game-test entrypoints, `journal_sync.path`, `journal_sync.game_dir`, real item
  use, Fabric reconnect, published LAN host and unmodified Mojang server checks survive.

## Visual evidence

Current screenshots in this directory cover rebound key, unreadable/config/save errors,
missing-file warning, forced lock-removal error, conflict prompts, LAN and vanilla
reconnect. The singleplayer lock-error screenshot was visually inspected: toast and
chat both show the error and path. The default conflict screenshot was inspected:
all choices are readable and conflict copy retains the visible focus border.
Screenshots supplement exact byte/time, book, backup-count and lifecycle assertions;
they do not replace them. `sha256.json` identifies copied evidence bytes.

## Expected diagnostic noise

The offline synthetic game-test account triggers upstream authentication/Realms/profile
errors. The environment also lacks the optional flite narrator and logs Java native-access
and Unsafe warnings, plus an upstream anisotropic-filter option warning. The filesystem
errors from deliberately blocked backup and lock paths are expected assertions. Both
full gates completed successfully; no unhandled integration failure remains.

## Pending dependency and human release check

Ticket 08's 120-page/cap checkbox remains explicitly pending ticket 05. Its codec/cap
branch was not integrated here. The coordinator must reconcile its load/save outcomes
with backup-aware overloads and map cap refusal to `JournalFeedback.Kind.CAP_EXCEEDED`,
then rerun the complete suite. Human Prism/LAN-join/external-server smoke checks remain
with ticket 10. These automated localhost tests do not claim that release check.
