package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.client.BusModel;
import gg.minebloxroyale.client.CarModel;
import gg.minebloxroyale.client.VanModel;
import gg.minebloxroyale.match.CarBlockEntity;
import gg.minebloxroyale.sheets.AvatarPartDef;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;

/**
 * Headless check of every model the mod draws from code, without the game: the cars, the Battle Bus, the reboot van,
 * every hat, hair, face, back bling and shoe item (each alone on the Roblox body, and every hat + hair, hat + face and
 * hair + face pair) and every glider. It runs the real drawing code into a recorder and looks at the quads: broken
 * numbers, and surfaces of different colours on the same plane over each other where someone could see them (they
 * flicker in the game). The render type these use draws both sides of every face, so which way a face is wound
 * doesn't matter; two solids touching back to back are always hidden, and so is a patch whose front looks into a
 * solid.
 *
 * Run (from fabric/, after a build into build-next):
 *   gradlew --init-script <next-build.gradle> --init-script tools/check/printcp.gradle printClientCp -DcpOut=cp.txt
 *   javac -d out -cp "$(cat cp.txt)" tools/check/ModelAudit.java
 *   java -cp "out;$(cat cp.txt)" gg.minebloxroyale.client.avatar.ModelAudit report.txt
 * -Daudit.only=<regex> checks only the models named like that; -Daudit.lines=true adds the source line of each piece.
 * Start java from a scratch folder: the game's logging writes a logs/ folder wherever it runs.
 */
public final class ModelAudit {
	/** Records quads: 4 vertices each, position, colour, normal, and which item drew it. */
	static final class Rec implements VertexConsumer {
		final List<float[]> v = new ArrayList<>(); // x y z nx ny nz
		final List<Integer> col = new ArrayList<>();
		final List<String> tag = new ArrayList<>();
		String current = "";
		float[] cur;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			cur = new float[] { x, y, z, 0, 0, 0 };
			v.add(cur);
			col.add(0xFFFFFFFF);
			tag.add(LINES ? current + "@" + StackWalker.getInstance().walk(f -> f.filter(fr -> fr.getClassName().matches(".*(BackBling|Cosmetics|Gliders|CarModel|BusModel|VanModel)"))
				.findFirst().map(fr -> fr.getClassName().substring(fr.getClassName().lastIndexOf('.') + 1) + ":" + fr.getLineNumber()).orElse("?")) : current);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			col.set(col.size() - 1, a << 24 | r << 16 | g << 8 | b);
			return this;
		}

		@Override
		public VertexConsumer setColor(int c) {
			col.set(col.size() - 1, c);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			cur[3] = x;
			cur[4] = y;
			cur[5] = z;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float w) {
			return this;
		}
	}

	static final RobloxAvatarRenderer.Pose STAND = new RobloxAvatarRenderer.Pose(0, 0, 0, 0, 0, 0, 0, 0);
	static final boolean LINES = Boolean.getBoolean("audit.lines");
	static final String ONLY = System.getProperty("audit.only");
	static final float NEAR = 1e-4F, FAR = 2e-3F; // blocks: flickers even up close / flickers from a distance
	static PrintWriter out;
	static int problems;
	static final Map<String, Integer> summary = new TreeMap<>();

	public static void main(String[] args) throws Exception {
		out = new PrintWriter(args[0], "UTF-8");
		PoseStack.Pose pose = new PoseStack().last();
		int light = 0xF000F0;

		// ---- cars, bus, van: single-sided, culled ----
		for (CarBlockEntity.Kind k : CarBlockEntity.Kind.values())
			for (int stage = 1; stage <= 4; stage++) { // new, scuffed, damaged, nearly wrecked
				String name = "car " + k.name().toLowerCase() + " stage " + stage;
				if (!want(name)) continue;
				Rec r = new Rec();
				CarModel.draw(pose, r, light, k, k == CarBlockEntity.Kind.POLICE ? 0xFF22252B : 0xFFD8352C, 0xFFF2F4F8, stage);
				analyse(name, r, false, false);
			}
		Rec bus = new Rec();
		if (want("battle bus")) {
			BusModel.draw(pose, bus, light);
			analyse("battle bus", bus, false, false);
		}
		for (boolean ready : new boolean[] { true, false }) {
			if (!want("reboot van" + (ready ? "" : " (recharging)"))) continue;
			Rec van = new Rec();
			VanModel.draw(pose, van, light, ready);
			analyse("reboot van" + (ready ? "" : " (recharging)"), van, false, false);
		}

		// ---- avatar cosmetics: double-sided ----
		try {
			gg.minebloxroyale.client.media.GameDirs.robloxContent(); // (finds Roblox, then fails on the game-only ffmpeg lookup)
		} catch (Throwable ignored) {
		}
		try {
			RobloxAssets.ready(); // (loads the R6 body meshes from the Roblox install; the texture part needs the game)
		} catch (Throwable ignored) {
		}
		out.println("Roblox body meshes loaded: " + (RobloxAssets.mesh("head") != null));
		LivingEntityRenderState state = new LivingEntityRenderState();
		Method drawMesh = RobloxAvatarRenderer.class.getDeclaredMethod("drawMesh", VertexConsumer.class, PoseStack.Pose.class, Matrix4f.class, RobloxMesh.class,
			boolean.class, int.class, int.class, int.class);
		drawMesh.setAccessible(true);
		Map<String, List<CosmeticDef>> bySlot = new LinkedHashMap<>();
		for (CosmeticDef c : Sheets.COSMETICS) bySlot.computeIfAbsent(c.slot(), s -> new ArrayList<>()).add(c);
		bySlot.forEach((s, l) -> out.println("slot " + s + ": " + l.size()));
		Consumer<Rec> body = r -> {
			r.current = "body";
			for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
				RobloxMesh m = RobloxAssets.mesh(part.id());
				if (m == null) continue;
				try {
					drawMesh.invoke(null, r, pose, RobloxAvatarRenderer.jointMatrix(part, state, STAND, 0, false), m, false, 0xFFF5CD9B, light, 0);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
		// Each item alone, against the body.
		for (CosmeticDef c : Sheets.COSMETICS) {
			if (c.slot().equals("shirt") || c.slot().equals("pants") || c.slot().equals("particle") || !want(c.slot() + " " + c.id())) continue;
			Rec r = new Rec();
			body.accept(r);
			r.current = c.id();
			Cosmetics.draw(r, pose, state, Map.of(c.slot(), c.id()), null, 0, false, light, 0);
			analyse(c.slot() + " " + c.id(), r, true, false);
		}
		// Hat with hair, hat with face, hair with face: only clashes between the two items count here.
		String[][] pairs = { { "hat", "hair" }, { "hat", "face" }, { "hair", "face" } };
		for (String[] p : pairs)
			for (CosmeticDef a : bySlot.getOrDefault(p[0], List.of()))
				for (CosmeticDef b : bySlot.getOrDefault(p[1], List.of())) {
					if (!want("combo " + a.id() + " + " + b.id())) continue;
					Rec r = new Rec();
					r.current = "combo";
					Cosmetics.draw(r, pose, state, Map.of(p[0], a.id(), p[1], b.id()), null, 0, false, light, 0);
					// Cosmetics.draw draws in slot order (hair, hat, face): tag by colour run is not possible, so draw
					// each alone to learn their vertex counts and tag the combined run by matching.
					Rec ra = new Rec(), rb = new Rec();
					ra.current = a.id();
					rb.current = b.id();
					Cosmetics.draw(ra, pose, state, Map.of(p[0], a.id()), null, 0, false, light, 0);
					Cosmetics.draw(rb, pose, state, Map.of(p[1], b.id()), null, 0, false, light, 0);
					tagCombo(r, ra, rb, a.id(), b.id());
					analyse("combo " + a.id() + " + " + b.id(), r, true, true);
				}

		// ---- gliders ----
		for (GliderDef g : Sheets.GLIDERS) {
			if (!want("glider " + g.id())) continue;
			Rec r = new Rec();
			r.current = g.id();
			Gliders.draw(new Smooth(r, pose, new Matrix4f(), light, 0), g);
			analyse("glider " + g.id(), r, true, false);
		}

		out.println();
		out.println("SUMMARY");
		summary.forEach((k, n) -> out.println("  " + k + ": " + n));
		out.println("models with problems: " + problems);
		out.close();
		System.out.println("done, problems in " + problems + " models");
	}

	/**
	 * Marks which vertices of the combined drawing belong to which item: the combined drawing is the two single
	 * drawings in some order (hair is drawn before the hat, and both before the face), unless the hat changes how hair
	 * is drawn (hats can hide hair or trim its crown), in which case the vertices are tagged by position match.
	 */
	static void tagCombo(Rec r, Rec ra, Rec rb, String a, String b) {
		Map<String, String> where = new HashMap<>();
		for (int i = 0; i < ra.v.size(); i++) where.put(key(ra.v.get(i), ra.col.get(i)), ra.tag.get(i));
		for (int i = 0; i < rb.v.size(); i++) where.putIfAbsent(key(rb.v.get(i), rb.col.get(i)), rb.tag.get(i));
		for (int i = 0; i < r.v.size(); i++) r.tag.set(i, where.getOrDefault(key(r.v.get(i), r.col.get(i)), "changed"));
	}

	static String key(float[] p, int c) {
		return Math.round(p[0] * 1e4) + "," + Math.round(p[1] * 1e4) + "," + Math.round(p[2] * 1e4) + "," + c;
	}

	// ---- analysis ----

	record Tri(float[] a, float[] b, float[] c, float[] n, float d, int color, String tag, float area, float[] out) {
	}

	static void analyse(String name, Rec r, boolean doubleSided, boolean onlyBetweenTags) {
		if (ONLY != null && !name.matches(ONLY)) return;
		int nq = r.v.size() / 4, bad = 0, backwards = 0, degenerate = 0;
		List<Tri> tris = new ArrayList<>();
		for (int q = 0; q < nq; q++) {
			// Smooth draws every quad twice (front, then back reversed): look at the front ones only.
			if (doubleSided && !item(r.tag.get(q * 4)).equals("body") && isBackOf(r, q)) continue;
			float[][] p = new float[4][];
			boolean finite = true;
			for (int k = 0; k < 4; k++) {
				p[k] = r.v.get(q * 4 + k);
				for (float f : p[k]) finite &= Float.isFinite(f);
			}
			if (!finite) {
				bad++;
				continue;
			}
			float[] g = add(cross(sub(p[1], p[0]), sub(p[2], p[0])), cross(sub(p[2], p[0]), sub(p[3], p[0])));
			float area = len(g) / 2;
			if (area < 1e-9F) {
				degenerate++;
				continue;
			}
			float[] n = { (p[0][3] + p[1][3] + p[2][3] + p[3][3]), (p[0][4] + p[1][4] + p[2][4] + p[3][4]), (p[0][5] + p[1][5] + p[2][5] + p[3][5]) };
			if (!doubleSided && dot(g, n) < 0) backwards++;
			int c = r.col.get(q * 4);
			String t = r.tag.get(q * 4);
			float ln = len(n);
			float[] out = ln > 1e-9F ? new float[] { n[0] / ln, n[1] / ln, n[2] / ln } : new float[3];
			addTri(tris, p[0], p[1], p[2], c, t, out);
			addTri(tris, p[0], p[2], p[3], c, t, out);
		}
		// Overlapping surfaces of different colours (and, for single-sided models, facing the same way).
		float cell = 0.06F;
		Map<Long, List<Integer>> grid = new HashMap<>();
		for (int i = 0; i < tris.size(); i++) {
			Tri t = tris.get(i);
			int[] lo = new int[3], hi = new int[3];
			for (int k = 0; k < 3; k++) {
				float mn = Math.min(t.a[k], Math.min(t.b[k], t.c[k])), mx = Math.max(t.a[k], Math.max(t.b[k], t.c[k]));
				lo[k] = (int) Math.floor((mn - FAR) / cell);
				hi[k] = (int) Math.floor((mx + FAR) / cell);
			}
			if ((long) (hi[0] - lo[0] + 1) * (hi[1] - lo[1] + 1) * (hi[2] - lo[2] + 1) > 4000) continue; // (huge: skip)
			for (int x = lo[0]; x <= hi[0]; x++)
				for (int y = lo[1]; y <= hi[1]; y++)
					for (int z = lo[2]; z <= hi[2]; z++) grid.computeIfAbsent(((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L), k -> new ArrayList<>()).add(i);
		}
		Map<String, float[]> clashes = new TreeMap<>(); // "colour a / colour b" -> area, min distance, where
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (List<Integer> l : grid.values())
			for (int ii = 0; ii < l.size(); ii++)
				for (int jj = ii + 1; jj < l.size(); jj++) {
					int i = Math.min(l.get(ii), l.get(jj)), j = Math.max(l.get(ii), l.get(jj));
					Tri a = tris.get(i), b = tris.get(j);
					if (a.color == b.color) continue;
					if (onlyBetweenTags && (item(a.tag).equals(item(b.tag)) || a.tag.equals("changed") || b.tag.equals("changed"))) continue;
					if (Math.abs(dot(a.n, b.n)) < 0.9995F) continue;
					// Closed solids (car, bus, van): two faces back to back (one solid resting on another) are always
					// hidden; only faces looking the same way at the same depth can show a flicker.
					// (Smooth's pieces are closed shapes too, drawn from both sides: the same holds for them.)
					if (dot(a.out, b.out) < 0.5F) continue;
					float dist = Math.max(Math.abs(dot(a.n, b.a) - a.d), Math.max(Math.abs(dot(a.n, b.b) - a.d), Math.abs(dot(a.n, b.c) - a.d)));
					if (dist > FAR) continue;
					if (!seen.add((long) i * tris.size() + j)) continue;
					float[] oc = overlapAt(a, b);
					float ov = oc[0];
					if (ov < 1e-6F) continue;
					// Only if someone can see it: the space in front of the patch must be open air, not the inside of a
					// solid (two undersides resting on the same plate line up exactly, but nobody can look at them).
					if (!open(tris, new float[] { oc[1], oc[2], oc[3] }, a.out)) continue;
					String key = (a.tag.equals(b.tag) ? a.tag : a.tag + " / " + b.tag) + "  " + hex(a.color) + " vs " + hex(b.color);
					float[] s = clashes.computeIfAbsent(key, k -> new float[] { 0, 1, 0, 0, 0 });
					s[0] += ov;
					if (dist < s[1]) {
						s[1] = dist;
						s[2] = (a.a[0] + a.b[0] + a.c[0]) / 3;
						s[3] = (a.a[1] + a.b[1] + a.c[1]) / 3;
						s[4] = (a.a[2] + a.b[2] + a.c[2]) / 3;
					}
				}
		// Only overlaps of a meaningful size count (a sliver along a seam is not a visible flicker).
		clashes.values().removeIf(s -> s[0] < 2e-5F);
		boolean any = bad > 0 || !clashes.isEmpty();
		summary.merge(any ? "with problems" : "clean", 1, Integer::sum);
		if (!any) {
			if (!name.startsWith("combo")) out.printf("ok   %s: %d quads%s%n", name, nq, doubleSided ? "" : " (" + backwards + " wound the other way: harmless, both sides are drawn)");
			return;
		}
		problems++;
		out.printf("FAIL %s: %d quads, %d with broken numbers, %d wound backwards, %d degenerate%n", name, nq, bad, backwards, degenerate);
		clashes.forEach((k, s) -> out.printf("       overlap %s: %.5f sq blocks, %.5f apart (%s), near (%.3f, %.3f, %.3f)%n", k, s[0], s[1],
			s[1] < NEAR ? "flickers up close" : "flickers from a distance", s[2], s[3], s[4]));
	}

	static boolean want(String name) {
		return ONLY == null || name.matches(ONLY);
	}

	static String item(String tag) {
		int i = tag.indexOf('@');
		return i < 0 ? tag : tag.substring(0, i);
	}

	static boolean isBackOf(Rec r, int q) {
		if (q == 0) return false;
		for (int k = 0; k < 4; k++) {
			float[] a = r.v.get(q * 4 + k), b = r.v.get((q - 1) * 4 + 3 - k);
			for (int m = 0; m < 3; m++)
				if (a[m] != b[m]) return false;
		}
		return true;
	}

	static void addTri(List<Tri> out, float[] a, float[] b, float[] c, int color, String tag, float[] outward) {
		float[] n = cross(sub(b, a), sub(c, a));
		float l = len(n);
		if (l < 1e-10F) return;
		n = new float[] { n[0] / l, n[1] / l, n[2] / l };
		out.add(new Tri(a, b, c, n, dot(n, a), color, tag, l / 2, outward));
	}

	/**
	 * Is the space just in front of point p (looking along n) open? Casts a ray along n: if the first surface it meets
	 * faces the same way as the ray, the ray started inside a solid; if it meets a surface very close, there is no room
	 * to look in.
	 */
	static boolean open(List<Tri> tris, float[] p, float[] n) {
		float best = Float.MAX_VALUE;
		Tri hit = null;
		for (Tri t : tris) {
			float d = ray(p, n, t);
			if (d > 1e-4F && d < best) {
				best = d;
				hit = t;
			}
		}
		if (hit == null) return true;
		if (dot(hit.out, n) > 0) return false; // inside a solid
		return best > 0.004F; // a surface right in front: no room to see it
	}

	/** Distance along the ray (p, dir) to triangle t, or -1. */
	static float ray(float[] p, float[] dir, Tri t) {
		float[] e1 = sub(t.b, t.a), e2 = sub(t.c, t.a), h = cross(dir, e2);
		float det = dot(e1, h);
		if (Math.abs(det) < 1e-12F) return -1;
		float f = 1 / det;
		float[] s = sub(p, t.a);
		float u = f * dot(s, h);
		if (u < 0 || u > 1) return -1;
		float[] q = cross(s, e1);
		float v = f * dot(dir, q);
		if (v < 0 || u + v > 1) return -1;
		return f * dot(e2, q);
	}

	/** Area where two (nearly) coplanar triangles overlap, and its middle: { area, x, y, z }. */
	static float[] overlapAt(Tri a, Tri b) {
		float[] u = sub(a.b, a.a);
		float lu = len(u);
		u = new float[] { u[0] / lu, u[1] / lu, u[2] / lu };
		float[] w = cross(a.n, u);
		List<float[]> poly = clip(a, b, u, w);
		if (poly.size() < 3) return new float[4];
		float cx = 0, cy = 0;
		for (float[] q : poly) {
			cx += q[0] / poly.size();
			cy += q[1] / poly.size();
		}
		return new float[] { Math.abs(signedArea(poly)), u[0] * cx + w[0] * cy + a.n[0] * a.d, u[1] * cx + w[1] * cy + a.n[1] * a.d, u[2] * cx + w[2] * cy + a.n[2] * a.d };
	}

	static List<float[]> clip(Tri a, Tri b, float[] u, float[] w) {
		List<float[]> pa = List.of(proj(a.a, u, w), proj(a.b, u, w), proj(a.c, u, w));
		List<float[]> pb = List.of(proj(b.a, u, w), proj(b.b, u, w), proj(b.c, u, w));
		List<float[]> poly = new ArrayList<>(pb);
		if (signedArea(pa) < 0) pa = List.of(pa.get(0), pa.get(2), pa.get(1));
		for (int i = 0; i < 3 && !poly.isEmpty(); i++) {
			float[] e0 = pa.get(i), e1 = pa.get((i + 1) % 3);
			List<float[]> next = new ArrayList<>();
			for (int k = 0; k < poly.size(); k++) {
				float[] p = poly.get(k), q = poly.get((k + 1) % poly.size());
				float sp = side(e0, e1, p), sq = side(e0, e1, q);
				if (sp >= 0) next.add(p);
				if (sp >= 0 != sq >= 0) {
					float t = sp / (sp - sq);
					next.add(new float[] { p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t });
				}
			}
			poly = next;
		}
		return poly;
	}

	static float side(float[] a, float[] b, float[] p) {
		return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
	}

	static float signedArea(List<float[]> p) {
		float s = 0;
		for (int i = 0; i < p.size(); i++) {
			float[] a = p.get(i), b = p.get((i + 1) % p.size());
			s += a[0] * b[1] - a[1] * b[0];
		}
		return s / 2;
	}

	static float[] proj(float[] p, float[] u, float[] w) {
		return new float[] { dot(p, u), dot(p, w) };
	}

	static String hex(int c) {
		return String.format("#%06X", c & 0xFFFFFF);
	}

	static float[] sub(float[] a, float[] b) {
		return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
	}

	static float[] add(float[] a, float[] b) {
		return new float[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
	}

	static float[] cross(float[] a, float[] b) {
		return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}

	static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	static float len(float[] a) {
		return (float) Math.sqrt(dot(a, a));
	}
}
