package com.crambeary.journalsync;

/** Shared feedback channel: core outcomes map to localized client messages without screen dependencies. */
public record JournalFeedback(Kind kind, String detail, int pageCount, int maxPages) {
	public JournalFeedback(Kind kind, String detail) { this(kind, detail, 0, 0); }
	public enum Kind { LOADED, MISSING, SAVED, LOAD_FAILED, SAVE_FAILED, INVALID_CONFIG, CAP_EXCEEDED, CONFLICT, KEPT_FILE, CONFLICT_COPY, LOCK_FAILED }

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
		} catch (Journal.PageCapExceededException e) {
			return new LoadResult(null, cap(file, e.pageCount(), net.minecraft.world.item.component.WritableBookContent.MAX_PAGES));
		} catch (java.io.IOException | RuntimeException e) {
			return new LoadResult(null, new JournalFeedback(Kind.LOAD_FAILED, file + ": " + e.getMessage()));
		}
	}

	/** Client load: one backup-aware call, including missing-file preservation. */
	public static LoadResult load(java.nio.file.Path file, java.util.List<String> currentPages,
		java.nio.file.Path gameDirectory, int backupCount) {
		Journal.LoadOutcome outcome = Journal.load(file, currentPages, gameDirectory, backupCount);
		if (outcome instanceof Journal.LoadOutcome.OverCap overCap) {
			return new LoadResult(null, cap(file, overCap.pageCount(), overCap.maxPages()));
		}
		if (outcome instanceof Journal.LoadOutcome.Failed failed) {
			return new LoadResult(null, new JournalFeedback(Kind.LOAD_FAILED, file + ": " + failed.reason()));
		}
		Journal.Loaded loaded = ((Journal.LoadOutcome.LoadedWithBackup) outcome).loaded();
		boolean missing = loaded.token().equals(Journal.LoadToken.ABSENT);
		if (missing) loaded = new Journal.Loaded(java.util.List.copyOf(currentPages), loaded.token());
		return new LoadResult(loaded, new JournalFeedback(missing ? Kind.MISSING : Kind.LOADED, file.toString()));
	}

	private static JournalFeedback cap(java.nio.file.Path file, int count, int limit) {
		return new JournalFeedback(Kind.CAP_EXCEEDED, file.toString(), count, limit);
	}

	public static JournalFeedback saved(Journal.SaveOutcome outcome, java.nio.file.Path file) {
		return switch (outcome) {
			case Journal.SaveOutcome.OverCap overCap -> cap(file, overCap.pageCount(), overCap.maxPages());
			case Journal.SaveOutcome.Failed failed -> new JournalFeedback(Kind.SAVE_FAILED, file + ": " + failed.reason());
			case Journal.SaveOutcome.Conflict conflict -> new JournalFeedback(Kind.CONFLICT, file.toString());
			case Journal.SaveOutcome.KeptFile kept -> new JournalFeedback(Kind.KEPT_FILE, file.toString());
			case Journal.SaveOutcome.SavedAsConflictCopy copy -> new JournalFeedback(Kind.CONFLICT_COPY, copy.copy().toString());
			case Journal.SaveOutcome.Saved saved -> new JournalFeedback(Kind.SAVED, file.toString());
		};
	}
}
