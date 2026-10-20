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
		unboundBookLeavesJournalUntouched(context, server, connection, name, journal);
		return journalLoadsAndSavesOnClose(context, server, connection, name, journal);
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
		context.waitTicks(15);
		context.takeScreenshot(name + "-save-error");
		Files.delete(journal);
		context.runOnClient(client -> {
			JournalSyncClient.journalKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
			KeyMapping.resetMapping();
		});
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
