# Book Copy ↔ markdown converter

Glue for the journaling prototype: edit a Minecraft book in your vault using
[Book Copy](https://modrinth.com/mod/book-copy)'s export/import commands.
Python 3 standard library only.

## Workflow

1. In game, hold the book and run Book Copy's export command. It writes an
   uncompressed NBT file (no extension, one `pages` list, no title) to
   `<instance>/minecraft/config/bookcopy/<name>`. A real sample is in
   `tools/fixtures/book1.nbt`.
2. Convert to a note in your vault:
   `python3 tools/book_md.py to-md ~/.local/share/PrismLauncher/instances/26.2/minecraft/config/bookcopy/book1 ~/vault/Minecraft\ Journal.md`
3. Edit the note. Pages are separated by a line containing exactly `<!-- page -->`.
   A note with no markers is auto-paginated (1024 characters per page, preferring
   line breaks; more than 100 pages is refused).
4. Convert back: `python3 tools/book_md.py to-book ~/vault/Minecraft\ Journal.md ~/.local/share/PrismLauncher/instances/26.2/minecraft/config/bookcopy/journal`
5. Import `journal` into a book and quill with Book Copy's import command.

Notes are LF-only. markdown → book → markdown is byte-identical.

## Tests

`cd tools && python3 -m unittest test_book_md`
