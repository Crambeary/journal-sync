package com.crambeary.journalsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JournalIntegrationTest {
	@TempDir Path dir;

	private List<String> backups(Path game) throws Exception {
		try (var paths = Files.list(game.resolve("journal-sync-backups"))) {
			return paths.map(p -> {
				try { return Files.readString(p); }
				catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
			}).toList();
		}
	}

	@Test void parsedCountRotatesBothLoadAndSaveBackups() throws Exception {
		Path config = dir.resolve("config.json");
		Files.writeString(config, "{\"backupCount\":2}");
		int count = JournalConfig.read(config).backupCount();
		Path file = dir.resolve("note.md");
		Files.writeString(file, "original");
		for (int i = 0; i < 5; i++) {
			assertInstanceOf(Journal.LoadOutcome.LoadedWithBackup.class, Journal.load(file, List.of("book-" + i), dir, count));
			Thread.sleep(5);
		}
		assertEquals(java.util.Set.of("book-3", "book-4"), java.util.Set.copyOf(backups(dir)));
		for (int i = 0; i < 4; i++) {
			assertInstanceOf(Journal.SaveOutcome.Saved.class,
				Journal.save(file, List.of("saved-" + i), Journal.load(file).token(), dir, count));
			Thread.sleep(5);
		}
		assertEquals(java.util.Set.of("saved-1", "saved-2"), java.util.Set.copyOf(backups(dir)));
	}

	@Test void keepBookBacksUpExactExternalBytesButOtherChoicesPreserveBytesAndTime() throws Exception {
		Path file = dir.resolve("note.md");
		Files.writeString(file, "original");
		var token = Journal.load(file).token();
		String external = "---\r\nmc-sync: true\r\n---\r\nexternal\r\n";
		Files.writeString(file, external);
		var modified = Files.getLastModifiedTime(file);
		assertInstanceOf(Journal.SaveOutcome.Conflict.class, Journal.save(file, List.of("mine"), token, dir, 2));
		assertEquals(JournalFeedback.Kind.CONFLICT, JournalFeedback.saved(new Journal.SaveOutcome.Conflict(), file).kind());
		var kept = Journal.save(file, List.of("mine"), token, Journal.Resolution.KEEP_FILE, dir, 2);
		assertEquals(JournalFeedback.Kind.KEPT_FILE, JournalFeedback.saved(kept, file).kind());
		var copied = Journal.save(file, List.of("mine"), token, Journal.Resolution.CONFLICT_COPY, dir, 2);
		assertEquals(JournalFeedback.Kind.CONFLICT_COPY, JournalFeedback.saved(copied, file).kind());
		assertEquals(external, Files.readString(file));
		assertEquals(modified, Files.getLastModifiedTime(file));
		assertFalse(Files.exists(dir.resolve("journal-sync-backups")));
		assertInstanceOf(Journal.SaveOutcome.Saved.class,
			Journal.save(file, List.of("mine"), token, Journal.Resolution.KEEP_BOOK, dir, 2));
		assertEquals(List.of(external), backups(dir));
		assertEquals("mine", Files.readString(file));
	}

	@Test void backupFailurePreventsNormalAndKeepBookOverwrite() throws Exception {
		Path file = dir.resolve("note.md");
		Files.writeString(file, "original");
		var token = Journal.load(file).token();
		Path blocked = dir.resolve("blocked");
		Files.writeString(blocked, "not a directory");
		assertInstanceOf(Journal.SaveOutcome.Failed.class, Journal.save(file, List.of("mine"), token, blocked, 2));
		assertEquals("original", Files.readString(file));
		Files.writeString(file, "external");
		assertInstanceOf(Journal.SaveOutcome.Failed.class,
			Journal.save(file, List.of("mine"), token, Journal.Resolution.KEEP_BOOK, blocked, 2));
		assertEquals("external", Files.readString(file));
	}

	@Test void missingFileLoadsOnceWithBackupAndLockThenCreatesOnSave() throws Exception {
		Path file = dir.resolve("new/note.md");
		var result = JournalFeedback.load(file, List.of("existing book"), dir, 2);
		assertTrue(result.succeeded());
		assertEquals(JournalFeedback.Kind.MISSING, result.feedback().kind());
		assertFalse(Files.exists(file));
		assertTrue(Files.exists(Journal.lockPath(file)));
		assertEquals(List.of("existing book"), backups(dir));
		assertInstanceOf(Journal.SaveOutcome.Saved.class, Journal.save(file, result.loaded().pages(), result.loaded().token(), dir, 2));
		assertEquals("existing book", Files.readString(file));
	}

	@Test void backupLockAndReadFailuresMapToLoadFailureAndRejectedReloadKeepsLock() throws Exception {
		Path file = dir.resolve("note.md");
		Files.writeString(file, "original");
		assertTrue(JournalFeedback.load(file, List.of("previous"), dir, 2).succeeded());
		String lock = Files.readString(Journal.lockPath(file));
		Files.delete(file);
		Files.createDirectory(file);
		assertEquals(JournalFeedback.Kind.LOAD_FAILED, JournalFeedback.load(file, List.of("book"), dir, 2).feedback().kind());
		assertEquals(lock, Files.readString(Journal.lockPath(file)));
		Path blocked = dir.resolve("blocked");
		Files.writeString(blocked, "not a directory");
		assertEquals(JournalFeedback.Kind.LOAD_FAILED,
			JournalFeedback.load(dir.resolve("missing.md"), List.of(), blocked, 2).feedback().kind());
		Path lockTarget = dir.resolve("locked.md");
		Files.createDirectory(Journal.lockPath(lockTarget));
		assertEquals(JournalFeedback.Kind.LOAD_FAILED, JournalFeedback.load(lockTarget, List.of(), dir, 2).feedback().kind());
	}

	@Test void capFeedbackRefusesFreshLoadAndReloadWithoutBackupOrLockChanges() throws Exception {
		Path file = dir.resolve("note.md");
		String overCap = String.join("\n<!-- page -->\n", java.util.Collections.nCopies(120, "page"));
		Files.writeString(file, overCap);
		var time = Files.getLastModifiedTime(file);
		for (var result : List.of(JournalFeedback.load(file, List.of("book")),
			JournalFeedback.load(file, List.of("book"), dir, 2))) {
			assertFalse(result.succeeded());
			assertEquals(JournalFeedback.Kind.CAP_EXCEEDED, result.feedback().kind());
			assertEquals(120, result.feedback().pageCount());
			assertEquals(100, result.feedback().maxPages());
		}
		assertFalse(Files.exists(Journal.lockPath(file)));
		assertFalse(Files.exists(dir.resolve("journal-sync-backups")));
		assertEquals(overCap, Files.readString(file));
		assertEquals(time, Files.getLastModifiedTime(file));

		Files.writeString(file, "original");
		var loaded = JournalFeedback.load(file, List.of("previous"), dir, 2).loaded();
		var lockTime = Files.getLastModifiedTime(Journal.lockPath(file));
		var before = backups(dir);
		Files.writeString(file, overCap);
		time = Files.getLastModifiedTime(file);
		assertFalse(JournalFeedback.load(file, loaded.pages(), dir, 2).succeeded());
		assertEquals(lockTime, Files.getLastModifiedTime(Journal.lockPath(file)));
		assertEquals(before, backups(dir));
		assertInstanceOf(Journal.SaveOutcome.Conflict.class,
			Journal.save(file, loaded.pages(), loaded.token(), dir, 2));
		assertEquals(overCap, Files.readString(file));
		assertEquals(time, Files.getLastModifiedTime(file));
	}

	@Test void everySaveOverloadValidatesBeforeBackupOverwriteOrConflictCopy() throws Exception {
		Path file = dir.resolve("note.md");
		Files.writeString(file, "original");
		var token = Journal.load(file).token();
		for (boolean conflict : List.of(false, true)) {
			Files.writeString(file, conflict ? "external" : "original");
			var time = Files.getLastModifiedTime(file);
			for (var pages : List.of(java.util.Collections.nCopies(120, "page"),
				List.of("x".repeat(net.minecraft.world.item.component.WritableBookContent.PAGE_EDIT_LENGTH + 1)))) {
				var results = new java.util.ArrayList<Journal.SaveOutcome>();
				results.add(Journal.save(file, pages, token));
				results.add(Journal.save(file, pages, token, dir, 2));
				for (var resolution : Journal.Resolution.values()) {
					results.add(Journal.save(file, pages, token, resolution));
					results.add(Journal.save(file, pages, token, resolution, dir, 2));
				}
				for (var result : results) {
					if (pages.size() == 120) {
						assertEquals(new Journal.SaveOutcome.OverCap(120, 100), result);
						assertEquals(JournalFeedback.Kind.CAP_EXCEEDED, JournalFeedback.saved(result, file).kind());
					} else assertInstanceOf(Journal.SaveOutcome.Failed.class, result);
				}
			}
			assertEquals(conflict ? "external" : "original", Files.readString(file));
			assertEquals(time, Files.getLastModifiedTime(file));
			try (var files = Files.list(dir)) { assertEquals(List.of(file), files.toList()); }
		}
	}

	@Test void normalCopyAndPriorBookBackupUseSameLosslessEncoding() throws Exception {
		Path file = dir.resolve("note.md");
		Files.writeString(file, "original");
		var pages = List.of("---\r\nmc-sync: true\r\n---\r\n漢🐻\n", "", "trailing\n\n", "");
		String encoded = String.join("\n<!-- page -->\n", pages);
		var loaded = ((Journal.LoadOutcome.LoadedWithBackup) Journal.load(file, pages, dir, 20)).loaded();
		assertEquals(List.of(encoded), backups(dir));
		assertInstanceOf(Journal.SaveOutcome.Saved.class, Journal.save(file, pages, loaded.token(), dir, 20));
		assertEquals(encoded, Files.readString(file));
		assertEquals(pages, Journal.load(file).pages());
		Files.writeString(file, "external\r\n");
		var copied = (Journal.SaveOutcome.SavedAsConflictCopy) Journal.save(file, pages, loaded.token(), Journal.Resolution.CONFLICT_COPY, dir, 20);
		assertEquals(encoded, Files.readString(copied.copy()));
		assertEquals(pages, Journal.load(copied.copy()).pages());
	}
}
