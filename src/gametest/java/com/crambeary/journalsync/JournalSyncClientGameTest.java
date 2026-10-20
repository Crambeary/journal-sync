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
		unboundBookLeavesJournalUntouched(context, server, connection, name, journal);
		escapeDiscardsBoundEdits(context, server, connection, name, journal);
		conflictsOfferAllResolutions(context, server, connection, name, journal);
		return journalLoadsAndSavesOnClose(context, server, connection, name, journal);
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
			// Done already sent vanilla's edit packet; the prompt controls only the journal file.
			assertEquals(name + ": server retains edited book", editedPages, serverBookPages(server));
			if (choice.equals("keep_book")) {
				assertEquals(name + ": keep book", edited, Files.readString(journal));
			} else {
				assertEquals(name + ": preserve CRLF and frontmatter", OBSIDIAN_EDIT, Files.readString(journal));
				assertEquals(name + ": preserve file time", modified, Files.getLastModifiedTime(journal));
			}
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
