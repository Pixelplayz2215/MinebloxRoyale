package gg.minebloxroyale.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * (1.0.1.4) Did the last game close by itself, and why (players' Melty, 1.0.1.3: crashes in nvoglv64.dll, NVIDIA's
 * OpenGL driver, and one with no log at all)? Runs before Minecraft starts (no game classes here). A marker file says a
 * game is running; a normal exit removes it (shutdown hook), a crash or a forced close leaves it. Then the cause is
 * looked for: the JVM's own crash file (hs_err_pid*.log in the game folder, for crashes the JVM catches), or Windows'
 * crash report for javaw.exe (WER, for crashes it can't, like the driver's). The cause goes in latest.log and melty.log
 * as one plain line, so players' next reports carry it; a crash inside a graphics driver also gets a line in the chat
 * saying how to fix it, since only the player can (by updating the driver).
 */
public final class LastRun {
	private static final Logger LOG = LogManager.getLogger("MinebloxRoyale");
	private static final Pattern FRAME = Pattern.compile("^#\\s+[A-Za-z]\\s+\\[([^+\\]]+)");
	private static final Pattern EXCEPTION = Pattern.compile("#\\s+(EXCEPTION_[A-Z_]+|SIG[A-Z]+)\\s*\\((0x[0-9a-fA-F]+)\\)");
	/** The words for the chat, once the player is in a world (null: nothing to say). */
	private static volatile String chat;
	/** The cause found at start, for melty.log (null: the last game ended normally, or this is the first). */
	private static volatile String cause;

	private LastRun() {
	}

	/** At start: what happened last time, then mark this game as running. */
	public static void check(Path gameDir, Path brDir) {
		Path marker = brDir.resolve("running.txt");
		try {
			if (Files.exists(marker)) {
				long started = 0;
				try {
					started = Long.parseLong(Files.readString(marker, StandardCharsets.UTF_8).trim().split("\\s+")[0]);
				} catch (IOException | RuntimeException e) {
					started = Files.getLastModifiedTime(marker).toMillis();
				}
				String[] found = fromHsErr(gameDir, brDir, started);
				if (found == null) found = fromWer(started);
				String what = found == null ? "no crash report found (a crash Windows didn't record, or the game was closed by force)"
					: "crashed in " + found[0] + (found[1].isEmpty() ? "" : " (" + found[1] + ")");
				String driver = found == null ? null : driver(found[0]);
				cause = "the last game closed by itself: " + what + (driver == null ? "" : ", " + driver + " graphics driver");
				LOG.info("BR: {}", cause);
				if (driver != null) chat = "Your last game crashed inside your " + driver + " graphics driver (" + found[0] + "). Updating your graphics driver usually fixes this: "
					+ (driver.equals("NVIDIA") ? "nvidia.com/drivers" : driver.equals("AMD") ? "amd.com/support" : "intel.com/support (Driver & Support Assistant)");
			}
			Files.createDirectories(brDir);
			Files.writeString(marker, System.currentTimeMillis() + " " + ProcessHandle.current().pid() + System.lineSeparator(), StandardCharsets.UTF_8);
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Files.deleteIfExists(marker);
				} catch (IOException ignored) {
				}
			}, "MinebloxRoyale last-run marker"));
		} catch (IOException | RuntimeException e) { // never in the way of the game starting
			LOG.info("BR: couldn't check how the last game ended ({})", e.toString());
		}
	}

	/** The line for melty.log (null if the last game ended normally). */
	static String cause() {
		return cause;
	}

	/** The chat line about a graphics driver crash, once (null: nothing to say). */
	static String takeChat() {
		String c = chat;
		chat = null;
		return c;
	}

	/**
	 * The JVM's crash file from that game (hs_err_pid*.log in the game folder, newer than its start): {module, code}.
	 * Moved into minebloxroyale/crashes afterwards, so it's only told once.
	 */
	static String[] fromHsErr(Path gameDir, Path brDir, long started) {
		String[] best = null;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(gameDir, "hs_err_pid*.log")) {
			for (Path f : files) {
				if (Files.getLastModifiedTime(f).toMillis() < started - 5000) continue;
				String[] found = parseHsErr(Files.readString(f, StandardCharsets.ISO_8859_1));
				if (found != null) best = found;
				try {
					Files.createDirectories(brDir.resolve("crashes"));
					Files.move(f, brDir.resolve("crashes").resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		} catch (IOException | RuntimeException e) {
			return best;
		}
		return best;
	}

	/** The faulting module and the exception from an hs_err file ({module, code}), or null. */
	static String[] parseHsErr(String text) {
		String code = "", module = null;
		String[] lines = text.split("\\r?\\n");
		for (int i = 0; i < lines.length && i < 60; i++) {
			Matcher e = EXCEPTION.matcher(lines[i]);
			if (code.isEmpty() && e.find()) code = e.group(2).toLowerCase(Locale.ROOT).replace("0x", "");
			if (lines[i].startsWith("# Problematic frame:") && i + 1 < lines.length) {
				Matcher m = FRAME.matcher(lines[i + 1]);
				module = m.find() ? m.group(1).trim() : lines[i + 1].replaceFirst("^#\\s*", "").trim();
			}
		}
		return module == null && code.isEmpty() ? null : new String[] { module == null ? "unknown" : module, code };
	}

	/** Windows' crash report for javaw.exe / java.exe since that game started: {module, code}, or null. */
	static String[] fromWer(long started) {
		String programData = System.getenv("ProgramData"), local = System.getenv("LOCALAPPDATA");
		String[] best = null;
		long bestTime = 0;
		for (String root : new String[] { programData, local }) {
			if (root == null || root.isBlank()) continue;
			for (String kind : new String[] { "ReportArchive", "ReportQueue" }) {
				Path dir = Path.of(root, "Microsoft", "Windows", "WER", kind);
				try (DirectoryStream<Path> reports = Files.newDirectoryStream(dir, "AppCrash_java*")) {
					for (Path r : reports) {
						long t = Files.getLastModifiedTime(r).toMillis();
						if (t < started - 5000 || t < bestTime) continue;
						Path wer = r.resolve("Report.wer");
						if (!Files.exists(wer)) continue;
						String[] found = parseWer(new String(Files.readAllBytes(wer), StandardCharsets.UTF_16));
						if (found != null) {
							best = found;
							bestTime = t;
						}
					}
				} catch (IOException | RuntimeException ignored) { // no such folder, or not ours to read
				}
			}
		}
		return best;
	}

	/** The fault module and exception code from a Windows Report.wer ({module, code}), or null. */
	static String[] parseWer(String text) {
		String[] names = new String[16], values = new String[16];
		for (String line : text.split("\\r?\\n")) {
			Matcher m = Pattern.compile("^Sig\\[(\\d+)\\]\\.(Name|Value)=(.*)$").matcher(line.trim());
			if (!m.find()) continue;
			int i = Integer.parseInt(m.group(1));
			if (i >= 16) continue;
			if (m.group(2).equals("Name")) names[i] = m.group(3).trim();
			else values[i] = m.group(3).trim();
		}
		String module = null, code = "";
		for (int i = 0; i < 16; i++) {
			if (names[i] == null || values[i] == null) continue;
			if (names[i].equalsIgnoreCase("Fault Module Name")) module = values[i];
			if (names[i].equalsIgnoreCase("Exception Code")) code = values[i].toLowerCase(Locale.ROOT);
		}
		return module == null ? null : new String[] { module, code };
	}

	/** Whose graphics driver a module is (null: not a graphics driver). */
	static String driver(String module) {
		String m = module.toLowerCase(Locale.ROOT);
		if (m.startsWith("nvoglv") || m.startsWith("nvwgf2") || m.startsWith("nvd3dum") || m.startsWith("nvlddmkm")) return "NVIDIA";
		if (m.startsWith("atio6axx") || m.startsWith("atioglxx") || m.startsWith("atig6pxx") || m.startsWith("amdxx") || m.startsWith("atiumd")) return "AMD";
		if (m.matches("ig[0-9a-z]*icd(32|64)\\.dll") || m.startsWith("igxelp") || m.startsWith("igd10") || m.startsWith("igd12")) return "Intel";
		return null;
	}
}
