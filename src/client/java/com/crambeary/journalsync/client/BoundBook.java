package com.crambeary.journalsync.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.crambeary.journalsync.Journal;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.WritableBookContent;
import org.slf4j.Logger;

/**
 * Ties one open book screen to the journal file: pressing Journal loads the file into the book and
 * binds the screen; closing a bound screen saves its pages back. Unbound screens are never touched.
 */
public final class BoundBook {
	/** System property that overrides the default journal path (used by tests; real config comes later). */
	public static final String PATH_PROPERTY = "journal_sync.path";

	private static final Logger LOGGER = LogUtils.getLogger();

	private record Binding(BookEditScreen screen, Path file, Journal.LoadToken token) {
	}

	public static final SystemToast.SystemToastId PAGE_CAP_TOAST = new SystemToast.SystemToastId();

	private static Binding binding;

	private BoundBook() {
	}

	public static boolean isBound(Screen screen) {
		return binding != null && binding.screen() == screen;
	}

	public static Path journalPath() {
		String override = System.getProperty(PATH_PROPERTY);
		if (override != null) {
			return Path.of(override);
		}
		return FabricLoader.getInstance().getGameDir().resolve("journal-sync").resolve("journal.md");
	}

	/** The Journal button / keybind action: fill the book from the journal file and bind the screen. */
	public static void press(BookEditScreen screen) {
		Path file = journalPath();
		Journal.Loaded loaded;
		try {
			loaded = Journal.load(file);
		} catch (Journal.PageCapExceededException e) {
			warnPageCap(e.pageCount(), WritableBookContent.MAX_PAGES);
			return;
		} catch (IOException e) {
			LOGGER.error("Could not read the journal at {}", file, e);
			return;
		}

		screen.pages.clear();
		screen.pages.addAll(loaded.pages().isEmpty() ? List.of("") : loaded.pages());
		screen.currentPage = 0;
		screen.updatePageContent();
		screen.updateButtonVisibility();
		// Clicking the Journal button moved focus onto it; put the cursor back in the page.
		screen.setInitialFocus();

		if (!isBound(screen)) {
			ScreenEvents.remove(screen).register(BoundBook::onScreenRemoved);
		}
		binding = new Binding(screen, file, loaded.token());
	}

	private static void warnPageCap(int pages, int limit) {
		SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), PAGE_CAP_TOAST,
			Component.translatable("journal_sync.cap.title", pages, limit),
			Component.translatable("journal_sync.cap.detail"));
	}

	private static void onScreenRemoved(Screen screen) {
		if (!isBound(screen)) {
			return;
		}
		Binding closing = binding;
		binding = null;

		Journal.SaveOutcome outcome = Journal.save(closing.file(), List.copyOf(closing.screen().pages), closing.token());
		if (outcome instanceof Journal.SaveOutcome.OverCap overCap) {
			warnPageCap(overCap.pageCount(), overCap.maxPages());
		}
		if (outcome instanceof Journal.SaveOutcome.Failed failed) {
			LOGGER.error("Could not save the journal to {}: {}", closing.file(), failed.reason());
		}
	}
}
