---
title: Journal Sync — a Minecraft book and quill synced with a markdown file
status: open
labels: [ready-for-agent]
created: 2026-10-19
---

# Journal Sync

## Problem Statement

I keep notes in a markdown vault (Obsidian) and I also want to keep a journal while
playing Minecraft. Today those are two separate places: anything I write in an
in-game book stays trapped in the world save, and anything I write in my vault is
invisible while I play. Existing mods only do one-off manual imports/exports
(commands or hotkeys that dump a book to a text file or paste text into a book), or
one-way file-to-book display. None keep a single journal in step with a markdown
file, and none of them work the same way in single-player, on LAN, and on a server.

## Solution

A client-only Fabric mod for Minecraft 26.2. While editing any book and quill, I
press a **Journal** button (or a rebindable keybind) and the book is filled with the
contents of my journal markdown file. That screen is now *bound* to the journal.
When I close it, the book's pages are written back to the file. Books I never bind
are never touched.

Because the mod only works on the client's book-editing screen and lets vanilla send
the edited pages to the server, it works identically in single-player, when joining
a LAN world, on my own dedicated server, and on other people's vanilla servers — no
server-side install.

The markdown file is the same text I see in the book: no format translation, so the
round trip is lossless. Page boundaries are stored as invisible marker lines. If the
file changed underneath me while the book was open, I'm asked what to do instead of
silently losing edits, and every overwrite is backed up.

## User Stories

1. As a player, I want to press a Journal button on the book and quill screen, so that the book fills with my journal file's contents.
2. As a player, I want a rebindable keybind that does the same as the Journal button, so that I can load the journal without reaching for the mouse.
3. As a player, I want closing a bound book to write its pages back to my journal file, so that what I wrote in-game appears in my vault.
4. As a player, I want books I did not bind to the journal to be completely untouched by the mod, so that ordinary books are never overwritten.
5. As a player, I want the binding to last only for the lifetime of the open screen, so that there is no hidden state to go stale between sessions.
6. As a player, I want the journal to work in single-player, so that I can journal in my own worlds.
7. As a player, I want the journal to work when I join a friend's LAN world, so that my journal follows me into shared play.
8. As a player, I want the journal to work on a dedicated server I run, so that my server sessions are journaled too.
9. As a player, I want the journal to work on other people's vanilla servers without them installing anything, so that I am not limited to servers I control.
10. As a player, I want the journal to be personal to me, so that other players on a server never see or affect my file.
11. As a player, I want one global journal file by default, so that the same journal follows me across every world and server.
12. As a player, I want to optionally override the journal path per world or per server using templates like world name or server address, so that I can keep separate journals when I want to.
13. As a player, I want the journal file path to be configurable to any location, including inside my vault, so that the note lives where I already keep notes.
14. As a vault user, I want the journal note to be ordinary markdown, so that any markdown tool — not just Obsidian — can read and edit it.
15. As a vault user, I want the text in the book and the text in the file to be identical, so that editing on either side never corrupts the other.
16. As a vault user, I want raw markdown characters (headings, list markers) to appear as-is in the book, so that nothing is lost translating formats.
17. As a vault user, I want page breaks stored as marker lines that are hidden in Obsidian's reading view, so that the note reads naturally while preserving pages.
18. As a vault user, I want a hand-written note without page markers to be paginated automatically on first load, so that I can start a journal from any existing note.
19. As a vault user, I want page markers written into the file on the first save after auto-pagination, so that pagination is stable from then on.
20. As a player, I want long lines and paragraphs to be split across pages the way the game's book limits require, so that loading never fails or truncates silently.
21. As a player, I want a clear warning if the journal exceeds the book's page cap (about 100 pages), so that I know syncing has stopped rather than discovering missing text later.
22. As a player, I want syncing to refuse rather than truncate when over the cap, so that the file is never cut short.
23. As a player, I want the mod to detect that the file changed while my bound book was open, so that edits I made in my vault meanwhile are not silently overwritten.
24. As a player, when such a conflict is detected on close, I want to choose between keeping the book, keeping the file, or saving the book as a conflict copy, so that I decide what wins.
25. As a player, I want "save as conflict copy" to be the default choice in the conflict prompt, so that the safest option is one keypress away.
26. As a player, I want last-write-wins when there is no conflict, so that normal use needs no prompts.
27. As a player, I want a backup of the file taken before every overwrite, so that a bad sync can always be undone.
28. As a player, I want a backup of the book's previous contents taken whenever I load the journal into it, so that loading never destroys what was in the book.
29. As a player, I want backups kept outside my vault in the game directory, so that my vault stays clean.
30. As a player, I want only the most recent 20 backups kept, rotating, so that backups don't grow without bound.
31. As a vault user, I want a lock sidecar file to exist next to the journal while a book is bound and be removed on close, so that tools (and a possible future Obsidian plugin) can tell Minecraft has the journal open.
32. As a vault user, I want an optional CSS snippet that styles page markers as visible dividers in Obsidian's editing view, so that I can see where pages break.
33. As a player, I want all settings in one plain JSON config file, so that I can configure the mod without an in-game config screen.
34. As a player, I want the frontmatter flag that marks a note as mod-managed to be preserved through round trips, so that I can tell mod-managed notes apart from hand-written ones.
35. As a player, I want clear in-game feedback when a load or save succeeds or fails (missing file, unreadable path, cap exceeded), so that I never wonder whether it synced.
36. As a player, I want a missing journal file to be created on first save, so that starting a new journal needs no setup.
37. As a developer, I want the sync logic to live in a core module with a small interface independent of screens, so that it can be tested thoroughly without driving the GUI.
38. As a developer, I want core tests to run against Minecraft's real book data types and limits, so that the tests catch disagreements with the actual game.
39. As a developer, I want end-to-end tests that run the real game client headlessly and save screenshots, so that an agent can verify the in-game flow without a human.
40. As a developer, I want CI to run the same test commands as the local agent loop, so that "passes locally" and "passes in CI" mean the same thing.
41. As the user, I want a short manual smoke test in my real launcher instance covering single-player, LAN, and my own server before work is called done, so that real-world behavior is confirmed.

## Implementation Decisions

- **Target:** Fabric loader, Minecraft 26.2 only, Java version as required by 26.2. Personal v1, MIT license from the start, built clean enough to publish later.
- **Client-only.** No server component. The mod acts only on the client's book-and-quill editing screen; vanilla's normal book-edit packet carries the result to whichever server (integrated, LAN host, dedicated) owns the item. Consequence, accepted: sync only happens while the book screen is open — there is no background sync.
- **Two modules:**
  - **core** — all behavior that does not need a screen: markdown ↔ pages codec, pagination against the game's real limits, page-cap check, conflict detection, backup writing and rotation, lock sidecar, path templating, config parsing. Exposes a small interface the client calls, roughly: *load(journal context) → pages + load token* and *save(pages, load token, conflict resolution?) → outcome* (saved / conflict detected / over cap / error). The load token carries the file's content hash at load time.
  - **client** — thin Fabric layer: adds the Journal button to the book and quill screen, registers the rebindable keybind (both invoke the same action), tracks which open screen is bound, calls core on load and on close, shows the conflict prompt and status feedback. No logic that could live in core lives here.
- **Binding:** explicit only. A screen becomes bound when the user presses the Journal button/keybind; binding ends when the screen closes. No item tagging (impossible client-only), no marker-on-page-1 opt-in in v1.
- **Text model:** plain text, lossless. The book shows the raw markdown source; no rendering of markdown to formatting codes.
- **Page encoding:** an HTML-comment marker line separates pages in the file. Files without any marker are auto-paginated on load; markers are written on the next save.
- **Page cap:** the game's book page limit (about 100 pages). Over the cap → refuse to load or save, warn, never truncate. No multi-book overflow in v1.
- **Conflict policy:** last-write-wins, with one guard — on close of a bound screen, if the file's current hash differs from the hash recorded at load, prompt: keep book / keep file / save book as conflict copy (default). Conflict copy is a sibling file with a distinguishing suffix and timestamp.
- **Backups:** in the game directory, not the vault. Written before every file overwrite and on every load (prior book contents). Rotation keeps the last 20.
- **Lock sidecar:** created next to the journal file when a screen binds, removed when it closes. Informational only in v1 (the mod does not refuse to load if one exists — it may be stale from a crash).
- **Config:** a single JSON file in the game's config folder (contract name: `journal-sync.json`). Holds: global journal path, optional per-world / per-server path templates (`{world-name}`, `{server-address}`), backup count. No config screen, no Mod Menu integration.
- **Frontmatter:** the codec treats leading YAML frontmatter as part of the journal text so it round-trips untouched; the `mc-sync: true` flag is a user convention, not something the mod enforces.
- **Obsidian side:** no plugin in v1. Ship a CSS snippet for page-marker styling. The lock sidecar is the hook a future plugin would use.
- **Repository:** GitHub repo `Crambeary/journal-sync`, private until the CI ticket, which makes it public (free Actions minutes) and adds GitHub Actions CI on Linux with a virtual framebuffer running the same commands as the local agent loop. The agent loop gates locally and does not depend on CI.

## Testing Decisions

- **Good tests assert external behavior only:** given a file and a book state, what pages load, what the file contains after save, what outcome is returned, what backups/lock files exist. Never assert on internal helper calls or private structure. Tests should survive a rewrite of the internals.
- **Seam 1 — core's public interface**, tested with Fabric Loader JUnit, bootstrapping Minecraft (version detection + registry bootstrap in a before-all hook) so tests run against the game's real writable-book data component and its real page/length limits. Covers:
  - markdown → pages → markdown round-trips unchanged (including frontmatter, empty pages, unicode, very long lines, trailing newlines); property-style tests over generated text;
  - auto-pagination of marker-less files and marker insertion on save;
  - cap enforcement (refuse, never truncate);
  - conflict detection via load-token hash, and each of the three resolutions;
  - backup creation on load and save, rotation at 20;
  - lock sidecar lifecycle;
  - path templating and config parsing, including missing/invalid config and missing journal file.
- **Seam 2 — the real client**, via Fabric client game tests, run headless under a virtual framebuffer, saving named screenshots as evidence. Scenarios:
  1. single-player: open a book and quill, press Journal → pages match the file;
  2. type, close → file updated, backup exists, lock removed;
  3. edit the file while the bound book is open, close → conflict prompt shown (screenshot);
  4. load a 120-page file → cap warning shown, file unchanged;
  5. the same load/save flow against a dedicated server, if the client game-test API can start one; LAN uses the same client path so this covers it. If the API cannot, scenario 5 moves to the manual smoke test.
- **Agent loop gate:** every iteration must pass the unit test task and the production client game-test task. CI runs the same two and uploads screenshots even on failure.
- **Done gate:** a manual smoke test by the user in their real Prism Launcher Fabric 26.2 instance: single-player, joining a LAN world, and their own dedicated server.
- **Prior art:** none in this repo (it is new). External prior art: the Fabric documentation's "Automated Testing" page for 26.2 (Fabric Loader JUnit setup, client game tests, production client game-test task, virtual framebuffer CI example).

## Out of Scope

- Server-side component, shared or multiplayer-owned journals, server logbooks.
- Background sync while the book screen is closed.
- Rendering markdown as in-game formatting (bold, colors, headings).
- Multiple books / overflow beyond the page cap; multiple journals at once.
- An Obsidian plugin (budget counter, lock banner, book preview).
- Opt-in by marker on page 1, item tagging.
- Config screen / Mod Menu integration.
- Other loaders (NeoForge, Forge, Quilt) and other Minecraft versions.
- Publishing to Modrinth/CurseForge.
- Third-party agent-driven test harnesses (e.g. MC Pilot) — explicitly rejected; pass/fail lives in Fabric's own tooling.

## Further Notes

- **Order of work:**
  1. Timeboxed one-evening glue prototype: Book Copy (confirmed for Fabric 26.2) plus a small JSON↔markdown script, to confirm the journaling habit is worth building for.
  2. Spike: prove client game tests run headless on the user's machine on 26.2, that the test API can open the book and quill screen, type, and click a mod-added button, and whether it can start a dedicated server.
  3. core with its tests.
  4. client.
  5. client game-test scenarios.
  6. manual smoke test.
- **Risks:** the client game-test API's screen/input/dedicated-server capabilities on 26.2 are not yet confirmed from documentation — the spike settles this before core work depends on it. 26.2 is very recent; expect mapping and API churn.
- **Environment facts at time of writing:** the user plays via Prism Launcher and has a Fabric 26.2 instance (loader 0.19.5). Their desktop is Wayland; headless tests should use the virtual framebuffer rather than the live session.
