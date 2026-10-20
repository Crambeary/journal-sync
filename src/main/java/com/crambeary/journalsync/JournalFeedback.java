package com.crambeary.journalsync;

/** Shared feedback channel: core outcomes map to localized client messages without screen dependencies. */
public record JournalFeedback(Kind kind, String detail) {
	public enum Kind { LOADED, MISSING, SAVED, LOAD_FAILED, SAVE_FAILED, INVALID_CONFIG, CAP_EXCEEDED }

	public String translationKey() {
		return "journal_sync.feedback." + kind.name().toLowerCase(java.util.Locale.ROOT);
	}

	public record LoadResult(Journal.Loaded loaded, JournalFeedback feedback) {
		public boolean succeeded() { return loaded != null; }
	}

	/** A missing file keeps the current book text, warns, and will be created on first save. */
	public static LoadResult load(java.nio.file.Path file, java.util.List<String> currentPages) {
		try {
			Journal.Loaded loaded = Journal.load(file);
			boolean missing = loaded.token().equals(Journal.LoadToken.ABSENT);
			if (missing) loaded = new Journal.Loaded(java.util.List.copyOf(currentPages), loaded.token());
			return new LoadResult(loaded, new JournalFeedback(missing ? Kind.MISSING : Kind.LOADED, file.toString()));
		} catch (java.io.IOException | RuntimeException e) {
			return new LoadResult(null, new JournalFeedback(Kind.LOAD_FAILED, file + ": " + e.getMessage()));
		}
	}

	public static JournalFeedback saved(Journal.SaveOutcome outcome, java.nio.file.Path file) {
		return outcome instanceof Journal.SaveOutcome.Failed failed
			? new JournalFeedback(Kind.SAVE_FAILED, file + ": " + failed.reason())
			: new JournalFeedback(Kind.SAVED, file.toString());
	}
}
