package gg.minebloxroyale.client.media;

import gg.minebloxroyale.Legacy;
import gg.minebloxroyale.MinebloxRoyale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Where the player's own games and the bundled ffmpeg are. MINEBLOXROYALE_ROBLOX and MINEBLOXROYALE_FORTNITE can give
 * the games' folders; without them the usual install places are tried. (1.0.1.1: Melty no longer passes them, since
 * naming {game:fortnite} in the launch made Melty stop Play for players without Fortnite, which is optional.) Nothing is copied out of these folders: files are read and decoded in memory while playing.
 */
public final class GameDirs {
	private static Path robloxContent, fortnite, ffmpeg;
	private static boolean resolved;

	private GameDirs() {
	}

	private static synchronized void resolve() {
		if (resolved) return;
		resolved = true;
		robloxContent = findRobloxContent();
		fortnite = findFortnite();
		ffmpeg = findFfmpeg();
		MinebloxRoyale.LOG.info("BR media: roblox content={} fortnite={} ffmpeg={}", robloxContent, fortnite, ffmpeg);
	}

	public static @Nullable Path robloxContent() {
		resolve();
		return robloxContent;
	}

	public static @Nullable Path fortnite() {
		resolve();
		return fortnite;
	}

	public static @Nullable Path ffmpeg() {
		resolve();
		return ffmpeg;
	}

	private static @Nullable Path env(String name) {
		String v = System.getenv(name);
		return v == null || v.isBlank() ? null : Path.of(v.trim());
	}

	/**
	 * A Roblox folder is either a version folder (has content/) or a root with Versions/<v>/content. Roblox isn't
	 * always in %LOCALAPPDATA%\Roblox (creator's friend: Roblox installed, still the Minecraft look): bootstrappers
	 * such as Bloxstrap or Fishstrap keep their own Versions folder, older installs used Program Files, and the
	 * Microsoft Store version lives in WindowsApps. The program Windows starts for Roblox links points at whichever one
	 * is used.
	 */
	private static @Nullable Path findRobloxContent() {
		List<Path> bases = new ArrayList<>();
		Path given = env("MINEBLOXROYALE_ROBLOX");
		if (given != null) {
			bases.add(given);
			bases.add(given.getParent());
		}
		Path launcher = robloxLauncher();
		if (launcher != null && launcher.getParent() != null) {
			bases.add(launcher.getParent());
			bases.add(launcher.getParent().getParent());
		}
		String local = System.getenv("LOCALAPPDATA");
		if (local != null) {
			bases.add(Path.of(local, "Roblox"));
			try (Stream<Path> s = Files.list(Path.of(local))) {
				s.filter(p -> Files.isDirectory(p.resolve("Versions"))).forEach(bases::add);
			} catch (IOException ignored) {
			}
		}
		for (String pf : new String[] { "ProgramFiles(x86)", "ProgramFiles" })
			if (System.getenv(pf) != null) bases.add(Path.of(System.getenv(pf), "Roblox"));
		for (Path base : bases) {
			if (base == null) continue;
			Path c = contentIn(base);
			if (c != null) return c;
		}
		Path store = storeRoblox();
		Path c = store == null ? null : contentIn(store);
		if (c == null) MinebloxRoyale.LOG.warn("BR media: no Roblox avatar files (content/avatar/meshes) found; looked in {}{}", bases, store == null ? "" : " and " + store);
		return c;
	}

	/**
	 * The program Windows opens for roblox-player links: Roblox itself (Versions/version-.../RobloxPlayerBeta.exe) or a
	 * bootstrapper (e.g. Bloxstrap.exe, with its Versions folder next to it).
	 */
	private static @Nullable Path robloxLauncher() {
		for (String key : new String[] { "HKCU\\Software\\Classes\\roblox-player\\shell\\open\\command", "HKCR\\roblox-player\\shell\\open\\command" }) {
			String out = run("reg", "query", key, "/ve");
			if (out == null) continue;
			Matcher m = Pattern.compile("\"([^\"]+?\\.exe)\"|REG_SZ\\s+([^\\s\"]+?\\.exe)", Pattern.CASE_INSENSITIVE).matcher(out);
			if (!m.find()) continue;
			try {
				return Path.of(m.group(1) != null ? m.group(1) : m.group(2));
			} catch (RuntimeException ignored) {
			}
		}
		return null;
	}

	/**
	 * The Microsoft Store version of Roblox (creator's friend), in C:\Program
	 * Files\WindowsApps\ROBLOXCORPORATION.ROBLOX_... That folder can't be listed, so Windows is asked where the package
	 * is. Only asked when nothing else was found (starting PowerShell takes a moment; this runs on a background thread
	 * at start-up).
	 */
	private static @Nullable Path storeRoblox() {
		String out = run("powershell", "-NoProfile", "-Command", "Get-AppxPackage -Name '*Roblox*' | ForEach-Object { $_.InstallLocation }");
		if (out == null) return null;
		Path first = null;
		for (String line : out.lines().map(String::trim).filter(l -> !l.isEmpty()).toList()) {
			try {
				Path p = Path.of(line);
				if (contentIn(p) != null) return p;
				if (first == null) first = p;
			} catch (RuntimeException ignored) {
			}
		}
		return first;
	}

	/** Runs a small Windows command and returns what it printed, or null (not Windows, failed, or took over 10 s). */
	private static @Nullable String run(String... command) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			p.getOutputStream().close();
			byte[] out = p.getInputStream().readAllBytes();
			if (!p.waitFor(10, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			return p.exitValue() == 0 ? new String(out, StandardCharsets.UTF_8) : null;
		} catch (IOException | RuntimeException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static @Nullable Path contentIn(Path base) {
		if (Files.isDirectory(base.resolve("content/avatar"))) return base.resolve("content");
		if (Files.isDirectory(base.resolve("avatar")) && Files.isDirectory(base.resolve("sounds"))) return base;
		Path versions = base.resolve("Versions");
		if (!Files.isDirectory(versions)) versions = base;
		try (Stream<Path> s = Files.list(versions)) {
			return s.filter(p -> Files.isDirectory(p.resolve("content/avatar/meshes")))
				.max(Comparator.comparingLong(p -> p.toFile().lastModified()))
				.map(p -> p.resolve("content")).orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Fortnite's folder (the one with FortniteGame). The Epic launcher lists its installs in LauncherInstalled.dat and
	 * in one manifest (.item) per game; the usual install places are tried too. A folder counts if it has the splash
	 * picture the hub shows. (It used to require Content\Movies, which a current install may not have: creator's
	 * friend, Fortnite installed, not found.)
	 */
	private static @Nullable Path findFortnite() {
		Path given = env("MINEBLOXROYALE_FORTNITE");
		if (given != null && Files.isDirectory(given.resolve("FortniteGame"))) return given;
		if (given != null && given.getFileName() != null && given.getFileName().toString().equals("FortniteGame")) return given.getParent();
		Path data = Path.of(System.getenv("ProgramData") != null ? System.getenv("ProgramData") : "C:/ProgramData", "Epic");
		List<Path> places = new ArrayList<>();
		installLocations(data.resolve("UnrealEngineLauncher/LauncherInstalled.dat"), places);
		try (Stream<Path> s = Files.list(data.resolve("EpicGamesLauncher/Data/Manifests"))) {
			s.filter(p -> p.toString().endsWith(".item")).forEach(p -> installLocations(p, places));
		} catch (IOException ignored) {
		}
		for (String pf : new String[] { "ProgramFiles", "ProgramFiles(x86)" })
			if (System.getenv(pf) != null) places.add(Path.of(System.getenv(pf), "Epic Games", "Fortnite"));
		for (Path p : places)
			if (Files.isRegularFile(p.resolve("FortniteGame/Content/Splash/Splash.bmp"))) return p;
		for (Path p : places)
			if (Files.isDirectory(p.resolve("FortniteGame"))) return p;
		MinebloxRoyale.LOG.warn("BR media: Fortnite not found; looked in {}", places);
		return null;
	}

	/** Every "InstallLocation" in one of the Epic launcher's files (JSON, backslashes doubled). */
	private static void installLocations(Path file, List<Path> into) {
		try {
			Matcher m = Pattern.compile("\"InstallLocation\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(file));
			while (m.find()) into.add(Path.of(m.group(1).replace("\\\\", "\\")));
		} catch (IOException | RuntimeException ignored) {
		}
	}

	private static @Nullable Path findFfmpeg() {
		Path given = env("MINEBLOXROYALE_FFMPEG");
		if (given != null && Files.isRegularFile(given)) return given;
		Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath();
		for (String folder : new String[] { Legacy.NEW_FOLDER, Legacy.OLD_FOLDER }) { // (an older build's folder, if it couldn't be moved)
			Path bundled = gameDir.resolve(folder).resolve("ffmpeg/ffmpeg.exe");
			if (Files.isRegularFile(bundled)) return bundled;
		}
		// Development: the project's .tools folder above the run directory.
		for (Path p = gameDir; p != null; p = p.getParent()) {
			Path tools = p.resolve(".tools/ffmpeg");
			if (!Files.isDirectory(tools)) continue;
			try (Stream<Path> s = Files.walk(tools, 3)) {
				return s.filter(f -> f.getFileName().toString().equals("ffmpeg.exe")).findFirst().orElse(null);
			} catch (IOException e) {
				return null;
			}
		}
		return null;
	}
}
