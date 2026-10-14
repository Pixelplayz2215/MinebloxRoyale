package gg.minebloxroyale.client;

import gg.minebloxroyale.Legacy;
import gg.minebloxroyale.hub.UnlockRule;
import gg.minebloxroyale.net.Net;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Your unlock stats from every Mineblox Royale world you've played in (kills, Victory Royales, obby, best
 * shooting-range round, range bullseyes), kept per Minecraft account outside the game folder (PlayerFiles:
 * %APPDATA%/MinebloxRoyale/unlocks.txt) and sent to any world you join, which counts them too. So emotes and gliders
 * you unlocked stay unlocked in your own world, in friends' worlds and in every later version (creator: unlocks belong
 * to the player, not the world or the build). Each world's server reports your stats there, filed under that world's id
 * (its room-code file); worlds from older builds, in this game folder or the launcher's other instances, are read once
 * the same way.
 */
public final class MyUnlocks {
	/** world id -> {kills, wins, obby 0/1, best range round, range bullseyes}, for the signed-in account. */
	private static final Map<String, int[]> WORLDS = new LinkedHashMap<>();
	private static String loadedFor;
	/** The world we're in (from its unlock stats), left out of "elsewhere"; null until they arrive. */
	private static String here;

	private MyUnlocks() {
	}

	private static Path file() {
		return PlayerFiles.dir().resolve("unlocks.txt");
	}

	private static void load() {
		String me = PlayerFiles.account();
		if (me.equals(loadedFor)) return;
		loadedFor = me;
		WORLDS.clear();
		try {
			for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
				// "account worldId kills wins obby [rangeBest bullseyes]" (older builds wrote the first five).
				String[] a = line.trim().split("\\s+");
				if ((a.length == 5 || a.length == 7) && a[0].equals(me))
					WORLDS.put(a[1], new int[] { Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), a.length == 7 ? Integer.parseInt(a[5]) : 0,
						a.length == 7 ? Integer.parseInt(a[6]) : 0 });
			}
		} catch (IOException | RuntimeException ignored) {
		}
		boolean found = false;
		for (Path instance : PlayerFiles.instances()) {
			try (Stream<Path> worlds = Files.list(instance.resolve("saves"))) {
				for (Path w : worlds.toList()) found |= importWorld(w, me);
			} catch (IOException | RuntimeException ignored) {
			}
		}
		if (found) save();
	}

	/** A Mineblox Royale world on this PC (leaderboard and obby files): my stats in it, if it has any. */
	private static boolean importWorld(Path world, String me) {
		try {
			// Worlds of older builds (other launcher instances) still have the old file names.
			Path board = Legacy.worldFile(world, "leaderboard.txt"), room = Legacy.worldFile(world, "room.txt");
			if (!Files.isRegularFile(board) || !Files.isRegularFile(room)) return false;
			String[] r = Files.readString(room, StandardCharsets.UTF_8).trim().split("\\s+");
			if (r.length != 2) return false;
			int kills = 0, wins = 0, obby = 0, range = 0, bulls = 0;
			for (String line : Files.readAllLines(board, StandardCharsets.UTF_8)) {
				String[] p = line.split("\t");
				if (p.length >= 6 && p[0].equals(me)) {
					wins = Integer.parseInt(p[2]);
					kills = Integer.parseInt(p[4]);
				}
			}
			Path unlocks = Legacy.worldFile(world, "unlocks.txt");
			if (Files.isRegularFile(unlocks))
				for (String line : Files.readAllLines(unlocks, StandardCharsets.UTF_8)) if (line.startsWith(me + "=") && line.length() > me.length() + 1) obby = 1;
			Path rangeFile = Legacy.worldFile(world, "range.txt");
			if (Files.isRegularFile(rangeFile))
				for (String line : Files.readAllLines(rangeFile, StandardCharsets.UTF_8)) {
					String[] kv = line.split("=", 2);
					if (kv.length != 2 || !kv[0].trim().equals(me)) continue;
					String[] n = kv[1].split(",");
					range = Integer.parseInt(n[0].trim());
					bulls = Integer.parseInt(n[1].trim());
				}
			return merge(r[1], new int[] { kills, wins, obby, range, bulls });
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Stats only grow: keep the higher of what's known and what's new ({kills, wins, obby, rangeBest, bullseyes}). */
	private static boolean merge(String worldId, int[] stats) {
		if (Arrays.stream(stats).allMatch(v -> v == 0)) return false;
		int[] old = WORLDS.get(worldId), now = stats.clone();
		if (old != null) for (int i = 0; i < now.length; i++) now[i] = Math.max(old[i], now[i]);
		if (Arrays.equals(old, now)) return false;
		WORLDS.put(worldId, now);
		return true;
	}

	/**
	 * Joined a world: send my stats from every world (it counts the other ones). Goes before the Locker outfit, which
	 * is checked against them.
	 */
	public static void onJoin() {
		load();
		here = null;
		StringBuilder b = new StringBuilder();
		WORLDS.forEach((id, s) -> b.append(b.isEmpty() ? "" : ";").append(id).append('=').append(s[0]).append(',').append(s[1]).append(',').append(s[2]).append(',')
			.append(s[3]).append(',').append(s[4]));
		ClientPlayNetworking.send(new Net.Action("career:" + b));
	}

	/** This world's server reported my stats in it: remember them under its id. */
	public static void onWorldStats(String worldId, UnlockRule.Stats st) {
		load();
		here = worldId;
		if (merge(worldId, new int[] { st.kills(), st.wins(), st.obby() ? 1 : 0, st.rangeBest(), st.bullseyes() })) save();
	}

	/** My stats from my other worlds. */
	public static UnlockRule.Stats elsewhere() {
		load();
		UnlockRule.Stats[] t = { UnlockRule.Stats.NONE };
		WORLDS.forEach((id, s) -> {
			if (!id.equals(here)) t[0] = t[0].plus(new UnlockRule.Stats(s[0], s[1], s[2] == 1, s[3], s[4]));
		});
		return t[0];
	}

	private static void save() {
		String me = PlayerFiles.account();
		List<String> lines = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) if (!line.startsWith(me + " ")) lines.add(line); // other accounts on this PC
		} catch (IOException ignored) {
		}
		WORLDS.forEach((id, s) -> lines.add(me + " " + id + " " + s[0] + " " + s[1] + " " + s[2] + " " + s[3] + " " + s[4]));
		try {
			Files.createDirectories(file().getParent());
			Files.write(file(), lines, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}
}
