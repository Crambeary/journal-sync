package com.crambeary.journalsync.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.crambeary.journalsync.Journal;
import com.crambeary.journalsync.JournalConfig;
import com.crambeary.journalsync.JournalFeedback;
import net.minecraft.client.Minecraft;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.slf4j.Logger;

/**
 * Ties one open book screen to the journal file: pressing Journal loads the file into the book and
 * binds the screen; closing a bound screen saves its pages back. Unbound screens are never touched.
 */
public final class BoundBook {
	/** System property that overrides the default journal path (used by tests and launcher overrides). */
	public static final String PATH_PROPERTY = "journal_sync.path";

	private static final Logger LOGGER = LogUtils.getLogger();

	private record Binding(BookEditScreen screen, Path file, Journal.LoadToken token) {
	}

	private static Binding binding;

	private BoundBook() {
	}

	public static boolean isBound(Screen screen) {
		return binding != null && binding.screen() == screen;
	}

	public static JournalConfig configuration() throws IOException {
		return JournalConfig.read(FabricLoader.getInstance().getConfigDir().resolve("journal-sync.json"));
	}

	public static Path journalPath() throws IOException {
		Minecraft client = Minecraft.getInstance();
		String world = client.getSingleplayerServer() == null ? null
			: client.getSingleplayerServer().getWorldData().getLevelName();
		String server = client.getCurrentServer() == null ? null : client.getCurrentServer().ip;
		return configuration().resolve(FabricLoader.getInstance().getGameDir(), world, server,
			System.getProperty(PATH_PROPERTY));
	}

	/** Both controls call this action. Failure leaves the screen and its binding untouched. */
	public static void press(BookEditScreen screen) {
		Path file;
		try {
			file = journalPath();
		} catch (IOException | RuntimeException e) {
			JournalStatus.show(new JournalFeedback(JournalFeedback.Kind.INVALID_CONFIG, e.getMessage()));
			return;
		}
		JournalFeedback.LoadResult result = JournalFeedback.load(file, screen.pages);
		JournalStatus.show(result.feedback());
		if (!result.succeeded()) return;
		Journal.Loaded loaded = result.loaded();

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

	private static void onScreenRemoved(Screen screen) {
		if (!isBound(screen)) {
			return;
		}
		Binding closing = binding;
		binding = null;

		Journal.SaveOutcome outcome = Journal.save(closing.file(), List.copyOf(closing.screen().pages), closing.token());
		JournalStatus.show(JournalFeedback.saved(outcome, closing.file()));
		if (outcome instanceof Journal.SaveOutcome.Failed failed) {
			LOGGER.error("Could not save the journal to {}: {}", closing.file(), failed.reason());
		}
	}
}
