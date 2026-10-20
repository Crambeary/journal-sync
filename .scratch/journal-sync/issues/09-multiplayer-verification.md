# 09 — Multiplayer verification

**What to build:** Proof that the load and save-on-close flow works when the book lives on a server, not just in single-player. LAN uses the same client path as a dedicated server, so a dedicated-server check covers it. Depending on ticket 02's finding, this is either an automated client game test against a dedicated server (scenario 5) or a written manual procedure the user follows.

**Blocked by:** 04 — Tracer bullet; and the dedicated-server finding from 02 — Walking skeleton + test-harness spike.

**Status:** ready-for-agent

- [ ] If the test API supports it: client game test connects to a dedicated server, loads the journal into a book and quill, edits, closes, and the file is updated (screenshot); the edited book persists server-side after reconnect
- [ ] Otherwise: a step-by-step manual procedure in the repo for testing against a vanilla dedicated server and a LAN-hosted world, which ticket 10 uses
- [ ] Confirmed no server-side install is needed (vanilla server)
