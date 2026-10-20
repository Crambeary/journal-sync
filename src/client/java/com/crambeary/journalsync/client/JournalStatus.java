package com.crambeary.journalsync.client;

import com.crambeary.journalsync.JournalFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Public client feedback sink for cap, conflict and backup outcomes added by other tickets. */
public final class JournalStatus {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(8000);
	private JournalStatus() { }

	public static void show(JournalFeedback feedback) {
		Minecraft client = Minecraft.getInstance();
		Component message = Component.translatable(feedback.translationKey());
		SystemToast.addOrUpdate(client.gui.toastManager(), TOAST, Component.literal("Journal Sync"), message);
		// Chat preserves the full path/reason after the toast disappears.
		client.gui.hud.getChat().addClientSystemMessage(Component.literal("[Journal Sync] ").append(message)
			.append(Component.literal(" — " + feedback.detail())));
	}
}
