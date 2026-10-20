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
		/** Escape was pressed: vanilla discards the edit, so the journal file must not take it either. */
		boolean discarded;

		Binding(BookEditScreen screen, Path file, Journal.LoadToken token) {
			this.screen = screen;
			this.file = file;
			this.token = token;
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

	/** The Journal button / keybind action: fill the book from the journal file and bind the screen. */
	public static void press(BookEditScreen screen) {
		Path file = journalPath();
		Journal.Loaded loaded;
		try {
			loaded = Journal.load(file);
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
			ScreenKeyboardEvents.beforeKeyPress(screen).register((pressed, event) -> {
				if (event.isEscape() && isBound(pressed)) {
					binding.discarded = true;
				}
			});
		}
		binding = new Binding(screen, file, loaded.token());
	}

	private static void onScreenRemoved(Screen screen) {
		if (!isBound(screen)) {
			return;
		}
		Binding closing = binding;
		binding = null;

		if (closing.discarded) {
			// Escape on a book and quill throws the edit away in vanilla; the file follows the book.
			LOGGER.info("Book closed with Escape; leaving the journal at {} as it is", closing.file);
			return;
		}

		List<String> pages = List.copyOf(closing.screen.pages);
		Journal.SaveOutcome outcome = Journal.save(closing.file, pages, closing.token);
		if (outcome instanceof Journal.SaveOutcome.Conflict) {
			askWhatToDo(closing.file, pages, closing.token);
		} else {
			report(closing.file, outcome);
		}
	}

	/**
	 * The file changed while the book was open: ask which side wins. Deferred a tick because this runs
	 * inside the book screen's own removal, which would otherwise overwrite the prompt with its null screen.
	 */
	private static void askWhatToDo(Path file, List<String> pages, Journal.LoadToken token) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.schedule(() -> minecraft.gui.setScreen(new ConflictScreen(
			resolution -> report(file, Journal.save(file, pages, token, resolution)))));
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
