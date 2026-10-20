# 04 — Tracer bullet: Load and save-on-close against one global file

**What to build:** Pressing Journal on an open book and quill fills it with the pages of one global journal markdown file and binds that screen. Closing the bound screen writes its pages back to the file. Books whose screen was never bound are left completely untouched. This establishes core's load/save interface (load returns pages plus a load token carrying the file's hash; save takes pages and the token and returns an outcome) and the thin client layer that calls it. Path comes from a minimal hardcoded/default setting for now; full config is ticket 08.

**Blocked by:** 02 — Walking skeleton + test-harness spike.

**Status:** ready-for-agent

- [ ] Core load/save interface exists and is covered by Fabric Loader JUnit tests through its public interface only
- [ ] Simple round trip works: a multi-page note with `<!-- page -->` markers loads into the book page-for-page and saves back unchanged
- [ ] Client game test (scenario 1): open book and quill, press Journal → book pages match the file (screenshot)
- [ ] Client game test (scenario 2): type text, close → file contains the edited text
- [ ] Client game test: open and close a book without pressing Journal → file untouched
- [ ] Works in the user's real single-player world (quick manual check noted in this ticket)
