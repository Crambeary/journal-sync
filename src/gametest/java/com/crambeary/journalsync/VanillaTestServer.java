package com.crambeary.journalsync;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Owns the entire lifetime of an unmodified Mojang server, including failure cleanup. */
final class VanillaTestServer implements AutoCloseable {
	final int port;
	final Path directory;
	private final Process process;
	private final BufferedWriter console;

	VanillaTestServer() throws IOException {
		port = freePort();
		Path root = Path.of(System.getProperty("journal_sync.test.serverRoot"));
		Files.createDirectories(root);
		directory = Files.createTempDirectory(root, "server-");
		Files.writeString(directory.resolve("eula.txt"), "eula=true\n");
		Files.writeString(directory.resolve("server.properties"), """
			server-ip=127.0.0.1
			server-port=%d
			online-mode=false
			enforce-secure-profile=false
			view-distance=2
			simulation-distance=2
			spawn-protection=0
			gamemode=creative
			level-type=minecraft:flat
			generate-structures=false
			max-players=2
			""".formatted(port));
		process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			"-Xms256m", "-Xmx768m", "-jar", System.getProperty("journal_sync.test.vanillaJar"), "nogui")
			.directory(directory.toFile()).redirectErrorStream(true)
			.redirectOutput(directory.resolve("console.log").toFile()).start();
		console = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
	}

	static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		}
	}

	boolean ready() {
		if (!process.isAlive()) throw new AssertionError("Vanilla server exited: " + log());
		return log().contains("Done (");
	}

	String log() {
		try {
			return Files.readString(directory.resolve("console.log"));
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	void command(String command) throws IOException {
		console.write(command);
		console.newLine();
		console.flush();
	}

	@Override
	public void close() throws Exception {
		try {
			if (process.isAlive()) {
				command("stop");
				if (!process.waitFor(30, TimeUnit.SECONDS)) process.destroy();
			}
		} finally {
			if (process.isAlive()) {
				process.destroyForcibly();
				if (!process.waitFor(10, TimeUnit.SECONDS)) throw new AssertionError("Vanilla server did not exit");
			}
			console.close();
		}
	}
}
