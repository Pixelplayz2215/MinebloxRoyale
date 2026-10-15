package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.client.WorldVisuals;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Contrails (v2.0.0, creator: "actual gliding (with a contrail (also unlockable and purchaseBle contrails))"): light
 * trails behind a player in the air, the cosmetics sheet's contrail slot (shape = the look, colour into colour2): from
 * the hands while skydiving (the body falls upright here, so trails from the feet would rise through it) and from the
 * glider's wingtips while riding it. A trail is where the hand or wingtip went in the world over the last part of a
 * second, drawn as a band facing the camera, tapering and fading to its end, the same as the Blender renders
 * (fabric/tools/blender/glider_video.py; look() is its look_at). Everyone without one picked trails the first free one,
 * as everyone in Fortnite has a contrail. (2.0.0-test2, creator: "Candy Stripe annimation is too choppy ... smoother
 * animations for all crontrails") A trail is drawn along a smooth curve through its points, resampled evenly and finely,
 * so its look sits on a steady grid whatever the frame rate; a striped one is cut exactly at each stripe's edge, so the
 * stripes slide instead of jumping from point to point; and it starts at the hand where the hand is this frame.
 */
public final class Contrails {
	private static final float S = RobloxAvatarRenderer.S;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** A look: its width at the hand (studs) and how long a point of it lasts (seconds). */
	private record Style(float width, float life) {
	}

	private static final Map<String, Style> STYLES = Map.ofEntries(Map.entry("streak", new Style(0.34F, 0.6F)), Map.entry("rainbow", new Style(0.38F, 0.7F)),
		Map.entry("bands", new Style(0.36F, 0.6F)), Map.entry("neon", new Style(0.16F, 0.55F)), Map.entry("sparkle", new Style(0.24F, 0.75F)),
		Map.entry("flame", new Style(0.42F, 0.45F)), Map.entry("wave", new Style(0.4F, 0.8F)), Map.entry("dots", new Style(0.34F, 0.7F)),
		// 2.0.0-test3 (creator: "more contrails"): lightning, a twisting ribbon, retro pixels, hearts
		Map.entry("zigzag", new Style(0.2F, 0.5F)), Map.entry("ribbon", new Style(0.4F, 0.75F)), Map.entry("pixel", new Style(0.3F, 0.8F)),
		Map.entry("hearts", new Style(0.32F, 0.8F)));
	private static final Style STREAK = STYLES.get("streak");

	/**
	 * One avatar's trails: two from the hands, two from the wingtips (each world points {x, y, z, seconds}, newest last;
	 * and where each tip was this frame, the trail's start).
	 */
	private static final class Trails {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final ArrayDeque<double[]>[] pts = new ArrayDeque[] { new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>() };
		final double[][] head = new double[4][];
		CosmeticDef look;
		double seen;
		/** A car's boost trail (2.0.0-test3): wider. */
		boolean car;
	}

	/** How much wider (and longer lasting) a car's boost trail is than an avatar's contrail of the same look. */
	private static final float CAR_WIDTH = 2.6F, CAR_LIFE = 1.3F;

	/** Points a trail in the world is drawn with (evenly along it), and the Locker's preview. */
	private static final int WORLD_POINTS = 64, PREVIEW_POINTS = 96;
	/** Stripes along a trail ("bands"), and how many go by a second: look_at's numbers. */
	private static final float STRIPES = 9, STRIPE_SPEED = 3;

	private static final Map<Integer, Trails> ALL = new HashMap<>();

	private Contrails() {
	}

	private static double now() {
		return System.nanoTime() / 1e9;
	}

	/** The contrail this outfit trails: the one picked, or the first free one. */
	static @Nullable CosmeticDef of(@Nullable Map<String, String> outfit) {
		CosmeticDef c = Cosmetics.worn(outfit, "contrail");
		if (c != null) return c;
		for (CosmeticDef d : Sheets.COSMETICS)
			if (d.slot().equals("contrail") && d.unlock().equals("default")) return d;
		return null;
	}

	/**
	 * This frame's trail heads for an avatar: tips (model space: its hands, or its glider's wingtips), model: the
	 * avatar's model space to the camera (the pose it's drawn with in the world).
	 */
	static void record(int entityId, @Nullable Map<String, String> outfit, Matrix4f model, Vector3f[] tips, boolean wingtips) {
		CosmeticDef look = of(outfit);
		if (look == null) return;
		Trails t = ALL.computeIfAbsent(entityId, k -> new Trails());
		double time = now();
		t.look = look;
		t.seen = time;
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vector3f w = new Vector3f();
		for (int i = 0; i < tips.length && i < 2; i++) {
			model.transformPosition(tips[i], w);
			ArrayDeque<double[]> q = t.pts[(wingtips ? 2 : 0) + i];
			double x = cam.x + w.x, y = cam.y + w.y, z = cam.z + w.z;
			t.head[(wingtips ? 2 : 0) + i] = new double[] { x, y, z, time };
			double[] last = q.peekLast();
			// (a new point every 30th of a second or so, or sooner when it moved: smooth at any frame rate)
			if (last != null && time - last[3] < 1 / 60.0 && (x - last[0]) * (x - last[0]) + (y - last[1]) * (y - last[1]) + (z - last[2]) * (z - last[2]) < 0.0025) continue;
			q.addLast(new double[] { x, y, z, time });
			if (q.size() > 160) q.removeFirst();
		}
	}

	/**
	 * A boosting car's boost trail this frame (2.0.0-test3, creator: "Car should have a contrail when boosting"): from its
	 * exhausts (the car's model space), model: that space to the camera; the look is its driver's (cosmetics slot
	 * boosttrail).
	 */
	public static void recordCar(int entityId, CosmeticDef look, Matrix4f model, Vector3f[] exhausts) {
		Trails t = ALL.computeIfAbsent(entityId, k -> new Trails());
		double time = now();
		t.look = look;
		t.car = true;
		t.seen = time;
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vector3f w = new Vector3f();
		for (int i = 0; i < exhausts.length && i < 2; i++) {
			model.transformPosition(exhausts[i], w);
			ArrayDeque<double[]> q = t.pts[i];
			double x = cam.x + w.x, y = cam.y + w.y, z = cam.z + w.z;
			t.head[i] = new double[] { x, y, z, time };
			double[] last = q.peekLast();
			if (last != null && time - last[3] < 1 / 60.0 && (x - last[0]) * (x - last[0]) + (y - last[1]) * (y - last[1]) + (z - last[2]) * (z - last[2]) < 0.01) continue;
			q.addLast(new double[] { x, y, z, time });
			if (q.size() > 160) q.removeFirst();
		}
	}

	/**
	 * The Locker's Boost Trails preview: the trail streaming back from a standing car's exhausts (its model space) as if it
	 * were boosting, lying flat behind it and standing up across it, so it shows from any angle.
	 */
	public static void carPreview(SubmitNodeCollector collector, PoseStack poseStack, CosmeticDef look, Vector3f[] exhausts) {
		Style st = STYLES.getOrDefault(look.shape(), STREAK);
		float tsec = (float) (now() % 1000), width = st.width() * S * CAR_WIDTH;
		int n = PREVIEW_POINTS;
		for (Vector3f e : exhausts) {
			Vector3f[] p = new Vector3f[n];
			float[] u = new float[n];
			for (int k = 0; k < n; k++) {
				u[k] = k / (n - 1F);
				p[k] = new Vector3f(e.x + 0.05F * (float) Math.sin(tsec * 3 - u[k] * 6), e.y + 0.08F * u[k], e.z - 4.2F * u[k]);
			}
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
				band(vc, pose, p, u, look, width, tsec, new Vector3f(0, 1, 0));
				band(vc, pose, p, u, look, width, tsec, new Vector3f(1, 0, 0));
			});
		}
	}

	/** Every trail in the world (WorldVisuals, camera-relative), older points dropped as they fade. */
	public static void draw(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		if (ALL.isEmpty()) return;
		double time = now();
		float tsec = (float) (time % 1000);
		for (Iterator<Trails> it = ALL.values().iterator(); it.hasNext();) {
			Trails t = it.next();
			Style st = STYLES.getOrDefault(t.look.shape(), STREAK);
			float life = st.life() * (t.car ? CAR_LIFE : 1), width = st.width() * S * (t.car ? CAR_WIDTH : 1);
			boolean any = false;
			for (ArrayDeque<double[]> q : t.pts) {
				while (!q.isEmpty() && time - q.peekFirst()[3] > life) q.removeFirst();
				any |= !q.isEmpty();
			}
			if (!any && time - t.seen > 2) {
				it.remove();
				continue;
			}
			for (int i = 0; i < t.pts.length; i++) {
				ArrayDeque<double[]> q = t.pts[i];
				if (q.size() < 2) continue;
				// newest first, camera-relative, with how far along the trail each is (0 at the hand, 1 at the end); first
				// where the tip is this frame (recorded since the newest point, still being recorded)
				double[] h = t.head[i];
				boolean live = h != null && h[3] > q.peekLast()[3] && time - h[3] < 0.1;
				int n = q.size() + (live ? 1 : 0);
				Vector3f[] p = new Vector3f[n];
				float[] u = new float[n];
				Iterator<double[]> d = q.descendingIterator();
				for (int k = 0; k < n; k++) {
					double[] a = live && k == 0 ? h : d.next();
					p[k] = new Vector3f((float) (a[0] - cam.x), (float) (a[1] - cam.y), (float) (a[2] - cam.z));
					u[k] = (float) Math.min(1, (time - a[3]) / life);
				}
				Strip strip = resample(p, u, WORLD_POINTS);
				CosmeticDef look = t.look;
				out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()),
					(pose, vc) -> band(vc, pose, strip.p(), strip.u(), look, width, tsec, null));
			}
		}
	}

	/** The Locker's preview: trails rising from the hands (model space, y down) as if falling, swaying a little. */
	static void preview(SubmitNodeCollector collector, PoseStack poseStack, @Nullable Map<String, String> outfit, Vector3f[] hands) {
		CosmeticDef look = of(outfit);
		if (look == null) return;
		Style st = STYLES.getOrDefault(look.shape(), STREAK);
		float tsec = (float) (now() % 1000);
		int n = PREVIEW_POINTS;
		for (Vector3f h : hands) {
			Vector3f[] p = new Vector3f[n];
			float[] u = new float[n];
			for (int k = 0; k < n; k++) {
				u[k] = k / (n - 1F);
				float age = u[k] * st.life();
				// (falling about 7 studs a second: the trail rises; the body drifts a little side to side)
				p[k] = new Vector3f(h.x + 0.12F * (float) Math.sin(tsec * 2.2 - age * 3), h.y - 7 * S * age, h.z);
			}
			// two bands crossed (facing front and facing the side), so it shows however the preview is turned
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
				band(vc, pose, p, u, look, st.width() * S, tsec, new Vector3f(0, 0, 1));
				band(vc, pose, p, u, look, st.width() * S, tsec, new Vector3f(1, 0, 0));
			});
		}
	}

	/** A trail's points (newest first) and how far along it each is (0 at the hand, 1 at the end). */
	private record Strip(Vector3f[] p, float[] u) {
	}

	/**
	 * The trail at m points evenly along it (by u, from its first point to its last), on a smooth curve through the
	 * recorded points (Catmull-Rom): its look then sits on a fine grid that doesn't change with the frame rate or with
	 * how far apart the points were recorded.
	 */
	private static Strip resample(Vector3f[] p, float[] u, int m) {
		int n = p.length, k = 0;
		Vector3f[] q = new Vector3f[m];
		float[] w = new float[m];
		for (int j = 0; j < m; j++) {
			float uj = u[0] + (u[n - 1] - u[0]) * j / (m - 1);
			while (k < n - 2 && u[k + 1] < uj) k++;
			float span = u[k + 1] - u[k], f = span <= 1e-6F ? 0 : Math.max(0, Math.min(1, (uj - u[k]) / span));
			q[j] = curve(p[Math.max(0, k - 1)], p[k], p[k + 1], p[Math.min(n - 1, k + 2)], f);
			w[j] = uj;
		}
		return new Strip(q, w);
	}

	/** The point f of the way from b to c on the Catmull-Rom curve through a, b, c, d. */
	private static Vector3f curve(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float f) {
		float f2 = f * f, f3 = f2 * f;
		return new Vector3f(cr(a.x, b.x, c.x, d.x, f, f2, f3), cr(a.y, b.y, c.y, d.y, f, f2, f3), cr(a.z, b.z, c.z, d.z, f, f2, f3));
	}

	private static float cr(float a, float b, float c, float d, float f, float f2, float f3) {
		return 0.5F * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f2 + (3 * b - a - 3 * c + d) * f3);
	}

	/**
	 * One trail as a band (p newest first, u along it): facing the camera (at the origin) or, given, across a fixed
	 * direction; neon a white core in a wide glow, wave weaving side to side, sparkle with bright sparks, dots just
	 * bubbles; bands cut at every stripe's edge (each piece one colour); zigzag a jagged bolt flickering into a new shape
	 * twelve times a second (a core in the first colour in a glow of the second); ribbon twisting over and over, one colour a side; pixel
	 * square blocks in the two colours by turns; hearts beating. glider_video.ribbons draws them the same.
	 */
	private static void band(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] p, float[] u, CosmeticDef look, float width, float time,
		@Nullable Vector3f across) {
		String style = look.shape();
		int n = p.length;
		Vector3f[] side = new Vector3f[n];
		for (int k = 0; k < n; k++) {
			Vector3f tan = new Vector3f(p[Math.min(n - 1, k + 1)]).sub(p[Math.max(0, k - 1)]);
			Vector3f view = across != null ? new Vector3f(across) : new Vector3f(p[k]).negate();
			Vector3f s = tan.cross(view);
			side[k] = s.lengthSquared() < 1e-10F ? new Vector3f(1, 0, 0) : s.normalize();
		}
		float[][] lk = new float[n][];
		for (int k = 0; k < n; k++) lk[k] = look(style, look.color(), look.color2(), u[k], time);
		if (style.equals("wave"))
			for (int k = 0; k < n; k++) p[k] = new Vector3f(p[k]).fma(0.35F * S * u[k] * (float) Math.sin(u[k] * 9 - time * 6), side[k]);
		if (style.equals("zigzag"))
			for (int k = 0; k < n; k++) p[k] = new Vector3f(p[k]).fma(0.42F * S * zig(u[k], time), side[k]);
		if (style.equals("pixel") || style.equals("hearts")) {
			blocks(vc, pose, p, u, side, lk, look, width, time, across);
			return;
		}
		if (style.equals("ribbon")) {
			ribbon(vc, pose, p, u, side, lk, look, width, time);
			return;
		}
		if (!style.equals("dots")) {
			boolean zigzag = style.equals("zigzag"), neon = style.equals("neon") || zigzag, bands = style.equals("bands");
			float[] a = rgb(look.color()), b = rgb(look.color2());
			for (int layer = neon ? 1 : 0; layer >= 0; layer--) {
				float wk = layer == 1 ? 2.6F : 1, ak = layer == 1 ? 0.35F : 1;
				for (int k = 0; k + 1 < n; k++) {
					float h0 = width * lk[k][4] * wk / 2, h1 = width * lk[k + 1][4] * wk / 2;
					if (bands) {
						// Cut where a stripe ends between the two points (the stripe count runs up along the trail): each
						// piece one colour, so its edges are sharp and slide smoothly as the stripes move.
						float s0 = u[k] * STRIPES - time * STRIPE_SPEED, s1 = u[k + 1] * STRIPES - time * STRIPE_SPEED;
						int stripe = (int) Math.floor(s0);
						for (float from = 0; from < 1;) {
							float to = s1 > s0 ? Math.min(1, (stripe + 1 - s0) / (s1 - s0)) : 1;
							float[] c = Math.floorMod(stripe, 2) == 0 ? a : b;
							piece(vc, pose, p[k], p[k + 1], side[k], side[k + 1], h0, h1, from, to, argb(c, lerp(lk[k][3], lk[k + 1][3], from)),
								argb(c, lerp(lk[k][3], lk[k + 1][3], to)));
							from = to;
							stripe++;
						}
						continue;
					}
					float[] k0 = zigzag ? layer == 0 ? a : b : neon && layer == 0 ? WHITE : lk[k], k1 = zigzag ? layer == 0 ? a : b : neon && layer == 0 ? WHITE : lk[k + 1];
					int c0 = argb(k0, lk[k][3] * ak), c1 = argb(k1, lk[k + 1][3] * ak);
					quad(vc, pose, new Vector3f(p[k]).fma(-h0, side[k]), new Vector3f(p[k]).fma(h0, side[k]), new Vector3f(p[k + 1]).fma(h1, side[k + 1]),
						new Vector3f(p[k + 1]).fma(-h1, side[k + 1]), c0, c0, c1, c1);
				}
			}
		}
		if (style.equals("sparkle") || style.equals("dots")) {
			boolean sparkle = style.equals("sparkle");
			for (int k = 0; k < n; k += sparkle ? 3 : 4) {
				float r;
				int c;
				if (sparkle) {
					float tw = 0.6F + 0.4F * (float) Math.sin(k * 2.3 + time * 14);
					r = width * 0.55F * tw * (1 - u[k] * 0.6F);
					float[] b = { Math.min(1, lk[k][0] * 0.5F + 0.6F), Math.min(1, lk[k][1] * 0.5F + 0.6F), Math.min(1, lk[k][2] * 0.5F + 0.6F) };
					c = argb(b, Math.min(1, lk[k][3] / 0.45F));
				} else {
					r = width * 0.55F * (0.7F + 0.3F * (float) Math.sin(k * 1.7));
					c = argb(lk[k], Math.min(1, lk[k][3] * 1.2F));
				}
				Vector3f view = across != null ? new Vector3f(across) : new Vector3f(p[k]).negate().normalize();
				Vector3f right = new Vector3f(side[k]), up = new Vector3f(view).cross(right).normalize();
				quad(vc, pose, new Vector3f(p[k]).fma(-r, right), new Vector3f(p[k]).fma(-r, up), new Vector3f(p[k]).fma(r, right),
					new Vector3f(p[k]).fma(r, up), c, c, c, c);
			}
		}
	}

	private static final float[] WHITE = { 1, 1, 1 };

	/**
	 * A bolt's sideways offset at u along it (about -1..1, none at the hand): two sharp zigzags of different lengths
	 * added (so it's jagged, not a spring), jumping to a new shape twelve times a second, like lightning flickering.
	 */
	static float zig(float u, float time) {
		float flick = (float) Math.floor(time * 12);
		return (0.7F * tri(u * 3.2F + flick * 0.618F) + 0.3F * tri(u * 7.7F + flick * 0.371F)) * Math.min(1, u * 4);
	}

	/** A triangle wave: -1 up to 1 and back each 1. */
	private static float tri(float x) {
		return 2 * Math.abs(2 * (x - (float) Math.floor(x + 0.5F))) - 1;
	}

	/**
	 * A ribbon twisting over and over as it streams back: its width turns through the trail's plane (cos of the twist,
	 * so it crosses itself where the twist is edge-on), the first colour on one face and the second on the other.
	 */
	private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] p, float[] u, Vector3f[] side, float[][] lk, CosmeticDef look, float width,
		float time) {
		float[] a = rgb(look.color()), b = rgb(look.color2());
		int n = p.length;
		float[] h = new float[n];
		int[] c = new int[n];
		for (int k = 0; k < n; k++) {
			float twist = (float) Math.cos(u[k] * 10 - time * 5);
			h[k] = width * lk[k][4] / 2 * twist;
			c[k] = argb(mix(b, a, 0.5F + 0.5F * Math.max(-1, Math.min(1, twist * 3))), lk[k][3]);
		}
		for (int k = 0; k + 1 < n; k++)
			quad(vc, pose, new Vector3f(p[k]).fma(-h[k], side[k]), new Vector3f(p[k]).fma(h[k], side[k]), new Vector3f(p[k + 1]).fma(h[k + 1], side[k + 1]),
				new Vector3f(p[k + 1]).fma(-h[k + 1], side[k + 1]), c[k], c[k], c[k + 1], c[k + 1]);
	}

	/**
	 * The pixel and hearts trails: no band, just shapes along it facing you (or across the fixed direction), each the
	 * right way up as it streams back (a heart's lobes towards the hand, its point trailing).
	 */
	private static void blocks(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] p, float[] u, Vector3f[] side, float[][] lk, CosmeticDef look, float width,
		float time, @Nullable Vector3f across) {
		boolean hearts = look.shape().equals("hearts");
		float[] a = rgb(look.color()), b = rgb(look.color2());
		for (int k = 0; k < p.length; k += hearts ? 4 : 3) {
			Vector3f view = across != null ? new Vector3f(across).normalize() : new Vector3f(p[k]).negate().normalize();
			Vector3f up = new Vector3f(p[Math.max(0, k - 1)]).sub(p[Math.min(p.length - 1, k + 1)]);
			up.sub(new Vector3f(view).mul(up.dot(view)));
			if (up.lengthSquared() < 1e-12F) up.set(side[k]).cross(view);
			up.normalize();
			Vector3f right = new Vector3f(up).cross(view).normalize();
			if (!hearts) { // a square block, the two colours by turns
				float r = width * 0.5F * (1 - u[k] * 0.5F);
				int c = argb((k / 3) % 2 == 0 ? a : b, Math.min(1, lk[k][3] * 1.3F));
				Vector3f o = new Vector3f(p[k]);
				quad(vc, pose, new Vector3f(o).fma(-r, right).fma(-r, up), new Vector3f(o).fma(r, right).fma(-r, up), new Vector3f(o).fma(r, right).fma(r, up),
					new Vector3f(o).fma(-r, right).fma(r, up), c, c, c, c);
				continue;
			}
			// a heart (the classic heart curve, as a fan of thin triangles), beating
			float r = width * 0.85F * (0.85F + 0.15F * (float) Math.sin(time * 9 + k)) * (1 - u[k] * 0.5F) / 17F;
			int c = argb(lk[k], Math.min(1, lk[k][3] * 1.3F));
			Vector3f centre = new Vector3f(p[k]).fma(-2 * r, up), prev = null;
			for (int i = 0; i <= HEART; i++) {
				double t = 2 * Math.PI * i / HEART;
				float x = (float) (16 * Math.pow(Math.sin(t), 3)), y = (float) (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
				Vector3f at = new Vector3f(p[k]).fma(x * r, right).fma(y * r, up);
				if (prev != null) quad(vc, pose, centre, prev, at, at, c, c, c, c);
				prev = at;
			}
		}
	}

	/** How many slices a heart is drawn with. */
	private static final int HEART = 14;

	/** The band between points p0 and p1 (sides s0, s1, half widths h0, h1) from fraction from to fraction to, coloured c0 to c1. */
	private static void piece(VertexConsumer vc, PoseStack.Pose pose, Vector3f p0, Vector3f p1, Vector3f s0, Vector3f s1, float h0, float h1, float from, float to,
		int c0, int c1) {
		Vector3f a = new Vector3f(p0).lerp(p1, from), b = new Vector3f(p0).lerp(p1, to);
		Vector3f sa = new Vector3f(s0).lerp(s1, from), sb = new Vector3f(s0).lerp(s1, to);
		if (sa.lengthSquared() > 1e-8F) sa.normalize();
		if (sb.lengthSquared() > 1e-8F) sb.normalize();
		float ha = lerp(h0, h1, from), hb = lerp(h0, h1, to);
		quad(vc, pose, new Vector3f(a).fma(-ha, sa), new Vector3f(a).fma(ha, sa), new Vector3f(b).fma(hb, sb), new Vector3f(b).fma(-hb, sb), c0, c0, c1, c1);
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
	}

	/**
	 * The colour (r, g, b 0..1), opacity and width factor at u along a trail (0 at the hand, 1 at its end), at time
	 * (seconds): glider_video.look_at, line for line.
	 */
	static float[] look(String style, int c1, int c2, float u, float time) {
		float fade = (float) Math.pow(1 - u, 1.4), w = 1 - u * 0.85F;
		float[] a = rgb(c1), b = rgb(c2), c;
		switch (style) {
			case "rainbow" -> c = hsv(((u * 1.2F - time * 0.6F) % 1F + 1F) % 1F, 0.75F, 1F);
			case "bands" -> c = Math.floorMod((int) Math.floor(u * STRIPES - time * STRIPE_SPEED), 2) == 0 ? a : b; // (band() cuts at the edges)
			case "flame" -> {
				w *= 0.75F + 0.25F * (float) (Math.sin(u * 37 + time * 23) * Math.sin(u * 13 - time * 17));
				c = mix(a, b, Math.min(1, u * 1.6F));
			}
			case "wave" -> {
				c = mix(a, b, u);
				fade *= 0.75F;
			}
			case "sparkle" -> {
				c = mix(a, b, u);
				fade *= 0.45F;
			}
			case "ribbon" -> {
				c = a; // (ribbon() colours each face)
				fade *= 0.9F;
			}
			default -> c = mix(a, b, u);
		}
		return new float[] { c[0], c[1], c[2], fade, w };
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int ca, int cb, int cc, int cd) {
		// both windings: seen from either side
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

	private static int argb(float[] c, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return a << 24 | Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
	}

	private static float[] rgb(int c) {
		return new float[] { (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F };
	}

	private static float[] mix(float[] a, float[] b, float f) {
		return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
	}

	private static float[] hsv(float h, float s, float v) {
		int i = (int) (h * 6) % 6;
		float f = h * 6 - (int) (h * 6), p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		return switch (i) {
			case 0 -> new float[] { v, t, p };
			case 1 -> new float[] { q, v, p };
			case 2 -> new float[] { p, v, t };
			case 3 -> new float[] { p, q, v };
			case 4 -> new float[] { t, p, v };
			default -> new float[] { v, p, q };
		};
	}
}
