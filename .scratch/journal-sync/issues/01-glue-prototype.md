# 01 — Glue prototype: Book Copy + JSON↔markdown converter

**What to build:** A small converter that turns a Book Copy export (JSON: title + array of pages) into a markdown note with `<!-- page -->` marker lines between pages, and turns such a note back into a Book Copy JSON file ready for import into a book and quill. The user then plays one timeboxed evening on Fabric 26.2 using Book Copy's export/import commands plus the converter, journaling into their vault, and records whether the habit is worth building a real mod for.

**Blocked by:** None — can start immediately.

**Status:** done — go.

- [x] Converter round-trips: markdown → JSON → markdown is byte-identical for a note with frontmatter, multiple pages, unicode, and empty pages
- [x] Converter handles a note without page markers by splitting it into pages within the game's per-page limit
- [x] Usage documented in the repo (how to export, convert, edit in vault, convert back, import)
- [x] Book Copy confirmed installed and working in the user's Prism Fabric 26.2 instance
- [x] User has played one evening with it and written a go/no-go note into this ticket

## Go/no-go (2026-10-19)

**Go.** Book Copy 0.2.0 works on Prism Fabric 26.2. The export → convert → edit → convert back → import loop worked end to end: unicode, an empty page and new pages all survived. Worth building the real mod.

Findings for later tickets: Book Copy's default export is uncompressed NBT (one `pages` list, no title), not JSON; real sample at `tools/fixtures/book1.nbt`.
