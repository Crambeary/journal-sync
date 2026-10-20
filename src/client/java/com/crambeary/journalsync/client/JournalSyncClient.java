package com.crambeary.journalsync.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;

public class JournalSyncClient implements ClientModInitializer {
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof BookEditScreen bookScreen) {
				int x = (width - BookEditScreen.IMAGE_WIDTH) / 2 - BUTTON_WIDTH - BUTTON_GAP;
				int y = (height - BookEditScreen.IMAGE_HEIGHT) / 2;
				Button journal = Button.builder(
						Component.translatable("journal_sync.button.journal"),
						button -> BoundBook.press(bookScreen))
					.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
				Screens.getWidgets(screen).add(journal);
			}
		});
	}
}
