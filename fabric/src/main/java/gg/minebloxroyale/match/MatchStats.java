package gg.minebloxroyale.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.minebloxroyale.bot.BotEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What everyone did this match, for the results card at the end (2.0.0-test5, creator: the Victory Royale screen,
 * "in the results tab for both Solo and teams, add the total calculated damage"): each player's and bot's eliminations,
 * the damage they dealt (every point taken off an opponent's shield and health, counted as the hit lands), revives,
 * reboots, when they went out and their placing, and each player's gold, reason by reason. Players by their UUID, bots
 * by name (a simulated bot has no entity).
 */
public final class MatchStats {
	public static final class Line {
		final String key, name;
		final boolean bot;
		int team, slot, kills, revives, reboots, place;
		float damage;
		long outAt = -1;
		final Map<String, Integer> gold = new LinkedHashMap<>();

		Line(String key, String name, boolean bot, int team) {
			this.key = key;
			this.name = name;
			this.bot = bot;
			this.team = team;
		}
	}

	private static final Map<String, Line> LINES = new LinkedHashMap<>();
	private static long startedAt;

	private MatchStats() {
	}

	/** The bus has left: everyone in this match, from nothing. */
	static void start(long now) {
		LINES.clear();
		startedAt = now;
	}

	static void player(UUID id, String name, int team, int slot) {
		Line l = LINES.computeIfAbsent(id.toString(), k -> new Line(k, name, false, team));
		l.team = team;
		l.slot = slot;
	}

	static void bot(String name, int team, int slot) {
		Line l = LINES.computeIfAbsent(name, k -> new Line(k, name, true, team));
		l.team = team;
		l.slot = slot;
	}

	private static Line of(Entity e) {
		if (e instanceof ServerPlayer p) return LINES.computeIfAbsent(p.getUUID().toString(), k -> new Line(k, p.getName().getString(), false, Teams.teamOf(p)));
		if (e instanceof BotEntity b) return LINES.computeIfAbsent(b.getName().getString(), k -> new Line(k, k, true, Teams.teamOf(b)));
		return null;
	}

	/** Damage dealt to an opponent (Combat): shield and health points together. */
	public static void damage(LivingEntity by, float points) {
		if (points <= 0 || MatchManager.get().state() != MatchManager.State.LIVE) return;
		Line l = of(by);
		if (l != null) l.damage += points;
	}

	static void kill(Entity killer, String byName) {
		Line l = killer != null ? of(killer) : byName != null ? LINES.get(byName) : null;
		if (l != null) l.kills++;
	}

	static void revive(LivingEntity by) {
		Line l = of(by);
		if (l != null) l.revives++;
	}

	static void reboot(LivingEntity by, int howMany) {
		Line l = of(by);
		if (l != null) l.reboots += howMany;
	}

	/** Out of the match (eliminated): when. */
	static void out(Entity e, long now) {
		Line l = of(e);
		if (l != null && l.outAt < 0) l.outAt = now;
	}

	/** How many teams (Solo: fighters) started this match. */
	static int teams() {
		return (int) LINES.values().stream().mapToInt(l -> l.team).distinct().count();
	}

	/** A whole team out (Solo: one player): its placing, for everyone on it. */
	static void placed(int team, int place) {
		for (Line l : LINES.values()) if (l.team == team && l.place == 0) l.place = place;
	}

	/** Gold a player earned this match, by reason (the same reason twice adds up). */
	static void gold(ServerPlayer p, String why, int amount) {
		if (amount <= 0) return;
		Line l = of(p);
		if (l != null) l.gold.merge(why, amount, Integer::sum);
	}

	/**
	 * The results card for one player (Net.Victory): won, the mode, where they placed, their stats, the gold they earned
	 * line by line, their team's rows (team modes), the winners' names, what they could buy next.
	 */
	static String results(ServerPlayer p, boolean won, boolean teamMode, String mode, int places, long now, List<String> winners, int goldNow, String[] next,
		double[] stage) {
		Line me = of(p);
		JsonObject o = new JsonObject();
		o.addProperty("won", won);
		o.addProperty("team", teamMode);
		o.addProperty("mode", mode);
		int place = won ? 1 : me != null && me.place > 0 ? me.place : places;
		o.addProperty("place", place);
		o.addProperty("of", places);
		long until = me != null && me.outAt >= 0 && !won ? me.outAt : now;
		o.addProperty("seconds", Math.max(0, (until - startedAt) / 20));
		o.add("me", row(me == null ? new Line(p.getUUID().toString(), p.getName().getString(), false, Teams.teamOf(p)) : me));
		JsonArray gold = new JsonArray();
		if (me != null)
			for (var e : me.gold.entrySet()) {
				JsonObject g = new JsonObject();
				g.addProperty("why", e.getKey());
				g.addProperty("gold", e.getValue());
				gold.add(g);
			}
		o.add("gold", gold);
		JsonArray team = new JsonArray();
		if (teamMode && me != null) {
			List<Line> mates = new ArrayList<>();
			for (Line l : LINES.values()) if (l.team == me.team) mates.add(l);
			mates.sort((a, b) -> Float.compare(b.damage, a.damage));
			for (Line l : mates) team.add(row(l));
		}
		o.add("teamRows", team);
		JsonArray w = new JsonArray();
		for (String n : winners) w.add(n);
		o.add("winners", w);
		o.addProperty("goldNow", goldNow);
		o.addProperty("nextItem", next == null ? "" : next[0]);
		o.addProperty("nextPrice", next == null ? 0 : Integer.parseInt(next[1]));
		if (stage != null) {
			o.addProperty("stageX", stage[0]);
			o.addProperty("stageY", stage[1]);
			o.addProperty("stageZ", stage[2]);
		}
		return o.toString();
	}

	private static JsonObject row(Line l) {
		JsonObject r = new JsonObject();
		r.addProperty("name", l.name);
		r.addProperty("bot", l.bot);
		r.addProperty("key", l.key);
		r.addProperty("kills", l.kills);
		r.addProperty("damage", Math.round(l.damage));
		r.addProperty("revives", l.revives);
		r.addProperty("reboots", l.reboots);
		r.addProperty("color", Teams.color(l.slot)); // (their teammate colour: team_colors, by slot)
		return r;
	}
}
