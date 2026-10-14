package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.HubPieceDef;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The hub's top 10, for this world only (everyone who has played a match in the host's world; no online server).
 * Ranked by win:loss ratio, ties broken by average kills per round; also shows each player's best kills in one round.
 * Saved in the world folder (minebloxroyale_leaderboard.txt).
 */
public final class Leaderboard {
	/** One player's totals. losses = matches - wins. */
	static final class Stats {
		String name;
		int wins, matches, kills, best;

		double winLoss() {
			int losses = matches - wins;
			return losses == 0 ? wins : (double) wins / losses;
		}

		double avgKills() {
			return matches == 0 ? 0 : (double) kills / matches;
		}
	}

	private static final String TAG = "br_leaderboard";
	private static final Map<UUID, Stats> STATS = new LinkedHashMap<>();
	private static boolean loaded, redraw = true;

	private Leaderboard() {
	}

	/** Lifetime {kills, wins} of a player in this world (emote and glider unlocks). */
	public static int[] stats(MinecraftServer server, UUID id) {
		load(server);
		Stats s = STATS.get(id);
		return s == null ? new int[] { 0, 0 } : new int[] { s.kills, s.wins };
	}

	/** A player's match is over: one more match, their kills, and a win if they won. */
	public static void record(MinecraftServer server, UUID id, String name, boolean won, int kills) {
		load(server);
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		UnlockRule.Stats before = p == null ? null : Obby.career(p);
		Stats s = STATS.computeIfAbsent(id, k -> new Stats());
		s.name = name;
		s.matches++;
		if (won) s.wins++;
		s.kills += kills;
		s.best = Math.max(s.best, kills);
		save(server);
		redraw = true;
		if (p != null) Obby.announceUnlocks(p, before);
	}

	static List<Stats> top10() {
		List<Stats> all = new ArrayList<>(STATS.values());
		all.sort(Comparator.comparingDouble(Stats::winLoss).reversed().thenComparing(Comparator.comparingDouble(Stats::avgKills).reversed())
			.thenComparing(Comparator.comparingInt((Stats s) -> s.best).reversed()));
		return all.subList(0, Math.min(10, all.size()));
	}

	/**
	 * Called every second: rewrites the board's rows when they changed, once the hub's entities are loaded (so the
	 * old rows are really removed before the new ones appear).
	 */
	public static void tick(MinecraftServer server) {
		if (!redraw) return;
		HubPieceDef board = HubBuilder.piece("leaderboard");
		if (board == null) return; // no leaderboard row in the hub sheet
		ServerLevel level = server.overworld();
		double[] t = HubBuilder.boardText(board);
		if (!level.areEntitiesLoaded(ChunkPos.pack(BlockPos.containing(t[0], t[1], t[2])))) return;
		load(server);
		redraw = false;
		HubBuilder.command(server, "kill @e[type=minecraft:text_display,tag=" + TAG + "]");
		int headerLines = board.label().split(Pattern.quote(" | ")).length;
		List<Stats> top = top10();
		float scale = (float) t[3] * 0.8F;
		if (top.isEmpty()) {
			HubBuilder.label(server, TAG, t[0], HubBuilder.boardLineY(board, headerLines + 0.5), t[2], "Play a match to get on the board!", "gray", scale, "fixed", board.facing(), false);
			return;
		}
		for (int i = 0; i < top.size(); i++) {
			Stats s = top.get(i);
			String color = i == 0 ? "gold" : i == 1 ? "white" : i == 2 ? "#E0915A" : "gray";
			String row = String.format(Locale.ROOT, "#%d %s - %.2f - %.1f - %d", i + 1, s.name, s.winLoss(), s.avgKills(), s.best);
			HubBuilder.label(server, TAG, t[0], HubBuilder.boardLineY(board, headerLines + i * 0.85), t[2], row, color, scale, "fixed", board.facing(), false);
		}
	}

	/** The hub was rebuilt (or the server started): draw the rows again. */
	public static void markDirty() {
		redraw = true;
	}

	private static void load(MinecraftServer server) {
		if (loaded) return;
		loaded = true;
		Path f = file(server);
		if (!Files.exists(f)) return;
		try {
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String[] p = line.split("\t");
				if (p.length < 6) continue;
				Stats s = new Stats();
				s.name = p[1];
				s.wins = Integer.parseInt(p[2]);
				s.matches = Integer.parseInt(p[3]);
				s.kills = Integer.parseInt(p[4]);
				s.best = Integer.parseInt(p[5]);
				STATS.put(UUID.fromString(p[0]), s);
			}
		} catch (Exception e) {
			MinebloxRoyale.LOG.warn("BR: couldn't read the leaderboard: {}", e.toString());
		}
	}

	private static void save(MinecraftServer server) {
		StringBuilder sb = new StringBuilder();
		for (var e : STATS.entrySet()) {
			Stats s = e.getValue();
			sb.append(e.getKey()).append('\t').append(s.name).append('\t').append(s.wins).append('\t').append(s.matches).append('\t').append(s.kills).append('\t').append(s.best).append('\n');
		}
		try {
			Files.writeString(file(server), sb.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			MinebloxRoyale.LOG.warn("BR: couldn't save the leaderboard: {}", e.toString());
		}
	}

	/** A new world (or server) starts with a fresh board. */
	public static void reset() {
		STATS.clear();
		loaded = false;
		redraw = true;
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_leaderboard.txt");
	}
}
