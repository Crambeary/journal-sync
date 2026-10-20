# 08 — Config, path templates, keybind, and feedback

**What to build:** All settings live in one JSON config file (`journal-sync.json`) in the game's config folder: global journal path, optional per-world and per-server path templates (`{world-name}`, `{server-address}`), and backup count. A missing journal file is created on first save. A rebindable keybind does the same as the Journal button. The player gets clear in-game feedback for success and for every failure (missing or unreadable path, invalid config, cap exceeded).

**Blocked by:** 04 — Tracer bullet.

**Status:** ready-for-agent

- [ ] Config parsed with sensible defaults when missing; invalid config reported, not crashed on
- [ ] Global path used by default; per-world and per-server templates resolve correctly (tested through core)
- [ ] Missing journal file created on first save
- [ ] Keybind appears in controls, is rebindable, and triggers the same action as the button
- [ ] Success and error feedback shown in-game for load and save (screenshot of at least one error case)
- [ ] Client game test presses the rebindable Journal keybind (via the game-test input API) and gets the same result as the button
- [ ] Core test: a 120-page note and a missing or unreadable journal path produce the in-game error feedback without touching the file
