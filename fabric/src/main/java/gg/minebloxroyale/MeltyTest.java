package gg.minebloxroyale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Melty's test of a release (test_release) on the creator's PC: while Melty's %LOCALAPPDATA%\Melty\test\mineblox-royale.json
 * is there, the game plays a match by itself (MatchManager's test mode, laid out with the file's seed) and reports each
 * step of it as "pass <name>" in the file's checks list, so a run shows the game was played, not only started. Players
 * never have that file, so for them none of this happens.
 */
public final class MeltyTest {
	public static final String SLUG = "mineblox-royale";
	private static final Path checks;
	private static final long seed;
	private static final Set<String> reported = new HashSet<>();

	static {
		Path list = null;
		long s = 0;
		String local = System.getenv("LOCALAPPDATA");
		Path file = local == null ? null : Path.of(local, "Melty", "test", SLUG + ".json");
		if (file != null && Files.isRegularFile(file)) {
			try {
				JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				boolean ours = !o.has("mod") || SLUG.equals(o.get("mod").getAsString());
				boolean current = !o.has("until") || Instant.parse(o.get("until").getAsString()).isAfter(Instant.now());
				if (ours && current && o.has("checks")) {
					list = Path.of(o.get("checks").getAsString());
					s = o.has("seed") ? o.get("seed").getAsLong() : 0;
					MinebloxRoyale.LOG.info("BR: Melty test run {} of {} (seed {}): the game plays a match by itself", o.has("run") ? o.get("run").getAsInt() : 0,
						o.has("runs") ? o.get("runs").getAsInt() : 0, s);
				}
			} catch (IOException | RuntimeException e) {
				MinebloxRoyale.LOG.warn("BR: couldn't read Melty's test file {}", file, e);
			}
		}
		checks = list;
		seed = s;
	}

	private MeltyTest() {
	}

	/** In one of Melty's test runs. */
	public static boolean active() {
		return checks != null;
	}

	public static long seed() {
		return seed;
	}

	/** This step of the test worked (reported once). */
	public static void pass(String check) {
		report(check, "pass " + check);
	}

	/** This step went wrong, and why (reported once). */
	public static void fail(String check, String why) {
		report(check, "fail " + check + ": " + why);
	}

	private static synchronized void report(String check, String line) {
		if (checks == null || !reported.add(check)) return;
		try {
			Files.writeString(checks, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't write Melty's test check {}", check, e);
		}
		MinebloxRoyale.LOG.info("BR: Melty test: {}", line);
	}
}
