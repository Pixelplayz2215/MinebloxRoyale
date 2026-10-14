package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The interact key (F, held): in every mode, next to a closed loot chest it searches it (creator: Fortnite's chests);
 * in team modes it also revives a knocked teammate next to you or reboots teammates at a reboot van with their cards,
 * which come first. The player's own game says when the key goes down and up; the server does the rest each tick, so
 * letting go, walking away or being knocked yourself stops it.
 */
public final class Interact {
	private static final Set<UUID> HOLDING = new HashSet<>();
	private static final Map<UUID, Integer> REBOOTING = new HashMap<>();
	/** A chest search going on: which chest, and for how many ticks so far. */
	private record Search(BlockPos chest, int ticks) {
	}

	private static final Map<UUID, Search> SEARCHING = new HashMap<>();
	/** What each player is doing right now, for their HUD: {label, progress 0..1}. */
	private static final Map<UUID, Object[]> NOW = new HashMap<>();

	private Interact() {
	}

	public static void set(ServerPlayer p, boolean down) {
		if (down) HOLDING.add(p.getUUID());
		else {
			HOLDING.remove(p.getUUID());
			REBOOTING.remove(p.getUUID());
			SEARCHING.remove(p.getUUID());
			NOW.remove(p.getUUID());
		}
	}

	static void clear() {
		HOLDING.clear();
		REBOOTING.clear();
		SEARCHING.clear();
		NOW.clear();
	}

	/**
	 * Entity ids of everyone rebooting at a van (players holding F, bots waiting there) and of players searching a
	 * chest, right now: { rebooting, searching } (everyone's game shows them at it).
	 */
	static int[][] busy(MinecraftServer server) {
		List<Integer> reboot = new ArrayList<>(), search = new ArrayList<>();
		for (UUID id : REBOOTING.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) reboot.add(p.getId());
		}
		for (BotEntity b : server.overworld().getEntities(BotEntity.TYPE, BotEntity::rebooting)) reboot.add(b.getId());
		for (UUID id : SEARCHING.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) search.add(p.getId());
		}
		return new int[][] { reboot.stream().mapToInt(Integer::intValue).toArray(), search.stream().mapToInt(Integer::intValue).toArray() };
	}

	/** The action this player is holding F for: {label, progress}, or null. */
	static Object[] current(UUID player) {
		return NOW.get(player);
	}

	static void tick(MinecraftServer server) {
		NOW.clear();
		if (HOLDING.isEmpty()) return;
		MatchManager mm = MatchManager.get();
		for (UUID id : HOLDING) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || !mm.isAlive(p) || Knocked.is(p)) {
				REBOOTING.remove(id);
				SEARCHING.remove(id);
				continue;
			}
			if (Teams.teamMode() && teamTick(p, id)) {
				SEARCHING.remove(id);
				continue;
			}
			REBOOTING.remove(id);
			searchTick(p, id);
		}
	}

	/** Reviving or rebooting (team modes): true if F is doing one of those this tick. */
	private static boolean teamTick(ServerPlayer p, UUID id) {
		double range = MinebloxRoyale.setting("reviveRange");
		LivingEntity down = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && Knocked.is(e) && Teams.sameTeam(p, e) && e.distanceTo(p) <= range)
			.stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
		if (down != null) {
			REBOOTING.remove(id);
			if (!Knocked.reviveTick(down, p)) NOW.put(id, new Object[] { "Reviving " + down.getName().getString(), Knocked.progress(down, p) });
			return true;
		}
		if (!Reboot.canReboot(p)) return false;
		int ticks = REBOOTING.merge(id, 1, Integer::sum);
		float need = MinebloxRoyale.setting("rebootSeconds") * 20;
		if (ticks >= need) {
			REBOOTING.remove(id);
			Reboot.reboot(p, Reboot.vanAt(p));
		} else {
			NOW.put(id, new Object[] { "Rebooting " + String.join(", ", Reboot.carried(p).stream().map(Reboot.Owner::name).toList()), ticks / need });
		}
		return true;
	}

	/**
	 * Searching the chest in front of you: chestSearchSeconds of holding F opens it. Looking at another chest starts
	 * again. (The player's own game draws the progress: ChestHud.)
	 */
	private static void searchTick(ServerPlayer p, UUID id) {
		BlockPos chest = LootChests.target(p);
		if (chest == null) {
			SEARCHING.remove(id);
			return;
		}
		Search s = SEARCHING.get(id);
		int ticks = s != null && s.chest().equals(chest) ? s.ticks() + 1 : 1;
		if (ticks >= LootChests.searchTicks()) {
			SEARCHING.remove(id);
			LootChests.open(p.level(), chest, p);
		} else {
			SEARCHING.put(id, new Search(chest, ticks));
		}
	}
}
