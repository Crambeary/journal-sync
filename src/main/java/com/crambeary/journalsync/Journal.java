package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

	/** What to do when the file changed on disk while the book was open. */
	public enum Resolution {
		/** Write the book's pages to a timestamped sibling file; the journal file is left untouched. */
		CONFLICT_COPY,
		/** Overwrite the journal file with the book's pages. */
		KEEP_BOOK,
		/** Leave the journal file as it is on disk and discard the book's pages. */
		KEEP_FILE;

		/** The safest choice, which loses nothing: what a prompt offers first and what dismissing it means. */
		public static final Resolution DEFAULT = CONFLICT_COPY;
	}

	/** What happened when saving. */
	public sealed interface SaveOutcome {
		/** The pages were written to the journal file. */
		record Saved() implements SaveOutcome {
		}

		/** The file changed since it was loaded and no {@link Resolution} was given; nothing was written. */
		record Conflict() implements SaveOutcome {
		}

		/** After a conflict, {@link Resolution#CONFLICT_COPY}: the pages were written to {@code copy}. */
		record SavedAsConflictCopy(Path copy) implements SaveOutcome {
		}

		/** After a conflict, {@link Resolution#KEEP_FILE}: the journal file was left as it is. */
		record KeptFile() implements SaveOutcome {
		}

		record Failed(String reason) implements SaveOutcome {
		}
	}

	private static final DateTimeFormatter COPY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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
	 * Writes the pages to the journal file, creating it (and its folder) if needed, unless the file
	 * changed since it was loaded: then nothing is written and {@link SaveOutcome.Conflict} is returned
	 * so the caller can ask what to do and call {@link #save(Path, List, LoadToken, Resolution)}.
	 * Failures are reported in the outcome rather than thrown, so a close handler never has to catch.
	 *
	 * @param token the token from the {@link #load} that this save follows
	 */
	public static SaveOutcome save(Path file, List<String> pages, LoadToken token) {
		return save(file, pages, token, null);
	}

	/**
	 * Like {@link #save(Path, List, LoadToken)}, but if the file changed since it was loaded, applies
	 * {@code resolution} instead of reporting a conflict. When the file is unchanged this is a plain
	 * save and {@code resolution} is ignored.
	 *
	 * @param resolution what to do on a conflict, or {@code null} to report {@link SaveOutcome.Conflict}
	 */
	public static SaveOutcome save(Path file, List<String> pages, LoadToken token, Resolution resolution) {
		try {
			if (!token.equals(currentToken(file))) {
				if (resolution == null) {
					return new SaveOutcome.Conflict();
				}
				return switch (resolution) {
					case KEEP_BOOK -> write(file, pages);
					case KEEP_FILE -> new SaveOutcome.KeptFile();
					case CONFLICT_COPY -> writeConflictCopy(file, pages);
				};
			}
			return write(file, pages);
		} catch (IOException | RuntimeException e) {
			return new SaveOutcome.Failed(e.getMessage() != null ? e.getMessage() : e.toString());
		}
	}

	/** The token for what is on disk right now. */
	private static LoadToken currentToken(Path file) throws IOException {
		try {
			return LoadToken.of(Files.readAllBytes(file));
		} catch (NoSuchFileException e) {
			return LoadToken.ABSENT;
		}
	}

	private static SaveOutcome write(Path file, List<String> pages) throws IOException {
		createParentFolder(file);
		Files.writeString(file, join(pages), StandardCharsets.UTF_8);
		return new SaveOutcome.Saved();
	}

	/** Writes {@code journal.md} as {@code journal.conflict-yyyyMMdd-HHmmss.md} beside it, never over an existing file. */
	private static SaveOutcome writeConflictCopy(Path file, List<String> pages) throws IOException {
		createParentFolder(file);
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String stem = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		String stamp = LocalDateTime.now().format(COPY_TIMESTAMP);
		for (int attempt = 1; ; attempt++) {
			String suffix = attempt == 1 ? "" : "-" + attempt;
			Path copy = file.resolveSibling(stem + ".conflict-" + stamp + suffix + extension);
			try {
				Files.writeString(copy, join(pages), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				return new SaveOutcome.SavedAsConflictCopy(copy);
			} catch (FileAlreadyExistsException taken) {
				// Another conflict copy in the same second: try the next suffix.
			}
		}
	}

	private static void createParentFolder(Path file) throws IOException {
		Path folder = file.toAbsolutePath().getParent();
		if (folder != null) {
			Files.createDirectories(folder);
		}
	}

	private static String join(List<String> pages) {
		return String.join(PAGE_SEPARATOR, pages);
	}

	private static List<String> splitPages(String text) {
		return List.of(text.split(Pattern.quote(PAGE_SEPARATOR), -1));
	}
}
