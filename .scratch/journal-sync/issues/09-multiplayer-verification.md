# 09 — Multiplayer verification

**What to build:** Proof that the load and save-on-close flow works when the book lives on a server, not just in single-player. LAN uses the same client path as a dedicated server, so a dedicated-server check covers it. Depending on ticket 02's finding, this is either an automated client game test against a dedicated server (scenario 5) or a written manual procedure the user follows.

**Blocked by:** 04 — Tracer bullet; and the dedicated-server finding from 02 — Walking skeleton + test-harness spike.

**Status:** ready-for-agent

**Approach (from ticket 02's finding):** the client game-test API can start and connect to a dedicated server (`context.worldBuilder().createServer()` then `server.connect()`), type into the book screen and click the Journal button, so take the automated path; the manual procedure is only needed for the "vanilla server" and LAN checks that ticket 10 covers. Give the player a real book and quill with `server.runCommand("give ...")` and open it by using the item, so the server holds the item the edit packet refers to. After reconnecting, read the item back with `connection.getServerPlayer()`. The test server is a Fabric server with Fabric API, so "vanilla server needs no install" is confirmed by the client-only `fabric.mod.json` (`"environment": "client"`) plus the manual smoke test.

- [ ] If the test API supports it: client game test connects to a dedicated server, loads the journal into a book and quill, edits, closes, and the file is updated (screenshot); the edited book persists server-side after reconnect
- [ ] Otherwise: a step-by-step manual procedure in the repo for testing against a vanilla dedicated server and a LAN-hosted world, which ticket 10 uses
- [ ] Confirmed no server-side install is needed (vanilla server)
