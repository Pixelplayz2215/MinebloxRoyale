package gg.minebloxroyale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The game was called Block Royale up to test9, when the creator renamed it Mineblox Royale; every name in the game now
 * uses the new one. This is the one place that still knows the old names, only so that nothing from older builds is
 * lost (creator: unlocks must survive updates): the first time the new build starts, a world's blockroyale_* files
 * (room code, leaderboard, unlock stats, outfits, island) take their new names, the game folder's "blockroyale" folder
 * and the "Block Royale" world move to their new names, and the per-account record in %APPDATA%\BlockRoyale is copied.
 * Fabric's list of the mod content a world used takes the new names too, so an older world opens without Fabric's
 * "Missing content detected!" warning.
 */
public final class Legacy {
	public static final String OLD_FILE_PREFIX = "blockroyale_", NEW_FILE_PREFIX = "minebloxroyale_";
	/** In the game folder: the bundled ffmpeg, Melty's files, older outfit files. */
	public static final String OLD_FOLDER = "blockroyale", NEW_FOLDER = "minebloxroyale";
	/** The world's folder in saves. */
	public static final String OLD_WORLD = "Block Royale", NEW_WORLD = "Mineblox Royale";
	/** The per-account unlocks and outfits in %APPDATA% (or the home folder). */
	public static final String OLD_APPDATA = "BlockRoyale", OLD_HOME = ".blockroyale";

	private Legacy() {
	}

	/** A world from an older build: its blockroyale_* files take their new names (once, before anything reads them). */
	public static void migrateWorld(Path worldDir) {
		if (!Files.isDirectory(worldDir)) return;
		int moved = 0;
		try (Stream<Path> s = Files.list(worldDir)) {
			for (Path p : s.toList()) {
				String n = p.getFileName().toString();
				if (!n.startsWith(OLD_FILE_PREFIX)) continue;
				Path to = worldDir.resolve(NEW_FILE_PREFIX + n.substring(OLD_FILE_PREFIX.length()));
				if (Files.exists(to)) continue;
				Files.move(p, to);
				moved++;
			}
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't rename this world's files from the old game name", e);
		}
		if (moved > 0) MinebloxRoyale.LOG.info("BR: {} world files renamed from the old game name (Block Royale)", moved);
	}

	/**
	 * Fabric keeps a list of the mod content each world used (data/fabric/registry_entries.dat) and, when any of it is
	 * gone, stops the world from opening with "Missing content detected!". Our items, the bot and the battle bus were
	 * blockroyale:... before the rename: the list takes their new names (before the world opens), since they are the
	 * same things. (Old copies of them left in the world - a gun in someone's inventory - still vanish on loading; the
	 * hub hands everyone a fresh loadout anyway.)
	 */
	public static void migrateContentList(Path worldDir) {
		Path file = worldDir.resolve("data").resolve("fabric").resolve("registry_entries.dat");
		if (!Files.isRegularFile(file)) return;
		try {
			CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			int[] renamed = { 0 };
			rename(tag, renamed);
			if (renamed[0] == 0) return;
			NbtIo.writeCompressed(tag, file);
			MinebloxRoyale.LOG.info("BR: {} mod content names in {} renamed from the old game name", renamed[0], worldDir.getFileName());
		} catch (IOException | RuntimeException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't rename the mod content list of {}", worldDir, e);
		}
	}

	/** Every "blockroyale:..." string in the tag becomes "minebloxroyale:...". */
	private static Tag rename(Tag t, int[] renamed) {
		if (t instanceof StringTag(String v) && v.startsWith(OLD_FOLDER + ":")) {
			renamed[0]++;
			return StringTag.valueOf(NEW_FOLDER + v.substring(OLD_FOLDER.length()));
		}
		if (t instanceof ListTag list) for (int i = 0; i < list.size(); i++) list.set(i, rename(list.get(i), renamed));
		if (t instanceof CompoundTag c) for (String k : List.copyOf(c.keySet())) c.put(k, rename(c.get(k), renamed));
		return t;
	}

	/**
	 * A world file by its new name, or by its old one when only an older build wrote it (another launcher instance).
	 */
	public static Path worldFile(Path worldDir, String name) {
		Path now = worldDir.resolve(NEW_FILE_PREFIX + name);
		if (Files.exists(now)) return now;
		Path old = worldDir.resolve(OLD_FILE_PREFIX + name);
		return Files.exists(old) ? old : now;
	}

	/** A file in an instance's game folder, by the new folder name or (older builds) the old one. */
	public static List<Path> instanceFiles(Path instance, String name) {
		return List.of(instance.resolve(NEW_FOLDER).resolve(name), instance.resolve(OLD_FOLDER).resolve(name));
	}

	/**
	 * A folder from an older build: moved to its new name when that doesn't exist yet, otherwise whatever is missing is
	 * copied across (nothing already there is replaced). Copy keeps the old one for older builds still installed.
	 */
	public static void migrateFolder(Path from, Path to, boolean keepOld) {
		if (!Files.isDirectory(from)) return;
		try {
			if (!Files.exists(to) && !keepOld) {
				Files.move(from, to);
				MinebloxRoyale.LOG.info("BR: moved {} to {} (game renamed)", from, to);
				return;
			}
			int copied = 0;
			try (Stream<Path> s = Files.walk(from)) {
				for (Path p : s.toList()) {
					Path t = to.resolve(from.relativize(p).toString());
					if (Files.isDirectory(p)) Files.createDirectories(t);
					else if (!Files.exists(t)) {
						Files.copy(p, t, StandardCopyOption.COPY_ATTRIBUTES);
						copied++;
					}
				}
			}
			if (copied > 0) MinebloxRoyale.LOG.info("BR: copied {} files from {} to {} (game renamed)", copied, from, to);
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't bring {} over to {}", from, to, e);
		}
	}
}
