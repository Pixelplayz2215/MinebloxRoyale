package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotBag;
import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.sheets.BotDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Every bot in a 100-fighter match. Bots ride the Battle Bus like players, jump at their own moment, skydive and glide
 * down, then start with nothing and loot. A bot near a real player is a real BotEntity; one far away is simulated like
 * Fortnite's distant players: it falls, lands, loots (rolls chest contents now and then), heals and shields itself with
 * what it found, walks to the safe zone, takes storm damage and fights nearby simulated bots. It becomes real again
 * when a player comes close, carrying the same bag, health and shield.
 *
 * Team modes (Duos, Trios, Squads): bots play in teams of the mode's size. A bot team jumps together and lands
 * together; a bot filling a real player's team jumps when that player does and lands next to them. Simulated teams stay
 * together, never fight each other, get knocked instead of eliminated while a teammate stands, and revive.
 */
public final class BotRoster {
	public enum Stage { BUS, DROP, GROUND }

	/** One bot place in the match: its team, its slot in it, and the real player it follows (null: a bot team). */
	public record Seat(int team, int slot, UUID leader) {
	}

	public static final class Sim {
		final BotDef def;
		final String name;
		String outfit = "";
		double x, y, z, landX, landZ;
		float health, shield, jumpAt;
		Stage stage = Stage.BUS;
		final BotBag bag = new BotBag();
		boolean alive = true;
		BotEntity entity;
		int team = -1, slot;
		UUID leader;
		/** Simulated knocked state (a real bot's is in Knocked). */
		boolean knocked;
		float bleedPerSecond;
		String knockerName;
		int knocks;
		/** Eliminations this match (shown to a real player's team). */
		int kills;
		/** Reboot cards it carried when it went back to being simulated. */
		final List<Reboot.Owner> cards = new ArrayList<>();

		Sim(BotDef def, String name, float health) {
			this.def = def;
			this.name = name;
			this.health = health;
		}
	}

	private final List<Sim> sims = new ArrayList<>();
	private final Map<BotEntity, Sim> byEntity = new HashMap<>();
	private final Map<Integer, double[]> teamLanding = new HashMap<>();
	private double fallbackX, fallbackZ;
	private float islandR;
	/**
	 * Game time of the last bot elimination (or the match start): too long without one and the endgame forces a fight.
	 */
	private long lastElim;

	public void clear() {
		for (Sim s : sims) if (s.entity != null) s.entity.discard();
		sims.clear();
		byEntity.clear();
		teamLanding.clear();
	}

	/** Everyone boards the bus empty-handed; each bot (or bot team) picks its own moment to jump. */
	public void start(List<Seat> seats, double cx, double cz, float radius, RandomSource rnd) {
		clear();
		lastElim = 0;
		fallbackX = cx;
		fallbackZ = cz;
		islandR = radius;
		Map<String, Integer> used = new HashMap<>();
		Map<Integer, Float> teamJump = new HashMap<>();
		for (int i = 0; i < seats.size(); i++) {
			Seat seat = seats.get(i);
			BotDef def = Sheets.BOTS.get(i % Sheets.BOTS.size());
			int n = used.merge(def.name(), 1, Integer::sum);
			Sim s = new Sim(def, n == 1 ? def.name() : def.name() + n, MinebloxRoyale.setting("maxHealth"));
			s.outfit = BotEntity.randomOutfit(rnd);
			s.team = seat.team();
			s.slot = seat.slot();
			s.leader = seat.leader();
			// Like real lobbies: a crowd jumps early, the rest spread out along the route. A team jumps together; a bot
			// with a real teammate jumps when they do (jumpAt 2 = never on its own, until the route ends).
			float own = rnd.nextFloat() < 0.35F ? 0.08F + rnd.nextFloat() * 0.2F : 0.1F + rnd.nextFloat() * 0.85F;
			s.jumpAt = s.leader != null ? 2F : Teams.teamMode() ? teamJump.computeIfAbsent(s.team, k -> own) : own;
			s.x = cx;
			s.z = cz;
			sims.add(s);
		}
	}

	public int alive() {
		int n = 0;
		for (Sim s : sims) if (s.alive) n++;
		return n;
	}

	public int realCount() {
		int n = 0;
		for (Sim s : sims) if (s.entity != null) n++;
		return n;
	}

	/** Teams with at least one bot still in (standing or knocked). */
	Set<Integer> aliveTeams() {
		Set<Integer> out = new HashSet<>();
		for (Sim s : sims) if (s.alive) out.add(s.team);
		return out;
	}

	/** Bots of a team (alive or not), in slot order: the HUD's teammate list. */
	List<Sim> team(int team) {
		List<Sim> out = new ArrayList<>();
		for (Sim s : sims) if (s.team == team) out.add(s);
		out.sort((a, b) -> Integer.compare(a.slot, b.slot));
		return out;
	}

	/** Names of a team's bots (a player's team message at the bus). */
	List<String> names(int team) {
		return team(team).stream().map(s -> s.name).toList();
	}

	static boolean knocked(Sim s) {
		return s.entity != null ? Knocked.is(s.entity) : s.knocked;
	}

	/** Is a bot of this team still standing (alive and not knocked), not counting `except`? */
	boolean standing(int team, Entity except) {
		for (Sim s : sims)
			if (s.alive && s.team == team && (s.entity == null || s.entity != except) && !knocked(s)) return true;
		return false;
	}

	/** An eliminated bot that could still come back (its card is out there). */
	boolean isOut(String name) {
		for (Sim s : sims) if (s.name.equals(name)) return !s.alive;
		return false;
	}

	/** A reboot van brought this bot back: it drops from above the van with nothing but the pickaxe. */
	boolean reboot(String name, double x, double y, double z, RandomSource rnd) {
		for (Sim s : sims) {
			if (!s.name.equals(name) || s.alive) continue;
			s.alive = true;
			s.entity = null;
			s.knocked = false;
			s.health = MinebloxRoyale.setting("maxHealth");
			s.shield = 0;
			s.bag.clear();
			s.cards.clear();
			s.stage = Stage.DROP;
			s.x = x;
			s.y = y;
			s.z = z;
			s.landX = x + rnd.nextInt(9) - 4;
			s.landZ = z + rnd.nextInt(9) - 4;
			return true;
		}
		return false;
	}

	/**
	 * Every tick, before anything else looks at bots (Knocked first of all): a bot entity Minecraft removed without it
	 * dying (its chunk unloaded when the players near it left) goes back to being simulated from where it was, keeping
	 * its health, bag, cards and knocked state; one that died without the roster hearing of it is out. So no bot is
	 * ever left counted as alive with nothing playing it, in any mode (creator: bots must last to the final moments;
	 * the old ghosts made "19 left" with no bots anywhere).
	 */
	public void reclaim() {
		for (Sim s : sims) {
			if (!s.alive || s.entity == null || !s.entity.isRemoved()) continue;
			if (s.entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
				byEntity.remove(s.entity);
				s.entity = null;
				s.alive = false;
				s.knocked = false;
			} else {
				handOver(s);
				MinebloxRoyale.LOG.info("BR: bot {} handed back to the simulation ({})", s.name, s.knocked ? "knocked" : "standing");
			}
		}
	}

	/** For the log: alive bots, how many are real, how many knocked, and the teams still in. */
	public String census() {
		int alive = 0, real = 0, knocked = 0, bus = 0, drop = 0;
		for (Sim s : sims) {
			if (!s.alive) continue;
			alive++;
			if (knocked(s)) knocked++;
			if (s.entity != null) real++;
			else if (s.stage == Stage.BUS) bus++;
			else if (s.stage == Stage.DROP) drop++;
		}
		return alive + " alive (" + real + " real, " + (alive - real) + " simulated: " + bus + " in the bus, " + drop + " dropping; " + knocked + " knocked), " + aliveTeams().size()
			+ " bot teams";
	}

	/** A real bot died (shot, storm...): mark its place in the roster. */
	public void onEntityDied(BotEntity e) {
		Sim s = byEntity.remove(e);
		if (s != null) {
			s.alive = false;
			s.entity = null;
			lastElim = e.level().getGameTime();
		}
	}

	/**
	 * Where a jumping bot aims to land: a chest spot near where it left the bus, if there is one. Teams land together.
	 */
	private void pickLanding(Sim s, RandomSource rnd) {
		double[] shared = Teams.teamMode() ? teamLanding.get(s.team) : null;
		if (shared != null) {
			s.landX = shared[0] + rnd.nextInt(7) - 3;
			s.landZ = shared[1] + rnd.nextInt(7) - 3;
			return;
		}
		MatchManager mm = MatchManager.get();
		List<BlockPos> spots = new ArrayList<>(mm.placedChests());
		spots.addAll(mm.chestSpots());
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < 12 && !spots.isEmpty(); i++) {
			BlockPos c = spots.get(rnd.nextInt(spots.size()));
			double d = Math.hypot(c.getX() - s.x, c.getZ() - s.z);
			if (d < bestD && d < 260) {
				best = c;
				bestD = d;
			}
		}
		if (best != null) {
			s.landX = best.getX() + rnd.nextInt(9) - 4;
			s.landZ = best.getZ() + rnd.nextInt(9) - 4;
		} else {
			double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * islandR * 0.85;
			s.landX = fallbackX + Math.cos(a) * d;
			s.landZ = fallbackZ + Math.sin(a) * d;
		}
		if (Teams.teamMode()) teamLanding.put(s.team, new double[] { s.landX, s.landZ });
	}

	private static double ground(ServerLevel level, double x, double z) {
		int bx = Mth.floor(x), bz = Mth.floor(z);
		return level.hasChunk(bx >> 4, bz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : 70;
	}

	public void tick(MinecraftServer server, List<ServerPlayer> players, Vec3 stormCenter, float stormRadius, float dps, Vec3 safe, float safeRadius, boolean stormHurts) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		RandomSource rnd = level.getRandom();
		MatchManager mm = MatchManager.get();
		// The bus by the match's clock (its entity may be frozen or unloaded far from everyone).
		boolean busFlying = mm.busFlying(now), doorsOpen = mm.busDoorsOpen(now);
		Vec3 busAt = busFlying ? mm.busPos(now) : null;
		float busProgress = mm.busProgress(now);
		// Riding and dropping move every tick (smooth if a player can see them); the rest once a second.
		for (Sim s : sims) {
			if (!s.alive || s.entity != null) continue;
			ServerPlayer lead = s.leader == null ? null : server.getPlayerList().getPlayer(s.leader);
			if (s.stage == Stage.BUS) {
				if (busAt != null) {
					s.x = busAt.x;
					s.y = busAt.y;
					s.z = busAt.z;
				}
				// With a real teammate: out of the bus right after them (or when they've left the match).
				boolean leaderOut = lead == null || !mm.isAlive(lead) || mm.leftBus(s.leader);
				if (doorsOpen && (!busFlying || (s.leader != null ? leaderOut : busProgress >= s.jumpAt))) {
					s.stage = Stage.DROP;
					pickLanding(s, rnd);
				}
			} else if (s.stage == Stage.DROP) {
				if (lead != null && mm.isAlive(lead) && mm.leftBus(s.leader)) {
					// Follow the real teammate down: land a few blocks from where they are.
					s.landX = lead.getX() + (s.slot % 2 == 0 ? 3 : -3);
					s.landZ = lead.getZ() + (s.slot > 1 ? 3 : -3);
				}
				double g = ground(level, s.x, s.z), above = s.y - g;
				boolean glide = above < MinebloxRoyale.setting("gliderHeight");
				double steer = MinebloxRoyale.setting(glide ? "gliderSteer" : "skydiveSteer"), fall = MinebloxRoyale.setting(glide ? "gliderFall" : "skydiveFall");
				double dx = s.landX - s.x, dz = s.landZ - s.z, d = Math.hypot(dx, dz);
				if (d > 1) {
					s.x += dx / d * Math.min(d, steer);
					s.z += dz / d * Math.min(d, steer);
				}
				s.y -= fall;
				if (s.y <= g) {
					s.y = g;
					s.stage = Stage.GROUND;
				}
			}
		}
		if (now % 20 == 0) secondTick(server, level, players, stormCenter, stormRadius, dps, safe, safeRadius, stormHurts, rnd, now);
		// Become real near players (also mid-air, so players see bots dropping and gliding next to them).
		float realize = MinebloxRoyale.setting("botRealizeRange");
		int maxReal = Math.round(MinebloxRoyale.setting("botMaxReal"));
		int real = realCount(), spawned = 0;
		for (Sim s : sims) {
			// At most 2 new bot entities per tick, so a crowd jumping out of the bus together doesn't stall the game.
			if (spawned >= 2) break;
			if (!s.alive || s.entity != null || s.stage == Stage.BUS || real >= maxReal) continue;
			if (nearest(players, s.x, s.y, s.z) > realize) continue;
			int bx = Mth.floor(s.x), bz = Mth.floor(s.z);
			if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
			double y = s.stage == Stage.DROP ? s.y : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			BotEntity e = BotEntity.spawn(level, s.def, s.name, new Vec3(s.x, y, s.z), s.bag, s.stage == Stage.DROP ? new Vec3(s.landX, 0, s.landZ) : null);
			if (e == null) continue;
			e.setOutfit(s.outfit);
			e.setTeam(s.team, s.slot, s.leader);
			e.setHealth(Math.max(1, s.health));
			Combat.setShield(e, s.shield);
			e.cards.addAll(s.cards);
			s.cards.clear();
			if (s.knocked) {
				Knocked.down(e, s.bleedPerSecond, null, s.knockerName);
				e.setHealth(Math.max(1, s.health));
				s.knocked = false;
			}
			s.entity = e;
			byEntity.put(e, s);
			real++;
			spawned++;
		}
	}

	private void secondTick(MinecraftServer server, ServerLevel level, List<ServerPlayer> players, Vec3 stormCenter, float stormRadius, float dps, Vec3 safe,
		float safeRadius, boolean stormHurts, RandomSource rnd, long now) {
		float virtualize = MinebloxRoyale.setting("botVirtualizeRange");
		float speed = MinebloxRoyale.setting("botSimSpeed");
		boolean teams = Teams.teamMode();
		if (lastElim == 0) lastElim = now;
		// The endgame (a small circle, or only a few teams left): no more chests to loot, and bots hunt each other down
		// instead of wandering (the last two teams used to sit apart healing forever: "nobody is getting eliminated").
		boolean endgame = safeRadius <= MinebloxRoyale.setting("botEndgameRadius") || aliveTeams().size() + (MatchManager.get().anyPlayerIn() ? 1 : 0) <= 3;
		List<Sim> virtual = new ArrayList<>();
		// Where each team's standing simulated bots are (they keep together).
		Map<Integer, double[]> centre = new HashMap<>();
		if (teams)
			for (Sim s : sims)
				if (s.alive && s.entity == null && s.stage == Stage.GROUND && !s.knocked) {
					double[] c = centre.computeIfAbsent(s.team, k -> new double[3]);
					c[0] += s.x;
					c[1] += s.z;
					c[2]++;
				}
		for (Sim s : sims) {
			if (!s.alive) continue;
			// (A bot entity removed without dying was already handed back to the simulation this tick: reclaim.)
			if (s.entity != null) {
				if (!s.entity.isAlive()) continue;
				s.x = s.entity.getX();
				s.y = s.entity.getY();
				s.z = s.entity.getZ();
				s.health = s.entity.getHealth();
				s.shield = Combat.shield(s.entity);
				s.stage = s.entity.dropping() ? Stage.DROP : Stage.GROUND;
				if (nearest(players, s.x, s.y, s.z) > virtualize) {
					BotEntity e = s.entity;
					handOver(s);
					e.discard();
				}
				continue;
			}
			if (s.stage != Stage.GROUND) continue;
			if (s.knocked) {
				// Simulated knocked bot: bleeds; a teammate close by may revive it; with nobody standing it's out. A
				// real player still standing on its team counts (it used to be eliminated whenever no other bot stood).
				// Nobody revives under fire: with an enemy close by, it's finished off instead (Fortnite).
				s.health -= s.bleedPerSecond;
				if (s.health <= 0 || !hasOtherStanding(s)) {
					eliminate(server, s, s.knockerName);
					continue;
				}
				Sim enemy = nearestEnemy(s, MinebloxRoyale.setting("botSimFightRange"));
				if (enemy != null && MatchManager.get().botsMayShoot(now) && rnd.nextFloat() < 0.5F) {
					eliminate(server, s, enemy.name);
					continue;
				}
				if (enemy == null && nearTeammate(s, 30) && rnd.nextFloat() < 0.15F) {
					s.knocked = false;
					s.health = MinebloxRoyale.setting("reviveHealth");
				}
				virtual.add(s);
				continue;
			}
			// Simulated looting: about one chest every 15 s until the bag is decent and has a gun (a bag that filled up
			// with heals first left the bot unarmed for good).
			if (!endgame && (s.bag.size() < 6 || s.bag.bestGun() == null) && rnd.nextFloat() < 0.07F) s.bag.addAll(Loot.chestContents(rnd));
			// Simulated farming (creator: bots harvest wood, brick and metal): a little now and then, as a real bot would, so
			// it has materials to build with when it becomes real.
			if (s.bag.totalMaterials() < 150 && rnd.nextFloat() < 0.08F) s.bag.addMaterial(Sheets.MATERIALS.get(rnd.nextInt(Sheets.MATERIALS.size())).id(), 10 + rnd.nextInt(21));
			// Simulated healing / shielding with what it carries (one item per second at most).
			s.bag.selfCare(s, rnd);
			// Walk towards the safe zone, wander inside it; a team member far from its team closes up first.
			double[] c = teams ? centre.get(s.team) : null;
			double tx = safe.x, tz = safe.z;
			boolean regroup = false;
			Sim prey = endgame ? nearestEnemy(s, Double.MAX_VALUE) : null;
			if (prey != null && Math.hypot(prey.x - safe.x, prey.z - safe.z) <= safeRadius + 30) {
				// Hunting: head for the closest enemy (as long as it isn't deep in the storm).
				tx = prey.x;
				tz = prey.z;
				regroup = true;
			} else if (c != null && c[2] > 1) {
				double mx = c[0] / c[2], mz = c[1] / c[2];
				if ((mx - s.x) * (mx - s.x) + (mz - s.z) * (mz - s.z) > 20 * 20) {
					tx = mx;
					tz = mz;
					regroup = true;
				}
			}
			double dx = tx - s.x, dz = tz - s.z, dist = Math.sqrt(dx * dx + dz * dz);
			if ((regroup || dist > safeRadius * 0.7) && dist > 1) {
				s.x += dx / dist * speed;
				s.z += dz / dist * speed;
			} else {
				s.x += (rnd.nextDouble() - 0.5) * speed;
				s.z += (rnd.nextDouble() - 0.5) * speed;
			}
			double sx = s.x - stormCenter.x, sz = s.z - stormCenter.z;
			if (stormHurts && sx * sx + sz * sz > (double) stormRadius * stormRadius) {
				// The shield takes it first, then health (creator).
				float toShield = Math.min(s.shield, dps);
				s.shield -= toShield;
				s.health -= dps - toShield;
				if (s.health <= 0) {
					down(server, s, null, rnd);
					if (!s.alive) continue;
				}
			}
			virtual.add(s);
		}
		// Simulated fights between nearby simulated bots of different teams: an armed bot beats an unarmed one; damage
		// hits shield first. Two unarmed bots fight with pickaxes, half as often: they used to never fight, so a late
		// game of unarmed bots stood still for minutes (creator: "10 people remain, yet I see nobody", no eliminations
		// for 4.5 min). A knocked bot that an enemy finds is finished off.
		float range = MinebloxRoyale.setting("botSimFightRange"), chance = MinebloxRoyale.setting("botSimFightChance") * (endgame ? 4 : 1);
		// Too long without an elimination (two teams that keep missing each other): the closest enemies fight it out.
		if (MatchManager.get().botsMayShoot(now) && now - lastElim > MinebloxRoyale.setting("botStallSeconds") * 20) forceFight(server, virtual, rnd, now);
		if (MatchManager.get().botsMayShoot(now))
			for (int i = 0; i < virtual.size(); i++) {
				Sim a = virtual.get(i);
				if (!a.alive) continue;
				for (int j = i + 1; j < virtual.size(); j++) {
					Sim b = virtual.get(j);
					if (!b.alive || teams && a.team == b.team || a.knocked && b.knocked) continue;
					double ddx = a.x - b.x, ddz = a.z - b.z;
					if (ddx * ddx + ddz * ddz > range * range || rnd.nextFloat() > chance) continue;
					if (a.knocked || b.knocked) {
						Sim down = a.knocked ? a : b;
						eliminate(server, down, (down == a ? b : a).name);
						if (a.knocked) break;
						continue;
					}
					boolean ga = a.bag.bestGun() != null, gb = b.bag.bestGun() != null;
					if (!ga && !gb && rnd.nextBoolean()) continue;
					Sim loser = ga && !gb ? b : gb && !ga ? a : (rnd.nextBoolean() ? a : b), winner = loser == a ? b : a;
					float hit = rnd.nextInt(80);
					float toShield = Math.min(winner.shield, hit);
					winner.shield -= toShield;
					winner.health = Math.max(5, winner.health - (hit - toShield));
					down(server, loser, winner.name, rnd);
					break;
				}
			}
	}

	/** The closest standing simulated enemy (another team, on the ground) within `within`, or null. */
	private Sim nearestEnemy(Sim s, double within) {
		Sim best = null;
		double bestD = within * within;
		boolean teams = Teams.teamMode();
		for (Sim o : sims) {
			if (o == s || !o.alive || o.entity != null || o.stage != Stage.GROUND || o.knocked || teams && o.team == s.team) continue;
			double dx = o.x - s.x, dz = o.z - s.z, dd = dx * dx + dz * dz;
			if (dd < bestD) {
				bestD = dd;
				best = o;
			}
		}
		return best;
	}

	/**
	 * The stall breaker: the two closest simulated enemies fight, and the loser is out (finished off if it was
	 * knocked).
	 */
	private void forceFight(MinecraftServer server, List<Sim> virtual, RandomSource rnd, long now) {
		Sim a = null, b = null;
		double bestD = Double.MAX_VALUE;
		boolean teams = Teams.teamMode();
		for (int i = 0; i < virtual.size(); i++)
			for (int j = i + 1; j < virtual.size(); j++) {
				Sim x = virtual.get(i), y = virtual.get(j);
				if (!x.alive || !y.alive || teams && x.team == y.team) continue;
				double dd = (x.x - y.x) * (x.x - y.x) + (x.z - y.z) * (x.z - y.z);
				if (dd < bestD) {
					bestD = dd;
					a = x;
					b = y;
				}
			}
		if (a == null) return;
		Sim loser = a.knocked ? a : b.knocked ? b : rnd.nextBoolean() ? a : b, winner = loser == a ? b : a;
		MinebloxRoyale.LOG.info("BR: no elimination for {} s: {} and {} ({} blocks apart) fight it out", (now - lastElim) / 20, a.name, b.name, Math.round(Math.sqrt(bestD)));
		eliminate(server, loser, winner.name);
	}

	/** A simulated bot at 0 health: knocked if a teammate still stands (team modes), otherwise eliminated. */
	private void down(MinecraftServer server, Sim s, String by, RandomSource rnd) {
		if (Teams.teamMode() && hasOtherStanding(s)) {
			s.knocks++;
			float seconds = MinebloxRoyale.setting("knockBleedSeconds") / (1F + MinebloxRoyale.setting("knockBleedFaster") * (s.knocks - 1));
			s.knocked = true;
			s.shield = 0;
			s.health = MinebloxRoyale.setting("maxHealth");
			s.bleedPerSecond = s.health / seconds;
			s.knockerName = by == null ? "the storm" : by;
			return;
		}
		eliminate(server, s, by);
	}

	private boolean hasOtherStanding(Sim s) {
		for (Sim o : sims) if (o != s && o.alive && o.team == s.team && !knocked(o)) return true;
		// A real player teammate standing counts too.
		return MatchManager.get().playerStanding(s.team);
	}

	private boolean nearTeammate(Sim s, double within) {
		for (Sim o : sims) {
			if (o == s || !o.alive || o.team != s.team || knocked(o)) continue;
			double dx = o.x - s.x, dz = o.z - s.z;
			if (dx * dx + dz * dz < within * within) return true;
		}
		return false;
	}

	/** A real bot goes back to being simulated: everything it carries and its knocked state come along. */
	private void handOver(Sim s) {
		BotEntity e = s.entity;
		byEntity.remove(e);
		s.entity = null;
		s.health = e.getHealth();
		s.shield = Combat.shield(e);
		s.cards.addAll(e.cards);
		e.cards.clear();
		if (Knocked.is(e)) {
			s.knocked = true;
			s.bleedPerSecond = Knocked.bleedPerSecond(e);
			s.knockerName = Knocked.knockerName(e);
			Knocked.forget(e.getUUID());
		}
	}

	private void eliminate(MinecraftServer server, Sim s, String by) {
		s.alive = false;
		s.knocked = false;
		if (by != null) creditKill(by);
		lastElim = server.overworld().getGameTime();
		Component msg = Component.literal(s.name + (by == null ? " was eliminated by the storm" : " was eliminated by " + by)).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
	}

	/** An elimination for the bot of this name (if it is one). */
	void creditKill(String name) {
		for (Sim s : sims)
			if (s.name.equals(name)) {
				s.kills++;
				return;
			}
	}

	/** Closest real player, counting height too (so a bot falling past a player's bus isn't realised miles above). */
	private static double nearest(List<ServerPlayer> players, double x, double y, double z) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			double dx = p.getX() - x, dy = (p.getY() - y) * 0.5, dz = p.getZ() - z;
			best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		return best;
	}

	// Simulated self-care needs a sim's numbers.
	public static float health(Sim s) {
		return s.health;
	}

	public static void heal(Sim s, float hp, float shield) {
		s.health = Math.min(MinebloxRoyale.setting("maxHealth"), s.health + hp);
		s.shield = Math.min(MinebloxRoyale.setting("maxShield"), s.shield + shield);
	}

	public static float shield(Sim s) {
		return s.shield;
	}
}
