package com.crambeary.journalsync;

import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings are read on each bind, so correcting the file needs no restart. Relative paths use gameDir. */
public record JournalConfig(String globalPath, String worldPathTemplate, String serverPathTemplate, int backupCount) {
	public static JournalConfig defaults() {
		return new JournalConfig("journal-sync/journal.md", null, null, 20);
	}

	public static JournalConfig read(Path configFile) throws IOException {
		if (Files.notExists(configFile)) {
			JournalConfig defaults = defaults();
			Files.createDirectories(configFile.toAbsolutePath().getParent());
			Files.writeString(configFile, "{\n  \"globalPath\": \"journal-sync/journal.md\",\n  \"backupCount\": 20\n}\n");
			return defaults;
		}
		try {
			JsonObject json = new GsonBuilder().setStrictness(Strictness.STRICT).create()
				.fromJson(Files.readString(configFile), JsonObject.class);
			String global = string(json, "globalPath", defaults().globalPath());
			String world = string(json, "worldPathTemplate", null);
			String server = string(json, "serverPathTemplate", null);
			int count = 20;
			if (json.has("backupCount")) {
				if (!json.get("backupCount").isJsonPrimitive()
					|| !json.getAsJsonPrimitive("backupCount").isNumber()) throw new IllegalArgumentException("backupCount must be an integer");
				count = json.get("backupCount").getAsBigDecimal().intValueExact();
				if (count < 0) throw new IllegalArgumentException("backupCount must be nonnegative");
			}
			JournalConfig config = new JournalConfig(global, world, server, count);
			// Validate even templates that are not active in this world.
			config.resolve(Path.of("."), null, null, null);
			config.resolve(Path.of("."), "world", null, null);
			config.resolve(Path.of("."), null, "server", null);
			return config;
		} catch (RuntimeException e) {
			throw new IOException("Invalid journal-sync.json: " + e.getMessage(), e);
		}
	}

	private static String string(JsonObject json, String key, String fallback) {
		if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
		if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
			throw new IllegalArgumentException(key + " must be a string");
		String value = json.get(key).getAsString();
		if (value.isBlank()) throw new IllegalArgumentException(key + " must not be blank");
		return value;
	}

	/** A server context takes precedence; replacement values cannot introduce path components. */
	public Path resolve(Path gameDir, String worldName, String serverAddress, String override) {
		String template = globalPath;
		if (serverAddress != null && serverPathTemplate != null) template = serverPathTemplate;
		else if (worldName != null && worldPathTemplate != null) template = worldPathTemplate;
		String resolved = override != null ? override : template
			.replace("{world-name}", safe(worldName)).replace("{server-address}", safe(serverAddress));
		if (resolved.isBlank() || resolved.contains("{") || resolved.contains("}"))
			throw new IllegalArgumentException("Invalid journal path or unknown template placeholder");
		return gameDir.resolve(Path.of(resolved)).normalize();
	}

	private static String safe(String value) {
		if (value == null) return "unknown";
		String safe = value.replaceAll("[^\\p{L}\\p{N}._-]", "_");
		return safe.equals(".") || safe.equals("..") || safe.isEmpty() ? "unknown" : safe;
	}
}
