package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Comparator;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

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

		Journal.SaveOutcome outcome = Journal.save(file, List.of("one", "two\nlines", "three"), token, dir.resolve("game"), 20);

		Assertions.assertEquals(new Journal.SaveOutcome.Saved(), outcome);
		Assertions.assertEquals(
			"one\n" + MARKER + "\ntwo\nlines\n" + MARKER + "\nthree", Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void aMultiPageNoteSavesBackUnchanged() throws Exception {
		String note = "---\nmc-sync: true\n---\n# Day 1\nfound iron\n" + MARKER + "\n\n" + MARKER + "\nünïcode ✓\n";
		Path file = writeFile(note);
		Journal.Loaded loaded = Journal.load(file);

		Journal.save(file, loaded.pages(), loaded.token(), dir.resolve("game"), 20);

		Assertions.assertEquals(note, Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void savingToAMissingFileCreatesItAndItsFolder() throws Exception {
		Path file = dir.resolve("vault").resolve("notes").resolve("journal.md");
		Journal.Loaded loaded = Journal.load(file);

		Journal.SaveOutcome outcome = Journal.save(file, List.of("fresh"), loaded.token(), dir.resolve("game"), 20);

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

	@Test
	void loadBacksUpThePreviousBookAndReplacesAStaleLock() throws Exception {
		Path file = writeFile("journal text");
		Path lock = Journal.lockPath(file);
		Files.writeString(lock, "crashed process");

		Journal.LoadOutcome outcome = Journal.load(file, List.of("old page", "old second"), dir.resolve("game"), 20);

		Journal.LoadOutcome.LoadedWithBackup loaded = Assertions.assertInstanceOf(Journal.LoadOutcome.LoadedWithBackup.class, outcome);
		Assertions.assertEquals(List.of("journal text"), loaded.loaded().pages());
		Assertions.assertEquals("old page\n" + MARKER + "\nold second", Files.readString(loaded.backup()));
		Assertions.assertEquals("Journal Sync is open\n", Files.readString(lock));
	}

	@Test
	void saveBacksUpTheOldFileBeforeEveryOverwriteAndRotatesToRequestedCount() throws Exception {
		Path file = writeFile("value-0");
		Path gameDirectory = dir.resolve("game");
		for (int i = 1; i <= 22; i++) {
			Journal.SaveOutcome outcome = Journal.save(file, List.of("value-" + i), Journal.LoadToken.ABSENT, gameDirectory, 20);
			Assertions.assertEquals(new Journal.SaveOutcome.Saved(), outcome);
			Thread.sleep(2);
		}
		Path backups = gameDirectory.resolve("journal-sync-backups");
		List<Path> retained;
		try (var stream = Files.list(backups)) {
			retained = stream.sorted(Comparator.comparingLong(path -> {
				try { return Files.getLastModifiedTime(path).toMillis(); }
				catch (IOException e) { throw new java.io.UncheckedIOException(e); }
			})).toList();
		}
		Assertions.assertEquals(20, retained.size());
		Assertions.assertEquals("value-2", Files.readString(retained.getFirst()));
		Assertions.assertEquals("value-21", Files.readString(retained.getLast()));
		Assertions.assertEquals("value-22", Files.readString(file));
	}

	@Test
	void lockCanBeRemovedIdempotently() throws Exception {
		Path file = dir.resolve("missing").resolve("journal.md");
		Assertions.assertEquals(new Journal.LockOutcome.Updated(), Journal.createLock(file));
		Assertions.assertTrue(Files.exists(Journal.lockPath(file)));
		Assertions.assertEquals(new Journal.LockOutcome.Updated(), Journal.removeLock(file));
		Assertions.assertFalse(Files.exists(Journal.lockPath(file)));
		Assertions.assertEquals(new Journal.LockOutcome.Updated(), Journal.removeLock(file));
	}

	@Test
	void loadReportsBackupAndLockFilesystemFailuresWithoutThrowing() throws Exception {
		Path blocked = dir.resolve("blocked");
		Files.writeString(blocked, "not a directory");
		Journal.LoadOutcome backupFailure = Journal.load(dir.resolve("journal.md"), List.of("previous"), blocked, 20);
		Assertions.assertInstanceOf(Journal.LoadOutcome.Failed.class, backupFailure);

		Journal.LoadOutcome lockFailure = Journal.load(blocked.resolve("journal.md"), List.of(), dir.resolve("game"), 20);
		Assertions.assertInstanceOf(Journal.LoadOutcome.Failed.class, lockFailure);
		Assertions.assertInstanceOf(Journal.LockOutcome.Failed.class, Journal.createLock(blocked.resolve("journal.md")));
		Assertions.assertInstanceOf(Journal.LockOutcome.Failed.class, Journal.removeLock(blocked.resolve("journal.md")));
	}

	@Test
	void saveReportsMissingReadOnlyAndUnwritableJournalTargetsWithoutThrowing() throws Exception {
		Path gameDirectory = dir.resolve("game");
		Assertions.assertInstanceOf(Journal.SaveOutcome.Saved.class,
			Journal.save(dir.resolve("missing-parent").resolve("journal.md"), List.of("new"), Journal.LoadToken.ABSENT, gameDirectory, 20));
		Path directoryTarget = dir.resolve("directory-target");
		Files.createDirectory(directoryTarget);
		Assertions.assertInstanceOf(Journal.SaveOutcome.Failed.class,
			Journal.save(directoryTarget, List.of("x"), Journal.LoadToken.ABSENT, gameDirectory, 20));
		Path blocked = dir.resolve("blocked-file");
		Files.writeString(blocked, "file");
		Assertions.assertInstanceOf(Journal.SaveOutcome.Failed.class,
			Journal.save(blocked.resolve("journal.md"), List.of("x"), Journal.LoadToken.ABSENT, gameDirectory, 20));
		Path readOnly = dir.resolve("read-only");
		Files.createDirectory(readOnly);
		Set<PosixFilePermission> original = Files.getPosixFilePermissions(readOnly);
		try {
			Files.setPosixFilePermissions(readOnly, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
			Assertions.assertInstanceOf(Journal.SaveOutcome.Failed.class,
				Journal.save(readOnly.resolve("journal.md"), List.of("x"), Journal.LoadToken.ABSENT, gameDirectory, 20));
			Assertions.assertInstanceOf(Journal.LockOutcome.Failed.class, Journal.createLock(readOnly.resolve("journal.md")));
			Journal.LoadOutcome load = Journal.load(readOnly.resolve("journal.md"), List.of(), dir.resolve("game-readonly"), 20);
			Assertions.assertInstanceOf(Journal.LoadOutcome.Failed.class, load);
		} finally {
			Files.setPosixFilePermissions(readOnly, original);
		}
	}
}
