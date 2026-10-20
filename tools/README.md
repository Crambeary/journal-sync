# Book Copy ↔ markdown converter

Glue for the journaling prototype: edit a Minecraft book in your vault using
[Book Copy](https://modrinth.com/mod/book-copy)'s export/import commands.
Python 3 standard library only.

## Workflow

1. In game, hold the book and run Book Copy's export command; it writes a JSON file
   (`title` + `pages`).
2. Convert to a note in your vault:
   `python3 tools/book_md.py to-md book.json ~/vault/Minecraft\ Journal.md`
3. Edit the note. Pages are separated by a line containing exactly `<!-- page -->`.
   A note with no markers is auto-paginated (1024 characters per page, preferring
   line breaks; more than 100 pages is refused).
4. Convert back: `python3 tools/book_md.py to-json ~/vault/Minecraft\ Journal.md book.json`
   (the JSON `title` is the note's file name).
5. Import `book.json` into a book and quill with Book Copy's import command.

Notes are LF-only. markdown → JSON → markdown is byte-identical; the title is not
stored in the note.

## Tests

`cd tools && python3 -m unittest test_book_md`
