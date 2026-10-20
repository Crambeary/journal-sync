import json
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
        book = book_md.markdown_to_book(NOTE, title="journal")
        self.assertEqual(book_md.book_to_markdown(book), NOTE)

    def test_pages_are_split_on_marker_lines(self):
        book = book_md.markdown_to_book(NOTE, title="journal")
        self.assertEqual(book["title"], "journal")
        self.assertEqual(len(book["pages"]), 3)
        self.assertEqual(book["pages"][1], "")
        self.assertTrue(book["pages"][0].startswith("---\nmc-sync"))

    def test_empty_pages_at_the_edges_survive(self):
        for note in ["\n<!-- page -->\nx", "x\n<!-- page -->\n", "\n<!-- page -->\n"]:
            book = book_md.markdown_to_book(note, title="t")
            self.assertEqual(book_md.book_to_markdown(book), note)

    def test_json_markdown_json_round_trips(self):
        book = {"title": "t", "pages": ["a\n", "", "b c", "ü"]}
        again = book_md.markdown_to_book(book_md.book_to_markdown(book), title="t")
        self.assertEqual(again, book)


class AutoPagination(unittest.TestCase):
    def test_note_without_markers_is_split_within_page_limit(self):
        paragraphs = "\n\n".join(f"Paragraph {i} " + "word " * 40 for i in range(30))
        book = book_md.markdown_to_book(paragraphs, title="t")
        self.assertGreater(len(book["pages"]), 1)
        for page in book["pages"]:
            self.assertLessEqual(len(page), book_md.PAGE_LIMIT)
        self.assertEqual("".join(book["pages"]), paragraphs)

    def test_single_unbroken_line_longer_than_limit_is_hard_split(self):
        line = "x" * (book_md.PAGE_LIMIT * 2 + 5)
        book = book_md.markdown_to_book(line, title="t")
        self.assertEqual([len(p) for p in book["pages"]], [book_md.PAGE_LIMIT, book_md.PAGE_LIMIT, 5])
        self.assertEqual("".join(book["pages"]), line)

    def test_limit_counts_utf16_units_like_the_game(self):
        text = "🌲" * book_md.PAGE_LIMIT  # 2 UTF-16 units each
        pages = book_md.markdown_to_book(text, title="t")["pages"]
        self.assertEqual(len(pages), 2)
        self.assertEqual("".join(pages), text)
        for page in pages:
            self.assertLessEqual(len(page.encode("utf-16-le")) // 2, book_md.PAGE_LIMIT)

    def test_short_note_is_one_page(self):
        self.assertEqual(book_md.markdown_to_book("hi\n", title="t")["pages"], ["hi\n"])

    def test_empty_note_is_one_empty_page(self):
        self.assertEqual(book_md.markdown_to_book("", title="t")["pages"], [""])

    def test_prefers_breaking_at_a_newline(self):
        text = ("a" * 1000) + "\n" + ("b" * 100)
        pages = book_md.markdown_to_book(text, title="t")["pages"]
        self.assertEqual(pages, ["a" * 1000 + "\n", "b" * 100])

    def test_over_page_cap_is_refused(self):
        text = "x" * (book_md.PAGE_LIMIT * (book_md.MAX_PAGES + 1))
        with self.assertRaises(book_md.TooManyPages):
            book_md.markdown_to_book(text, title="t")


class Cli(unittest.TestCase):
    def test_to_md_and_to_json_files(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            note = d / "My Journal.md"
            note.write_text(NOTE, encoding="utf-8")
            out_json = d / "out.json"
            self.assertEqual(book_md.main(["to-json", str(note), str(out_json)]), 0)
            data = json.loads(out_json.read_text(encoding="utf-8"))
            self.assertEqual(data["title"], "My Journal")
            back = d / "back.md"
            self.assertEqual(book_md.main(["to-md", str(out_json), str(back)]), 0)
            self.assertEqual(back.read_bytes(), NOTE.encode("utf-8"))


if __name__ == "__main__":
    unittest.main()
