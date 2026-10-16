package gg.minebloxroyale.match;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * (1.0.0.4) Old islands off the disk. Every match is played on a new island, on land never generated before, and the
 * old ones used to stay in the world folder for good: around 100 MB a match, until a player's disk ran full (Melty,
 * 2026-10-15: "There is not enough space on the disk" while the next island was being made). When the world opens,
 * before any chunk is loaded, the overworld's region files are deleted except the hub's, the next island's
 * (minebloxroyale_island.txt) and any the island restore list still has blocks in (a session that stopped mid-match puts
 * those back first). A deleted island's land is simply new land again. Only ever in Mineblox Royale's own world: its
 * folder, with the hub built in it; any other world the mod is loaded into is left exactly as it is. Plain file work,
 * no game classes.
 */
public final class OldIslands {
	/** Region files (512 x 512 blocks) kept either way round the hub at 0, 0: far past anything the hub builds. */
	static final int HUB_REGIONS = 2;
	/**
	 * The mod's own world's folder (AutoWorld.WORLD, Legacy.NEW_WORLD; Legacy.OLD_WORLD from before the rename). (1.0.1.1)
	 * Also Minecraft's demo world: a Minecraft account that doesn't own the game plays in demo mode, whose title screen
	 * offers only "Play Demo World", and players went on playing Mineblox Royale there. Its islands were never removed,
	 * until the disk ran full mid-match (Melty, 2026-10-16: "There is not enough space on the disk" in Demo_World).
	 */
	static final List<String> WORLDS = List.of("Mineblox Royale", "Block Royale", "Demo_World");
	private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
	/** A chunk too big for its region file is kept beside it (c.x.z.mcc, chunk coordinates). */
	private static final Pattern OVERSIZED = Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.mcc");

	/** What was removed: files, bytes, and files that couldn't be (left for next time). */
	public record Result(int files, long bytes, int failed) {
	}

	private OldIslands() {
	}

	/**
	 * Deletes the old islands' region files in the world folder; islandRadius is how far an island's chunks reach from
	 * its centre (blocks). Never throws: whatever can't be read or deleted is left as it is.
	 */
	public static Result prune(Path world, double islandRadius) {
		if (!ours(world)) return new Result(0, 0, 0);
		Set<Long> keep = new HashSet<>();
		for (int rx = -HUB_REGIONS; rx <= HUB_REGIONS; rx++)
			for (int rz = -HUB_REGIONS; rz <= HUB_REGIONS; rz++) keep.add(key(rx, rz));
		// the next island (as IslandPregen saved it: "x z chunks-done terrain"), with a region to spare all round
		try {
			String[] s = Files.readString(world.resolve("minebloxroyale_island.txt"), StandardCharsets.UTF_8).trim().split("\\s+");
			double cx = Double.parseDouble(s[0]), cz = Double.parseDouble(s[1]), r = islandRadius + 512;
			for (int rx = Math.floorDiv((int) Math.floor(cx - r), 512); rx <= Math.floorDiv((int) Math.ceil(cx + r), 512); rx++)
				for (int rz = Math.floorDiv((int) Math.floor(cz - r), 512); rz <= Math.floorDiv((int) Math.ceil(cz + r), 512); rz++) keep.add(key(rx, rz));
		} catch (IOException | RuntimeException e) { // no island yet, or unreadable: only the hub is certain
		}
		// blocks still to be put back after a session that stopped mid-match ("x y z block" a line, IslandRestore)
		try {
			for (String line : Files.readAllLines(world.resolve("minebloxroyale_restore.txt"), StandardCharsets.UTF_8)) {
				String[] p = line.trim().split(" ", 4);
				if (p.length < 3) continue;
				try {
					int x = Integer.parseInt(p[0]), z = Integer.parseInt(p[2]);
					for (int dx = -1; dx <= 1; dx++)
						for (int dz = -1; dz <= 1; dz++) keep.add(key(Math.floorDiv(x, 512) + dx, Math.floorDiv(z, 512) + dz));
				} catch (NumberFormatException e) {
				}
			}
		} catch (IOException | RuntimeException e) { // none: nothing to put back
		}
		int files = 0, failed = 0;
		long bytes = 0;
		for (Path dim : List.of(world.resolve("dimensions").resolve("minecraft").resolve("overworld"), world)) {
			for (String kind : List.of("region", "entities", "poi")) {
				Path dir = dim.resolve(kind);
				if (!Files.isDirectory(dir)) continue;
				try (DirectoryStream<Path> list = Files.newDirectoryStream(dir)) {
					for (Path f : list) {
						String name = f.getFileName().toString();
						Matcher m = REGION.matcher(name), c = OVERSIZED.matcher(name);
						long k;
						if (m.matches()) k = key(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
						else if (c.matches()) k = key(Math.floorDiv(Integer.parseInt(c.group(1)), 32), Math.floorDiv(Integer.parseInt(c.group(2)), 32));
						else continue;
						if (keep.contains(k)) continue;
						try {
							long size = Files.size(f);
							Files.delete(f);
							files++;
							bytes += size;
						} catch (IOException | RuntimeException e) {
							failed++;
						}
					}
				} catch (IOException | RuntimeException e) {
					failed++;
				}
			}
		}
		return new Result(files, bytes, failed);
	}

	/**
	 * Mineblox Royale's own world: the folder the mod makes and opens, with the hub built in it (HubBuilder's
	 * minebloxroyale_hub.txt). Never a player's own world that the mod happens to be loaded into.
	 */
	static boolean ours(Path world) {
		try {
			Path dir = world.toAbsolutePath().normalize();
			return dir.getFileName() != null && WORLDS.contains(dir.getFileName().toString()) && Files.isRegularFile(dir.resolve("minebloxroyale_hub.txt"));
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static long key(int rx, int rz) {
		return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
	}
}
