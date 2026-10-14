package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.net.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Knocked, not out (creator: yes, crawl and revive; Duos and up). At 0 health a fighter whose team still has someone
 * standing is knocked instead of eliminated: they crawl, can't shoot, build, heal or loot, and bleed out over
 * knockBleedSeconds (faster each further knock in the match). A teammate holding the interact key (F) next to them for
 * reviveSeconds brings them back with reviveHealth. Enemies can finish them. A knocked fighter whose whole team is
 * down (or out) is eliminated: credit goes to whoever knocked them.
 */
public final class Knocked {
	/** Client side: entity ids the server says are knocked (players and bots), for poses and drawing. */
	public static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();
	private static final Identifier CRAWL = MinebloxRoyale.id("knocked_crawl");

	private static final class Down {
		float bleedPerSecond;
		UUID knocker;
		String knockerName;
	}

	/** A revive going on: who is reviving, for how long, and the tick it was last held (released = gone). */
	public record Revive(UUID by, String byName, int ticks, long lastTick) {
	}

	private static final Map<UUID, Down> DOWN = new LinkedHashMap<>();
	private static final Map<UUID, Integer> KNOCKS = new HashMap<>();
	private static final Map<UUID, Revive> REVIVING = new HashMap<>();
	private static boolean changed;

	private Knocked() {
	}

	/** Is this fighter knocked down? Works on both sides (the client asks the list the server sends). */
	public static boolean is(Entity e) {
		if (e == null) return false;
		if (e.level().isClientSide()) return CLIENT.contains(e.getId());
		return DOWN.containsKey(e.getUUID());
	}

	static void clearAll() {
		DOWN.clear();
		KNOCKS.clear();
		REVIVING.clear();
		changed = true;
	}

	/** The knocked list changed since last asked (MatchManager sends it to everyone then). */
	static boolean takeChanged() {
		boolean c = changed;
		changed = false;
		return c;
	}

	/** Entity ids of everyone knocked right now. */
	static int[] ids(MinecraftServer server) {
		List<Integer> out = new ArrayList<>();
		for (UUID id : DOWN.keySet()) {
			Entity e = find(server, id);
			if (e != null) out.add(e.getId());
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	private static Entity find(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p != null ? p : server.overworld().getEntity(id);
	}

	/**
	 * Lethal damage: knocked instead of eliminated if this is a team match and a teammate is still standing. True =
	 * knocked (the hit doesn't kill).
	 */
	static boolean tryKnock(LivingEntity victim, Entity attacker) {
		MatchManager mm = MatchManager.get();
		if (!Teams.teamMode() || mm.state() != MatchManager.State.LIVE || DOWN.containsKey(victim.getUUID())) return false;
		if (victim instanceof ServerPlayer p ? !mm.isAlive(p) : !(victim instanceof BotEntity)) return false;
		if (!mm.hasStandingTeammate(victim)) return false;
		int n = KNOCKS.merge(victim.getUUID(), 1, Integer::sum);
		float seconds = MinebloxRoyale.setting("knockBleedSeconds") / (1F + MinebloxRoyale.setting("knockBleedFaster") * (n - 1));
		String by = attacker == null ? "the storm" : attacker.getName().getString();
		down(victim, MinebloxRoyale.setting("maxHealth") / seconds, attacker == null ? null : attacker.getUUID(), by);
		victim.setHealth(victim.getMaxHealth());
		MinecraftServer server = victim.level().getServer();
		MatchManager.broadcast(server, Component.empty().append(MatchManager.who(victim)).append(" was knocked down by ")
			.append(attacker == null ? Component.literal(by) : MatchManager.who(attacker)).withStyle(ChatFormatting.GOLD));
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			if (p == victim || Teams.sameTeam(p, victim)) Net.sfxTo(p, "knocked", victim.position());
		if (victim instanceof ServerPlayer p)
			MatchManager.title(p, Component.literal("KNOCKED").withStyle(ChatFormatting.RED), Component.literal("Crawl to cover - a teammate can revive you"), 3, 40, 10);
		MinebloxRoyale.LOG.info("BR: {} knocked by {} (knock {}, {} s to bleed out)", victim.getName().getString(), by, n, Math.round(seconds));
		return true;
	}

	/** Puts a fighter in the knocked state (also a simulated bot becoming real while knocked). */
	static void down(LivingEntity e, float bleedPerSecond, UUID knocker, String knockerName) {
		Down d = new Down();
		d.bleedPerSecond = bleedPerSecond;
		d.knocker = knocker;
		d.knockerName = knockerName;
		DOWN.put(e.getUUID(), d);
		Combat.setShield(e, 0);
		Combat.stopRegen(e);
		e.stopUsingItem();
		if (e instanceof BotEntity bot) {
			bot.onKnocked();
			AttributeInstance speed = bot.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null && !speed.hasModifier(CRAWL))
				speed.addTransientModifier(new AttributeModifier(CRAWL, MinebloxRoyale.setting("knockCrawlSpeed") - 1F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			bot.setPose(Pose.SWIMMING);
		} else {
			e.setPose(Pose.SWIMMING); // players: kept so by PlayerPoseMixin while knocked (crawling is slow on its own)
		}
		changed = true;
	}

	/** Out of the knocked state with no effects on health (revived, finished, or gone back to being simulated). */
	static void up(LivingEntity e) {
		if (DOWN.remove(e.getUUID()) == null) return;
		REVIVING.remove(e.getUUID());
		AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(CRAWL);
		e.setPose(Pose.STANDING);
		if (e instanceof BotEntity bot) bot.onRevived();
		changed = true;
	}

	/** Bleed-out health per second, for a knocked fighter (0 if not knocked). */
	public static float bleedPerSecond(Entity e) {
		Down d = DOWN.get(e.getUUID());
		return d == null ? 0 : d.bleedPerSecond;
	}

	public static String knockerName(Entity e) {
		Down d = DOWN.get(e.getUUID());
		return d == null ? null : d.knockerName;
	}

	/** Who gets the elimination if this knocked fighter is finished off by the storm or bleeds out. */
	static Entity knocker(MinecraftServer server, Entity e) {
		Down d = DOWN.get(e.getUUID());
		return d == null || d.knocker == null ? null : find(server, d.knocker);
	}

	/** A revive in progress on this knocked fighter (null = none). */
	public static Revive reviving(UUID knocked) {
		return REVIVING.get(knocked);
	}

	/**
	 * One tick of `by` reviving `target` (a player holding F next to them, or a bot). True once the revive is done.
	 * Switching to someone else, or letting go for a tick, starts again from zero.
	 */
	public static boolean reviveTick(LivingEntity target, LivingEntity by) {
		if (!is(target) || is(by) || !Teams.sameTeam(target, by)) return false;
		long now = target.level().getGameTime();
		Revive r = REVIVING.get(target.getUUID());
		int ticks = r != null && r.by().equals(by.getUUID()) && r.lastTick() >= now - 1 ? r.ticks() + 1 : 1;
		REVIVING.put(target.getUUID(), new Revive(by.getUUID(), by.getName().getString(), ticks, now));
		if (ticks < Math.round(MinebloxRoyale.setting("reviveSeconds") * 20)) return false;
		revive(target, by);
		return true;
	}

	/** Revive progress 0..1 that `by` has on `target` right now (0 if none). */
	public static float progress(LivingEntity target, LivingEntity by) {
		Revive r = REVIVING.get(target.getUUID());
		if (r == null || !r.by().equals(by.getUUID()) || r.lastTick() < target.level().getGameTime() - 1) return 0;
		return r.ticks() / (MinebloxRoyale.setting("reviveSeconds") * 20);
	}

	static void revive(LivingEntity target, LivingEntity by) {
		up(target);
		target.setHealth(MinebloxRoyale.setting("reviveHealth"));
		MinecraftServer server = target.level().getServer();
		MatchManager.broadcast(server, Component.empty().append(MatchManager.who(by)).append(" revived ").append(MatchManager.who(target)).withStyle(ChatFormatting.GREEN));
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			if (p == target || Teams.sameTeam(p, target)) Net.sfxTo(p, "revived", target.position());
		if (target instanceof ServerPlayer p) MatchManager.title(p, Component.literal("REVIVED").withStyle(ChatFormatting.GREEN), Component.literal("by " + by.getName().getString()), 3, 30, 10);
		MinebloxRoyale.LOG.info("BR: {} revived {}", by.getName().getString(), target.getName().getString());
	}

	/** Every tick of a live match: bleeding (once a second), dropped revives, and teams with nobody left standing. */
	static void tick(MinecraftServer server) {
		if (DOWN.isEmpty()) return;
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		REVIVING.values().removeIf(r -> r.lastTick() < now - 1);
		MatchManager mm = MatchManager.get();
		for (UUID id : new ArrayList<>(DOWN.keySet())) {
			Entity e = find(server, id);
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				// Gone (left the game; a bot unloaded is handed over by the roster first).
				if (e == null || !e.isAlive()) DOWN.remove(id);
				changed = true;
				continue;
			}
			Down d = DOWN.get(id);
			if (d == null) continue;
			boolean wiped = !mm.hasStandingTeammate(le);
			// No bleeding while a teammate is reviving them (Fortnite).
			boolean beingRevived = REVIVING.containsKey(id);
			if (!wiped && now % 20 == 0 && !beingRevived) {
				le.setHealth(le.getHealth() - d.bleedPerSecond);
				if (le.getHealth() > 0.01F) continue;
			} else if (!wiped) {
				continue;
			}
			finish(server, le, wiped ? "nobody left standing to revive them" : "bled out");
		}
	}

	/** Eliminated while knocked: bled out, or the rest of the team is down too. Credit goes to the knocker. */
	static void finish(MinecraftServer server, LivingEntity e, String why) {
		Entity credit = knocker(server, e);
		String by = knockerName(e);
		up(e);
		MinebloxRoyale.LOG.info("BR: {} eliminated while knocked ({}), credit {}", e.getName().getString(), why, by);
		if (e instanceof ServerPlayer p) MatchManager.get().eliminate(p, credit, by);
		else if (e instanceof BotEntity bot) bot.finish(credit, by);
	}

	/** Leaving or a new match: forget a fighter's knocked state (no messages). */
	static void forget(UUID id) {
		if (DOWN.remove(id) != null) changed = true;
		REVIVING.remove(id);
	}
}
