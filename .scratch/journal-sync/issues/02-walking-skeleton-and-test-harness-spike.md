# 02 — Walking skeleton + test-harness spike

**What to build:** A Fabric mod for Minecraft 26.2 that builds locally and loads in the user's Prism Fabric 26.2 instance, adding an inert **Journal** button to the book and quill screen. Alongside it, the two test seams from the spec are proven to work on this machine: a Fabric Loader JUnit test that bootstraps Minecraft and reads the real writable-book limits, and a headless (virtual framebuffer) client game test that opens a book and quill, clicks the Journal button, and saves a screenshot. Everything runs locally only — no CI here.

**Blocked by:** 01 — Glue prototype (requires a "go").

**Status:** ready-for-agent

- [ ] Mod builds with a single local command and the jar loads in the Prism Fabric 26.2 instance without errors
- [ ] Journal button visible on the book and quill screen (screenshot as evidence)
- [ ] Unit test task passes, including one test that bootstraps Minecraft and asserts against the real page-count and page-length limits
- [ ] Production client game-test task passes headless under a virtual framebuffer, leaving a named screenshot of the book screen with the button clicked
- [ ] Written finding (in this ticket) on whether the client game-test API on 26.2 can: open the book and quill screen, type text into it, click a mod-added button, and start/connect to a dedicated server
- [ ] The finding is reflected in ticket 09's approach
