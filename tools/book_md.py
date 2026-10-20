"""Convert between a Book Copy export (JSON) and a markdown note.

A note is the book's pages joined by a marker line, so
markdown -> JSON -> markdown is byte-identical. Notes are LF-only.

    book_md.py to-md   book.json  note.md
    book_md.py to-json note.md    book.json
"""

import argparse
import json
import sys
from pathlib import Path

MARKER = "<!-- page -->"
SEPARATOR = f"\n{MARKER}\n"
PAGE_LIMIT = 1024  # WritableBookContent.PAGE_EDIT_LENGTH, in UTF-16 units
MAX_PAGES = 100  # WritableBookContent.MAX_PAGES


class TooManyPages(ValueError):
    pass


def book_to_markdown(book):
    return SEPARATOR.join(book["pages"])


def markdown_to_book(text, title):
    pages = text.split(SEPARATOR) if SEPARATOR in text else _paginate(text)
    if len(pages) > MAX_PAGES:
        raise TooManyPages(f"{len(pages)} pages exceeds the {MAX_PAGES}-page book limit")
    return {"title": title, "pages": pages}


def _utf16_len(text):
    return len(text.encode("utf-16-le")) // 2


def _fit(text):
    """Longest prefix length (in code points) within PAGE_LIMIT UTF-16 units."""
    units = 0
    for i, ch in enumerate(text):
        units += 2 if ord(ch) > 0xFFFF else 1
        if units > PAGE_LIMIT:
            return i
    return len(text)


def _paginate(text):
    pages = []
    while _utf16_len(text) > PAGE_LIMIT:
        fit = _fit(text)
        cut = text.rfind("\n", 0, fit) + 1  # keep the newline on this page
        if cut == 0:
            cut = fit
        pages.append(text[:cut])
        text = text[cut:]
    pages.append(text)
    return pages


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("to-md", "to-json"):
        p = sub.add_parser(name)
        p.add_argument("source", type=Path)
        p.add_argument("dest", type=Path)
    args = parser.parse_args(argv)

    try:
        if args.command == "to-md":
            book = json.loads(args.source.read_text(encoding="utf-8"))
            with open(args.dest, "w", encoding="utf-8", newline="") as f:
                f.write(book_to_markdown(book))
        else:
            with open(args.source, encoding="utf-8", newline="") as f:
                text = f.read()
            book = markdown_to_book(text, title=args.source.stem)
            args.dest.write_text(
                json.dumps(book, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
    except TooManyPages as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
