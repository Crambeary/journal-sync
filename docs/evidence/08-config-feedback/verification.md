# Ticket 08 verification — 2026-10-20

Baseline locked wrapper build: `BUILD SUCCESSFUL in 12s`.
Final locked wrapper tasks `build runProductionClientGameTest`: **`BUILD SUCCESSFUL in 1m 3s`**,
`15 actionable tasks: 11 executed, 4 up-to-date`. All Gradle invocations used the supplied
JDK-25 wrapper under the shared game-test flock; production client ran under real Xvfb.

Unit results from the final build:

- `com.crambeary.journalsync.BookLimitsTest`: 3 tests, 0 failures, 0 errors.
- `com.crambeary.journalsync.JournalConfigTest`: 5 tests, 0 failures, 0 errors.
- `com.crambeary.journalsync.JournalTest`: 10 tests, 0 failures, 0 errors.

The Fabric production client test completed both singleplayer and dedicated-server scenarios,
including vanilla reconnect persistence checks. `feedbackAndReboundKey` verifies the registered
mapping in Controls, rebinds through the vanilla Controls key handler, presses the resulting
mapping via Fabric test input, asserts page equality with the button, and checks the actual
latest client-chat message after each operation. It also checks separate character events do
not insert the shortcut into the loaded pages. Existing button round trip and unbound-book
scenarios still pass.

Screenshots (copied unchanged from the successful run):

- [0000_singleplayer-rebound-key-loaded.png](0000_singleplayer-rebound-key-loaded.png)
- [0001_singleplayer-saved-feedback.png](0001_singleplayer-saved-feedback.png)
- [0002_singleplayer-unreadable-error.png](0002_singleplayer-unreadable-error.png)
- [0003_singleplayer-invalid-config.png](0003_singleplayer-invalid-config.png)
- [0004_singleplayer-missing-warning.png](0004_singleplayer-missing-warning.png)
- [0005_singleplayer-save-error.png](0005_singleplayer-save-error.png)
- [0009_dedicated-server-rebound-key-loaded.png](0009_dedicated-server-rebound-key-loaded.png)
- [0010_dedicated-server-saved-feedback.png](0010_dedicated-server-saved-feedback.png)
- [0011_dedicated-server-unreadable-error.png](0011_dedicated-server-unreadable-error.png)
- [0012_dedicated-server-invalid-config.png](0012_dedicated-server-invalid-config.png)
- [0013_dedicated-server-missing-warning.png](0013_dedicated-server-missing-warning.png)
- [0014_dedicated-server-save-error.png](0014_dedicated-server-save-error.png)

Visually inspected the singleplayer loaded, saved, and unreadable screenshots: load shows the
Bound button and loaded toast; save shows the saved toast; unreadable shows the error toast
with the unchanged book text. Other screenshots capture invalid config, missing-file warning,
and close-time write failure, repeated for the dedicated-server connection.

Expected environment noise: offline Fabric test identity causes Mojang/Realms authentication
errors and a profile lookup timeout; narrator library flite is absent; vanilla reports an
anisotropic filtering option warning; Java reports native/Unsafe deprecations. These did not
fail the task. The two deliberately forced save failures log errors as well as visible feedback.

Not claimed by this evidence: page-cap enforcement/120-page feedback (ticket 05), configured
backup rotation (07), or a real-launcher human smoke test. See the ticket's integration list.
