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

## Journal path

Settings live in `<game dir>/config/journal-sync.json`, created with defaults on first Journal press:

```json
{
  "globalPath": "journal-sync/journal.md",
  "backupCount": 20
}
```

Paths can be absolute (including a vault outside Minecraft) or relative to the game directory.
Optional `worldPathTemplate` and `serverPathTemplate` override the global path in single-player
and multiplayer respectively. For example, `"worldPathTemplate": "journals/{world-name}.md"`
and `"serverPathTemplate": "journals/{server-address}.md"`. Spaces, address colons, slashes and
other unsafe filename characters in replacement values become underscores. Server context takes
precedence over world context. Unknown placeholders and invalid settings are reported in-game;
fix the JSON and press Journal again without restarting.

The JVM argument `-Djournal_sync.path=/full/path/to/journal.md` overrides the selected path
(config must still be valid). A missing journal keeps the book's existing text, shows a warning,
and is created with its parent directories when the bound book closes.

Press **Journal**, or **J** while editing a book and quill, to bind it. Rebind **Bind book to journal**
in Options → Controls → Key Binds → Miscellaneous. The button reads **Bound** while syncing that
screen. Toasts report load/save results; chat retains the full path and any error details.

`backupCount` accepts a nonnegative integer (default 20); rotation wiring awaits ticket 07.
