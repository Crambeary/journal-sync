package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

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
}
