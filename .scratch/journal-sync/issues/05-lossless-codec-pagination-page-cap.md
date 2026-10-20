# 05 — Lossless codec, pagination, page cap, and CSS snippet

**What to build:** Any markdown note round-trips losslessly through the book: frontmatter, unicode, very long lines, empty pages and trailing newlines all survive. A hand-written note without page markers is paginated automatically on load to fit the game's real limits, and markers are written on the next save. A journal over the book's page cap is refused on load and on save with a clear in-game warning — never truncated. Ship an Obsidian CSS snippet that shows page markers as visible dividers.

**Blocked by:** 04 — Tracer bullet.

**Status:** ready-for-agent

- [ ] Property-style round-trip tests over generated text pass against the real book component
- [ ] Marker-less note auto-paginates within real per-page limits; next save writes markers; subsequent loads are stable
- [ ] Frontmatter (including a `mc-sync: true` flag) round-trips untouched
- [ ] Over-cap journal: load refused and save refused, file unchanged, outcome reported
- [ ] Client game test (scenario 4): loading a 120-page file shows the cap warning and leaves the file unchanged (screenshot)
- [ ] CSS snippet in the repo with install instructions; markers remain hidden in Obsidian reading view
