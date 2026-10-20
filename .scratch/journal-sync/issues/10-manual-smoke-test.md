# 10 — Manual smoke test

**What to build:** The user confirms real-world behavior in their own Prism Launcher Fabric 26.2 instance before the project is called done: single-player, joining a LAN world, and their own dedicated server, journaling into their actual vault note.

**Blocked by:** 05, 06, 07, 08, 09.

**Status:** ready-for-human

**Already covered by automated tests (tickets 04, 06 to 09), so this only needs a quick confirmation:** loading alongside the Prism instance's real mod set, a vanilla Mojang server, a LAN host, the real item-use path, the keybind, external edits and stale locks. What stays manual: launching through Prism itself on the Wayland desktop, the real vault path, Obsidian rendering the CSS snippet, and joining the user's own server over the network.

- [ ] Single-player: load, edit, close → vault note updated; Obsidian edit → reload shows it
- [ ] LAN: joining a LAN-hosted world, same flow works
- [ ] Own dedicated server (vanilla, no server mod): same flow works
- [ ] Conflict prompt seen once in real use and behaved as expected
- [ ] Backups present in the game directory; vault contains no mod clutter besides the journal note (and lock only while open)
- [ ] User signs off in this ticket
