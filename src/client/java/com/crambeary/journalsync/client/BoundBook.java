package com.crambeary.journalsync.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.crambeary.journalsync.Journal;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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

	private static final class Binding {
		final BookEditScreen screen;
		final Path file;
		final Journal.LoadToken token;
		final Path gameDirectory;
		final int backupCount;
		/** Escape was pressed: vanilla discards the edit, so the journal file must not take it either. */
		boolean discarded;

		Binding(BookEditScreen screen, Path file, Journal.LoadToken token, Path gameDirectory, int backupCount) {
			this.screen = screen;
			this.file = file;
			this.token = token;
			this.gameDirectory = gameDirectory;
			this.backupCount = backupCount;
		}
	}

	private static Binding binding;

	private BoundBook() {
	}

	public static boolean isBound(Screen screen) {
		return binding != null && binding.screen == screen;
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
		Journal.LoadOutcome outcome = Journal.load(file, List.copyOf(screen.pages), gameDirectory, 20);
		if (outcome instanceof Journal.LoadOutcome.Failed failed) {
			LOGGER.error("Could not load journal at {}: {}", file, failed.reason());
			return;
		}
		Journal.Loaded loaded = ((Journal.LoadOutcome.LoadedWithBackup) outcome).loaded();

		screen.pages.clear();
		screen.pages.addAll(loaded.pages().isEmpty() ? List.of("") : loaded.pages());
		screen.currentPage = 0;
		screen.updatePageContent();
		screen.updateButtonVisibility();
		// Clicking the Journal button moved focus onto it; put the cursor back in the page.
		screen.setInitialFocus();

		if (!isBound(screen)) {
			ScreenEvents.remove(screen).register(BoundBook::onScreenRemoved);
			ScreenKeyboardEvents.beforeKeyPress(screen).register((pressed, event) -> {
				if (event.isEscape() && isBound(pressed)) {
					binding.discarded = true;
				}
			});
		}
		binding = new Binding(screen, file, loaded.token(), gameDirectory, 20);
	}

	private static void onScreenRemoved(Screen screen) {
		if (!isBound(screen)) {
			return;
		}
		Binding closing = binding;
		binding = null;
		releaseLock(closing);

		if (closing.discarded) {
			// Escape on a book and quill throws the edit away in vanilla; the file follows the book.
			LOGGER.info("Book closed with Escape; leaving the journal at {} as it is", closing.file);
			return;
		}

		List<String> pages = List.copyOf(closing.screen.pages);
		Journal.SaveOutcome outcome = Journal.save(closing.file, pages, closing.token, closing.gameDirectory, closing.backupCount);
		if (outcome instanceof Journal.SaveOutcome.Conflict) {
			askWhatToDo(closing, pages);
		} else {
			report(closing.file, outcome);
		}
	}

	/**
	 * The file changed while the book was open: ask which side wins. Deferred a tick because this runs
	 * inside the book screen's own removal, which would otherwise overwrite the prompt with its null screen.
	 */
	private static void askWhatToDo(Binding closing, List<String> pages) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.schedule(() -> minecraft.gui.setScreen(new ConflictScreen(
			resolution -> {
				report(closing.file, Journal.save(closing.file, pages, closing.token, resolution, closing.gameDirectory, closing.backupCount));
				releaseLock(closing);
			})));
	}

	private static void releaseLock(Binding closing) {
		if (Journal.removeLock(closing.file) instanceof Journal.LockOutcome.Failed failed) {
			LOGGER.error("Could not remove journal lock: {}", failed.reason());
		}
	}

	private static void report(Path file, Journal.SaveOutcome outcome) {
		switch (outcome) {
			case Journal.SaveOutcome.Failed failed ->
				LOGGER.error("Could not save the journal to {}: {}", file, failed.reason());
			case Journal.SaveOutcome.SavedAsConflictCopy copied ->
				LOGGER.info("The journal at {} changed while the book was open; saved the book as {}", file, copied.copy());
			case Journal.SaveOutcome.KeptFile kept ->
				LOGGER.info("The journal at {} changed while the book was open; kept the file, dropped the book's changes", file);
			case Journal.SaveOutcome.Conflict conflict ->
				LOGGER.warn("The journal at {} changed while the book was open and was not saved", file);
			case Journal.SaveOutcome.Saved saved -> {
			}
		}
	}
}
