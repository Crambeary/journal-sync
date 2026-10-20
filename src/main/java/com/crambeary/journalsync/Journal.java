package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Core of Journal Sync: moves a markdown journal file to and from the pages of a book,
 * independent of any screen. Pages are separated in the file by {@value #PAGE_MARKER} lines.
 */
public final class Journal {
	public static final String PAGE_MARKER = "<!-- page -->";
	private static final String PAGE_SEPARATOR = "\n" + PAGE_MARKER + "\n";

	private Journal() {
	}

	/** The pages read from the journal file, plus the token to hand back to {@link #save}. */
	public record Loaded(List<String> pages, LoadToken token) {
	}

	/** Identifies the content of the journal file at load time. */
	public record LoadToken(String contentHash) {
		/** The token for a journal file that did not exist when it was loaded. */
		public static final LoadToken ABSENT = new LoadToken(null);

		static LoadToken of(byte[] fileBytes) {
			try {
				return new LoadToken(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fileBytes)));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-256 is required by the Java platform", e);
			}
		}
	}

	/** What happened when saving. */
	public sealed interface SaveOutcome {
		record Saved() implements SaveOutcome {
		}

		record Failed(String reason) implements SaveOutcome {
		}
	}

	/**
	 * Reads the journal file into pages. A missing file is an empty journal, not an error.
	 *
	 * @throws IOException if the file exists but cannot be read
	 */
	public static Loaded load(Path file) throws IOException {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (NoSuchFileException e) {
			return new Loaded(List.of(), LoadToken.ABSENT);
		}
		return new Loaded(splitPages(new String(bytes, StandardCharsets.UTF_8)), LoadToken.of(bytes));
	}

	/**
	 * Writes the pages to the journal file, creating it (and its folder) if needed. Failures are
	 * reported in the outcome rather than thrown, so a close handler never has to catch.
	 *
	 * @param token the token from the {@link #load} that this save follows; not yet compared against
	 *              the file (conflict detection arrives in a later ticket)
	 */
	public static SaveOutcome save(Path file, List<String> pages, LoadToken token) {
		try {
			Path folder = file.toAbsolutePath().getParent();
			if (folder != null) {
				Files.createDirectories(folder);
			}
			Files.writeString(file, String.join(PAGE_SEPARATOR, pages), StandardCharsets.UTF_8);
			return new SaveOutcome.Saved();
		} catch (IOException | RuntimeException e) {
			return new SaveOutcome.Failed(e.getMessage() != null ? e.getMessage() : e.toString());
		}
	}

	private static List<String> splitPages(String text) {
		return List.of(text.split(Pattern.quote(PAGE_SEPARATOR), -1));
	}
}
