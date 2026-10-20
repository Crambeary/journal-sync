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
