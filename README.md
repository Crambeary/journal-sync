# Journal Sync

A client-only Fabric mod for Minecraft 26.2 that keeps a book and quill in step with a markdown journal file. See `docs/specs/journal-sync.md`.

## Commands

- `./gradlew build` — compile and run the unit tests (Fabric Loader JUnit, bootstraps Minecraft).
- `./gradlew test` — unit tests only.
- `./gradlew runProductionClientGameTest` — runs the real client headless under Xvfb and saves screenshots to `run/screenshots/`.

## Environment notes

- Needs JDK 25 to run Gradle (Gradle 9.7.1 cannot run on JDK 27). Point `JAVA_HOME` at a JDK 25.
- The game test needs `xvfb-run` (package `xorg-server-xvfb` and `xorg-xauth`).
- Loom opens Unix domain sockets, so Gradle will not run inside a sandbox that forbids them.

## Running the game test against a launcher's mods

`./gradlew runProductionClientGameTest -PprismModsDir=<launcher instance>/minecraft/mods` runs the same test with exactly the jars in that folder (for example Fabric API and Book Copy from the Prism 26.2 instance) instead of the default Fabric API. The jars are referenced by path and never committed; only the two game-test modules the published Fabric API bundle leaves out are added on top.

## Journal path (until config arrives in ticket 08)

The journal file is `<game dir>/journal-sync/journal.md`. Override it with the JVM argument `-Djournal_sync.path=/full/path/to/journal.md`.

## Markdown pages and Obsidian

Page boundaries use `\n<!-- page -->\n`. This exact sequence is reserved as a
page separator; inline comments are ordinary text. Existing boundaries and empty
pages are preserved. Marker-less notes and oversized pages are split at Minecraft's
1024 UTF-16-unit page limit without dropping whitespace or splitting Unicode
surrogate pairs. Loading does not modify the file; saving inserts markers at the
new boundaries, and later loads keep those boundaries stable. Frontmatter is
ordinary text (including `mc-sync: true`); it is never parsed or rewritten.

More than 100 pages refuses the sync with an in-game warning and leaves the file
unchanged. Invalid UTF-8 is refused rather than replaced with corrupted text.

The optional [Obsidian CSS snippet](docs/obsidian/README.md) adds editing-view
dividers. Its install guide includes the manual Reading view check.
