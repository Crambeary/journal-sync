package com.crambeary.journalsync.client;

import java.util.concurrent.atomic.AtomicInteger;

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

	/** How many times the Journal button has been pressed; lets tests observe the (still inert) button. */
	public static final AtomicInteger JOURNAL_PRESSES = new AtomicInteger();

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof BookEditScreen) {
				int x = (width - BookEditScreen.IMAGE_WIDTH) / 2 - BUTTON_WIDTH - BUTTON_GAP;
				int y = (height - BookEditScreen.IMAGE_HEIGHT) / 2;
				Button journal = Button.builder(
						Component.translatable("journal_sync.button.journal"),
						button -> JOURNAL_PRESSES.incrementAndGet())
					.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
				Screens.getWidgets(screen).add(journal);
			}
		});
	}
}
