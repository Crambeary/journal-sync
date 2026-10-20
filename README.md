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

## Journal path (until config arrives in ticket 08)

The journal file is `<game dir>/journal-sync/journal.md`. Override it with the JVM argument `-Djournal_sync.path=/full/path/to/journal.md`.
