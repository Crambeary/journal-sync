# Page dividers in Obsidian

1. Copy `journal-sync-pages.css` from this folder to your vault's
   `.obsidian/snippets/` folder (create it if needed).
2. Open **Settings → Appearance → CSS snippets**, reload the list, then enable
   **journal-sync-pages**.
3. Add `cssclasses: journal-sync` to your journal's existing YAML frontmatter:

   ```yaml
   ---
   mc-sync: true
   cssclasses: journal-sync
   ---
   ```

4. In Source mode (or Live Preview while the source comment is exposed), put
   `<!-- page -->` alone on a line. Expect a subtle divider above that line.
   CSS cannot match an element's source text, so this snippet styles HTML comment
   lines in tagged notes, including other HTML comments. Obsidian may hide the
   comment token in Live Preview when the cursor is elsewhere; use Source mode
   to see all dividers continuously. The marker text remains editable.
5. Switch to Reading view. Expect the journal prose with no marker text or
   dividers. HTML comments are omitted by the Markdown renderer; the snippet is
   scoped to `.markdown-source-view.mod-cm6`, adds no generated content, and
   cannot reveal comments or add dividers in Reading view.

Manual verification requires Obsidian: perform steps 1–5 in your vault, check a
note without `cssclasses: journal-sync` stays unstyled, and toggle the snippet
back off to confirm it affects appearance only. The automated Minecraft tests
cannot verify Obsidian's DOM, theme, or rendering behavior.
