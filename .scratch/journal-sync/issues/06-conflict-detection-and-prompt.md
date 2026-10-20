# 06 — Conflict detection and prompt

**What to build:** If the journal file changed on disk while a bound book was open (for example, edited in Obsidian), closing the book does not silently overwrite it. Instead the player is prompted: keep book, keep file, or save book as a conflict copy — with conflict copy as the default. Without a conflict, saving stays last-write-wins with no prompt.

**Blocked by:** 04 — Tracer bullet.

**Status:** ready-for-agent

- [ ] Core detects conflict by comparing the file's current hash to the load token's hash
- [ ] Each resolution tested through core's interface: keep book overwrites the file, keep file leaves it untouched, conflict copy writes a timestamped sibling file and leaves the original untouched
- [ ] No prompt and a normal save when the file is unchanged
- [ ] Prompt defaults to conflict copy
- [ ] Client game test (scenario 3): edit file while bound book is open, close → prompt shown (screenshot), default choice produces a conflict copy
