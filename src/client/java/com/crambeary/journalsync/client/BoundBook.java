package com.crambeary.journalsync.client;

import java.nio.file.Path;
import java.util.List;

import com.crambeary.journalsync.Journal;
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
	/** System property that overrides the default journal path (used by tests; real config comes later). */
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

	public static Path journalPath() {
		String override = System.getProperty(PATH_PROPERTY);
		if (override != null) {
			return Path.of(override);
		}
		return FabricLoader.getInstance().getGameDir().resolve("journal-sync").resolve("journal.md");
	}

	private static Path gameDirectory() {
		String override = System.getProperty("journal_sync.game_dir");
		return override == null ? FabricLoader.getInstance().getGameDir() : Path.of(override);
	}

	/** The Journal button / keybind action: fill the book from the journal file and bind the screen. */
	public static void press(BookEditScreen screen) {
		Path file = journalPath();
		Path gameDirectory = gameDirectory();
		Journal.LoadOutcome loadOutcome = Journal.load(file, List.copyOf(screen.pages), gameDirectory, 20);
		if (loadOutcome instanceof Journal.LoadOutcome.Failed failed) {
			LOGGER.error("Could not back up or read the journal at {}: {}", file, failed.reason());
			return;
		}
		Journal.Loaded loaded = ((Journal.LoadOutcome.LoadedWithBackup) loadOutcome).loaded();

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

		Journal.SaveOutcome outcome = Journal.save(closing.file(), List.copyOf(closing.screen().pages), closing.token(),
			gameDirectory(), 20);
		if (outcome instanceof Journal.SaveOutcome.Failed failed) {
			LOGGER.error("Could not save the journal to {}: {}", closing.file(), failed.reason());
		}
		Journal.LockOutcome lock = Journal.removeLock(closing.file());
		if (lock instanceof Journal.LockOutcome.Failed failed) {
			LOGGER.error("Could not remove journal lock at {}: {}", Journal.lockPath(closing.file()), failed.reason());
		}
	}
}
