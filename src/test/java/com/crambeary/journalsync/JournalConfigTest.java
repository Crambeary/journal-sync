package com.crambeary.journalsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JournalConfigTest {
	@TempDir Path dir;

	@Test void missingConfigCreatesDefaultsAndUsesGlobalPath() throws Exception {
		Path file = dir.resolve("config/journal-sync.json");
		JournalConfig config = JournalConfig.read(file);
		assertEquals(20, config.backupCount());
		assertEquals(dir.resolve("journal-sync/journal.md"), config.resolve(dir, "world", "server", null));
		assertEquals(config, JournalConfig.read(file));
	}

	@Test void templatesAndOverrideResolveThroughCore() throws Exception {
		Path file = dir.resolve("journal-sync.json");
		Files.writeString(file, """
			{"globalPath":"vault/global.md", "worldPathTemplate":"vault/{world-name}.md",
			 "serverPathTemplate":"servers/{server-address}.md", "backupCount":7}
			""");
		JournalConfig config = JournalConfig.read(file);
		assertEquals(7, config.backupCount());
		assertEquals(dir.resolve("vault/global.md"), config.resolve(dir, null, null, null));
		assertEquals(dir.resolve("vault/My_World.md"), config.resolve(dir, "My World", null, null));
		assertEquals(dir.resolve("servers/host_25565.md"), config.resolve(dir, "World", "host:25565", null));
		assertEquals(dir.resolve("override.md"), config.resolve(dir, "World", "host", "override.md"));
		assertEquals(dir.resolve("absolute.md"), config.resolve(dir, null, null, dir.resolve("absolute.md").toString()));
		assertEquals(dir.resolve("vault/.._escape.md"), config.resolve(dir, "../escape", null, null));
	}

	@Test void malformedAndInvalidSettingsAreReportedWithoutReplacingConfig() throws Exception {
		Path file = dir.resolve("journal-sync.json");
		for (String invalid : List.of("{", "[]", "null", "{globalPath:\"note.md\"}", "{} trailing", "{\"globalPath\":false}", "{\"globalPath\":\"\"}",
			"{\"backupCount\":-1}", "{\"backupCount\":1.5}", "{\"backupCount\":\"20\"}",
			"{\"worldPathTemplate\":\"{typo}.md\"}")) {
			Files.writeString(file, invalid);
			assertThrows(java.io.IOException.class, () -> JournalConfig.read(file), invalid);
			assertEquals(invalid, Files.readString(file));
		}
	}

	@Test void missingJournalFeedbackPreservesBookAndCreatesOnlyOnSave() throws Exception {
		Path file = dir.resolve("vault/new.md");
		var result = JournalFeedback.load(file, List.of("existing book"));
		assertTrue(result.succeeded());
		assertEquals(JournalFeedback.Kind.MISSING, result.feedback().kind());
		assertEquals(List.of("existing book"), result.loaded().pages());
		assertFalse(Files.exists(file));
		var saved = Journal.save(file, result.loaded().pages(), result.loaded().token());
		assertEquals(JournalFeedback.Kind.SAVED, JournalFeedback.saved(saved, file).kind());
		assertEquals("existing book", Files.readString(file));
	}

	@Test void unreadableJournalProducesFeedbackWithoutChangingFiles() throws Exception {
		Path sentinel = dir.resolve("untouched.md");
		Files.writeString(sentinel, "unchanged");
		var result = JournalFeedback.load(dir, List.of("book"));
		assertFalse(result.succeeded());
		assertEquals(JournalFeedback.Kind.LOAD_FAILED, result.feedback().kind());
		assertEquals("unchanged", Files.readString(sentinel));
		assertEquals(JournalFeedback.Kind.SAVE_FAILED,
			JournalFeedback.saved(Journal.save(dir, List.of("book"), Journal.LoadToken.ABSENT), dir).kind());
	}
}
