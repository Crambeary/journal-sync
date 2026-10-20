import tempfile
import unittest
from pathlib import Path

import book_md

NOTE = (
    "---\nmc-sync: true\n---\n# Day 1\nFound iron — ünïcode ✓ 🌲\n"
    "<!-- page -->\n"
    "\n"
    "<!-- page -->\n"
    "Third page\n"
)


class RoundTrip(unittest.TestCase):
    def test_markdown_json_markdown_is_byte_identical(self):
        book = book_md.markdown_to_book(NOTE)
        self.assertEqual(book_md.book_to_markdown(book), NOTE)

    def test_pages_are_split_on_marker_lines(self):
        book = book_md.markdown_to_book(NOTE)
        self.assertEqual(len(book["pages"]), 3)
        self.assertEqual(book["pages"][1], "")
        self.assertTrue(book["pages"][0].startswith("---\nmc-sync"))

    def test_empty_pages_at_the_edges_survive(self):
        for note in ["\n<!-- page -->\nx", "x\n<!-- page -->\n", "\n<!-- page -->\n"]:
            book = book_md.markdown_to_book(note)
            self.assertEqual(book_md.book_to_markdown(book), note)

    def test_json_markdown_json_round_trips(self):
        book = {"pages": ["a\n", "", "b c", "🌲", "ü"]}
        again = book_md.markdown_to_book(book_md.book_to_markdown(book))
        self.assertEqual(again, book)


class AutoPagination(unittest.TestCase):
    def test_note_without_markers_is_split_within_page_limit(self):
        paragraphs = "\n\n".join(f"Paragraph {i} " + "word " * 40 for i in range(30))
        book = book_md.markdown_to_book(paragraphs)
        self.assertGreater(len(book["pages"]), 1)
        for page in book["pages"]:
            self.assertLessEqual(len(page), book_md.PAGE_LIMIT)
        self.assertEqual("".join(book["pages"]), paragraphs)

    def test_single_unbroken_line_longer_than_limit_is_hard_split(self):
        line = "x" * (book_md.PAGE_LIMIT * 2 + 5)
        book = book_md.markdown_to_book(line)
        self.assertEqual([len(p) for p in book["pages"]], [book_md.PAGE_LIMIT, book_md.PAGE_LIMIT, 5])
        self.assertEqual("".join(book["pages"]), line)

    def test_limit_counts_utf16_units_like_the_game(self):
        text = "🌲" * book_md.PAGE_LIMIT  # 2 UTF-16 units each
        pages = book_md.markdown_to_book(text)["pages"]
        self.assertEqual(len(pages), 2)
        self.assertEqual("".join(pages), text)
        for page in pages:
            self.assertLessEqual(len(page.encode("utf-16-le")) // 2, book_md.PAGE_LIMIT)

    def test_short_note_is_one_page(self):
        self.assertEqual(book_md.markdown_to_book("hi\n")["pages"], ["hi\n"])

    def test_empty_note_is_one_empty_page(self):
        self.assertEqual(book_md.markdown_to_book("")["pages"], [""])

    def test_prefers_breaking_at_a_newline(self):
        text = ("a" * 1000) + "\n" + ("b" * 100)
        pages = book_md.markdown_to_book(text)["pages"]
        self.assertEqual(pages, ["a" * 1000 + "\n", "b" * 100])

    def test_over_page_cap_is_refused(self):
        text = "x" * (book_md.PAGE_LIMIT * (book_md.MAX_PAGES + 1))
        with self.assertRaises(book_md.TooManyPages):
            book_md.markdown_to_book(text)


FIXTURE = Path(__file__).parent / "fixtures" / "book1.nbt"  # real Book Copy export


class BookCopyFormat(unittest.TestCase):
    def test_reads_real_book_copy_export(self):
        book = book_md.nbt_to_book(FIXTURE.read_bytes())
        self.assertEqual(len(book["pages"]), 1)
        self.assertTrue(book["pages"][0].startswith("Entry 1, \nInspired by Youtube"))

    def test_real_export_round_trips_byte_for_byte(self):
        raw = FIXTURE.read_bytes()
        self.assertEqual(book_md.book_to_nbt(book_md.nbt_to_book(raw)), raw)

    def test_unicode_nul_and_empty_pages_round_trip(self):
        book = {"pages": ["ünï ✓ 🌲", "", "a\x00b", "line\nbreak"]}
        self.assertEqual(book_md.nbt_to_book(book_md.book_to_nbt(book)), book)

    def test_supplementary_chars_use_modified_utf8_surrogates(self):
        raw = book_md.book_to_nbt({"pages": ["🌲"]})
        self.assertIn(b"\xed\xa0\xbc\xed\xbc\xb2", raw)  # D83C DF32 as two 3-byte units

    def test_rejects_non_book_nbt(self):
        with self.assertRaises(ValueError):
            book_md.nbt_to_book(b"\x0a\x00\x00\x00")


class Cli(unittest.TestCase):
    def test_to_md_and_to_nbt_files(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            note = d / "My Journal.md"
            note.write_text(NOTE, encoding="utf-8")
            out = d / "book"
            self.assertEqual(book_md.main(["to-book", str(note), str(out)]), 0)
            back = d / "back.md"
            self.assertEqual(book_md.main(["to-md", str(out), str(back)]), 0)
            self.assertEqual(back.read_bytes(), NOTE.encode("utf-8"))


if __name__ == "__main__":
    unittest.main()
