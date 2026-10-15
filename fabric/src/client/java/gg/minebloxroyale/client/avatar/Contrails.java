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
 * as everyone in Fortnite has a contrail.
 */
public final class Contrails {
	private static final float S = RobloxAvatarRenderer.S;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** A look: its width at the hand (studs) and how long a point of it lasts (seconds). */
	private record Style(float width, float life) {
	}

	private static final Map<String, Style> STYLES = Map.of("streak", new Style(0.34F, 0.6F), "rainbow", new Style(0.38F, 0.7F), "bands",
		new Style(0.36F, 0.6F), "neon", new Style(0.16F, 0.55F), "sparkle", new Style(0.24F, 0.75F), "flame", new Style(0.42F, 0.45F), "wave",
		new Style(0.4F, 0.8F), "dots", new Style(0.34F, 0.7F));
	private static final Style STREAK = STYLES.get("streak");

	/** One avatar's trails: two from the hands, two from the wingtips (each world points {x, y, z, seconds}, newest last). */
	private static final class Trails {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final ArrayDeque<double[]>[] pts = new ArrayDeque[] { new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>() };
		CosmeticDef look;
		double seen;
	}

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
			double[] last = q.peekLast();
			// (a new point every 30th of a second or so, or sooner when it moved: smooth at any frame rate)
			if (last != null && time - last[3] < 1 / 60.0 && (x - last[0]) * (x - last[0]) + (y - last[1]) * (y - last[1]) + (z - last[2]) * (z - last[2]) < 0.0025) continue;
			q.addLast(new double[] { x, y, z, time });
			if (q.size() > 160) q.removeFirst();
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
			boolean any = false;
			for (ArrayDeque<double[]> q : t.pts) {
				while (!q.isEmpty() && time - q.peekFirst()[3] > st.life()) q.removeFirst();
				any |= !q.isEmpty();
			}
			if (!any && time - t.seen > 2) {
				it.remove();
				continue;
			}
			for (ArrayDeque<double[]> q : t.pts) {
				if (q.size() < 2) continue;
				// newest first, camera-relative, with how far along the trail each is (0 at the hand, 1 at the end)
				int n = q.size();
				Vector3f[] p = new Vector3f[n];
				float[] u = new float[n];
				Iterator<double[]> d = q.descendingIterator();
				for (int k = 0; k < n; k++) {
					double[] a = d.next();
					p[k] = new Vector3f((float) (a[0] - cam.x), (float) (a[1] - cam.y), (float) (a[2] - cam.z));
					u[k] = (float) Math.min(1, (time - a[3]) / st.life());
				}
				CosmeticDef look = t.look;
				out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()),
					(pose, vc) -> band(vc, pose, p, u, look, st.width() * S, tsec, null));
			}
		}
	}

	/** The Locker's preview: trails rising from the hands (model space, y down) as if falling, swaying a little. */
	static void preview(SubmitNodeCollector collector, PoseStack poseStack, @Nullable Map<String, String> outfit, Vector3f[] hands) {
		CosmeticDef look = of(outfit);
		if (look == null) return;
		Style st = STYLES.getOrDefault(look.shape(), STREAK);
		float tsec = (float) (now() % 1000);
		int n = 28;
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

	/**
	 * One trail as a band (p newest first, u along it): facing the camera (at the origin) or, given, across a fixed
	 * direction; neon a white core in a wide glow, wave weaving side to side, sparkle with bright sparks, dots just
	 * bubbles.
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
		if (!style.equals("dots")) {
			boolean neon = style.equals("neon");
			for (int layer = neon ? 1 : 0; layer >= 0; layer--) {
				float wk = layer == 1 ? 2.6F : 1, ak = layer == 1 ? 0.35F : 1;
				for (int k = 0; k + 1 < n; k++) {
					float h0 = width * lk[k][4] * wk / 2, h1 = width * lk[k + 1][4] * wk / 2;
					int c0 = argb(neon && layer == 0 ? WHITE : lk[k], lk[k][3] * ak), c1 = argb(neon && layer == 0 ? WHITE : lk[k + 1], lk[k + 1][3] * ak);
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
	 * The colour (r, g, b 0..1), opacity and width factor at u along a trail (0 at the hand, 1 at its end), at time
	 * (seconds): glider_video.look_at, line for line.
	 */
	static float[] look(String style, int c1, int c2, float u, float time) {
		float fade = (float) Math.pow(1 - u, 1.4), w = 1 - u * 0.85F;
		float[] a = rgb(c1), b = rgb(c2), c;
		switch (style) {
			case "rainbow" -> c = hsv(((u * 1.2F - time * 0.6F) % 1F + 1F) % 1F, 0.75F, 1F);
			case "bands" -> c = Math.floorMod((int) (u * 9 - time * 3), 2) == 0 ? a : b;
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
