# 05 — Lossless codec, pagination, page cap, and CSS snippet

**What to build:** Any markdown note round-trips losslessly through the book: frontmatter, unicode, very long lines, empty pages and trailing newlines all survive. A hand-written note without page markers is paginated automatically on load to fit the game's real limits, and markers are written on the next save. A journal over the book's page cap is refused on load and on save with a clear in-game warning — never truncated. Ship an Obsidian CSS snippet that shows page markers as visible dividers.

**Blocked by:** 04 — Tracer bullet.

**Status:** done

- [x] Property-style round-trip tests over generated text pass against the real book component — `JournalTest.generatedMarkedNotesRoundTripThroughRealBookUnchanged` (200 seeded notes, byte equality) and `generatedMarkerlessNotesPaginateLosslesslyAndBecomeStable` (100 seeded notes), both through `WritableBookContent.CODEC`; 24 unit tests pass
- [x] Marker-less note auto-paginates within real per-page limits; next save writes markers; subsequent loads are stable — `JournalTest.generatedMarkerlessNotesPaginateLosslesslyAndBecomeStable`, `veryLongLinesAndSurrogateBoundaryPaginateWithoutLosingCharacters`, `exactPageAndCountLimitsAreAcceptedByRealBook`; game test `markerlessNoteLoadsAndSavesLosslessly` checks the real editor, Done handler, and server-held pages in both worlds
- [x] Frontmatter (including a `mc-sync: true` flag) round-trips untouched — `JournalTest.frontmatterAndTrailingNewlinesRoundTripUntouched` checks exact UTF-8 bytes including CRLF and Unicode; generated marker-less cases and the real-editor scenario also retain the flag
- [x] Over-cap journal: load refused and save refused, file unchanged, outcome reported — `JournalTest.overCapLoadIsReportedAndLeavesBytesAndModifiedTimeUnchanged`, `autoPaginationAlsoRefusesOverCapRatherThanTruncating`, `overCapSaveIsReportedBeforeAnyFileOrFolderIsChanged`; load reports `PageCapExceededException`, save reports `SaveOutcome.OverCap`, client shows a system toast
- [x] Client game test (scenario 4): loading a 120-page file shows the cap warning and leaves the file unchanged (screenshot) — `JournalSyncClientGameTest.overCapLoadLeavesBookAndFileUntouched` checks bytes and modified time before and after Done, no binding, original book text, and toast presence; `docs/evidence/05-lossless-codec/0001_singleplayer-120-page-cap-warning.png` and `0005_dedicated-server-120-page-cap-warning.png` (visually inspected)
- [x] CSS snippet in the repo with install instructions; markers remain hidden in Obsidian reading view — `docs/obsidian/journal-sync-pages.css` and `docs/obsidian/README.md`; selectors target editing view only and add no generated content, while HTML comments are omitted in Reading view. **Obsidian visual verification is manual and remains for Marc**, with precise steps below; not claimed as an automated result.

## How it works

- `Journal.load(Path)` decodes UTF-8 strictly, splits the existing exact marker
  separator (`\n<!-- page -->\n`) with trailing empty pages retained, then splits
  oversized pages using `WritableBookContent.PAGE_EDIT_LENGTH` (1024 UTF-16 units).
  A boundary moves back one unit when necessary to keep a surrogate pair intact.
  Whitespace, CRLF, combining characters, frontmatter and trailing newlines are
  never normalized. The marker separator is reserved syntax, as in ticket 04.
- Loading does not write anything. The next save inserts separator lines at new
  page boundaries. Concatenating the auto-paginated pages recovers the entire
  original text; the saved file differs only by inserted separators. Existing
  valid marked files save byte-for-byte unchanged, including empty pages. Later
  loads/saves keep the new boundaries stable.
- The cap comes from `WritableBookContent.MAX_PAGES` (100). Load throws the
  additive `Journal.PageCapExceededException` before the client changes the book
  or binds the screen. Save returns the additive `SaveOutcome.OverCap` before any
  folder or file write. An oversized individual page supplied to save returns
  `Failed` instead of writing a book Minecraft's codec cannot represent.
- `BoundBook` catches cap refusals and shows a system toast with the page count,
  limit, and “Sync refused. File unchanged.” General feedback remains ticket 08.
- The optional snippet lives in `docs/obsidian/journal-sync-pages.css`, with
  installation steps in `docs/obsidian/README.md`. It styles CodeMirror HTML
  comment lines in notes tagged `cssclasses: journal-sync`. CSS cannot select
  source text, so other HTML comment lines in those notes receive the same style.
  It selects only `.markdown-source-view.mod-cm6` and generates no content.
  Obsidian's Markdown renderer omits HTML comments, so Reading view keeps the
  markers hidden and gets no divider from this snippet.

## Manual Obsidian check (for Marc)

1. Copy `docs/obsidian/journal-sync-pages.css` into your vault's
   `.obsidian/snippets/`; reload and enable **journal-sync-pages** under
   Settings → Appearance → CSS snippets.
2. In a journal note, retain `mc-sync: true` and add
   `cssclasses: journal-sync` to the existing YAML frontmatter. Add two short
   paragraphs separated by a standalone `<!-- page -->` line.
3. In Source mode, check a divider appears above the marker. In Live Preview,
   put the cursor on the marker to expose its source if Obsidian hides it.
4. Switch to Reading view: expect the paragraphs without marker text or a divider.
5. Verify a note without the CSS class stays unstyled; toggle the snippet off and
   confirm the markdown source never changed.

This requires the user's Obsidian vault and UI. It is **not an automated check**;
visual confirmation in Obsidian remains for Marc. The shipped selector scope and
HTML-comment format explain the intended Reading-view behavior.

## Integration notes for tickets 06–08

- Keep cap checks before conflict-resolution overwrites, backup/lock side effects,
  and any alternate save path. Handle `SaveOutcome.OverCap` in exhaustive outcome
  switches. The existing `Loaded`, `LoadToken`, `load` and `save` signatures remain.
- Ticket 08 can replace `BoundBook`'s minimal toast with its central feedback path;
  preserve the visible refusal and unchanged-file behavior.
- Ticket 06 must still guard a stale bound book against a file changed externally
  to an over-cap note. This ticket refuses the attempted load but intentionally
  does not implement hash conflict checking or change existing binding lifetime.

## Validation result (2026-10-20)

All Gradle commands used the supplied wrapper under the shared `flock` lock.
Initial build: `BUILD SUCCESSFUL in 25s`. Final build: `BUILD SUCCESSFUL in 9s`
(`10 actionable tasks: 2 executed, 8 up-to-date`), with 24 unit tests and zero
failures/errors/skips. Production client game test under real Xvfb:
`BUILD SUCCESSFUL in 48s` (`11 actionable tasks: 7 executed, 4 up-to-date`).
Both singleplayer and dedicated-server scenarios passed. Evidence summary and
persisted JUnit testcase results: `docs/evidence/05-lossless-codec/validation.md`
and sibling `TEST-*.xml`. `git diff --check` is clean. Offline account HTTP 401
profile lookups and JVM dependency warnings did not fail either gate.
