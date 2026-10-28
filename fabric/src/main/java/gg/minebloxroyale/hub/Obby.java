package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.net.RoomCode;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.GrantDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Roblox obby rules: checkpoints, fall below the course -> back to your checkpoint, finish -> unlock the reward emote.
 */
public final class Obby {
	private static final Map<UUID, Integer> CHECKPOINT = new HashMap<>();
	/**
	 * Checkpoint 0 (2.0.0-test6.3, creator: "When stepped on the blue and Obby is attempted that's checkpoint 0, if player
	 * falls off Obby before checkpoint one, TP back to the blue platform rather than the HUB spawn. If player steps back on
	 * the gray floor, checkpoint 0 is erased for that player"): who has stood on the blue obby start since they last
	 * stood on the hub's floor.
	 */
	private static final Set<UUID> AT_START = new HashSet<>();
	/**
	 * (2.0.0) How long each player has stood on the hub's floor since checkpoint 0 (ticks): it's only cleared after
	 * FLOOR_TICKS there, so a moment's touch of the floor beside the blue start doesn't clear it.
	 */
	private static final Map<UUID, Integer> ON_FLOOR = new HashMap<>();
	private static final int FLOOR_TICKS = 10;
	private static final Map<UUID, Set<String>> UNLOCKS = new HashMap<>();
	/**
	 * The obby timer (2.0.0, creator: "Add a timer to the Obby that starts as soon as you touch the first red Obby Jump"):
	 * the server tick each player's run started on (touching the first stage), and each player's best time in this world
	 * (ticks, minebloxroyale_obby_times.txt). A fall back to a checkpoint keeps it running; back to the blue start, or
	 * onto the hub's floor, stops it without a time (it starts again at the first red jump).
	 */
	private static final Map<UUID, Long> RUN = new HashMap<>();
	private static final Map<UUID, Integer> BEST = new HashMap<>();
	/**
	 * Each player's stats from their other Mineblox Royale worlds, as their own game reports them on joining
	 * (MyUnlocks): unlocks belong to the player, not the world (creator: emotes and gliders vanished in a friend's
	 * world). Trusted as reported: it's friends playing together.
	 */
	private static final Map<UUID, UnlockRule.Stats> ELSEWHERE = new HashMap<>();
	private static MinecraftServer loadedFor, worldIdFor;
	private static String worldId;

	private Obby() {
	}

	public static void tick(MinecraftServer server) {
		if (loadedFor != server) load(server);
		if (MatchManager.get().state() == MatchManager.State.LIVE && server.getPlayerList().getPlayers().stream().allMatch(MatchManager.get()::isAlive)) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.gameMode() != GameType.ADVENTURE) continue;
			if (p.getY() < MinebloxRoyale.setting("obbyFallY") && p.getY() > MinebloxRoyale.setting("obbyFallY") - 120) {
				respawnAtCheckpoint(p);
				continue;
			}
			if (!p.onGround()) continue;
			// The blue start: checkpoint 0 (said once a run). Anywhere else on the hub's floor clears it.
			if (HubBuilder.onObbyStart(p.getX(), p.getY(), p.getZ())) {
				ON_FLOOR.remove(p.getUUID());
				if (AT_START.add(p.getUUID()) && CHECKPOINT.getOrDefault(p.getUUID(), 0) == 0)
					p.sendSystemMessage(Component.literal("Obby start: fall before checkpoint 1 and you're back here").withStyle(ChatFormatting.AQUA), true);
			} else if (AT_START.contains(p.getUUID()) && HubBuilder.onHubFloor(p.getX(), p.getY(), p.getZ())
				&& ON_FLOOR.merge(p.getUUID(), 1, Integer::sum) >= FLOOR_TICKS) {
				AT_START.remove(p.getUUID());
				ON_FLOOR.remove(p.getUUID());
				stopTimer(p);
			}
			if (RUN.containsKey(p.getUUID()) && HubBuilder.onHubFloor(p.getX(), p.getY(), p.getZ())) stopTimer(p); // (walked off the course)
			for (HubBuilder.Platform pl : HubBuilder.platforms()) {
				if (!pl.contains(p.getX(), p.getY(), p.getZ())) continue;
				int idx = pl.stage().index();
				// The first red jump starts the timer (once a run).
				if (idx == firstStage() && !RUN.containsKey(p.getUUID())) {
					RUN.put(p.getUUID(), (long) server.getTickCount());
					ServerPlayNetworking.send(p, new Net.ObbyTimer(1, 0, BEST.getOrDefault(p.getUUID(), 0), ""));
				}
				if (pl.stage().finish()) {
					finish(server, p);
					break;
				}
				if (pl.stage().checkpoint() && CHECKPOINT.getOrDefault(p.getUUID(), 0) < idx) {
					CHECKPOINT.put(p.getUUID(), idx);
					Net.sfxTo(p, "ui_click", p.position());
					long n = HubBuilder.platforms().stream().filter(x -> x.stage().checkpoint() && !x.stage().finish() && x.stage().index() <= idx).count();
					long of = HubBuilder.platforms().stream().filter(x -> x.stage().checkpoint() && !x.stage().finish()).count();
					p.sendSystemMessage(Component.literal("Checkpoint " + n + "/" + of + " reached").withStyle(ChatFormatting.GREEN), true);
				}
			}
		}
	}

	/** The first stage of the course (its lowest index): the first red jump, where the timer starts. */
	private static int firstStage() {
		int first = Integer.MAX_VALUE;
		for (HubBuilder.Platform pl : HubBuilder.platforms()) if (!pl.stage().finish()) first = Math.min(first, pl.stage().index());
		return first;
	}

	/** A run that ends without a time (back to the blue start, onto the hub's floor): the timer goes from their screen. */
	private static void stopTimer(ServerPlayer p) {
		if (RUN.remove(p.getUUID()) != null) ServerPlayNetworking.send(p, new Net.ObbyTimer(0, 0, 0, ""));
	}

	/**
	 * The run's time at the gold platform: their best in this world (New best!), and the obby-time presets it earns
	 * (presets sheet unlock obbytime: 45, 30 and 15 seconds or less), shown on their screen for 4 seconds.
	 */
	private static void timed(MinecraftServer server, ServerPlayer p) {
		Long start = RUN.remove(p.getUUID());
		if (start == null) return;
		int ticks = (int) (server.getTickCount() - start), best = BEST.getOrDefault(p.getUUID(), 0);
		boolean newBest = best == 0 || ticks < best;
		if (newBest) {
			BEST.put(p.getUUID(), ticks);
			saveTimes(server);
		}
		List<String> earned = new ArrayList<>();
		for (PresetDef preset : Sheets.PRESETS)
			if (preset.unlock().equals("obbytime") && ticks <= preset.need() * 20 && Gold.grant(p, preset)) earned.add(preset.name());
		String line = (newBest ? "New best!" : "Best " + clock(best)) + (earned.isEmpty() ? "" : "  Unlocked: " + String.join(", ", earned));
		ServerPlayNetworking.send(p, new Net.ObbyTimer(2, ticks, newBest ? ticks : best, line));
		if (!earned.isEmpty())
			p.sendSystemMessage(Component.literal("Unlocked: " + String.join(", ", earned) + " - wear it from the Locker (K)").withStyle(ChatFormatting.GOLD));
		MinebloxRoyale.LOG.info("BR: {} ran the obby in {}{}", p.getName().getString(), clock(ticks), newBest ? " (best)" : "");
	}

	/** m:ss.t (ticks). */
	static String clock(int ticks) {
		int tenths = ticks / 2;
		return tenths / 600 + ":" + String.format(java.util.Locale.ROOT, "%02d.%d", tenths / 10 % 60, tenths % 10);
	}

	/** (2.0.0) A preset earned by playing (a Sky Tower's top): told on screen, the Locker's NEW badge does the rest. */
	public static void presetEarned(ServerPlayer p, PresetDef preset) {
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(preset.name() + " - wear it from the Locker (K)")));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("PRESET UNLOCKED").withStyle(ChatFormatting.GOLD)));
	}

	/** The gold platform: unlock the obby emote (once), then back to the middle of the hub with checkpoints reset. */
	private static void finish(MinecraftServer server, ServerPlayer p) {
		timed(server, p);
		boolean had = obbyDone(p), unlocked = false;
		for (EmoteDef e : Sheets.EMOTES) {
			if (!e.unlock().equals("obby")) continue;
			Set<String> mine = UNLOCKS.computeIfAbsent(p.getUUID(), k -> new LinkedHashSet<>());
			if (!mine.add(e.id())) continue;
			save(server);
			sendUnlocks(p); // the player's game also remembers it for every other world
			if (had) continue; // already finished it in another world
			unlocked = true;
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Emote unlocked: " + e.name() + " (press B)")));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("OBBY COMPLETE!").withStyle(ChatFormatting.GOLD)));
			Net.emoteAll(server, p, e.id());
			MinebloxRoyale.LOG.info("BR: {} finished the obby, unlocked {}", p.getName().getString(), e.id());
		}
		if (!unlocked) {
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("You already have the obby emote")));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("OBBY COMPLETE!").withStyle(ChatFormatting.GOLD)));
		}
		CHECKPOINT.remove(p.getUUID());
		AT_START.remove(p.getUUID());
		ON_FLOOR.remove(p.getUUID());
		Vec3 at = HubBuilder.spawnPos();
		p.fallDistance = 0;
		p.setDeltaMovement(Vec3.ZERO);
		p.teleportTo(server.overworld(), at.x, at.y, at.z, Set.of(), 180F, 0F, true);
		Net.sfxTo(p, "ui_click", at);
	}

	/**
	 * Fell off: back to your last checkpoint; before checkpoint 1, to the blue obby start if you came from it
	 * (checkpoint 0, facing the course), else to the hub spawn.
	 */
	public static void respawnAtCheckpoint(ServerPlayer p) {
		int cp = CHECKPOINT.getOrDefault(p.getUUID(), 0);
		boolean start = cp == 0 && AT_START.contains(p.getUUID());
		if (cp == 0) stopTimer(p); // (back to the start: the next run's timer starts at the first red jump again)
		Vec3 at = start ? HubBuilder.obbyStartPos() : cp == 0 ? HubBuilder.spawnPos()
			: HubBuilder.platforms().stream().filter(pl -> pl.stage().index() == cp).findFirst().map(HubBuilder.Platform::top).orElse(HubBuilder.spawnPos());
		p.fallDistance = 0;
		p.setDeltaMovement(Vec3.ZERO);
		p.teleportTo(p.level().getServer().overworld(), at.x, at.y, at.z, Set.of(), start ? 90F : p.getYRot(), 0F, true); // (90: facing west, along the course)
		Net.sfxAll(p.level().getServer(), "getup", at);
	}

	public static void resetProgress(ServerPlayer p) {
		stopTimer(p);
		CHECKPOINT.remove(p.getUUID());
		AT_START.remove(p.getUUID());
		ON_FLOOR.remove(p.getUUID());
	}

	/**
	 * Emotes unlock by the obby, wins, kills, a range score or bullseyes, over all the player's worlds (emotes sheet).
	 */
	public static boolean hasEmote(ServerPlayer p, EmoteDef e) {
		return tester(p) || UnlockRule.has(e.unlock(), e.need(), career(p), e.id());
	}

	public static boolean hasGlider(ServerPlayer p, GliderDef g) {
		return tester(p) || UnlockRule.has(g.unlock(), g.need(), career(p), g.id());
	}

	/** Locker items: free, apart from the Dominus sets (cosmetics sheet unlock / need). */
	public static boolean hasCosmetic(ServerPlayer p, CosmeticDef c) {
		return tester(p) || UnlockRule.has(c.unlock(), c.need(), career(p), c.id());
	}

	/**
	 * The player's unlock stats in this world: kills and wins (leaderboard), the obby, and their shooting-range record.
	 */
	private static UnlockRule.Stats here(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		int[] board = Leaderboard.stats(server, p.getUUID()), range = RangeStats.get(server, p.getUUID()), gold = Gold.get(server, p.getUUID());
		return new UnlockRule.Stats(board[0], board[1], obbyDoneHere(p), range[0], range[1], gold[0], gold[1], Gold.owned(server, p.getUUID()));
	}

	/** Lifetime unlock stats: this world's plus the player's other worlds. */
	public static UnlockRule.Stats career(ServerPlayer p) {
		return here(p).plus(ELSEWHERE.getOrDefault(p.getUUID(), UnlockRule.Stats.NONE));
	}

	/**
	 * The player's own game reports their stats from every world they've played in
	 * ("worldId=kills,wins,obby,rangeBest,bullseyes,goldEarned,goldSpent,item+item;...", the later ones missing from older
	 * builds); the other worlds'
	 * count here too. Sent on joining, before the Locker outfit (which is checked against unlocks).
	 */
	public static void setCareer(ServerPlayer p, String report) {
		MinecraftServer server = p.level().getServer();
		if (loadedFor != server) load(server);
		String here = worldId(server);
		UnlockRule.Stats t = UnlockRule.Stats.NONE;
		for (String entry : report.split(";")) {
			String[] kv = entry.split("=", 2);
			if (kv.length != 2 || kv[0].equals(here)) continue;
			String[] n = kv[1].split(",");
			try {
				// (v2.0.0 adds gold earned, gold spent and what it bought: "item+item", "-" for none)
				Set<String> bought = n.length > 7 && !n[7].isBlank() && !n[7].trim().equals("-") ? Set.of(n[7].trim().split("[+]")) : Set.of();
				t = t.plus(new UnlockRule.Stats(num(n, 0), num(n, 1), num(n, 2) == 1, num(n, 3), num(n, 4), num(n, 5), num(n, 6), bought));
			} catch (RuntimeException ignored) {
			}
		}
		ELSEWHERE.put(p.getUUID(), t);
		MinebloxRoyale.LOG.info("BR: {} brings unlock stats from other worlds: {} kills, {} wins, obby {}, range best {}, {} bullseyes, {} gold, {} bought",
			p.getName().getString(), t.kills(), t.wins(), t.obby() ? "done" : "not done", t.rangeBest(), t.bullseyes(), t.gold(), t.owned().size());
	}

	/** A count from a career report (0 when it's missing: older builds send fewer). */
	private static int num(String[] n, int i) {
		return i < n.length ? Math.max(0, Integer.parseInt(n[i].trim())) : 0;
	}

	/**
	 * This world's id, which each player's game files their stats under: the random id kept with its room code
	 * (minebloxroyale_room.txt), which worlds from older builds already have, so their stats can be read from the file
	 * too.
	 */
	public static String worldId(MinecraftServer server) {
		if (worldIdFor != server) {
			worldId = RoomCode.of(server).owner();
			worldIdFor = server;
		}
		return worldId;
	}

	/**
	 * Every emote and glider unlocked: the dev test account (MinebloxDev, only in the development run), and the players
	 * the creator granted everything (grants sheet), by account id or, in offline test worlds, by name.
	 */
	public static boolean tester(ServerPlayer p) {
		String name = p.getGameProfile().name(), id = p.getUUID().toString();
		if (MinebloxRoyale.devAccount(p)) return true;
		for (GrantDef g : Sheets.GRANTS) if (g.uuid().equalsIgnoreCase(id) || g.name().equalsIgnoreCase(name)) return true;
		return false;
	}

	/** Finished the obby, in this world or another one. */
	public static boolean obbyDone(ServerPlayer p) {
		return obbyDoneHere(p) || ELSEWHERE.getOrDefault(p.getUUID(), UnlockRule.Stats.NONE).obby();
	}

	private static boolean obbyDoneHere(ServerPlayer p) {
		// Only (re)load for a new world: reloading on every call also wiped every obby checkpoint (each emote checks
		// this).
		MinecraftServer server = p.level().getServer();
		if (loadedFor != server) load(server);
		return !UNLOCKS.getOrDefault(p.getUUID(), Set.of()).isEmpty();
	}

	/**
	 * The client's copy of this world's unlock stats: "kills;wins;obby(0/1);all(0/1);worldId;rangeBest;bullseyes;goldEarned;
	 * goldSpent;item+item" (v2.0.0: gold and what it bought, "-" for nothing). Its
	 * game remembers them under the world id and adds the player's other worlds (the Locker shows what's still locked).
	 */
	public static void sendUnlocks(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		UnlockRule.Stats st = here(p);
		ServerPlayNetworking.send(p, new Net.Unlocks(st.kills() + ";" + st.wins() + ";" + (st.obby() ? 1 : 0) + ";" + (tester(p) ? 1 : 0) + ";" + worldId(server) + ";"
			+ st.rangeBest() + ";" + st.bullseyes() + ";" + st.goldEarned() + ";" + st.goldSpent() + ";" + (st.owned().isEmpty() ? "-" : String.join("+", st.owned()))));
	}

	/** After a match or a range round: tell the player about every emote and glider their new stats just unlocked. */
	public static void announceUnlocks(ServerPlayer p, UnlockRule.Stats before) {
		UnlockRule.Stats st = career(p);
		List<String> got = new ArrayList<>();
		for (EmoteDef e : Sheets.EMOTES)
			if (UnlockRule.has(e.unlock(), e.need(), st) && !UnlockRule.has(e.unlock(), e.need(), before)) got.add(e.name() + " emote");
		for (var g : Sheets.GLIDERS)
			if (UnlockRule.has(g.unlock(), g.need(), st) && !UnlockRule.has(g.unlock(), g.need(), before)) got.add(g.name());
		for (CosmeticDef c : Sheets.COSMETICS)
			if (UnlockRule.has(c.unlock(), c.need(), st) && !UnlockRule.has(c.unlock(), c.need(), before)) got.add(c.name());
		sendUnlocks(p);
		if (!got.isEmpty())
			p.sendSystemMessage(Component.literal("Unlocked: " + String.join(", ", got) + " - equip them in the Locker (K)").withStyle(ChatFormatting.GOLD));
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_unlocks.txt");
	}

	private static Path timesFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_obby_times.txt");
	}

	private static void saveTimes(MinecraftServer server) {
		StringBuilder b = new StringBuilder();
		BEST.forEach((k, v) -> b.append(k).append('=').append(v).append('\n'));
		try {
			Files.writeString(timesFile(server), b.toString());
		} catch (IOException e) {
			MinebloxRoyale.couldntSave("obby times", timesFile(server), e);
		}
	}

	private static void load(MinecraftServer server) {
		loadedFor = server;
		UNLOCKS.clear();
		CHECKPOINT.clear();
		ELSEWHERE.clear();
		RUN.clear();
		BEST.clear();
		try {
			for (String line : Files.readAllLines(timesFile(server))) {
				String[] kv = line.split("=", 2);
				if (kv.length == 2) BEST.put(UUID.fromString(kv[0].trim()), Integer.parseInt(kv[1].trim()));
			}
		} catch (IOException | RuntimeException ignored) { // (no times yet)
		}
		try {
			for (String line : Files.readAllLines(file(server))) {
				String[] kv = line.split("=", 2);
				if (kv.length == 2 && !kv[1].isBlank()) UNLOCKS.put(UUID.fromString(kv[0].trim()), new LinkedHashSet<>(Set.of(kv[1].trim().split(","))));
			}
		} catch (IOException | IllegalArgumentException ignored) {
		}
	}

	private static void save(MinecraftServer server) {
		StringBuilder b = new StringBuilder();
		UNLOCKS.forEach((k, v) -> b.append(k).append('=').append(String.join(",", v)).append('\n'));
		try {
			Files.writeString(file(server), b.toString());
		} catch (IOException e) {
			MinebloxRoyale.couldntSave("unlocks", file(server), e);
		}
	}
}
