package com.crambeary.journalsync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.crambeary.journalsync.client.BoundBook;
import com.crambeary.journalsync.client.ConflictScreen;
import net.minecraft.client.gui.components.Button;
import com.crambeary.journalsync.client.JournalSyncClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WritableBookContent;

/**
 * Journal Sync end to end: the book is obtained with {@code give} and opened by using the item, so
 * the server holds the stack the edit packet refers to, and results are read from the server-side item.
 */
@SuppressWarnings("UnstableApiUsage")
public class JournalSyncClientGameTest implements FabricClientGameTest {
	private static final int RIGHT_MOUSE_BUTTON = 1;
	private static final String PAGE_MARKER = "\n<!-- page -->\n";
	private static final List<String> NOTE_PAGES = List.of(
		"# Day 1\nFound iron.",
		"Day 2\nBuilt a farm.",
		"Day 3\nExplored a cave.");
	private static final String NOTE = String.join(PAGE_MARKER, NOTE_PAGES);
	private static final String TYPED = " Edited in game.";

	@Override
	public void runTest(ClientGameTestContext context) {
		try {
			runScenariosInAllWorlds(context);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void runScenariosInAllWorlds(ClientGameTestContext context) throws IOException {
		Path journal = Files.createTempDirectory("journal-sync-gametest").resolve("journal.md");
		System.setProperty(BoundBook.PATH_PROPERTY, journal.toString());
		System.setProperty("journal_sync.game_dir", journal.getParent().resolve("game").toString());

		TestWorldSave save;
		List<String> savedPages;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			singleplayer.getConnection().waitForChunksRender();
			savedPages = runScenarios(context, singleplayer.getServer(), singleplayer.getConnection(), "singleplayer", journal);
		}
		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			assertEquals("singleplayer: book after reconnect", savedPages, serverBookPages(reopened.getServer()));
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				savedPages = runScenarios(context, server, connection, "dedicated-server", journal);
			}
			try (TestDedicatedServerConnection reconnected = server.connect()) {
				reconnected.waitForChunksRender();
				assertEquals("dedicated-server: book after reconnect", savedPages, serverBookPages(server));
			}
		}
	}

	/** Runs the unbound and bound scenarios in one world; returns the pages the server holds at the end. */
	private static List<String> runScenarios(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String name, Path journal)
		throws IOException {
		feedbackAndReboundKey(context, server, connection, name, journal);
		configuredBackupsAndBinding(context, server, connection, name, journal);
		backupAndLockErrors(context, server, connection, journal);
		unboundBookLeavesJournalUntouched(context, server, connection, name, journal);
		escapeDiscardsBoundEdits(context, server, connection, name, journal);
		conflictsOfferAllResolutions(context, server, connection, name, journal);
		List<String> saved = journalLoadsAndSavesOnClose(context, server, connection, name, journal);
		System.out.println("Integration 07/08: " + name + " config/count/binding, backup/lock errors, keybind, missing-file, conflicts and Escape assertions passed");
		return saved;
	}

	private static void feedbackAndReboundKey(ClientGameTestContext context, TestServerContext server,
		TestServerConnection connection, String name, Path journal) throws IOException {
		// Exercise vanilla's Controls rebind handler, then press the mapping through Fabric test input.
		context.setScreen(() -> new KeyBindsScreen(null, net.minecraft.client.Minecraft.getInstance().options));
		context.runOnClient(client -> {
			assertTrue("registered in Controls", java.util.Arrays.asList(client.options.keyMappings).contains(JournalSyncClient.journalKey));
			((KeyBindsScreen) client.gui.screen()).selectedKey = JournalSyncClient.journalKey;
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_K);
		context.runOnClient(client -> assertEquals("rebound key", "key.keyboard.k", JournalSyncClient.journalKey.saveString()));
		context.setScreen(() -> null);
		Files.writeString(journal, NOTE);
		BookEditScreen screen = openBookTheRealWay(context, server, connection);
		context.getInput().pressKey(JournalSyncClient.journalKey);
		context.waitTick();
		assertTrue("key binds screen", BoundBook.isBound(screen));
		assertEquals("key loads same pages as button", NOTE_PAGES, List.copyOf(screen.pages));
		context.getInput().holdKey(JournalSyncClient.journalKey);
		context.getInput().typeChar('k');
		context.getInput().releaseKey(JournalSyncClient.journalKey);
		assertEquals("shortcut character is not inserted into journal", NOTE_PAGES, List.copyOf(screen.pages));
		assertFeedback(context, "Journal loaded");
		context.waitTicks(15);
		context.takeScreenshot(name + "-rebound-key-loaded");
		clickDone(context, connection);
		assertFeedback(context, "Journal saved");
		context.waitTicks(15);
		context.takeScreenshot(name + "-saved-feedback");

		// An existing directory is a deterministic unreadable journal even when tests run as root.
		System.setProperty(BoundBook.PATH_PROPERTY, journal.getParent().toString());
		screen = openBookTheRealWay(context, server, connection);
		context.getInput().typeChars("keep this book");
		context.clickScreenButton("journal_sync.button.journal");
		context.waitTick();
		assertTrue("failed load remains unbound", !BoundBook.isBound(screen));
		assertEquals("failed load preserves text", List.of("keep this book"), List.copyOf(screen.pages));
		assertEquals("failed load preserves journal", NOTE, Files.readString(journal));
		assertFeedback(context, "Cannot read journal");
		context.waitTicks(15);
		context.takeScreenshot(name + "-unreadable-error");
		clickDone(context, connection);

		Path config = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("journal-sync.json");
		String validConfig = Files.readString(config);
		try {
			Files.writeString(config, "{ invalid");
			screen = openBookTheRealWay(context, server, connection);
			context.clickScreenButton("journal_sync.button.journal");
			assertTrue("invalid config does not bind", !BoundBook.isBound(screen));
			assertFeedback(context, "Invalid journal-sync.json");
			context.waitTicks(15);
			context.takeScreenshot(name + "-invalid-config");
			clickDone(context, connection);
		} finally {
			Files.writeString(config, validConfig);
		}

		System.setProperty(BoundBook.PATH_PROPERTY, journal.toString());
		Files.delete(journal);
		screen = openBookTheRealWay(context, server, connection);
		context.getInput().typeChars("new journal text");
		context.getInput().pressKey(JournalSyncClient.journalKey);
		context.waitTick();
		assertFeedback(context, "Journal missing");
		assertEquals("missing file preserves text", List.of("new journal text"), List.copyOf(screen.pages));
		assertTrue("missing file not created until close", !Files.exists(journal));
		context.waitTicks(15);
		context.takeScreenshot(name + "-missing-warning");
		clickDone(context, connection);
		assertEquals("first save creates journal", "new journal text", Files.readString(journal));

		// Bind a valid file, then replace it with a directory to force a close-time write failure.
		screen = openBookTheRealWay(context, server, connection);
		context.getInput().pressKey(JournalSyncClient.journalKey);
		Files.delete(journal);
		Files.createDirectory(journal);
		clickDone(context, connection);
		assertFeedback(context, "Journal save failed");
		assertTrue("save failure removes lock", !Files.exists(Journal.lockPath(journal)));
		context.waitTicks(15);
		context.takeScreenshot(name + "-save-error");
		Files.delete(journal);
		context.runOnClient(client -> {
			JournalSyncClient.journalKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
			KeyMapping.resetMapping();
		});
	}

	/** Exercise actual config resolution and snapshot policy, including rejected reloads. */
	private static void configuredBackupsAndBinding(ClientGameTestContext context, TestServerContext server,
		TestServerConnection connection, String name, Path journal) throws IOException {
		Path config = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("journal-sync.json");
		String originalConfig = Files.readString(config);
		Path game = journal.getParent().resolve(name + "-configured-game");
		Path file = game.resolve("configured.md");
		Path backups = game.resolve("journal-sync-backups");
		Files.createDirectories(backups);
		for (int i = 0; i < 5; i++) {
			Path seed = backups.resolve("seed-" + i + ".md");
			Files.writeString(seed, "seed-" + i);
			Files.setLastModifiedTime(seed, FileTime.fromMillis(1000 + i));
		}
		Files.writeString(file, "configured note");
		System.clearProperty(BoundBook.PATH_PROPERTY);
		System.setProperty("journal_sync.game_dir", game.toString());
		try {
			Files.writeString(config, "{\"globalPath\":\"configured.md\",\"backupCount\":2}");
			BookEditScreen book = openBookTheRealWay(context, server, connection);
			context.getInput().typeChars("previous book");
			context.clickScreenButton("journal_sync.button.journal");
			assertEquals("config resolves actual client path", List.of("configured note"), List.copyOf(book.pages));
			assertEquals("load uses configured count", 2, backupContents(backups).size());
			assertTrue("load backs up previous book", backupContents(backups).contains("previous book"));
			assertTrue("configured lock exists", Files.exists(Journal.lockPath(file)));

			// Invalid config and failed read must preserve this binding and its pages/lock.
			Files.writeString(config, "{invalid");
			context.getInput().pressKey(JournalSyncClient.journalKey);
			assertFeedback(context, "Invalid journal-sync.json");
			assertTrue("invalid config preserves valid binding", BoundBook.isBound(book));
			Files.writeString(config, "{\"globalPath\":\".\",\"backupCount\":9}");
			context.getInput().pressKey(JournalSyncClient.journalKey);
			assertFeedback(context, "Cannot read journal");
			assertTrue("failed reload preserves valid binding", BoundBook.isBound(book));
			assertEquals("failed reload preserves book", List.of("configured note"), List.copyOf(book.pages));
			assertTrue("failed reload preserves lock", Files.exists(Journal.lockPath(file)));

			// Changing every policy input while bound cannot redirect save or change rotation.
			Files.writeString(config, "{\"globalPath\":\"other.md\",\"backupCount\":9}");
			System.setProperty(BoundBook.PATH_PROPERTY, game.resolve("override.md").toString());
			System.setProperty("journal_sync.game_dir", game.resolve("other-game").toString());
			context.getInput().typeChars(" edited");
			clickDone(context, connection);
			assertEquals("binding retains resolved path", "configured note edited", Files.readString(file));
			assertEquals("save retains configured count", 2, backupContents(backups).size());
			assertTrue("save backs up overwritten file", backupContents(backups).contains("configured note"));
			assertTrue("previous book remains recoverable", backupContents(backups).contains("previous book"));
			assertTrue("save removes original lock", !Files.exists(Journal.lockPath(file)));
			assertTrue("save ignores changed path", !Files.exists(game.resolve("override.md")));
			assertTrue("save ignores changed game dir", !Files.exists(game.resolve("other-game")));
		} finally {
			Files.writeString(config, originalConfig);
			System.setProperty(BoundBook.PATH_PROPERTY, journal.toString());
			System.setProperty("journal_sync.game_dir", journal.getParent().resolve("game").toString());
		}
	}

	private static void backupAndLockErrors(ClientGameTestContext context, TestServerContext server,
		TestServerConnection connection, Path journal) throws IOException {
		Files.writeString(journal, NOTE);
		Path blockedGame = journal.getParent().resolve("blocked-game");
		Files.writeString(blockedGame, "not a directory");
		System.setProperty("journal_sync.game_dir", blockedGame.toString());
		BookEditScreen book = openBookTheRealWay(context, server, connection);
		context.getInput().typeChars("preserve me");
		context.clickScreenButton("journal_sync.button.journal");
		assertFeedback(context, "Cannot read journal");
		assertTrue("backup failure leaves unbound", !BoundBook.isBound(book));
		assertEquals("backup failure preserves book", List.of("preserve me"), List.copyOf(book.pages));
		assertTrue("backup failure creates no lock", !Files.exists(Journal.lockPath(journal)));
		System.setProperty("journal_sync.game_dir", journal.getParent().resolve("game").toString());
		Files.delete(blockedGame);

		Files.createDirectory(Journal.lockPath(journal));
		context.clickScreenButton("journal_sync.button.journal");
		assertFeedback(context, "Cannot read journal");
		assertTrue("lock failure leaves unbound", !BoundBook.isBound(book));
		assertEquals("lock failure preserves book", List.of("preserve me"), List.copyOf(book.pages));
		Files.delete(Journal.lockPath(journal));
		context.clickScreenButton("journal_sync.button.journal");
		assertTrue("retry binds successfully", BoundBook.isBound(book));
		// Force a backup failure after binding: close still releases the sidecar.
		Path backupFolder = journal.getParent().resolve("game/journal-sync-backups");
		Path hidden = backupFolder.resolveSibling("held-backups");
		Files.move(backupFolder, hidden);
		Files.writeString(backupFolder, "not a directory");
		clickDone(context, connection);
		assertFeedback(context, "Journal save failed");
		assertEquals("backup failure preserves journal bytes", NOTE, Files.readString(journal));
		assertTrue("backup failure close removes lock", !Files.exists(Journal.lockPath(journal)));
		Files.delete(backupFolder);
		Files.move(hidden, backupFolder);

		book = openBookTheRealWay(context, server, connection);
		context.clickScreenButton("journal_sync.button.journal");
		Files.delete(Journal.lockPath(journal));
		Files.createDirectory(Journal.lockPath(journal));
		Files.writeString(Journal.lockPath(journal).resolve("block-delete"), "blocked");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		assertFeedback(context, "Cannot remove journal lock");
		assertTrue("lock removal failure ends binding", !BoundBook.isBound(book));
		context.waitTicks(15);
		context.takeScreenshot("integration-lock-removal-error");
		Files.delete(Journal.lockPath(journal).resolve("block-delete"));
		Files.delete(Journal.lockPath(journal));
	}

	private static List<String> backupContents(Path folder) throws IOException {
		try (var paths = Files.list(folder)) {
			return paths.map(path -> {
				try { return Files.readString(path); }
				catch (IOException e) { throw new UncheckedIOException(e); }
			}).toList();
		}
	}

	private static void assertFeedback(ClientGameTestContext context, String expected) {
		context.runOnClient(client -> {
			try {
				var state = client.gui.hud.getChat().storeState();
				var field = state.getClass().getDeclaredField("messages");
				field.setAccessible(true);
				@SuppressWarnings("unchecked")
				var messages = (List<net.minecraft.client.multiplayer.chat.GuiMessage>) field.get(state);
				assertTrue("latest visible feedback contains " + expected,
					!messages.isEmpty() && messages.getFirst().content().getString().contains(expected));
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("Cannot inspect rendered chat feedback", e);
			}
		});
	}

	/** Open and close a book without pressing Journal: the file is not touched, vanilla editing still works. */
	private static void unboundBookLeavesJournalUntouched(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String name, Path journal)
		throws IOException {
		Files.writeString(journal, NOTE, StandardCharsets.UTF_8);
		FileTime modifiedBefore = Files.getLastModifiedTime(journal);

		BookEditScreen screen = openBookTheRealWay(context, server, connection);
		context.getInput().typeChars("plain book text");
		context.waitTick();
		assertTrue(name + ": screen must not be bound before Journal is pressed", !BoundBook.isBound(screen));
		context.takeScreenshot(name + "-unbound-book");
		clickDone(context, connection);

		assertEquals(name + ": journal bytes", NOTE, Files.readString(journal, StandardCharsets.UTF_8));
		assertEquals(name + ": journal modified time", modifiedBefore, Files.getLastModifiedTime(journal));
		assertEquals(name + ": unbound book still saved by vanilla", List.of("plain book text"), serverBookPages(server));
	}

	/** Scenarios 1 and 2: Journal fills the book from the file, closing writes the edited pages back. */
	private static List<String> journalLoadsAndSavesOnClose(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String name, Path journal)
		throws IOException {
		Files.writeString(journal, NOTE, StandardCharsets.UTF_8);

		BookEditScreen screen = openBookTheRealWay(context, server, connection);
		context.clickScreenButton("journal_sync.button.journal");
		context.waitTick();

		assertTrue(name + ": screen must be bound after Journal is pressed", BoundBook.isBound(screen));
		assertEquals(name + ": book pages after Journal", NOTE_PAGES, List.copyOf(screen.pages));
		context.takeScreenshot(name + "-journal-loaded");

		context.getInput().typeChars(TYPED);
		context.waitTick();
		context.takeScreenshot(name + "-journal-edited");
		clickDone(context, connection);

		List<String> expected = new ArrayList<>(NOTE_PAGES);
		expected.set(0, expected.getFirst() + TYPED);
		assertTrue(name + ": ordinary save shows no prompt", context.computeOnClient(mc -> mc.gui.screen() == null));
		assertEquals(name + ": journal file after close", String.join(PAGE_MARKER, expected),
			Files.readString(journal, StandardCharsets.UTF_8));
		assertEquals(name + ": server-side book after close", expected, serverBookPages(server));
		assertTrue(name + ": binding must end with the screen", !BoundBook.isBound(screen));
		assertTrue(name + ": lock sidecar must be removed after close", !Files.exists(Journal.lockPath(journal)));
		Path backupDirectory = journal.getParent().resolve("game").resolve("journal-sync-backups");
		try (var backups = Files.list(backupDirectory)) {
			assertTrue(name + ": prior book backup must exist after load", backups.findAny().isPresent());
		}
		return expected;
	}


	/** Escape mirrors vanilla discard, including when an external edit would otherwise need a prompt. */
	private static void escapeDiscardsBoundEdits(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String name, Path journal)
		throws IOException {
		Files.writeString(journal, NOTE, StandardCharsets.UTF_8);
		BookEditScreen screen = openBookTheRealWay(context, server, connection);
		context.clickScreenButton("journal_sync.button.journal");
		context.getInput().typeChars(TYPED);
		Files.writeString(journal, OBSIDIAN_EDIT, StandardCharsets.UTF_8);
		FileTime modified = Files.getLastModifiedTime(journal);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(3);
		connection.waitForServerboundPackets();
		assertTrue(name + ": Escape closes without a prompt", context.computeOnClient(mc -> mc.gui.screen() == null));
		assertTrue(name + ": Escape ends binding", !BoundBook.isBound(screen));
		assertEquals(name + ": Escape preserves external bytes", OBSIDIAN_EDIT, Files.readString(journal));
		assertEquals(name + ": Escape preserves modified time", modified, Files.getLastModifiedTime(journal));
		assertTrue(name + ": Escape removes lock", !Files.exists(Journal.lockPath(journal)));
		assertEquals(name + ": Escape discards vanilla book edits", List.of(), serverBookPages(server));
	}

	private static final String OBSIDIAN_EDIT =
		"---\r\nmc-sync: true\r\ntags: [minecraft]\r\n---\r\n# Vault edit\r\nFound diamonds.\r\n";

	/** Scenario 3: edit externally while bound; test all buttons and the real one-key default. */
	private static void conflictsOfferAllResolutions(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String name, Path journal)
		throws IOException {
		for (String choice : List.of("default", "keep_file", "keep_book", "escape")) {
			Files.writeString(journal, NOTE, StandardCharsets.UTF_8);
			BookEditScreen book = openBookTheRealWay(context, server, connection);
			context.clickScreenButton("journal_sync.button.journal");
			context.getInput().typeChars(TYPED);
			context.waitTick();
			List<String> editedPages = context.computeOnClient(mc -> List.copyOf(book.pages));
			String edited = String.join(PAGE_MARKER, editedPages);
			Files.writeString(journal, OBSIDIAN_EDIT, StandardCharsets.UTF_8);
			FileTime modified = Files.getLastModifiedTime(journal);
			List<Path> before = conflictCopies(journal);
			clickDone(context, connection);
			context.waitForScreen(ConflictScreen.class);
			assertEquals(name + ": prompt leaves external bytes intact", OBSIDIAN_EDIT, Files.readString(journal));
			assertEquals(name + ": prompt writes no copy yet", before, conflictCopies(journal));
			assertTrue(name + ": conflict copy has initial focus", context.computeOnClient(mc ->
				mc.gui.screen().getFocused() instanceof Button button
					&& button.getMessage().getString().equals("Save as conflict copy")));
			context.takeScreenshot(name + "-conflict-" + choice);
			if (choice.equals("default")) {
				context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
			} else if (choice.equals("escape")) {
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			} else {
				context.clickScreenButton("journal_sync.conflict." + choice);
			}
			context.waitFor(mc -> mc.gui.screen() == null);
			context.waitTick();
			assertTrue(name + ": conflict ends binding", !BoundBook.isBound(book));
			assertTrue(name + ": conflict removes lock", !Files.exists(Journal.lockPath(journal)));
			// Done already sent vanilla's edit packet; the prompt controls only the journal file.
			assertEquals(name + ": server retains edited book", editedPages, serverBookPages(server));
			if (choice.equals("keep_book")) {
				assertEquals(name + ": keep book", edited, Files.readString(journal));
				assertTrue(name + ": keep book backs up raw external bytes", backupContents(journal.getParent().resolve("game/journal-sync-backups")).contains(OBSIDIAN_EDIT));
				assertFeedback(context, "Journal saved");
			} else {
				assertEquals(name + ": preserve CRLF and frontmatter", OBSIDIAN_EDIT, Files.readString(journal));
				assertEquals(name + ": preserve file time", modified, Files.getLastModifiedTime(journal));
			}
			if (choice.equals("keep_file")) assertFeedback(context, "External journal kept");
			if (choice.equals("default") || choice.equals("escape")) assertFeedback(context, "Book saved as a conflict copy");
			List<Path> after = conflictCopies(journal);
			if (choice.equals("default") || choice.equals("escape")) {
				List<Path> created = after.stream().filter(path -> !before.contains(path)).toList();
				assertEquals(name + ": exactly one conflict copy", 1, created.size());
				assertTrue(name + ": timestamped sibling", created.getFirst().getFileName().toString()
					.matches("journal\\.conflict-\\d{8}-\\d{6}(-\\d+)?\\.md"));
				assertEquals(name + ": copy contains book", edited, Files.readString(created.getFirst()));
			} else {
				assertEquals(name + ": no extra copy", before, after);
			}
		}
	}

	private static List<Path> conflictCopies(Path journal) throws IOException {
		try (var paths = Files.list(journal.getParent())) {
			return paths.filter(path -> path.getFileName().toString().startsWith("journal.conflict-"))
				.sorted().toList();
		}
	}

	private static BookEditScreen openBookTheRealWay(
		ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("clear @a");
		server.runCommand("give @a writable_book");
		connection.waitForClientboundPackets();
		context.getInput().pressMouse(RIGHT_MOUSE_BUTTON);
		context.waitForScreen(BookEditScreen.class);
		context.waitTick();
		return (BookEditScreen) context.computeOnClient(minecraft -> minecraft.gui.screen());
	}

	private static void clickDone(ClientGameTestContext context, TestServerConnection connection) {
		context.clickScreenButton("gui.done");
		context.waitTick();
		connection.waitForServerboundPackets();
	}

	private static List<String> serverBookPages(TestServerContext server) {
		return server.computeOnServer(minecraft -> {
			WritableBookContent content = minecraft.getPlayerList().getPlayers().getFirst()
				.getMainHandItem().get(DataComponents.WRITABLE_BOOK_CONTENT);
			return content == null ? List.<String>of() : content.getPages(false).toList();
		});
	}

	private static void assertTrue(String message, boolean condition) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertEquals(String what, Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}
}
