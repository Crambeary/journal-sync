# Journal Sync

A client-only Fabric mod for Minecraft 26.2 that keeps a book and quill in step with a markdown journal file. See `docs/specs/journal-sync.md`.

## Commands

- `./gradlew build` — compile and run the unit tests (Fabric Loader JUnit, bootstraps Minecraft).
- `./gradlew test` — unit tests only.
- `./gradlew runProductionClientGameTest` — runs the real client headless under Xvfb and saves screenshots to `run/screenshots/`.

The game test also downloads the unmodified Mojang 26.2 server through Mojang's version
manifest, verifies the metadata and server SHA-1, and caches it in
`build/vanilla-test-server/`. The jar is never committed. The test starts a separate
offline server bound to `127.0.0.1` on an available port, accepts the EULA only in
its throwaway directory under `build/vanilla-test-runs/`, and stops the process in
`finally` (including on assertion failures). It uses no server mods. CI runs this
same task and needs network access for the Mojang download. Server console logs
remain in that build directory for diagnosis. The LAN case publishes an integrated
world with `publishServer` and checks the host's book flow.

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

`backupCount` accepts a nonnegative integer (default 20) and applies to both prior-book and
pre-overwrite backups in `<game dir>/journal-sync-backups`. Zero retains no backups.
The resolved path and backup policy are retained for the open binding. A `.lock` sidecar
exists while bound; stale locks do not block loading. Done saves; Escape discards journal
edits as vanilla does. Conflicts offer keep book (with backup), keep file, or a conflict
copy; Enter and Escape in the prompt safely choose a copy.
