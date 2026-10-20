package com.crambeary.journalsync;

import com.crambeary.journalsync.client.JournalSyncClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

@SuppressWarnings("UnstableApiUsage")
public class JournalSyncClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			pressJournalOnBookScreen(context, "singleplayer");
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			pressJournalOnBookScreen(context, "dedicated-server");
		}
	}

	private static void pressJournalOnBookScreen(ClientGameTestContext context, String scenario) {
		int pressesBefore = JournalSyncClient.JOURNAL_PRESSES.get();

		context.setScreen(() -> new BookEditScreen(
			net.minecraft.client.Minecraft.getInstance().player,
			new ItemStack(Items.WRITABLE_BOOK),
			InteractionHand.MAIN_HAND,
			WritableBookContent.EMPTY));
		context.waitForScreen(BookEditScreen.class);
		context.takeScreenshot(scenario + "-book-screen");

		context.getInput().typeChars("hello");
		context.waitTick();
		context.clickScreenButton("journal_sync.button.journal");
		context.waitTick();

		int presses = JournalSyncClient.JOURNAL_PRESSES.get() - pressesBefore;
		if (presses != 1) {
			throw new AssertionError(scenario + ": expected the Journal button to be pressed once, was " + presses);
		}
		context.takeScreenshot(scenario + "-book-screen-journal-clicked");
	}
}
