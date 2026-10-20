package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.nio.file.DirectoryStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.UUID;

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

		record OverCap(int pageCount, int maxPages) implements SaveOutcome { }

		record Failed(String reason) implements SaveOutcome {
		}
	}

	/** Result of a load that must preserve the book's previous contents first. */
	public sealed interface LoadOutcome {
		record LoadedWithBackup(Loaded loaded, Path backup) implements LoadOutcome { }
		record OverCap(int pageCount, int maxPages) implements LoadOutcome { }
		record Failed(String reason) implements LoadOutcome { }
	}

	/** Result of creating or removing the informational lock sidecar. */
	public sealed interface LockOutcome {
		record Updated() implements LockOutcome { }
		record Failed(String reason) implements LockOutcome { }
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
		return new Loaded(splitPages(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()), LoadToken.of(bytes));
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
		return save(file, pages, token, null, Path.of(System.getProperty("user.dir")), 20);
	}

	/**
	 * Like {@link #save(Path, List, LoadToken)}, but if the file changed since it was loaded, applies
	 * {@code resolution} instead of reporting a conflict. When the file is unchanged this is a plain
	 * save and {@code resolution} is ignored.
	 *
	 * @param resolution what to do on a conflict, or {@code null} to report {@link SaveOutcome.Conflict}
	 */
	public static SaveOutcome save(Path file, List<String> pages, LoadToken token, Resolution resolution) {
		return save(file, pages, token, resolution, Path.of(System.getProperty("user.dir")), 20);
	}

	public static SaveOutcome save(Path file, List<String> pages, LoadToken token, Path gameDirectory, int backupCount) {
		return save(file, pages, token, null, gameDirectory, backupCount);
	}

	public static SaveOutcome save(Path file, List<String> pages, LoadToken token, Resolution resolution, Path gameDirectory, int backupCount) {
		try {
			if (pages.size() > WritableBookContent.MAX_PAGES) {
				return new SaveOutcome.OverCap(pages.size(), WritableBookContent.MAX_PAGES);
			}
			if (pages.stream().anyMatch(page -> page.length() > WritableBookContent.PAGE_EDIT_LENGTH)) {
				return new SaveOutcome.Failed("Journal page exceeds the book character limit; file unchanged.");
			}
			if (!token.equals(currentToken(file))) {
				if (resolution == null) {
					return new SaveOutcome.Conflict();
				}
				return switch (resolution) {
					case KEEP_BOOK -> write(file, pages, gameDirectory, backupCount);
					case KEEP_FILE -> new SaveOutcome.KeptFile();
					case CONFLICT_COPY -> writeConflictCopy(file, pages);
				};
			}
			return write(file, pages, gameDirectory, backupCount);
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

	private static SaveOutcome write(Path file, List<String> pages, Path gameDirectory, int backupCount) throws IOException {
		if (Files.exists(file)) {
			writeBackup(gameDirectory, file.getFileName().toString(), Files.readAllBytes(file), backupCount);
		}
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
			ensureWritableDirectory(folder);
		}
	}

	private static String join(List<String> pages) {
		return String.join(PAGE_SEPARATOR, pages);
	}

	/** Reads the journal, preserves the current book, and creates/replaces the informational lock before binding. */
	public static LoadOutcome load(Path file, List<String> previousBookPages, Path gameDirectory, int backupCount) {
		try {
			Loaded loaded = load(file);
			Path backup = writeBackup(gameDirectory, "book", join(previousBookPages).getBytes(StandardCharsets.UTF_8), backupCount);
			LockOutcome lock = createLock(file);
			if (lock instanceof LockOutcome.Failed failed) return new LoadOutcome.Failed(failed.reason());
			return new LoadOutcome.LoadedWithBackup(loaded, backup);
		} catch (PageCapExceededException e) {
			return new LoadOutcome.OverCap(e.pageCount(), WritableBookContent.MAX_PAGES);
		} catch (IOException | RuntimeException e) {
			return new LoadOutcome.Failed(message(e));
		}
	}

	/** Creates or replaces the informational sidecar. A stale sidecar never blocks loading. */
	public static LockOutcome createLock(Path file) {
		try {
			Path lock = lockPath(file);
			Path parent = lock.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
				ensureWritableDirectory(parent);
			}
			Files.writeString(lock, "Journal Sync is open\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			return new LockOutcome.Updated();
		} catch (IOException | RuntimeException e) {
			return new LockOutcome.Failed(message(e));
		}
	}

	/** Removes the sidecar when the bound screen closes. */
	public static LockOutcome removeLock(Path file) {
		try {
			Path lock = lockPath(file);
			Path parent = lock.toAbsolutePath().getParent();
			if (parent != null && Files.exists(parent)) ensureWritableDirectory(parent);
			Files.deleteIfExists(lock);
			return new LockOutcome.Updated();
		} catch (IOException | RuntimeException e) {
			return new LockOutcome.Failed(message(e));
		}
	}

	public static Path lockPath(Path file) { return file.resolveSibling(file.getFileName() + ".lock"); }

	private static Path writeBackup(Path gameDirectory, String prefix, byte[] bytes, int backupCount) throws IOException {
		if (backupCount < 0) throw new IllegalArgumentException("backupCount must not be negative");
		Path folder = gameDirectory.resolve("journal-sync-backups");
		Files.createDirectories(folder);
		ensureWritableDirectory(folder);
		Path backup = folder.resolve(prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".md");
		Files.write(backup, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.md")) {
			List<Path> backups = new java.util.ArrayList<>();
			stream.forEach(backups::add);
			backups.sort(Comparator.comparing(Journal::modified).reversed());
			for (int i = backupCount; i < backups.size(); i++) Files.deleteIfExists(backups.get(i));
		}
		return backup;
	}

	private static java.nio.file.attribute.FileTime modified(Path path) {
		try { return Files.getLastModifiedTime(path); }
		catch (IOException e) { return java.nio.file.attribute.FileTime.fromMillis(Long.MIN_VALUE); }
	}

	private static String message(Exception e) { return e.getMessage() != null ? e.getMessage() : e.toString(); }

	private static void ensureWritableDirectory(Path directory) throws IOException {
		try {
			var permissions = Files.getPosixFilePermissions(directory);
			if (!permissions.contains(PosixFilePermission.OWNER_WRITE)
				&& !permissions.contains(PosixFilePermission.GROUP_WRITE)
				&& !permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
				throw new AccessDeniedException(directory.toString(), null, "directory has no write permission");
			}
		} catch (UnsupportedOperationException ignored) {
			// Files.isWritable covers filesystems without POSIX permission bits.
			if (!Files.isWritable(directory)) throw new AccessDeniedException(directory.toString());
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
