package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.world.item.component.WritableBookContent;

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

	/** Load refusal before a screen can be bound or its existing pages replaced. */
	public static final class PageCapExceededException extends IOException {
		private final int pageCount;

		public PageCapExceededException(int pageCount) {
			super("Journal has " + pageCount + " pages; the book limit is " + WritableBookContent.MAX_PAGES
				+ ". Sync refused; file unchanged.");
			this.pageCount = pageCount;
		}

		public int pageCount() {
			return pageCount;
		}
	}

	/** What happened when saving. */
	public sealed interface SaveOutcome {
		record Saved() implements SaveOutcome {
		}

		record OverCap(int pageCount, int maxPages) implements SaveOutcome {
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
		return new Loaded(splitPages(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()), LoadToken.of(bytes));
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
			if (pages.size() > WritableBookContent.MAX_PAGES) {
				return new SaveOutcome.OverCap(pages.size(), WritableBookContent.MAX_PAGES);
			}
			if (pages.stream().anyMatch(page -> page.length() > WritableBookContent.PAGE_EDIT_LENGTH)) {
				return new SaveOutcome.Failed("Journal page exceeds the book character limit; file unchanged.");
			}
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

	private static List<String> splitPages(String text) throws PageCapExceededException {
		List<String> pages = new ArrayList<>();
		// Preserve explicit boundaries, empty pages and every character inside each page. Oversized
		// pages (including an entire marker-less note) acquire additional boundaries on next save.
		for (String page : text.split(Pattern.quote(PAGE_SEPARATOR), -1)) {
			int start = 0;
			do {
				int end = Math.min(page.length(), start + WritableBookContent.PAGE_EDIT_LENGTH);
				// Minecraft counts UTF-16 units. Never cut a supplementary character in half.
				if (end < page.length() && Character.isHighSurrogate(page.charAt(end - 1))
					&& Character.isLowSurrogate(page.charAt(end))) {
					end--;
				}
				pages.add(page.substring(start, end));
				start = end;
			} while (start < page.length());
		}
		if (pages.size() > WritableBookContent.MAX_PAGES) {
			throw new PageCapExceededException(pages.size());
		}
		return List.copyOf(pages);
	}
}
