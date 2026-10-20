package com.crambeary.journalsync.client;

import java.util.function.Consumer;

import com.crambeary.journalsync.Journal;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/**
 * Asks what to do when the journal file changed while the book was open. A vanilla confirm screen with
 * three buttons; the default ({@link Journal.Resolution#DEFAULT}) comes first, so it holds the initial
 * focus, and Escape or leaving the world means the default too. The choice is passed to core by the caller.
 */
public final class ConflictScreen extends ConfirmScreen {
	private static final int BUTTON_WIDTH = 130;

	private final Consumer<Journal.Resolution> onChoice;
	private boolean chosen;
	private Button defaultButton;

	public ConflictScreen(Consumer<Journal.Resolution> onChoice) {
		// Key handling below resolves Escape to the safe default.
		super(
			yes -> {
			},
			Component.translatable("journal_sync.conflict.title"),
			Component.translatable("journal_sync.conflict.message"));
		this.onChoice = onChoice;
	}

	@Override
	protected void addButtons(LinearLayout buttons) {
		defaultButton = buttons.addChild(button("journal_sync.conflict.conflict_copy", Journal.Resolution.DEFAULT));
		buttons.addChild(button("journal_sync.conflict.keep_book", Journal.Resolution.KEEP_BOOK));
		buttons.addChild(button("journal_sync.conflict.keep_file", Journal.Resolution.KEEP_FILE));
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(defaultButton);
	}

	private Button button(String translationKey, Journal.Resolution resolution) {
		return Button.builder(Component.translatable(translationKey), pressed -> choose(resolution))
			.width(BUTTON_WIDTH)
			.build();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.isEscape()) {
			choose(Journal.Resolution.DEFAULT);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		// Closed some other way, e.g. the connection dropped: still never lose the book.
		if (!chosen) {
			chosen = true;
			onChoice.accept(Journal.Resolution.DEFAULT);
		}
	}

	private void choose(Journal.Resolution resolution) {
		if (chosen) {
			return;
		}
		chosen = true;
		onChoice.accept(resolution);
		if (minecraft.gui.screen() == this) {
			minecraft.gui.setScreen(null);
		}
	}
}
