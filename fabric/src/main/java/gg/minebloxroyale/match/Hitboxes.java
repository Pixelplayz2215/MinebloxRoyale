package gg.minebloxroyale.match;

import gg.minebloxroyale.bot.BotEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a player's shot hits (2.0.0, a player's Melty review on 1.0.1.4: "hitbox not so good"). Three things made a shot
 * on what you see miss:
 * - The body you see is the blocky Roblox one, 1.44 blocks shoulder to shoulder with its arms, but Minecraft's hitbox is
 *   a 0.6-block column, so shots on the arms and the edges of the torso went straight through. A player's shot now also
 *   hits the body's own parts (legs, torso with arms, head: avatar_parts, 0.36 blocks a stud), turned the way the body
 *   faces and a little padded; the column still counts too.
 * - Lag: other players and bots show where they were a moment ago (your ping, plus Minecraft smoothing their movement),
 *   and the shot reaches the server later still. Every fighter's last 0.6 s of positions are kept here, and a player's
 *   shot also counts against where the target was on their screen when they fired.
 * - The camera sits over the right shoulder: Guns.shoot runs the hit along the crosshair's own line from the camera, and
 *   the bullet still needs a clear path from the gun.
 * Bots' shots keep the plain column and no rewind, so how often they hit stays as tuned.
 */
public final class Hitboxes {
	/** Ticks of positions kept (0.6 s). */
	private static final int KEEP = 12;

	/** A player's shot's hit: who, where, and whether on the head. */
	public record Hit(LivingEntity entity, Vec3 at, boolean head) {
	}

	private static final Map<Integer, ArrayDeque<HitShapes.Sample>> HISTORY = new HashMap<>();

	private Hitboxes() {
	}

	/** Every server tick: where every player and bot is (the last 0.6 s are kept). */
	public static void record(MinecraftServer server) {
		long now = server.getTickCount();
		Set<Integer> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels())
			for (Entity e : level.getAllEntities()) {
				if (!(e instanceof LivingEntity le) || !(le instanceof Player || le instanceof BotEntity) || !le.isAlive()) continue;
				ArrayDeque<HitShapes.Sample> h = HISTORY.computeIfAbsent(le.getId(), k -> new ArrayDeque<>());
				h.addLast(sample(le, now));
				while (h.size() > KEEP) h.removeFirst();
				seen.add(le.getId());
			}
		HISTORY.keySet().retainAll(seen);
	}

	private static HitShapes.Sample sample(LivingEntity e, long tick) {
		// Upright: standing (or just jumping), not crouching, crawling, in a car, or skydiving and gliding (lying flat).
		boolean upright = e.getPose() == Pose.STANDING && !e.isPassenger() && (e.onGround() || e.fallDistance < 3);
		return new HitShapes.Sample(tick, e.getX(), e.getY(), e.getZ(), e.yBodyRot, upright, e.getBbWidth(), e.getBbHeight(), e.getEyeHeight());
	}

	/**
	 * A player's shot along from-to (the crosshair's line, cut at the first block): the first fighter it hits, where they
	 * are now or where they were on the shooter's screen (their ping, plus Minecraft's smoothing), or null.
	 */
	public static Hit pick(ServerLevel level, ServerPlayer shooter, Vec3 from, Vec3 to, Predicate<Entity> ok) {
		long now = level.getServer().getTickCount();
		int back = Math.round(shooter.connection.latency() / 50F);
		long newest = now - Math.max(0, back - 1), oldest = now - Math.min(KEEP - 1, back + 3);
		Vec3 d = to.subtract(from);
		double best = Double.MAX_VALUE;
		Hit hit = null;
		for (Entity e : level.getEntities(shooter, new AABB(from, to).inflate(4.0), ok)) {
			if (!(e instanceof LivingEntity le)) continue;
			List<HitShapes.Sample> where = new ArrayList<>();
			where.add(sample(le, now));
			ArrayDeque<HitShapes.Sample> h = HISTORY.get(le.getId());
			if (h != null) for (HitShapes.Sample s : h) if (s.tick() >= oldest && s.tick() <= newest) where.add(s);
			for (HitShapes.Sample s : where) {
				double t = HitShapes.rayBody(from.x, from.y, from.z, d.x, d.y, d.z, s);
				if (t < 0 || t >= best) continue;
				best = t;
				Vec3 at = from.add(d.scale(t));
				hit = new Hit(le, at, HitShapes.head(s, at.y - s.y()));
			}
		}
		return hit;
	}

}
