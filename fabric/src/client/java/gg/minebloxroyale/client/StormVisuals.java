package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import gg.minebloxroyale.MinebloxRoyale;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The storm wall (creator: more detailed, without costing frame rate). All the detail is in one cloud texture made once
 * (swirling, domain-warped purple clouds with wind streaks), scrolled across the wall, so a frame costs about what the
 * old flat wall did:
 * - two cloud layers, the outer one 1.5 blocks further out, finer and drifting the other way (depth as you move);
 * - a soft top that fades into the sky;
 * - a glowing seam that follows the ground where the storm meets the land;
 * - now and then a lightning bolt, with the wall flaring for a moment.
 * Only the arc within 260 blocks of you is drawn, in pieces about 4 blocks wide.
 */
public final class StormVisuals {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int TEX = 256;
	private static Identifier clouds;

	private StormVisuals() {
	}

	public static void draw(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		double[] st = ClientState.storm();
		double sx = st[0], sz = st[1], r = st[2];
		if (r < 0.5) return;
		double dx = cam.x - sx, dz = cam.z - sz;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (Math.abs(dist - r) > 260) return;
		boolean inside = dist < r;
		double y0 = Math.max(-64, cam.y - 80), y1 = cam.y + 160, fadeFrom = y1 - 70;
		double reach = Math.min(Math.PI, 260 / Math.max(1, r) + 0.2);
		double mid = Math.atan2(dz, dx);
		int segments = Math.max(12, Math.min(160, (int) (reach * 2 * r / 4)));
		long ms = System.currentTimeMillis();
		// Seconds in a 100 s loop: every scroll speed below times 100 is a whole number of texture tiles, so the loop
		// is seamless.
		double t = (ms % 100_000L) / 1000.0;
		Bolt bolt = bolt(ms, sx, sz, mid, reach);
		float flare = bolt == null ? 1F : 1.35F;

		// Ground under each piece's edge (the land the storm touches), for the glowing seam.
		ClientLevel level = Minecraft.getInstance().level;
		double[] ground = new double[segments + 1];
		for (int i = 0; i <= segments; i++) {
			double a = mid - reach + 2 * reach * i / segments;
			ground[i] = groundAt(level, sx + Math.cos(a) * r, sz + Math.sin(a) * r, cam.y - 6);
		}

		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(clouds()), (pose, vc) -> {
			for (int layer = 0; layer < 2; layer++) {
				// Layer 0: the main wall. Layer 1: finer clouds 1.5 blocks further out, drifting the other way,
				// fainter. Both rise slowly.
				double rr = r + (layer == 0 ? 0 : 1.5), tileU = layer == 0 ? 56 : 34, tileV = layer == 0 ? 72 : 44;
				double du = layer == 0 ? 0.02 * t : -0.03 * t, dv = layer == 0 ? 0.05 * t : 0.08 * t;
				float strength = layer == 0 ? 0.8F : 0.5F; // headroom for the lightning flare
				for (int i = 0; i < segments; i++) {
					double a0 = mid - reach + 2 * reach * i / segments, a1 = mid - reach + 2 * reach * (i + 1) / segments;
					double x0 = sx + Math.cos(a0) * rr, z0 = sz + Math.sin(a0) * rr, x1 = sx + Math.cos(a1) * rr, z1 = sz + Math.sin(a1) * rr;
					float nx = (float) Math.cos((a0 + a1) / 2) * (inside ? -1 : 1), nz = (float) Math.sin((a0 + a1) / 2) * (inside ? -1 : 1);
					float u0 = (float) (a0 * r / tileU + du), u1 = (float) (a1 * r / tileU + du);
					// A slow brightness swirl round the wall on top of the texture's own movement.
					float swirl = (float) (0.85 + 0.15 * Math.sin(a0 * 9 + t * 1.3 + layer * 2));
					int solid = color(strength * swirl * flare, 1F), clear = color(strength * swirl * flare, 0F);
					panel(vc, pose, cam, x0, z0, x1, z1, y0, fadeFrom, u0, u1, tileV, dv, solid, solid, nx, nz);
					panel(vc, pose, cam, x0, z0, x1, z1, fadeFrom, y1, u0, u1, tileV, dv, solid, clear, nx, nz);
				}
			}
		});
		// Plain colours (white texture): the glowing seam along the ground, bright at the land and fading upwards, and
		// the lightning. Both a little on your side of the wall, so the clouds don't dim them.
		double front = inside ? -0.3 : 0.3;
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			for (int i = 0; i < segments; i++) {
				double a0 = mid - reach + 2 * reach * i / segments, a1 = mid - reach + 2 * reach * (i + 1) / segments;
				double rr = r + front;
				double x0 = sx + Math.cos(a0) * rr, z0 = sz + Math.sin(a0) * rr, x1 = sx + Math.cos(a1) * rr, z1 = sz + Math.sin(a1) * rr;
				float nx = (float) Math.cos((a0 + a1) / 2) * (inside ? -1 : 1), nz = (float) Math.sin((a0 + a1) / 2) * (inside ? -1 : 1);
				quad(vc, pose, cam, x0, ground[i] - 0.5, z0, x1, ground[i + 1] - 0.5, z1, ground[i] + 3, ground[i + 1] + 3, 0.5F, 0.5F, 0.5F, 0.5F,
					0xE8E2A8FF, 0x00E2A8FF, nx, nz);
			}
			if (bolt != null) {
				int at = Math.max(0, Math.min(segments, (int) Math.round((bolt.angle - (mid - reach)) / (2 * reach) * segments)));
				bolt.draw(vc, pose, cam, r + front * 1.5, ground[at], y1 - 30);
			}
		});
	}

	/** A wall panel from ya to yb; v follows world height so the clouds don't slide as you climb. */
	private static void panel(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x0, double z0, double x1, double z1, double ya, double yb,
		float u0, float u1, double tileV, double dv, int colBottom, int colTop, float nx, float nz) {
		float va = (float) (-ya / tileV + dv), vb = (float) (-yb / tileV + dv);
		quad(vc, pose, cam, x0, ya, z0, x1, ya, z1, yb, yb, u0, u1, va, vb, colBottom, colTop, nx, nz);
	}

	/** Both windings (the one facing away is culled), so it shows from inside the storm and out. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x0, double ya0, double z0, double x1, double ya1, double z1,
		double yb0, double yb1, float u0, float u1, float va, float vb, int colBottom, int colTop, float nx, float nz) {
		v(vc, pose, cam, x0, yb0, z0, u0, vb, colTop, nx, nz);
		v(vc, pose, cam, x0, ya0, z0, u0, va, colBottom, nx, nz);
		v(vc, pose, cam, x1, ya1, z1, u1, va, colBottom, nx, nz);
		v(vc, pose, cam, x1, yb1, z1, u1, vb, colTop, nx, nz);
		v(vc, pose, cam, x1, yb1, z1, u1, vb, colTop, -nx, -nz);
		v(vc, pose, cam, x1, ya1, z1, u1, va, colBottom, -nx, -nz);
		v(vc, pose, cam, x0, ya0, z0, u0, va, colBottom, -nx, -nz);
		v(vc, pose, cam, x0, yb0, z0, u0, vb, colTop, -nx, -nz);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x, double y, double z, float u, float v, int color, float nx, float nz) {
		vc.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(color).setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, 0, nz);
	}

	/** Vertex colour: white tinted by brightness (the texture has the colours), alpha scaled. */
	private static int color(float bright, float alpha) {
		int c = Math.max(0, Math.min(255, Math.round(255 * Math.min(1F, bright))));
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha * Math.min(1F, 0.55F + 0.45F * bright))));
		return a << 24 | c << 16 | c << 8 | c;
	}

	private static double groundAt(ClientLevel level, double x, double z, double fallback) {
		if (level == null) return fallback;
		int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		return h <= level.getMinY() + 1 ? fallback : h; // not loaded: keep it near you
	}

	// ---- lightning ----

	/** A bolt on the drawn arc: a chance every 0.9 s, lasting 0.18 s; the same for every frame of that flash. */
	private record Bolt(double angle, double sx, double sz, long seed) {
		/**
		 * A jagged white-violet ribbon on the wall (at radius rr), from high up down to the ground, 12 zig-zag pieces.
		 */
		void draw(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double rr, double groundY, double topY) {
			Random rnd = new Random(seed);
			double tx = -Math.sin(angle), tz = Math.cos(angle); // along the wall
			double cx = sx + Math.cos(angle) * rr, cz = sz + Math.sin(angle) * rr, w = 0.45;
			float nx = (float) Math.cos(angle), nz = (float) Math.sin(angle);
			int steps = 12, col = 0xF8F4ECFF;
			double off = 0;
			for (int i = 0; i < steps; i++) {
				double ya = topY + (groundY - topY) * i / steps, yb = topY + (groundY - topY) * (i + 1) / steps; // ya above yb
				double next = off + (rnd.nextDouble() - 0.5) * 5;
				double ax = cx + tx * off, az = cz + tz * off, bx = cx + tx * next, bz = cz + tz * next;
				// Slanted piece: top edge centred on this step's offset, bottom edge on the next one's; both windings.
				double[][] c = { { ax - tx * w, ya, az - tz * w }, { bx - tx * w, yb, bz - tz * w }, { bx + tx * w, yb, bz + tz * w }, { ax + tx * w, ya, az + tz * w } };
				for (int k = 0; k < 4; k++) v(vc, pose, cam, c[k][0], c[k][1], c[k][2], 0.5F, 0.5F, col, nx, nz);
				for (int k = 3; k >= 0; k--) v(vc, pose, cam, c[k][0], c[k][1], c[k][2], 0.5F, 0.5F, col, -nx, -nz);
				off = next;
			}
		}
	}

	/**
	 * A chance every 0.9 s (30%), lasting 0.18 s, somewhere on the drawn arc; the same bolt for every frame of that
	 * flash.
	 */
	private static Bolt bolt(long ms, double sx, double sz, double mid, double reach) {
		long slot = ms / 900;
		if (ms % 900 > 180) return null;
		Random rnd = new Random(slot * 0x9E3779B97F4A7C15L);
		if (rnd.nextFloat() > 0.3F) return null;
		return new Bolt(mid - reach * 0.8 + rnd.nextDouble() * reach * 1.6, sx, sz, rnd.nextLong());
	}

	// ---- the cloud texture ----

	/** Made once: 256 x 256, seamless both ways, smooth (linear) filtering so it doesn't shimmer in the distance. */
	private static Identifier clouds() {
		if (clouds != null) return clouds;
		NativeImage img = new NativeImage(TEX, TEX, false);
		for (int y = 0; y < TEX; y++)
			for (int x = 0; x < TEX; x++) {
				double u = (double) x / TEX, v = (double) y / TEX;
				// Domain warp: clouds that curl round each other.
				double wu = u + 0.18 * fbm(u + 0.31, v + 0.47, 4, 3, 7), wv = v + 0.18 * fbm(u + 0.73, v + 0.11, 4, 3, 11);
				double d = fbm(wu, wv, 4, 4, 1);
				// Wind streaks: noise stretched sideways.
				double streak = Math.max(0, fbmAniso(u, v + 0.37 * d, 2, 22, 2, 5) - 0.58) * 2.4;
				d = Math.max(0, Math.min(1, (d - 0.22) / 0.62));
				int r = lerp(0x2A, 0x86, d) + (int) (Math.max(0, d - 0.62) * 260) + (int) (streak * 120);
				int g = lerp(0x06, 0x28, d) + (int) (Math.max(0, d - 0.62) * 300) + (int) (streak * 110);
				int b = lerp(0x4E, 0xD8, d) + (int) (Math.max(0, d - 0.62) * 120) + (int) (streak * 60);
				int a = (int) (110 + 120 * d + streak * 40);
				img.setPixel(x, y, clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
			}
		clouds = MinebloxRoyale.id("storm_clouds");
		DynamicTexture tex = new DynamicTexture(() -> "minebloxroyale storm clouds", img) {
			{
				this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
			}
		};
		Minecraft.getInstance().getTextureManager().register(clouds, tex);
		return clouds;
	}

	/** Seamless fractal noise on the unit square: octaves of value noise at base, 2 x base... cells per side. */
	private static double fbm(double u, double v, int octaves, int base, int salt) {
		double sum = 0, amp = 0.5, norm = 0;
		for (int o = 0; o < octaves; o++) {
			int cells = base << o;
			sum += amp * value(u * cells, v * cells, cells, cells, o * 31 + salt);
			norm += amp;
			amp *= 0.5;
		}
		return sum / norm;
	}

	private static double fbmAniso(double u, double v, int cellsU, int cellsV, int octaves, int salt) {
		double sum = 0, amp = 0.5, norm = 0;
		for (int o = 0; o < octaves; o++) {
			sum += amp * value(u * (cellsU << o), v * (cellsV << o), cellsU << o, cellsV << o, o * 17 + salt);
			norm += amp;
			amp *= 0.5;
		}
		return sum / norm;
	}

	/** Smooth value noise that wraps every pu x pv cells (so the texture tiles). */
	private static double value(double x, double y, int pu, int pv, int salt) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		double fx = x - ix, fy = y - iy;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = hash(ix, iy, pu, pv, salt), b = hash(ix + 1, iy, pu, pv, salt), c = hash(ix, iy + 1, pu, pv, salt), e = hash(ix + 1, iy + 1, pu, pv, salt);
		return (a + (b - a) * fx) * (1 - fy) + (c + (e - c) * fx) * fy;
	}

	private static double hash(int x, int y, int pu, int pv, int salt) {
		int h = Math.floorMod(x, pu) * 73856093 ^ Math.floorMod(y, pv) * 19349663 ^ salt * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	private static int lerp(int a, int b, double t) {
		return (int) Math.round(a + (b - a) * t);
	}

	private static int clamp(int c) {
		return Math.max(0, Math.min(255, c));
	}
}
