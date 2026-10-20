package com.crambeary.journalsync.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;

public class JournalSyncClient implements ClientModInitializer {
	public static KeyMapping journalKey;
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;

	@Override
	public void onInitializeClient() {
		journalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"journal_sync.key.journal", GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof BookEditScreen bookScreen) {
				// GLFW delivers character events separately from key events. Do not type the
				// journal shortcut into the freshly loaded page when bound to a letter.
				boolean[] shortcutHeld = {false};
				ScreenKeyboardEvents.allowCharType(screen).register((active, event) -> !shortcutHeld[0]);
				ScreenKeyboardEvents.beforeKeyRelease(screen).register((active, event) -> {
					if (journalKey.matches(event)) shortcutHeld[0] = false;
				});
				ScreenKeyboardEvents.allowKeyPress(screen).register((active, event) -> {
					if (journalKey.matches(event)) {
						shortcutHeld[0] = true;
						BoundBook.press(bookScreen);
						return false;
					}
					return true;
				});
				int x = (width - BookEditScreen.IMAGE_WIDTH) / 2 - BUTTON_WIDTH - BUTTON_GAP;
				int y = (height - BookEditScreen.IMAGE_HEIGHT) / 2;
				Button journal = Button.builder(
						Component.translatable("journal_sync.button.journal"),
						button -> BoundBook.press(bookScreen))
					.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
				Screens.getWidgets(screen).add(journal);
				ScreenEvents.afterTick(screen).register(active -> journal.setMessage(Component.translatable(
					BoundBook.isBound(screen) ? "journal_sync.button.bound" : "journal_sync.button.journal")));
			}
		});
	}
}
