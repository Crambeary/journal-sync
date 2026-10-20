package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.HexFormat;
import java.util.List;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WritableBookContent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest {
	private static final String MARKER = "<!-- page -->";

	@TempDir
	Path dir;

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private Path writeFile(String content) throws IOException {
		Path file = dir.resolve("journal.md");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private static String sha256Hex(String content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void loadSplitsTheFileIntoPagesAtMarkerLines() throws Exception {
		Path file = writeFile("first page\n" + MARKER + "\nsecond page\nwith two lines\n" + MARKER + "\nthird");

		Journal.Loaded loaded = Journal.load(file);

		Assertions.assertEquals(List.of("first page", "second page\nwith two lines", "third"), loaded.pages());
	}

	@Test
	void loadTokenCarriesTheSha256OfTheFileBytes() throws Exception {
		String content = "héllo\n" + MARKER + "\nwörld";
		Path file = writeFile(content);

		Journal.Loaded loaded = Journal.load(file);

		Assertions.assertEquals(sha256Hex(content), loaded.token().contentHash());
	}

	@Test
	void loadOfAFileWithoutMarkersIsASingleKnownPage() throws Exception {
		Path file = writeFile("just a short note");

		Assertions.assertEquals(List.of("just a short note"), Journal.load(file).pages());
	}

	@Test
	void loadOfAMissingFileIsAnEmptyJournalWithAnAbsentToken() throws Exception {
		Journal.Loaded loaded = Journal.load(dir.resolve("does-not-exist.md"));

		Assertions.assertEquals(List.of(), loaded.pages());
		Assertions.assertEquals(Journal.LoadToken.ABSENT, loaded.token());
	}

	@Test
	void loadOfAnUnreadablePathFails() {
		Assertions.assertThrows(IOException.class, () -> Journal.load(dir));
	}

	@Test
	void saveWritesPagesJoinedByMarkerLines() throws Exception {
		Path file = writeFile("old");
		Journal.LoadToken token = Journal.load(file).token();

		Journal.SaveOutcome outcome = Journal.save(file, List.of("one", "two\nlines", "three"), token);

		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), outcome);
		Assertions.assertEquals(
			"one\n" + MARKER + "\ntwo\nlines\n" + MARKER + "\nthree", Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void aMultiPageNoteSavesBackUnchanged() throws Exception {
		String note = "---\nmc-sync: true\n---\n# Day 1\nfound iron\n" + MARKER + "\n\n" + MARKER + "\nünïcode ✓\n";
		Path file = writeFile(note);
		Journal.Loaded loaded = Journal.load(file);

		Journal.save(file, loaded.pages(), loaded.token());

		Assertions.assertEquals(note, Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void savingToAMissingFileCreatesItAndItsFolder() throws Exception {
		Path file = dir.resolve("vault").resolve("notes").resolve("journal.md");
		Journal.Loaded loaded = Journal.load(file);

		Journal.SaveOutcome outcome = Journal.save(file, List.of("fresh"), loaded.token());

		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), outcome);
		Assertions.assertEquals("fresh", Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void savingToAnUnwritablePathReportsFailureInsteadOfThrowing() throws Exception {
		Journal.SaveOutcome outcome = Journal.save(dir, List.of("x"), Journal.LoadToken.ABSENT);

		Assertions.assertInstanceOf(Journal.SaveOutcome.Failed.class, outcome);
	}

	@Test
	void loadedPagesFitTheRealBookComponent() throws Exception {
		Path file = writeFile("one\n" + MARKER + "\ntwo");

		List<Filterable<String>> pages = Journal.load(file).pages().stream().map(Filterable::passThrough).toList();

		Assertions.assertEquals(2, new WritableBookContent(pages).pages().size());
	}
	/** Exercise the actual Minecraft codec, not only its constructor (which checks page count). */
	private static List<String> throughRealBook(List<String> pages) {
		WritableBookContent book = new WritableBookContent(pages.stream().map(Filterable::passThrough).toList());
		var encoded = WritableBookContent.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
		return WritableBookContent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().getPages(false).toList();
	}

	@Test
	void generatedMarkedNotesRoundTripThroughRealBookUnchanged() throws Exception {
		Random random = new Random(0x5eed);
		String[] atoms = {"a", " ", "\n", "\r\n", "é", "漢", "🐻", "e\u0301", "# ", "<!-- inline -->"};
		for (int sample = 0; sample < 200; sample++) {
			List<String> pages = new ArrayList<>();
			for (int page = 0, count = 1 + random.nextInt(12); page < count; page++) {
				StringBuilder text = new StringBuilder();
				for (int n = random.nextInt(90); n > 0; n--) {
					text.append(atoms[random.nextInt(atoms.length)]);
				}
				pages.add(text.toString());
			}
			if (sample % 2 == 0) {
				pages.add(""); // trailing empty pages must survive too
			}
			String original = String.join("\n" + MARKER + "\n", pages);
			Path file = writeFile(original);
			Journal.Loaded loaded = Journal.load(file);
			Assertions.assertEquals(pages, loaded.pages(), "sample " + sample);
			Assertions.assertEquals(new Journal.SaveOutcome.Saved(),
				Journal.save(file, throughRealBook(loaded.pages()), loaded.token()));
			Assertions.assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
		}
	}

	@Test
	void generatedMarkerlessNotesPaginateLosslesslyAndBecomeStable() throws Exception {
		Random random = new Random(505);
		String[] atoms = {"x", "\n", "\r\n", "🐻", "漢", "é", " ", "\t"};
		for (int sample = 0; sample < 100; sample++) {
			StringBuilder text = new StringBuilder("---\nmc-sync: true\ntitle: '日記'\n---\n");
			for (int i = random.nextInt(7000); i > 0; i--) {
				text.append(atoms[random.nextInt(atoms.length)]);
			}
			text.append("\n\n");
			assertPaginationStable(text.toString());
		}
	}

	private void assertPaginationStable(String original) throws Exception {
		Path file = writeFile(original);
		Journal.Loaded loaded = Journal.load(file);
		Assertions.assertEquals(original, String.join("", loaded.pages()));
		Assertions.assertEquals(original, Files.readString(file), "load must not write markers");
		List<String> bookPages = throughRealBook(loaded.pages());
		Assertions.assertTrue(bookPages.stream().allMatch(page -> page.length() <= WritableBookContent.PAGE_EDIT_LENGTH));
		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), Journal.save(file, bookPages, loaded.token()));
		String saved = Files.readString(file);
		Assertions.assertEquals(String.join("\n" + MARKER + "\n", bookPages), saved);
		Journal.Loaded reloaded = Journal.load(file);
		Assertions.assertEquals(bookPages, reloaded.pages());
		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), Journal.save(file, throughRealBook(reloaded.pages()), reloaded.token()));
		Assertions.assertEquals(saved, Files.readString(file));
	}

	@Test
	void veryLongLinesAndSurrogateBoundaryPaginateWithoutLosingCharacters() throws Exception {
		assertPaginationStable("x".repeat(WritableBookContent.PAGE_EDIT_LENGTH - 1) + "🐻"
			+ "y".repeat(WritableBookContent.PAGE_EDIT_LENGTH * 3) + "\n\n");
	}

	@Test
	void frontmatterAndTrailingNewlinesRoundTripUntouched() throws Exception {
		String original = "---\r\nmc-sync: true\r\ntitle: '日記 🐻'\r\ntags: [one, two]\r\n---\r\nbody\n\n";
		Path file = writeFile(original);
		Journal.Loaded loaded = Journal.load(file);
		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), Journal.save(file, throughRealBook(loaded.pages()), loaded.token()));
		Assertions.assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
	}

	@Test
	void overCapLoadIsReportedAndLeavesBytesAndModifiedTimeUnchanged() throws Exception {
		String original = String.join("\n" + MARKER + "\n", Collections.nCopies(120, "page"));
		Path file = writeFile(original);
		var modified = Files.getLastModifiedTime(file);
		var refused = Assertions.assertThrows(Journal.PageCapExceededException.class, () -> Journal.load(file));
		Assertions.assertEquals(120, refused.pageCount());
		Assertions.assertTrue(refused.getMessage().contains("100"));
		Assertions.assertEquals(original, Files.readString(file));
		Assertions.assertEquals(modified, Files.getLastModifiedTime(file));
	}

	@Test
	void autoPaginationAlsoRefusesOverCapRatherThanTruncating() throws Exception {
		String original = "x".repeat(WritableBookContent.MAX_PAGES * WritableBookContent.PAGE_EDIT_LENGTH + 1);
		Path file = writeFile(original);
		var refused = Assertions.assertThrows(Journal.PageCapExceededException.class, () -> Journal.load(file));
		Assertions.assertEquals(WritableBookContent.MAX_PAGES + 1, refused.pageCount());
		Assertions.assertEquals(original, Files.readString(file));
	}

	@Test
	void overCapSaveIsReportedBeforeAnyFileOrFolderIsChanged() throws Exception {
		Path file = writeFile("keep me");
		Journal.LoadToken token = Journal.load(file).token();
		var modified = Files.getLastModifiedTime(file);
		List<String> pages = Collections.nCopies(WritableBookContent.MAX_PAGES + 1, "page");
		var expected = new Journal.SaveOutcome.OverCap(pages.size(), WritableBookContent.MAX_PAGES);
		Assertions.assertEquals(expected, Journal.save(file, pages, token));
		Assertions.assertEquals("keep me", Files.readString(file));
		Assertions.assertEquals(modified, Files.getLastModifiedTime(file));
		Path absent = dir.resolve("absent/journal.md");
		Assertions.assertEquals(expected, Journal.save(absent, pages, Journal.LoadToken.ABSENT));
		Assertions.assertFalse(Files.exists(absent.getParent()));
	}

	@Test
	void exactPageAndCountLimitsAreAcceptedByRealBook() throws Exception {
		List<String> pages = Collections.nCopies(WritableBookContent.MAX_PAGES, "x".repeat(WritableBookContent.PAGE_EDIT_LENGTH));
		Path file = dir.resolve("maximum.md");
		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), Journal.save(file, throughRealBook(pages), Journal.LoadToken.ABSENT));
		Assertions.assertEquals(pages, throughRealBook(Journal.load(file).pages()));
	}

	@Test
	void oversizedPageSaveRefusesInsteadOfWritingInvalidBookText() throws Exception {
		Path file = writeFile("keep me");
		Assertions.assertInstanceOf(Journal.SaveOutcome.Failed.class, Journal.save(file,
			List.of("x".repeat(WritableBookContent.PAGE_EDIT_LENGTH + 1)), Journal.load(file).token()));
		Assertions.assertEquals("keep me", Files.readString(file));
	}

	@Test
	void oversizedExplicitPagesArePaginatedAndEmptyPagesPreserved() throws Exception {
		String longPage = "a".repeat(WritableBookContent.PAGE_EDIT_LENGTH + 1);
		Path file = writeFile(String.join("\n" + MARKER + "\n", "", longPage, "", ""));
		Journal.Loaded loaded = Journal.load(file);
		Assertions.assertEquals(List.of("", longPage.substring(0, WritableBookContent.PAGE_EDIT_LENGTH), "a", "", ""), loaded.pages());
		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), Journal.save(file, throughRealBook(loaded.pages()), loaded.token()));
		Assertions.assertEquals(loaded.pages(), Journal.load(file).pages());
	}

	@Test
	void malformedUtf8IsRefusedRatherThanSilentlyReplaced() throws Exception {
		Path file = dir.resolve("invalid.md");
		byte[] original = {(byte) 0xc3, (byte) 0x28};
		Files.write(file, original);
		Assertions.assertThrows(IOException.class, () -> Journal.load(file));
		Assertions.assertArrayEquals(original, Files.readAllBytes(file));
	}

}
