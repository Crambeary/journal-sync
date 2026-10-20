# 09 — Multiplayer verification

**What to build:** Proof that the load and save-on-close flow works when the book lives on a server, not just in single-player. LAN uses the same client path as a dedicated server, so a dedicated-server check covers it. Depending on ticket 02's finding, this is either an automated client game test against a dedicated server (scenario 5) or a written manual procedure the user follows.

**Blocked by:** 04 — Tracer bullet; and the dedicated-server finding from 02 — Walking skeleton + test-harness spike.

**Status:** done

**Approach:** automated. Ticket 04's existing `JournalSyncClientGameTest` verifies the Fabric dedicated server. Ticket 09 adds `MultiplayerJournalGameTest` and `VanillaTestServer` in separate files for the real vanilla process and published LAN host. No changes to 04's test class are needed.

- [x] Client game test connects to a Fabric dedicated server, loads, edits, closes, checks the updated file, and checks server-owned pages after reconnect — `JournalSyncClientGameTest.runScenariosInAllWorlds` / `journalLoadsAndSavesOnClose`; screenshots `docs/evidence/09-multiplayer/0004_dedicated-server-journal-loaded.png` and `0005_dedicated-server-journal-edited.png`.
- [x] Otherwise: manual-only fallback — **N/A**, the API supports the automated path and both dedicated-server variants passed. The short manual LAN/vanilla procedure below remains available for ticket 10's real launcher/network check.
- [x] Confirmed no server-side install is needed — `VanillaTestServer` launches only `java -jar server.jar nogui` in a fresh directory, with no Fabric loader/API/mods. Manifest-verified jar provenance: `docs/evidence/09-multiplayer/mojang-server-provenance.json`; vanilla startup, item NBT, two joins, and shutdown: `docs/evidence/09-multiplayer/vanilla-server.log`. Production mod metadata is also client-only (`src/main/resources/fabric.mod.json`).
- [x] Vanilla server: separate real Mojang 26.2 process bound to `127.0.0.1` on an available port; real item use, load/edit/Done, exact file assertion, server-console item-data assertion, disconnect/reconnect and exact reopened-book pages — `MultiplayerJournalGameTest.vanillaServerSavesAfterReconnect`; screenshots `docs/evidence/09-multiplayer/0008_09-vanilla-server-loaded.png`, `0009_09-vanilla-server-edited.png`, and `0010_09-vanilla-server-reconnected.png`.
- [x] Book opens by giving and using a real book and quill, never `setScreen` — existing `JournalSyncClientGameTest.openBookTheRealWay` and new `MultiplayerJournalGameTest.useBook` (`give @a writable_book`, right mouse button); the vanilla log records both the give and edited server-owned writable-book component.
- [x] LAN host world is actually published with `publishServer(MultiplayerScope.LAN, CREATIVE, true, port)` and `isPublished()` asserted; load/edit/Done checks exact file bytes and server-owned pages — `MultiplayerJournalGameTest.lanHostPublishesAndSaves`; `docs/evidence/09-multiplayer/0006_09-lan-host-loaded.png`, `0007_09-lan-host-edited.png`, and publication in `client-results.log`. The dedicated-server tests exercise the joining-client network path; no second client is required by this ticket.

## How it works

- `gradle/vanilla-test-server.gradle` resolves the project's pinned `minecraft_version` through Mojang's version manifest, verifies the version metadata SHA-1 and the downloaded server SHA-1, and revalidates cached jar bytes on every invocation. The jar and provenance cache live under `build/vanilla-test-server/`; no jar is committed. `runProductionClientGameTest` depends on this task and passes the absolute jar path to the test JVM, so CI's existing command runs these cases too.
- `VanillaTestServer` creates a fresh directory under `build/vanilla-test-runs/`, writes EULA acceptance there only, sets offline mode and loopback binding, and starts the Mojang jar in a separate JVM (256–768 MiB heap). A dynamically selected localhost port avoids the Fabric test server's port. Console commands give the book and query its server-owned data. The test waits for the edited text in that query before disconnecting, then reconnects and opens the returned item without pressing Journal: the two exact pages must already be present.
- Try-with-resources owns the server, and a `finally` disconnects the client. Shutdown sends `stop`, waits, and escalates to process termination if necessary. The successful run's server log ends with all dimensions saved; a post-run JVM listing confirmed neither this worktree's client nor its vanilla server remained.
- Both new world cases use a two-page note; page 1 is edited, page 2 must remain unchanged. File bytes are asserted after Done. Screenshots show loaded/edited screens; assertions, server NBT, and the reconnect screenshot establish persistence. `docs/evidence/09-multiplayer/client-results.log` records the successful final assertions.
- Existing production behavior is unchanged. Ticket 05–08 features are not prerequisites for these short valid notes. When integrating their work, keep both game-test entrypoints in `src/gametest/resources/fabric.mod.json` and rerun the complete suite; ticket 08 must preserve the explicit path override used by 04 and 09 (or migrate both tests to its configuration seam).

## Validation (2026-10-20)

All Gradle invocations used the supplied JDK/cache/Xvfb wrapper and the shared `flock` lock.

- Initial `build`: `BUILD SUCCESSFUL in 11s`; 13 JUnit tests, zero failures/errors/skips.
- Final `build runProductionClientGameTest`: `BUILD SUCCESSFUL in 1m 46s` (16 actionable tasks: 2 executed, 14 up-to-date). Both registered client test classes completed. Actual assertion summary: `Ticket 09: LAN host and unmodified vanilla server load/edit/save/reconnect assertions passed`.
- Final standalone `build` after all edits: `BUILD SUCCESSFUL in 1s` (10 actionable tasks: 10 up-to-date).
- Expected test-launcher noise: synthetic offline account authentication/Realms failures, unavailable narrator library, and upstream Java native/Unsafe warnings. These did not fail the tests. No new mod/server failure remained.

## Manual LAN/vanilla procedure for ticket 10

1. In Prism Fabric 26.2, install Journal Sync and Fabric API **on the joining client only**. Configure the journal path to a disposable note containing two short pages separated by `<!-- page -->` on its own line. Keep a copy of the note.
2. Have another player open a 26.2 world to LAN, allow commands, and give your joining player a book and quill. Join from Prism, hold the book and right-click, press Journal, check both pages, type a distinctive suffix, and click Done. Confirm the suffix in the local note. Disconnect/rejoin and open the same book **without pressing Journal**; the suffix and second page must still be there.
3. Repeat against your own unmodified Mojang 26.2 dedicated server with **no server mods installed**. Give the book from its console (`give <player> writable_book`). Check the local note and disconnect/rejoin persistence in the same way. Use normal server authentication settings for the real server; offline mode above is only for the isolated automated test.
4. Record Prism/mod versions, server type/address (no credentials), screenshots, and pass/fail in ticket 10. Ticket 10 still owns human confirmation of the real launcher, vault/Obsidian, LAN join, and external network server; those are not claimed by these localhost tests.


## Ticket 07 / 08 integration

Both game-test entrypoints and the Mojang server task remain registered. The combined
integration reruns the original Fabric dedicated-server reconnect, real vanilla-server
reconnect and published LAN-host assertions with config, feedback, backups and sidecar
lifecycle installed. Current evidence: `docs/evidence/integration-07-08/verification.md`.


## Final integration with ticket 05

Codec/cap integration is resolved; original backup, binding, config, keybind,
conflict, LAN and vanilla-server contracts remain covered. Final combined
validation and retained human checks: `docs/evidence/final-integration/verification.md`.
