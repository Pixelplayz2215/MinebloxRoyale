package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Bullets you can see (v2.0.0, creator: "the bullets when they shoot should be better"): every shot (Net.Shot) is a
 * bright streak flying from the gun's muzzle to where it hit, in its gun's tracer colour and length (weapons sheet),
 * with a muzzle flash, sparks and dust where it lands (a puff of red on a hit) and, for the sniper, a thin trail of
 * smoke hanging in the air behind it. Every shot also kicks: its shooter's recoil animation, and your view when it's
 * yours (Aim).
 */
public final class Tracers {
	/** Bullets fly this fast (blocks a second): quick, but you see them go. */
	private static final float SPEED = 280, SNIPER_SPEED = 420;
	/** The muzzle flash lasts this long (seconds); the sniper's smoke trail this long. */
	private static final float FLASH = 0.06F, SMOKE = 0.9F;
	private static final int FULL_BRIGHT = 0xF000F0;

	private record Bullet(Vec3 from, Vec3 to, double born, float speed, float length, int color, boolean hit, boolean smoke) {
	}

	private record Flash(Vec3 at, double born, float size, float spin) {
	}

	private static final List<Bullet> BULLETS = new ArrayList<>();
	private static final List<Flash> FLASHES = new ArrayList<>();
	/** Each avatar's gun hand lately (world x, y, z, seconds): where its bullets leave from. */
	private static final Map<Integer, double[]> HANDS = new HashMap<>();
	/** Each shooter's last shot: {seconds, the gun's kick}. */
	private static final Map<Integer, double[]> KICKS = new HashMap<>();
	/** Bullets whose impact has been shown. */
	private static final Set<Bullet> LANDED = Collections.newSetFromMap(new IdentityHashMap<>());

	private Tracers() {
	}

	private static double now() {
		return System.nanoTime() / 1e9;
	}

	/** Where an avatar's gun hand was drawn this frame (world): RobloxAvatarRenderer. */
	public static void hand(int entityId, double x, double y, double z) {
		HANDS.put(entityId, new double[] { x, y, z, now() });
	}

	/** How long ago this entity last fired (seconds; large when never) and that gun's kick: the avatar's recoil. */
	public static float @Nullable [] lastShot(int entityId) {
		double[] k = KICKS.get(entityId);
		return k == null ? null : new float[] { (float) (now() - k[0]), (float) k[1] };
	}

	public static void receive(Net.Shot p) {
		Minecraft mc = Minecraft.getInstance();
		WeaponDef w = Sheets.WEAPONS_BY_ID.get(p.weapon());
		if (w == null || mc.level == null || p.ends().length < 3) return;
		double time = now();
		KICKS.put(p.shooter(), new double[] { time, w.kick() });
		if (mc.player != null && p.shooter() == mc.player.getId()) Aim.kick(w.recoilDeg(), w.automatic());
		Vec3 eye = new Vec3(p.x(), p.y(), p.z());
		Vec3 first = new Vec3(p.ends()[0], p.ends()[1], p.ends()[2]);
		Vec3 muzzle = muzzle(mc, p.shooter(), eye, first, time);
		boolean sniper = w.scope();
		float speed = sniper ? SNIPER_SPEED : SPEED;
		// (a rocket is its own flying thing, RocketEntity: just the kick and the flash here)
		for (int i = 0; i + 2 < p.ends().length && !w.projectile().equals("rocket"); i += 3) {
			Vec3 to = new Vec3(p.ends()[i], p.ends()[i + 1], p.ends()[i + 2]);
			boolean hit = i / 3 < 32 && (p.hits() >> (i / 3) & 1) != 0;
			BULLETS.add(new Bullet(muzzle, to, time, speed, w.tracerLength(), w.tracer() & 0xFFFFFF, hit, sniper));
		}
		float size = w.pellets() > 1 || w.projectile().equals("rocket") ? 0.55F : sniper ? 0.5F : w.automatic() ? 0.3F : 0.36F;
		FLASHES.add(new Flash(muzzle, time, size, mc.level.getRandom().nextFloat() * 90));
		if (BULLETS.size() > 400) BULLETS.subList(0, BULLETS.size() - 400).clear();
	}

	/**
	 * Where a shot leaves from: the end of the gun in the shooter's hand as just drawn (the hand, then half a block on
	 * towards where it hit), or near the eye when its avatar wasn't drawn (your own scope view; off screen).
	 */
	private static Vec3 muzzle(Minecraft mc, int shooter, Vec3 eye, Vec3 at, double time) {
		boolean mine = mc.player != null && shooter == mc.player.getId();
		double[] h = HANDS.get(shooter);
		if (h != null && time - h[3] < 0.25 && !(mine && Aim.scoped())) {
			Vec3 hand = new Vec3(h[0], h[1], h[2]);
			Vec3 d = at.subtract(hand);
			return d.lengthSqr() < 1e-4 ? hand : hand.add(d.normalize().scale(0.55));
		}
		Vec3 d = at.subtract(eye);
		Vec3 dir = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
		return eye.add(dir.scale(0.5)).add(0, -0.18, 0);
	}

	/** Each client tick: impacts land when their bullet gets there; old hands and kicks are forgotten. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			BULLETS.clear();
			FLASHES.clear();
			LANDED.clear();
			return;
		}
		double time = now();
		for (Bullet b : BULLETS)
			if (time >= b.born() + b.from().distanceTo(b.to()) / b.speed() && !LANDED.contains(b)) land(mc, b);
		HANDS.values().removeIf(h -> time - h[3] > 5);
		KICKS.values().removeIf(k -> time - k[0] > 10);
	}

	private static void land(Minecraft mc, Bullet b) {
		LANDED.add(b);
		var r = mc.level.getRandom();
		Vec3 p = b.to();
		Vec3 back = b.from().subtract(p).normalize().scale(0.08);
		if (b.hit()) { // someone: a red puff
			for (int i = 0; i < 4; i++)
				mc.level.addParticle(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.2, 0.1, (r.nextDouble() - 0.5) * 0.2);
			return;
		}
		// the ground or a wall: sparks, a little dust of what it hit, smoke
		BlockState st = mc.level.getBlockState(BlockPos.containing(p.subtract(back)));
		if (!st.isAir()) for (int i = 0; i < 4; i++)
			mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), p.x + back.x, p.y + back.y, p.z + back.z, back.x * 2, 0.05 + r.nextDouble() * 0.1, back.z * 2);
		for (int i = 0; i < 3; i++)
			mc.level.addParticle(ParticleTypes.CRIT, p.x + back.x, p.y + back.y, p.z + back.z, back.x * 4 + (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.25,
				back.z * 4 + (r.nextDouble() - 0.5) * 0.3);
		mc.level.addParticle(ParticleTypes.SMOKE, p.x + back.x, p.y + back.y, p.z + back.z, 0, 0.02, 0);
	}

	/** The bullets in flight, their flashes and the sniper's smoke (WorldVisuals: camera-relative). */
	public static void draw(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		if (BULLETS.isEmpty() && FLASHES.isEmpty()) return;
		double time = now();
		for (Iterator<Bullet> it = BULLETS.iterator(); it.hasNext();) {
			Bullet b = it.next();
			double dist = b.from().distanceTo(b.to());
			double life = b.smoke() ? Math.max(dist / b.speed(), 0) + SMOKE : (dist + b.length()) / b.speed();
			if (time - b.born() > life + 0.05) {
				it.remove();
				LANDED.remove(b);
			}
		}
		FLASHES.removeIf(f -> time - f.born() > FLASH);
		List<Bullet> bullets = List.copyOf(BULLETS);
		List<Flash> flashes = List.copyOf(FLASHES);
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			for (Bullet b : bullets) {
				Vec3 dir = b.to().subtract(b.from());
				double dist = dir.length();
				if (dist < 1e-3) continue;
				dir = dir.scale(1 / dist);
				double flown = (time - b.born()) * b.speed();
				// the streak: its head flies to where it hit, its tail following tracerLength behind
				double head = Math.min(dist, flown), tail = Math.max(0, Math.min(dist, flown - b.length()));
				if (tail < head) {
					Vector3f h = rel(b.from().add(dir.scale(head)), cam), t = rel(b.from().add(dir.scale(tail)), cam);
					band(vc, pose, t, h, 0.12F, b.color(), 0x00, 0x80); // the glow
					band(vc, pose, t, h, 0.035F, 0xFFFFFF, 0x30, 0xFF); // the white-hot core
				}
				if (b.smoke()) { // the sniper's trail: from the gun to where it went, fading
					double age = time - b.born() - Math.min(dist, flown) / b.speed();
					float a = (float) Math.max(0, 1 - Math.max(0, age) / SMOKE);
					if (a > 0) band(vc, pose, rel(b.from(), cam), rel(b.from().add(dir.scale(head)), cam), 0.07F, 0xD8DCE4, (int) (a * 70), (int) (a * 110));
				}
			}
			for (Flash f : flashes) {
				float k = (float) ((time - f.born()) / FLASH), s = f.size() * (1 - 0.4F * k);
				int a = (int) (255 * (1 - k));
				star(vc, pose, rel(f.at(), cam), s, f.spin(), 0xFFF2C0, a);
				star(vc, pose, rel(f.at(), cam), s * 0.45F, f.spin() + 45, 0xFFFFFF, a);
			}
		});
	}

	private static Vector3f rel(Vec3 p, Vec3 cam) {
		return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
	}

	/** A band from a to b (camera-relative), width across, facing the camera; alpha from a's end to b's. */
	static void band(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, float width, int rgb, int alphaA, int alphaB) {
		Vector3f along = new Vector3f(b).sub(a), mid = new Vector3f(a).add(b).mul(0.5F);
		Vector3f side = along.cross(mid.negate(new Vector3f()));
		if (side.lengthSquared() < 1e-10F) return;
		side.normalize(width / 2);
		int ca = alphaA << 24 | rgb, cb = alphaB << 24 | rgb;
		quad(vc, pose, new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side), new Vector3f(b).sub(side), ca, ca, cb, cb);
	}

	/** A four-pointed flash at p, facing the camera, turned spin degrees. */
	private static void star(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, float size, float spin, int rgb, int alpha) {
		Vector3f view = new Vector3f(p).negate().normalize();
		Vector3f right = new Vector3f(view).cross(0, 1, 0);
		if (right.lengthSquared() < 1e-6F) right.set(1, 0, 0);
		right.normalize();
		Vector3f up = new Vector3f(right).cross(view).normalize();
		float a = (float) Math.toRadians(spin);
		Vector3f r = new Vector3f(right).mul((float) Math.cos(a)).add(new Vector3f(up).mul((float) Math.sin(a)));
		Vector3f u = new Vector3f(up).mul((float) Math.cos(a)).sub(new Vector3f(right).mul((float) Math.sin(a)));
		int c = alpha << 24 | rgb, edge = rgb;
		for (int k = 0; k < 2; k++) { // two long thin diamonds crossed
			Vector3f l = k == 0 ? r : u, w = k == 0 ? u : r;
			quad(vc, pose, new Vector3f(p).fma(-size, l), new Vector3f(p).fma(-size * 0.18F, w), new Vector3f(p).fma(size, l), new Vector3f(p).fma(size * 0.18F, w), edge,
				c, edge, c);
		}
	}

	static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd) {
		v(vc, pose, a, ca);
		v(vc, pose, b, cb);
		v(vc, pose, c, cc);
		v(vc, pose, d, cd);
		v(vc, pose, d, cd);
		v(vc, pose, c, cc);
		v(vc, pose, b, cb);
		v(vc, pose, a, ca);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, int color) {
		vc.addVertex(pose, p.x, p.y, p.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
	}
}
