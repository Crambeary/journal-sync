package com.crambeary.journalsync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.DirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;
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

	/** Result of a load that must preserve the book's previous contents first. */
	public sealed interface LoadOutcome {
		record LoadedWithBackup(Loaded loaded, Path backup) implements LoadOutcome { }
		record Failed(String reason) implements LoadOutcome { }
	}

	/** Result of creating or removing the informational lock sidecar. */
	public sealed interface LockOutcome {
		record Updated() implements LockOutcome { }
		record Failed(String reason) implements LockOutcome { }
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
		return save(file, pages, token, Path.of(System.getProperty("user.dir")), 20);
	}

	/** Saves through the one overwrite path, backing up the old journal before replacing it. */
	public static SaveOutcome save(Path file, List<String> pages, LoadToken token, Path gameDirectory, int backupCount) {
		try {
			if (Files.exists(file)) {
				writeBackup(gameDirectory, file.getFileName().toString(), Files.readAllBytes(file), backupCount);
			}
			Path folder = file.toAbsolutePath().getParent();
			if (folder != null) {
				Files.createDirectories(folder);
				ensureWritableDirectory(folder);
			}
			Files.writeString(file, String.join(PAGE_SEPARATOR, pages), StandardCharsets.UTF_8);
			return new SaveOutcome.Saved();
		} catch (IOException | RuntimeException e) {
			return new SaveOutcome.Failed(e.getMessage() != null ? e.getMessage() : e.toString());
		}
	}

	/** Preserves the book's current pages, replaces any stale lock, then loads the journal. */
	public static LoadOutcome load(Path file, List<String> previousBookPages, Path gameDirectory, int backupCount) {
		try {
			Path backup = writeBackup(gameDirectory, "book", String.join(PAGE_SEPARATOR, previousBookPages)
				.getBytes(StandardCharsets.UTF_8), backupCount);
			LockOutcome lock = createLock(file);
			if (lock instanceof LockOutcome.Failed failed) return new LoadOutcome.Failed(failed.reason());
			try {
				return new LoadOutcome.LoadedWithBackup(load(file), backup);
			} catch (IOException | RuntimeException e) {
				removeLock(file);
				return new LoadOutcome.Failed(message(e));
			}
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
			backups.sort(Comparator.comparingLong(Journal::modified).reversed());
			for (int i = backupCount; i < backups.size(); i++) Files.deleteIfExists(backups.get(i));
		}
		return backup;
	}

	private static long modified(Path path) {
		try { return Files.getLastModifiedTime(path).toMillis(); }
		catch (IOException e) { return Long.MIN_VALUE; }
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

	private static List<String> splitPages(String text) {
		return List.of(text.split(Pattern.quote(PAGE_SEPARATOR), -1));
	}
}
