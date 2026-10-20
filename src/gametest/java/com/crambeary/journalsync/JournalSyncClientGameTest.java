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
		assertEquals(name + ": journal file after close", String.join(PAGE_MARKER, expected),
			Files.readString(journal, StandardCharsets.UTF_8));
		assertEquals(name + ": server-side book after close", expected, serverBookPages(server));
		assertTrue(name + ": binding must end with the screen", !BoundBook.isBound(screen));
		return expected;
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
