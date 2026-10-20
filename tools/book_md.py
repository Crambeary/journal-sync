"""Convert between a Book Copy export and a markdown note.

A Book Copy export is an uncompressed NBT file (no extension) holding one
`pages` list of strings, found in <instance>/minecraft/config/bookcopy/.

A note is the book's pages joined by a marker line, so
markdown -> JSON -> markdown is byte-identical. Notes are LF-only.

    book_md.py to-md   book  note.md
    book_md.py to-book note.md  book
"""

import argparse
import struct
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


def markdown_to_book(text):
    pages = text.split(SEPARATOR) if SEPARATOR in text else _paginate(text)
    if len(pages) > MAX_PAGES:
        raise TooManyPages(f"{len(pages)} pages exceeds the {MAX_PAGES}-page book limit")
    return {"pages": pages}


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


TAG_END, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 8, 9, 10


def _mutf8_encode(text):
    # Modified UTF-8: NUL as C0 80, supplementary chars as two 3-byte surrogates.
    raw = text.encode("utf-16-le", "surrogatepass")
    units = struct.unpack(f"<{len(raw) // 2}H", raw)
    return b"".join(
        b"\xc0\x80" if u == 0 else chr(u).encode("utf-8", "surrogatepass") for u in units
    )


def _mutf8_decode(data):
    return data.replace(b"\xc0\x80", b"\x00").decode("utf-8", "surrogatepass").encode(
        "utf-16", "surrogatepass"
    ).decode("utf-16")


def book_to_nbt(book):
    pages = book["pages"]
    out = bytearray(struct.pack(">BHBH", TAG_COMPOUND, 0, TAG_LIST, 5) + b"pages")
    out += struct.pack(">BI", TAG_STRING, len(pages))
    for page in pages:
        encoded = _mutf8_encode(page)
        out += struct.pack(">H", len(encoded)) + encoded
    out.append(TAG_END)
    return bytes(out)


def nbt_to_book(data):
    try:
        root, rootname, tag, namelen = struct.unpack_from(">BHBH", data)
        name = data[6 : 6 + namelen]
        (kind, count), at = struct.unpack_from(">BI", data, 6 + namelen), 11 + namelen
        if root != TAG_COMPOUND or tag != TAG_LIST or name != b"pages" or kind != TAG_STRING:
            raise ValueError
        pages = []
        for _ in range(count):
            (n,) = struct.unpack_from(">H", data, at)
            pages.append(_mutf8_decode(data[at + 2 : at + 2 + n]))
            at += 2 + n
        if data[at:] != bytes([TAG_END]):
            raise ValueError
    except (struct.error, ValueError, UnicodeError):
        raise ValueError("not a Book Copy export (expected NBT compound with a 'pages' list)")
    return {"pages": pages}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("to-md", "to-book"):
        sub.add_parser(name).add_argument("source", type=Path)
        sub.choices[name].add_argument("dest", type=Path)
    args = parser.parse_args(argv)

    try:
        if args.command == "to-md":
            book = nbt_to_book(args.source.read_bytes())
            with open(args.dest, "w", encoding="utf-8", newline="") as f:
                f.write(book_to_markdown(book))
        else:
            with open(args.source, encoding="utf-8", newline="") as f:
                book = markdown_to_book(f.read())
            args.dest.write_bytes(book_to_nbt(book))
    except ValueError as e:  # includes TooManyPages
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
