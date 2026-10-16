package gg.minebloxroyale.client;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * (1.0.1.1) Melty now runs Mineblox Royale in a Minecraft it sets up itself (it brings Prism Launcher, Java and the
 * player's sign-in), instead of the Prism Launcher copy the package used to carry in %LOCALAPPDATA%\MinebloxRoyale.
 * That copy's game folder held the player's world. The first start in the new place takes it across (room code,
 * leaderboard, match count), with the game's settings: moved when both are on the same drive (nothing copied, no disk
 * space needed), otherwise copied without the old islands, which are made again. Unlocks and outfits were never there
 * (%APPDATA%\MinebloxRoyale). Runs before Minecraft starts (no game classes here); never throws.
 */
final class OldInstall {
	private static final Logger LOG = LogManager.getLogger("MinebloxRoyale");
	private static final String WORLD = "Mineblox Royale";
	/** Region files kept when copying: the hub's, around 0, 0 (OldIslands.HUB_REGIONS). */
	private static final int HUB_REGIONS = 2;
	private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
	private static final Pattern OVERSIZED = Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.mcc");
	private static final Set<String> CHUNK_FOLDERS = Set.of("region", "entities", "poi");
	/** The island and its restore list belong to land that isn't copied: a new island is picked instead. */
	private static final Set<String> ISLAND_FILES = Set.of("minebloxroyale_island.txt", "minebloxroyale_restore.txt", "session.lock");
	/** The old Prism copy's own downloads and caches (never "instances", which holds the player's files). */
	private static final String[] CACHES = { "assets", "libraries", "java", "meta", "metacache", "cache" };

	private OldInstall() {
	}

	static void moveTo(Path game) {
		try {
			String local = System.getenv("LOCALAPPDATA");
			if (local == null || local.isBlank()) return;
			Path old = Path.of(local, "MinebloxRoyale", "Prism Launcher", "instances", "Mineblox Royale", "minecraft");
			if (!Files.isDirectory(old) || !Files.isDirectory(game) || Files.isSameFile(old, game)) return;
			// Not during one of Melty's test runs on the creator's PC (MeltyTest): their world stays for their own game.
			if (Files.exists(Path.of(local, "Melty", "test", "mineblox-royale.json"))) return;
			removeOldDownloads(old.getParent().getParent().getParent()); // first: a copy to another drive may need the room
			Path oldWorld = old.resolve("saves").resolve(WORLD), world = game.resolve("saves").resolve(WORLD);
			if (Files.isDirectory(oldWorld) && !Files.exists(world)) {
				Files.createDirectories(world.getParent());
				try {
					Files.move(oldWorld, world);
					LOG.info("BR: your Mineblox Royale world came across from the earlier install (moved)");
				} catch (IOException e) { // another drive
					try {
						copyWithoutIslands(oldWorld, world);
					} catch (IOException | RuntimeException half) { // never leave half a world to open
						deleteTree(world);
						throw half;
					}
					LOG.info("BR: your Mineblox Royale world came across from the earlier install (copied; a new island is made)");
				}
			}
			Path options = game.resolve("options.txt"), oldOptions = old.resolve("options.txt");
			if (Files.isRegularFile(oldOptions) && !Files.exists(options)) {
				Files.copy(oldOptions, options);
				LOG.info("BR: your game settings came across from the earlier install");
			}
		} catch (IOException | RuntimeException e) {
			LOG.info("BR: the earlier install's world stays where it was ({}); this one makes its own", e.getMessage());
		}
	}

	/**
	 * What the old Prism copy downloaded for itself (Minecraft's assets and libraries, its Java, its version lists:
	 * about 700 MB) stays behind when Melty removes that copy's own files, and nothing uses it any more. It's removed
	 * once Prism itself is gone, so a disk that was nearly full gets the room back. Nothing of the player's: the
	 * instance folder (the world if it couldn't come across, screenshots) is left as it is.
	 */
	private static void removeOldDownloads(Path prism) {
		if (!Files.isDirectory(prism) || Files.exists(prism.resolve("prismlauncher.exe"))) return; // gone, or still in use
		long freed = 0;
		for (String cache : CACHES) {
			Path dir = prism.resolve(cache);
			if (Files.isDirectory(dir)) freed += deleteTree(dir);
		}
		if (freed > 0) LOG.info("BR: removed what the earlier install's launcher had downloaded ({} MB freed)", freed >> 20);
	}

	private static void copyWithoutIslands(Path from, Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();
				boolean top = file.getParent().equals(from);
				if (top && (ISLAND_FILES.contains(name) || name.startsWith("minebloxroyale_map_"))) return FileVisitResult.CONTINUE;
				if (CHUNK_FOLDERS.contains(file.getParent().getFileName().toString()) && !hub(name)) return FileVisitResult.CONTINUE;
				Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** Deletes a folder and everything in it as far as it can; returns the bytes freed. Never throws. */
	private static long deleteTree(Path dir) {
		long[] freed = { 0 };
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					try {
						Files.deleteIfExists(file);
						freed[0] += attrs.size();
					} catch (IOException | RuntimeException e) { // in use: left for next time
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path d, IOException e) {
					try {
						Files.deleteIfExists(d);
					} catch (IOException | RuntimeException ignored) { // not empty: something was left
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException | RuntimeException e) {
			LOG.info("BR: couldn't tidy up {} ({})", dir.getFileName(), e.getMessage());
		}
		return freed[0];
	}

	/** A region file (or oversized chunk) of the hub's land; any other file in a chunk folder isn't land and is kept. */
	private static boolean hub(String name) {
		Matcher r = REGION.matcher(name), c = OVERSIZED.matcher(name);
		if (r.matches()) return Math.abs(Integer.parseInt(r.group(1))) <= HUB_REGIONS && Math.abs(Integer.parseInt(r.group(2))) <= HUB_REGIONS;
		if (c.matches()) return Math.abs(Math.floorDiv(Integer.parseInt(c.group(1)), 32)) <= HUB_REGIONS && Math.abs(Math.floorDiv(Integer.parseInt(c.group(2)), 32)) <= HUB_REGIONS;
		return true;
	}
}
