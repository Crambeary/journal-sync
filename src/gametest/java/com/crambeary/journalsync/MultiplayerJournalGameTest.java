package com.crambeary.journalsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.crambeary.journalsync.client.BoundBook;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Ticket 09: actual vanilla network connection and a published LAN host, without screen injection. */
@SuppressWarnings("UnstableApiUsage")
public final class MultiplayerJournalGameTest implements FabricClientGameTest {
	private static final List<String> PAGES = List.of("Multiplayer journal.", "Second page preserved.");
	private static final String EDIT = " Saved on server.";
	private static final List<String> EDITED = List.of(PAGES.getFirst() + EDIT, PAGES.getLast());
	private static final String SEPARATOR = "\n<!-- page -->\n";

	@Override
	public void runTest(ClientGameTestContext context) {
		String previous = System.getProperty(BoundBook.PATH_PROPERTY);
		try {
			Path journal = Files.createTempDirectory("journal-sync-multiplayer-").resolve("journal.md");
			System.setProperty(BoundBook.PATH_PROPERTY, journal.toString());
			lanHostPublishesAndSaves(context, journal);
			vanillaServerSavesAfterReconnect(context, journal);
			System.out.println("Ticket 09: LAN host and unmodified vanilla server load/edit/save/reconnect assertions passed");
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (previous == null) System.clearProperty(BoundBook.PATH_PROPERTY);
			else System.setProperty(BoundBook.PATH_PROPERTY, previous);
		}
	}

	private static void lanHostPublishesAndSaves(ClientGameTestContext context, Path journal) throws Exception {
		try (var world = context.worldBuilder().create()) {
			var connection = world.getConnection();
			connection.waitForChunksRender();
			int port = VanillaTestServer.freePort();
			context.runOnClient(client -> {
				var server = client.getSingleplayerServer();
				if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, GameType.CREATIVE, true, port)
					|| !server.isPublished()) throw new AssertionError("LAN publication failed");
			});
			world.getServer().runCommand("give @a writable_book");
			connection.waitForClientboundPackets();
			loadEditAndClose(context, journal, "09-lan-host");
			connection.waitForServerboundPackets();
			equal("LAN server item", EDITED, world.getServer().computeOnServer(server -> connection.getServerPlayer()
				.getMainHandItem().get(DataComponents.WRITABLE_BOOK_CONTENT).getPages(false).toList()));
		}
	}

	private static void vanillaServerSavesAfterReconnect(ClientGameTestContext context, Path journal) throws Exception {
		try (var server = new VanillaTestServer()) {
			try {
				context.waitFor(client -> server.ready(), 6000);
				connect(context, server.port);
				server.command("give @a writable_book");
				context.waitFor(client -> client.player.getMainHandItem().is(Items.WRITABLE_BOOK));
				loadEditAndClose(context, journal, "09-vanilla-server");
				// Console reads server-owned data; this is also the packet-processing barrier before disconnect.
				server.command("data get entity @p SelectedItem");
				context.waitFor(client -> server.log().contains(EDITED.getFirst()), 600);
				disconnect(context);
				context.waitFor(client -> server.log().contains("left the game"), 600);
				connect(context, server.port);
				context.waitFor(client -> client.player.getMainHandItem().is(Items.WRITABLE_BOOK));
				useBook(context);
				equal("Vanilla book after reconnect", EDITED, screenPages(context));
				context.takeScreenshot("09-vanilla-server-reconnected");
				context.clickScreenButton("gui.done");
				System.out.println("Vanilla server evidence: " + server.directory);
			} finally {
				disconnect(context);
			}
		}
	}

	private static void connect(ClientGameTestContext context, int port) {
		context.runOnClient(client -> {
			String address = "127.0.0.1:" + port;
			ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
				new ServerData("Ticket 09 vanilla", address, ServerData.Type.OTHER), false, null);
		});
		context.waitFor(client -> client.player != null && client.level != null && client.gui.screen() == null, 1200);
	}

	private static void disconnect(ClientGameTestContext context) {
		context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
		context.waitFor(client -> client.player == null && client.level == null);
	}

	private static void loadEditAndClose(ClientGameTestContext context, Path journal, String name) throws Exception {
		Files.writeString(journal, String.join(SEPARATOR, PAGES));
		useBook(context);
		context.clickScreenButton("journal_sync.button.journal");
		equal(name + " loaded pages", PAGES, screenPages(context));
		context.takeScreenshot(name + "-loaded");
		context.getInput().typeChars(EDIT);
		context.waitTick();
		context.takeScreenshot(name + "-edited");
		context.clickScreenButton("gui.done");
		context.waitTick();
		equal(name + " file after Done", String.join(SEPARATOR, EDITED), Files.readString(journal));
	}

	private static void useBook(ClientGameTestContext context) {
		context.getInput().pressMouse(1);
		context.waitForScreen(BookEditScreen.class);
		context.waitTick();
	}

	private static List<String> screenPages(ClientGameTestContext context) {
		return context.computeOnClient(client -> List.copyOf(((BookEditScreen) client.gui.screen()).pages));
	}

	private static void equal(String label, Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
	}
}
