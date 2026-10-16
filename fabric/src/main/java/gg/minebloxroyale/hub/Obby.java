package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.net.RoomCode;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.GrantDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
	private static final Map<UUID, Set<String>> UNLOCKS = new HashMap<>();
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
			for (HubBuilder.Platform pl : HubBuilder.platforms()) {
				if (!pl.contains(p.getX(), p.getY(), p.getZ())) continue;
				int idx = pl.stage().index();
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

	/** The gold platform: unlock the obby emote (once), then back to the middle of the hub with checkpoints reset. */
	private static void finish(MinecraftServer server, ServerPlayer p) {
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
		Vec3 at = HubBuilder.spawnPos();
		p.fallDistance = 0;
		p.setDeltaMovement(Vec3.ZERO);
		p.teleportTo(server.overworld(), at.x, at.y, at.z, Set.of(), 180F, 0F, true);
		Net.sfxTo(p, "ui_click", at);
	}

	public static void respawnAtCheckpoint(ServerPlayer p) {
		int cp = CHECKPOINT.getOrDefault(p.getUUID(), 0);
		Vec3 at = cp == 0 ? HubBuilder.spawnPos() : HubBuilder.platforms().stream().filter(pl -> pl.stage().index() == cp).findFirst().map(HubBuilder.Platform::top).orElse(HubBuilder.spawnPos());
		p.fallDistance = 0;
		p.setDeltaMovement(Vec3.ZERO);
		p.teleportTo(p.level().getServer().overworld(), at.x, at.y, at.z, Set.of(), p.getYRot(), 0F, true);
		Net.sfxAll(p.level().getServer(), "getup", at);
	}

	public static void resetProgress(ServerPlayer p) {
		CHECKPOINT.remove(p.getUUID());
	}

	/**
	 * Emotes unlock by the obby, wins, kills, a range score or bullseyes, over all the player's worlds (emotes sheet).
	 */
	public static boolean hasEmote(ServerPlayer p, EmoteDef e) {
		return tester(p) || UnlockRule.has(e.unlock(), e.need(), career(p));
	}

	public static boolean hasGlider(ServerPlayer p, GliderDef g) {
		return tester(p) || UnlockRule.has(g.unlock(), g.need(), career(p));
	}

	/** Locker items: free, apart from the Dominus sets (cosmetics sheet unlock / need). */
	public static boolean hasCosmetic(ServerPlayer p, CosmeticDef c) {
		return tester(p) || UnlockRule.has(c.unlock(), c.need(), career(p));
	}

	/**
	 * The player's unlock stats in this world: kills and wins (leaderboard), the obby, and their shooting-range record.
	 */
	private static UnlockRule.Stats here(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		int[] board = Leaderboard.stats(server, p.getUUID()), range = RangeStats.get(server, p.getUUID());
		return new UnlockRule.Stats(board[0], board[1], obbyDoneHere(p), range[0], range[1]);
	}

	/** Lifetime unlock stats: this world's plus the player's other worlds. */
	public static UnlockRule.Stats career(ServerPlayer p) {
		return here(p).plus(ELSEWHERE.getOrDefault(p.getUUID(), UnlockRule.Stats.NONE));
	}

	/**
	 * The player's own game reports their stats from every world they've played in
	 * ("worldId=kills,wins,obby,rangeBest,bullseyes;...", the last two missing from older builds); the other worlds'
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
				t = t.plus(new UnlockRule.Stats(num(n, 0), num(n, 1), num(n, 2) == 1, num(n, 3), num(n, 4)));
			} catch (RuntimeException ignored) {
			}
		}
		ELSEWHERE.put(p.getUUID(), t);
		MinebloxRoyale.LOG.info("BR: {} brings unlock stats from other worlds: {} kills, {} wins, obby {}, range best {}, {} bullseyes", p.getName().getString(), t.kills(),
			t.wins(), t.obby() ? "done" : "not done", t.rangeBest(), t.bullseyes());
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
	 * The client's copy of this world's unlock stats: "kills;wins;obby(0/1);all(0/1);worldId;rangeBest;bullseyes". Its
	 * game remembers them under the world id and adds the player's other worlds (the Locker shows what's still locked).
	 */
	public static void sendUnlocks(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		UnlockRule.Stats st = here(p);
		ServerPlayNetworking.send(p, new Net.Unlocks(st.kills() + ";" + st.wins() + ";" + (st.obby() ? 1 : 0) + ";" + (tester(p) ? 1 : 0) + ";" + worldId(server) + ";"
			+ st.rangeBest() + ";" + st.bullseyes()));
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

	private static void load(MinecraftServer server) {
		loadedFor = server;
		UNLOCKS.clear();
		CHECKPOINT.clear();
		ELSEWHERE.clear();
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
