package gg.minebloxroyale.client.avatar;

import gg.minebloxroyale.sheets.CosmeticDef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Vector3f;

/**
 * Back bling (creator: polished, with more detail), drawn in the torso's joint space (studs, y up, front -Z; e = the
 * torso's extents). Feathered wings with an arm, flight feathers and coverts (bat wings for Vespertilio, painted
 * butterfly wings); capes with folds, a lining, trim, a collar and brooches (ermine, a star, a sun, a crown or icicles
 * on some); backpacks with a padded body, pockets, zips, straps with buckles and a chest strap (a police badge, a red
 * cross, camo and a bedroll...); a sheathed sword and katana, a Viking and a knight's shield, a jetpack with flames,
 * tanks with valves and hoses, a robot battery, a guitar with pickups, frets and strings, a skateboard with trucks and
 * wheels, a money bag, a violin case, a lasso, a wizard's staff, a pitchfork, balloons on curly strings and a frying
 * pan (with an egg). Most hang from a strap across the chest.
 */
final class BackBling {
	private static final int GOLD = 0xF2C230, METAL = 0x9AA2AD, DARK = 0x2A2A30, LEATHER = 0x5A3A22, WHITE = 0xF4F6FA, ROPE = 0xC9A66B;

	private BackBling() {
	}

	/** The torso: centre x / y, back and front faces (z), top (y), half width. */
	private record T(float cx, float cy, float bk, float fr, float top, float hw) {
	}

	/** One back accessory on a torso with extents e (minX, minY, minZ, maxX, maxY, maxZ). */
	static void draw(Smooth s, CosmeticDef c, float[] e) {
		T t = new T((e[0] + e[3]) / 2, (e[1] + e[4]) / 2, e[5], e[2], e[4], (e[3] - e[0]) / 2);
		int a = c.color(), b = c.color2();
		String id = c.id();
		switch (c.shape()) {
			case "wings" -> {
				if (id.contains("butterfly")) butterfly(s, t, a, b);
				else if (id.contains("vespertilio")) batWings(s, t, a, b);
				else featherWings(s, t, id, a, b);
			}
			case "cape" -> cape(s, t, id, a, b);
			case "backpack" -> backpack(s, t, id, a, b);
			case "sword" -> sword(s, t, a, b);
			case "katana" -> katana(s, t, a, b);
			case "round_shield" -> roundShield(s, t, a, b);
			case "kite_shield" -> kiteShield(s, t, a, b);
			case "jetpack" -> jetpack(s, t, a, b);
			case "tank" -> {
				if (id.contains("battery")) battery(s, t, a, b);
				else tank(s, t, id, a, b);
			}
			case "guitar" -> guitar(s, t, a, b);
			case "skateboard" -> skateboard(s, t, a, b);
			case "moneybag" -> moneyBag(s, t, a, b);
			case "violin" -> violinCase(s, t, a, b);
			case "lasso" -> lasso(s, t, a, b);
			case "staff" -> staff(s, t, a, b);
			case "pitchfork" -> pitchfork(s, t, a, b);
			case "balloons" -> balloons(s, t, a, b);
			case "pan" -> pan(s, t, a, b);
			default -> {
			}
		}
	}

	// ---- wings ----

	/**
	 * Angel, Shadow, Empyreus and Infernus wings: an arm from the shoulder blades, 12 flight feathers, coverts and
	 * small shoulder feathers.
	 */
	private static void featherWings(Smooth s, T t, String id, int a, int b) {
		boolean infernus = id.contains("infernus");
		int arm = id.contains("empyreus") ? b : shade(a, 0.9F), tip = id.contains("dark") || infernus ? b : -1, covert = infernus ? shade(a, 1.6F) : b;
		for (float side : new float[] { -1, 1 }) {
			float[][] bone = { { t.cx + side * 0.22F, t.cy + 0.45F, t.bk + 0.1F }, { t.cx + side * 0.85F, t.cy + 1.05F, t.bk + 0.3F },
				{ t.cx + side * 1.65F, t.cy + 1.3F, t.bk + 0.45F }, { t.cx + side * 2.25F, t.cy + 1.0F, t.bk + 0.52F } };
			s.at(0, 0, 0).color(arm).tube(bone, new float[] { 0.08F, 0.075F, 0.06F, 0.035F });
			// Flight feathers hanging from the arm, fanning out and longer towards the tip (inner ones on top).
			for (int i = 0; i < 12; i++) {
				float f = i / 11F;
				float[] p = along(bone, 0.04F + 0.96F * f);
				feather(s, p[0], p[1], p[2] + 0.012F + 0.008F * (11 - i), side * (6 + 52 * (float) Math.pow(f, 1.3)), 0.7F + 0.85F * f, 0.12F + 0.035F * f,
					i % 2 == 0 ? a : shade(a, 0.93F), tip);
			}
			// Coverts over their roots, and small feathers at the shoulder.
			for (int i = 0; i < 9; i++) {
				float f = i / 8F;
				float[] p = along(bone, 0.02F + 0.86F * f);
				feather(s, p[0], p[1] - 0.04F, p[2] + 0.11F + 0.006F * (8 - i), side * (4 + 42 * f), 0.42F + 0.22F * f, 0.12F, i % 2 == 0 ? covert : shade(covert, 0.92F), -1);
			}
			for (int i = 0; i < 4; i++) {
				float[] p = along(bone, 0.05F + 0.12F * i);
				feather(s, p[0], p[1] + 0.03F, p[2] + 0.17F + 0.005F * (3 - i), side * (2 + 10 * i), 0.26F, 0.1F, shade(covert, 1.08F), -1);
			}
			if (infernus) for (int k = 0; k < 4; k++) { // embers drifting off the burning tips
				float[] p = along(bone, 0.55F + 0.13F * k);
				s.at(p[0] + side * (0.35F + 0.15F * k), p[1] - 1.35F - 0.12F * (k % 2), p[2] + 0.1F).color(k % 2 == 0 ? 0xFFD23A : 0xFF6A1F).blob(0.04F, 0.04F, 0.04F);
			}
		}
	}

	/**
	 * A feather hanging from (x, y, z) flat against the back, turned ang degrees out from straight down; an optional
	 * coloured tip.
	 */
	private static void feather(Smooth s, float x, float y, float z, float ang, float len, float w, int color, int tip) {
		double r = Math.toRadians(ang);
		float dx = (float) Math.sin(r), dy = (float) -Math.cos(r);
		s.at(x + dx * len / 2, y + dy * len / 2, z).rotZ(ang).rotX(90).color(color).lens(w, len / 2, 0.025F);
		if (tip >= 0) s.at(x + dx * len * 0.84F, y + dy * len * 0.84F, z + 0.004F).rotZ(ang).rotX(90).color(tip).lens(w * 0.8F, len * 0.17F, 0.027F);
	}

	/**
	 * Butterfly wings: an upper and a lower wing each side with a border, veins and spots, and a body with antennae.
	 */
	private static void butterfly(Smooth s, T t, int a, int b) {
		float z = t.bk + 0.16F;
		for (float side : new float[] { -1, 1 }) {
			wingLobe(s, t.cx + side * 0.78F, t.cy + 0.62F, z, side * -28, 0.72F, 0.5F, a, b);
			wingLobe(s, t.cx + side * 0.6F, t.cy - 0.12F, z, side * 32, 0.48F, 0.36F, shade(a, 0.94F), b);
			for (float[] v : new float[][] { { 1.3F, 1.05F }, { 1.38F, 0.5F }, { 0.95F, -0.45F } })
				s.at(0, 0, 0).color(shade(b, 0.8F)).tube(new float[][] { { t.cx + side * 0.12F, t.cy + 0.3F, z + 0.035F },
					{ t.cx + side * v[0] * 0.55F, t.cy + 0.3F + (v[1] - 0.3F) * 0.5F, z + 0.04F }, { t.cx + side * v[0], t.cy + v[1], z + 0.035F } },
					new float[] { 0.018F, 0.014F, 0.008F }, false, 6);
			for (float[] d : new float[][] { { 1.05F, 0.85F, 0.13F }, { 0.68F, 0.98F, 0.08F }, { 0.75F, -0.22F, 0.1F } }) {
				s.at(t.cx + side * d[0], t.cy + d[1], z + 0.042F).rotX(90).color(b).lens(d[2], d[2], 0.02F);
				s.at(t.cx + side * d[0], t.cy + d[1], z + 0.052F).rotX(90).color(WHITE).lens(d[2] * 0.45F, d[2] * 0.45F, 0.02F);
			}
		}
		s.at(t.cx, t.cy + 0.3F, z + 0.06F).color(0x2A2230).ellipsoid(0.08F, 0.55F, 0.08F);
		for (float side : new float[] { -1, 1 }) {
			s.at(0, 0, 0).color(0x2A2230).tube(new float[][] { { t.cx + side * 0.03F, t.cy + 0.82F, z + 0.06F }, { t.cx + side * 0.15F, t.cy + 1.15F, z + 0.1F },
				{ t.cx + side * 0.3F, t.cy + 1.3F, z + 0.08F } }, new float[] { 0.02F, 0.015F, 0.012F });
			s.at(t.cx + side * 0.3F, t.cy + 1.3F, z + 0.08F).color(b).blob(0.035F, 0.035F, 0.035F);
		}
	}

	/**
	 * One butterfly wing: a flat lobe at (x, y, z) turned ang degrees, rx by ry, with a slightly bigger border behind
	 * it.
	 */
	private static void wingLobe(Smooth s, float x, float y, float z, float ang, float rx, float ry, int color, int border) {
		s.at(x, y, z - 0.014F).rotZ(ang).rotX(90).color(border).scale(rx * 1.08F, 1, ry * 1.1F).lathe(new float[] { 0, 1, 0 }, new float[] { -0.02F, 0, 0.02F }, 24);
		s.at(x, y, z).rotZ(ang).rotX(90).color(color).scale(rx, 1, ry).lathe(new float[] { 0, 1, 0 }, new float[] { -0.03F, 0, 0.03F }, 24);
	}

	/** Vespertilio: bat wings - an arm to the wrist, three finger bones, scalloped membranes between them, a claw. */
	private static void batWings(Smooth s, T t, int a, int b) {
		int skin = shade(b, 0.55F);
		for (float side : new float[] { -1, 1 }) {
			float[] root = { t.cx + side * 0.25F, t.cy + 0.55F, t.bk + 0.1F }, bottom = { t.cx + side * 0.3F, t.cy - 0.55F, t.bk + 0.12F },
				wrist = { t.cx + side * 1.25F, t.cy + 1.35F, t.bk + 0.38F };
			float[][] tips = { { t.cx + side * 2.45F, t.cy + 1.15F, t.bk + 0.55F }, { t.cx + side * 2.3F, t.cy + 0.2F, t.bk + 0.5F }, { t.cx + side * 1.6F, t.cy - 0.5F, t.bk + 0.4F } };
			for (int k = 0; k < 3; k++) membrane(s, wrist, tips[k], k < 2 ? wrist : root, k < 2 ? tips[k + 1] : bottom, wrist, skin);
			s.at(0, 0, 0).color(a).tube(new float[][] { root, { t.cx + side * 0.75F, t.cy + 1.1F, t.bk + 0.25F }, wrist }, new float[] { 0.07F, 0.065F, 0.06F });
			for (float[] p : tips)
				s.at(0, 0, 0).color(a).tube(new float[][] { wrist, { (wrist[0] + p[0]) / 2, (wrist[1] + p[1]) / 2 + 0.06F, (wrist[2] + p[2]) / 2 + 0.02F }, p },
					new float[] { 0.045F, 0.035F, 0.012F });
			s.at(wrist[0], wrist[1] + 0.05F, wrist[2]).rotZ(-side * 20).color(b).cone(0.04F, 0.16F, 8);
		}
	}

	/** A bat-wing membrane between the edges p0-p1 and q0-q1, its far edge pulled in towards 'in' between the bones. */
	private static void membrane(Smooth s, float[] p0, float[] p1, float[] q0, float[] q1, float[] in, int color) {
		s.at(0, 0, 0).color(color).surface((u, v, o) -> {
			float x = lerp(lerp(p0[0], p1[0], u), lerp(q0[0], q1[0], u), v), y = lerp(lerp(p0[1], p1[1], u), lerp(q0[1], q1[1], u), v),
				z = lerp(lerp(p0[2], p1[2], u), lerp(q0[2], q1[2], u), v), pull = 0.28F * (float) Math.sin(Math.PI * v) * u * u;
			o.set(x + (in[0] - x) * pull, y + (in[1] - y) * pull, z + (in[2] - z) * pull);
		}, 8, 6);
	}

	// ---- capes ----

	/**
	 * A cape: folds that deepen towards the hem, a darker lining, trim down the sides and round the hem, a collar over
	 * the shoulders with a brooch each side of the chest and a chain between. King's and Queen's capes have ermine; the
	 * Hero Cape a star, Frigidus a snowflake and icicles, Aureus a sun, Rex and the King's Cape a crown.
	 */
	private static void cape(Smooth s, T t, String id, int a, int b) {
		boolean ermine = id.contains("royal") || id.contains("queen");
		Smooth.Surface cloth = (u, v, o) -> {
			float x = (u - 0.5F) * 2 * (t.hw * 0.98F + 0.32F * v), arch = 1 - (2 * u - 1) * (2 * u - 1);
			float fold = 0.06F * v * (float) Math.sin(u * Math.PI * 7);
			o.set(t.cx + x, t.top - 0.06F - v * 2.85F, t.bk + 0.07F + 0.3F * v + 0.1F * arch * (0.3F + v) + fold);
		};
		s.at(0, 0, 0).color(a).surface(cloth, 21, 14);
		s.at(0, 0, -0.02F).color(shade(a, 0.68F)).surface(cloth, 14, 10);
		int trim = ermine ? WHITE : b;
		float r = ermine ? 0.085F : 0.04F;
		List<float[]> edge = new ArrayList<>();
		edge.addAll(line(s, cloth, false, 0, r, trim));
		edge.addAll(line(s, cloth, true, 1, r * 1.15F, trim));
		edge.addAll(line(s, cloth, false, 1, r, trim));
		if (!ermine && !id.contains("frigidus")) line(s, cloth, true, 0.92F, 0.018F, trim); // a thin second line inside the hem
		float y = t.top, cr = ermine ? 0.11F : 0.065F;
		float[][] collar = { { t.cx - 0.55F, y - 0.15F, t.fr - 0.07F }, { t.cx - 0.74F, y + 0.04F, t.fr + 0.25F }, { t.cx - 0.86F, y + 0.04F, t.bk - 0.05F },
			{ t.cx - 0.45F, y + 0.01F, t.bk + 0.08F }, { t.cx, y + 0.02F, t.bk + 0.1F }, { t.cx + 0.45F, y + 0.01F, t.bk + 0.08F }, { t.cx + 0.86F, y + 0.04F, t.bk - 0.05F },
			{ t.cx + 0.74F, y + 0.04F, t.fr + 0.25F }, { t.cx + 0.55F, y - 0.15F, t.fr - 0.07F } };
		s.at(0, 0, 0).color(trim).tube(collar, fill(collar.length, cr));
		if (ermine) {
			// Ermine: black tufts dotted along the white fur.
			for (int i = 0; i < edge.size(); i += 2) {
				float[] p = edge.get(i);
				s.at(p[0], p[1], p[2] + r * 0.95F).rotX(90).color(0x16161A).lens(0.025F, 0.045F, 0.012F);
			}
			for (int i = 2; i < collar.length - 2; i++) s.at(collar[i][0], collar[i][1] + 0.02F, collar[i][2] + cr * 0.95F).rotX(90).color(0x16161A).lens(0.025F, 0.045F, 0.012F);
		}
		for (float side : new float[] { -1, 1 }) {
			s.at(t.cx + side * 0.55F, y - 0.17F, t.fr - 0.06F).rotX(-90).color(GOLD).lathe(new float[] { 0, 0.11F, 0.12F, 0.09F, 0 }, new float[] { 0, 0, 0.012F, 0.03F, 0.03F }, 16);
			s.at(t.cx + side * 0.55F, y - 0.17F, t.fr - 0.09F).color(ermine || id.contains("hero") ? 0xD7263D : b).blob(0.05F, 0.05F, 0.02F);
		}
		s.at(0, 0, 0).color(GOLD).tube(new float[][] { { t.cx - 0.45F, y - 0.2F, t.fr - 0.08F }, { t.cx, y - 0.34F, t.fr - 0.06F }, { t.cx + 0.45F, y - 0.2F, t.fr - 0.08F } },
			new float[] { 0.016F, 0.016F, 0.016F }, false, 6);
		// The emblem on the upper back.
		Vector3f p = new Vector3f();
		cloth.at(0.5F, 0.3F, p);
		float ex = p.x, ey = p.y, ez = p.z + 0.035F;
		if (id.contains("hero")) {
			s.at(ex, ey, ez - 0.01F).rotX(90).color(GOLD).torus(0.36F, 0.035F, 0, 360);
			star(s, ex, ey, ez, 0.32F, GOLD);
		} else if (id.contains("frigidus")) {
			for (int k = 0; k < 3; k++) s.at(ex, ey, ez).rotZ(k * 60).rotX(90).color(b).lens(0.04F, 0.34F, 0.02F);
			for (int k = 0; k < 6; k++) {
				double ang = Math.toRadians(k * 60 + 90);
				s.at(ex + (float) Math.cos(ang) * 0.24F, ey + (float) Math.sin(ang) * 0.24F, ez + 0.01F).rotZ(k * 60).rotX(90).color(b).lens(0.1F, 0.035F, 0.02F);
			}
			// Icicles along the hem.
			for (int i = 0; i < 12; i++) {
				cloth.at((i + 0.5F) / 12, 1, p);
				s.at(p.x, p.y - 0.03F, p.z).rotX(180).color(i % 2 == 0 ? 0xE6F7FF : b).cone(0.045F, 0.12F + 0.08F * (i % 3), 8);
			}
		} else if (id.contains("aureus")) {
			s.at(ex, ey, ez).rotX(90).color(b).lens(0.16F, 0.16F, 0.03F);
			for (int k = 0; k < 12; k++) s.at(ex, ey, ez).rotZ(k * 30).move(0, 0.27F, 0).rotX(90).color(k % 2 == 0 ? b : GOLD).lens(0.035F, 0.09F, 0.02F);
		} else if (id.contains("rex") || id.contains("royal")) {
			crown(s, ex, ey, ez, 1.6F, id.contains("rex") ? b : GOLD, id.contains("rex") ? GOLD : 0xD7263D);
		} else if (id.contains("queen")) {
			// A fleur-de-lis.
			s.at(ex, ey + 0.13F, ez).rotX(90).color(GOLD).lens(0.11F, 0.32F, 0.025F);
			for (float side : new float[] { -1, 1 }) s.at(ex + side * 0.19F, ey + 0.03F, ez).rotZ(side * 40).rotX(90).color(GOLD).lens(0.085F, 0.24F, 0.022F);
			s.at(ex, ey - 0.13F, ez + 0.01F).rotX(90).color(GOLD).lens(0.27F, 0.055F, 0.025F);
			s.at(ex, ey - 0.27F, ez).rotX(90).color(GOLD).lens(0.07F, 0.12F, 0.022F);
		} else if (id.contains("purple")) {
			for (float[] q : new float[][] { { -0.25F, 0.08F }, { 0.25F, 0.08F }, { 0, -0.18F } }) star(s, ex + q[0], ey + q[1], ez, 0.12F, b);
		}
	}

	/** A tube along the cloth: at v = fixed across it (alongU) or at u = fixed down it; its points. */
	private static List<float[]> line(Smooth s, Smooth.Surface f, boolean alongU, float fixed, float r, int color) {
		float[][] pts = new float[13][];
		Vector3f p = new Vector3f();
		for (int i = 0; i < 13; i++) {
			if (alongU) f.at(i / 12F, fixed, p);
			else f.at(fixed, i / 12F, p);
			pts[i] = new float[] { p.x, p.y, p.z + 0.01F };
		}
		s.at(0, 0, 0).color(color).tube(pts, fill(13, r));
		return Arrays.asList(pts);
	}

	/** A crown facing out from the back (k = its size): a band, five points with pearls, and gems. */
	private static void crown(Smooth s, float x, float y, float z, float k, int color, int gem) {
		s.at(x, y - 0.08F * k, z).color(color);
		box(s, 0.24F * k, 0.06F * k, 0.015F, 0.01F, 0.01F);
		for (int i = 0; i < 5; i++) {
			float px = x + (-0.2F + i * 0.1F) * k, h = (i == 2 ? 0.24F : i % 2 == 0 ? 0.16F : 0.2F) * k;
			s.at(px, y - 0.03F * k, z).color(color).cone(0.045F * k, h, 6);
			s.at(px, y - 0.03F * k + h, z).color(WHITE).blob(0.025F * k, 0.025F * k, 0.025F);
		}
		for (int i = 0; i < 3; i++) s.at(x + (-0.12F + i * 0.12F) * k, y - 0.08F * k, z + 0.02F).color(gem).blob(0.028F * k, 0.028F * k, 0.015F);
	}

	/** A five-pointed star facing out from the back. */
	private static void star(Smooth s, float x, float y, float z, float r, int color) {
		for (int k = 0; k < 5; k++) s.at(x, y, z).rotZ(k * 72).move(0, r * 0.5F, 0).rotX(90).color(color).lens(r * 0.2F, r * 0.5F, 0.02F);
		s.at(x, y, z + 0.002F).rotX(90).color(color).lens(r * 0.38F, r * 0.38F, 0.022F);
	}

	// ---- packs ----

	/**
	 * A backpack: a padded bag rounded over the top with a grab loop and a zip, a front pocket with its own zip, side
	 * pockets, and straps over the shoulders with buckles and a chest strap. Police Pack: a gold badge and a reflective
	 * band. Med Pack: a red cross and band. Army Pack: camo, webbing and a bedroll. Explorer Pack: a blanket roll,
	 * buckled straps and a coil of rope. Book Bag: a flap with two buckled straps. Backpack: a water bottle and a star
	 * charm.
	 */
	private static void backpack(Smooth s, T t, String id, int a, int b) {
		boolean satchel = id.contains("book"), army = id.contains("army"), explorer = id.contains("explorer"), med = id.contains("med"), police = id.contains("police");
		float z = t.bk + 0.28F, y0 = t.cy - 0.86F, y1 = t.cy + 0.62F, hz = 0.26F, face = z + hz;
		s.at(t.cx, (y0 + y1) / 2, z).color(a);
		box(s, 0.62F, (y1 - y0) / 2, hz, 0.09F, 0.16F);
		straps(s, t, y1 - 0.15F, y0 + 0.12F, z, shade(a, 0.75F), METAL, false);
		s.at(t.cx, y1 + 0.01F, z).rotX(90).color(shade(a, 0.75F)).torus(0.12F, 0.025F, 180, 360);
		s.at(0, 0, 0).color(DARK).tube(new float[][] { { t.cx - 0.55F, y1 - 0.24F, face + 0.006F }, { t.cx + 0.55F, y1 - 0.24F, face + 0.006F } }, new float[] { 0.014F, 0.014F }, false, 6);
		s.at(t.cx + 0.42F, y1 - 0.3F, face + 0.02F).color(METAL);
		box(s, 0.025F, 0.06F, 0.012F, 0.01F, 0.01F);
		// Side pockets.
		for (float side : new float[] { -1, 1 }) {
			s.at(t.cx + side * 0.68F, y0 + 0.32F, z).color(shade(a, 0.85F));
			box(s, 0.07F, 0.28F, 0.18F, 0.03F, 0.03F);
		}
		if (satchel) {
			// The flap over the front, with two buckled straps.
			s.at(t.cx, y1 - 0.45F, face + 0.03F).color(b);
			box(s, 0.6F, 0.42F, 0.03F, 0.02F, 0.02F);
			for (float side : new float[] { -1, 1 }) {
				s.at(t.cx + side * 0.3F, y1 - 0.5F, face + 0.065F).color(shade(b, 0.75F));
				box(s, 0.05F, 0.5F, 0.01F, 0.005F, 0.005F);
				s.at(t.cx + side * 0.3F, y0 + 0.62F, face + 0.08F).color(GOLD);
				box(s, 0.07F, 0.05F, 0.012F, 0.005F, 0.005F);
			}
			return;
		}
		int pocket = med ? a : police ? shade(a, 1.3F) : army ? shade(a, 0.85F) : b;
		s.at(t.cx, y0 + 0.36F, face + 0.07F).color(pocket);
		box(s, 0.44F, 0.3F, 0.08F, 0.05F, 0.07F);
		s.at(0, 0, 0).color(DARK).tube(new float[][] { { t.cx - 0.36F, y0 + 0.58F, face + 0.152F }, { t.cx + 0.36F, y0 + 0.58F, face + 0.152F } }, new float[] { 0.012F, 0.012F }, false, 6);
		s.at(t.cx + 0.3F, y0 + 0.53F, face + 0.16F).color(METAL);
		box(s, 0.02F, 0.05F, 0.01F, 0.008F, 0.008F);
		float pz = face + 0.155F, py = y0 + 0.33F;
		if (police) {
			star(s, t.cx, py, pz + 0.01F, 0.17F, GOLD);
			s.at(t.cx, t.cy + 0.05F, z).color(0xD9DEE4);
			box(s, 0.625F, 0.045F, hz + 0.008F, 0.005F, 0.005F); // a reflective band round the bag
		} else if (med) {
			s.at(t.cx, py, pz).color(b);
			box(s, 0.06F, 0.19F, 0.012F, 0.005F, 0.005F);
			s.at(t.cx, py, pz).color(b);
			box(s, 0.19F, 0.06F, 0.012F, 0.005F, 0.005F);
			s.at(t.cx, y1 - 0.1F, z).color(b);
			box(s, 0.625F, 0.06F, hz + 0.008F, 0.005F, 0.005F);
		} else if (army) {
			int[] cols = { shade(a, 0.65F), 0x5C4A2E, shade(a, 1.25F) };
			for (int i = 0; i < 9; i++) {
				float px = -0.45F + 0.9F * (float) ((i * 0.618) % 1), py2 = y0 + 0.75F + 0.6F * (float) ((i * 0.382 + 0.1) % 1);
				s.at(t.cx + px, py2, face + 0.008F).rotZ(i * 40).rotX(90).color(cols[i % 3]).lens(0.12F, 0.07F, 0.01F);
			}
			for (int k = 0; k < 3; k++) {
				s.at(t.cx, py - 0.14F + k * 0.12F, pz).color(shade(a, 0.6F));
				box(s, 0.4F, 0.018F, 0.008F, 0.004F, 0.004F);
			}
			bedroll(s, t.cx, y1 + 0.17F, z + 0.02F, 0x4E6B3A);
		} else if (explorer) {
			for (float side : new float[] { -1, 1 }) {
				s.at(t.cx + side * 0.2F, py + 0.05F, pz).color(shade(b, 0.9F));
				box(s, 0.04F, 0.3F, 0.008F, 0.004F, 0.004F);
				s.at(t.cx + side * 0.2F, py - 0.12F, pz + 0.012F).color(GOLD);
				box(s, 0.055F, 0.04F, 0.008F, 0.004F, 0.004F);
			}
			bedroll(s, t.cx, y1 + 0.17F, z + 0.02F, 0x8B2E2E);
			for (int k = 0; k < 3; k++) s.at(t.cx - 0.76F - k * 0.015F, y0 + 0.75F, z + k * 0.02F).rotZ(90).color(k % 2 == 0 ? ROPE : shade(ROPE, 0.85F)).torus(0.17F, 0.028F, 0, 360);
		} else {
			// A water bottle in the right pocket and a star charm on the zip.
			s.at(t.cx + 0.7F, y0 + 0.45F, z).color(0x5AB4F2).lathe(new float[] { 0, 0.08F, 0.09F, 0.09F, 0.06F, 0.045F, 0.045F, 0 },
				new float[] { 0, 0, 0.03F, 0.42F, 0.5F, 0.54F, 0.6F, 0.6F }, 12);
			s.at(t.cx + 0.7F, y0 + 1.05F, z).color(WHITE).lathe(new float[] { 0, 0.05F, 0.05F, 0 }, new float[] { 0, 0, 0.06F, 0.06F }, 10);
			s.at(0, 0, 0).color(METAL).tube(new float[][] { { t.cx + 0.42F, y1 - 0.36F, face + 0.03F }, { t.cx + 0.42F, y1 - 0.52F, face + 0.05F } }, new float[] { 0.008F, 0.008F }, false, 4);
			star(s, t.cx + 0.42F, y1 - 0.6F, face + 0.06F, 0.09F, 0xFF4FD8);
		}
	}

	/** A rolled blanket strapped across the top of a pack. */
	private static void bedroll(Smooth s, float x, float y, float z, int color) {
		s.at(x, y, z).rotZ(90).color(color).lathe(new float[] { 0, 0.17F, 0.18F, 0.18F, 0.17F, 0 }, new float[] { -0.62F, -0.62F, -0.6F, 0.6F, 0.62F, 0.62F }, 16);
		s.at(x - 0.62F, y, z).rotZ(90).color(shade(color, 0.7F)).torus(0.11F, 0.02F, 0, 360);
		for (float side : new float[] { -1, 1 }) s.at(x + side * 0.4F, y, z).rotZ(90).color(LEATHER).torus(0.185F, 0.022F, 0, 360);
	}

	/**
	 * Pack straps: over each shoulder and down the chest, then round the side to the pack's bottom, or (belt) into a
	 * waist belt all round (for narrow things like tanks); buckles and a chest strap.
	 */
	private static void straps(Smooth s, T t, float topY, float botY, float bagZ, int color, int metal, boolean belt) {
		for (float side : new float[] { -1, 1 }) {
			float x = t.cx + side * 0.48F, ox = t.cx + side * (t.hw + 0.035F);
			float[][] pts = belt
				? new float[][] { { t.cx + side * 0.34F, topY, bagZ - 0.08F }, { x + side * 0.02F, t.top + 0.05F, t.bk - 0.1F }, { x + side * 0.03F, t.top + 0.05F, (t.bk + t.fr) / 2 },
					{ x + side * 0.03F, t.top + 0.03F, t.fr - 0.03F }, { x, t.cy + 0.55F, t.fr - 0.06F }, { x, t.cy - 0.6F, t.fr - 0.06F } }
				: new float[][] { { x, topY, bagZ - 0.08F }, { x + side * 0.02F, t.top + 0.05F, t.bk - 0.1F }, { x + side * 0.03F, t.top + 0.05F, (t.bk + t.fr) / 2 },
					{ x + side * 0.03F, t.top + 0.03F, t.fr - 0.03F }, { x, t.cy + 0.55F, t.fr - 0.06F }, { x, t.cy - 0.45F, t.fr - 0.06F }, { ox, t.cy - 0.55F, t.fr - 0.04F },
					{ ox, t.cy - 0.6F, t.bk + 0.04F }, { t.cx + side * 0.6F, botY, t.bk + 0.06F } };
			s.at(0, 0, 0).color(color).tube(pts, fill(pts.length, 0.055F));
			s.at(x, t.cy + 0.05F, t.fr - 0.09F).color(metal);
			box(s, 0.08F, 0.05F, 0.025F, 0.01F, 0.01F);
		}
		s.at(0, 0, 0).color(color).tube(new float[][] { { t.cx - 0.48F, t.cy + 0.38F, t.fr - 0.08F }, { t.cx + 0.48F, t.cy + 0.38F, t.fr - 0.08F } }, new float[] { 0.03F, 0.03F });
		s.at(t.cx, t.cy + 0.38F, t.fr - 0.1F).color(DARK);
		box(s, 0.07F, 0.045F, 0.02F, 0.01F, 0.01F);
		if (!belt) return;
		float y = t.cy - 0.64F, ox = t.hw + 0.045F, fz = t.fr - 0.045F, bz = t.bk + 0.045F;
		float[][] loop = { { t.cx, y, fz }, { t.cx + ox, y, fz }, { t.cx + ox, y, bz }, { t.cx - ox, y, bz }, { t.cx - ox, y, fz }, { t.cx, y, fz } };
		s.at(0, 0, 0).color(color).tube(loop, fill(loop.length, 0.06F));
		s.at(t.cx, y, fz - 0.05F).color(metal);
		box(s, 0.1F, 0.075F, 0.025F, 0.01F, 0.01F);
	}

	/**
	 * A strap from the right shoulder across the chest to the left hip and back up across the back, with a buckle (-1 =
	 * none).
	 */
	private static void sling(Smooth s, T t, int color, int buckle) {
		float ox = t.hw + 0.035F, fz = t.fr - 0.035F, bz = t.bk + 0.035F;
		float[][] loop = { { t.cx + 0.62F, t.top + 0.045F, (fz + bz) / 2 }, { t.cx + 0.56F, t.top + 0.02F, fz }, { t.cx - 0.78F, t.cy - 0.72F, fz }, { t.cx - ox, t.cy - 0.8F, fz },
			{ t.cx - ox, t.cy - 0.82F, (fz + bz) / 2 }, { t.cx - ox, t.cy - 0.8F, bz }, { t.cx - 0.78F, t.cy - 0.72F, bz }, { t.cx + 0.56F, t.top + 0.02F, bz },
			{ t.cx + 0.62F, t.top + 0.045F, (fz + bz) / 2 } };
		s.at(0, 0, 0).color(color).tube(loop, fill(loop.length, 0.05F));
		if (buckle < 0) return;
		float dx = -1.34F, dy = t.cy - 0.72F - t.top - 0.02F;
		s.at(t.cx - 0.11F, (t.top + 0.02F + t.cy - 0.72F) / 2, fz - 0.035F).rotZ((float) Math.toDegrees(Math.atan2(-dx, dy))).color(buckle);
		box(s, 0.085F, 0.065F, 0.022F, 0.01F, 0.01F);
	}

	// ---- weapons and shields ----

	/**
	 * A sheathed sword: a leather scabbard with metal bands, throat and tip, a crossguard, a wrapped grip and a pommel.
	 */
	private static void sword(Smooth s, T t, int a, int b) {
		sling(s, t, LEATHER, GOLD);
		Line l = Line.of(t.cx - 0.62F, t.cy - 1.0F, t.cx + 0.4F, t.cy + 0.78F, t.bk + 0.2F);
		to(s, l.at(l.len / 2)).rotZ(l.ang).color(0x4A2E1A);
		box(s, 0.1F, l.len / 2, 0.05F, 0.03F, 0.02F);
		for (float d : new float[] { 0.55F, 1.45F }) {
			to(s, l.at(d)).rotZ(l.ang).color(b);
			box(s, 0.112F, 0.035F, 0.06F, 0.01F, 0.01F);
		}
		// The chape caps the scabbard's tip, a hair past its end (ends level with each other flicker).
		to(s, l.at(0.095F)).rotZ(l.ang).color(b);
		box(s, 0.112F, 0.1F, 0.062F, 0.07F, 0.01F);
		to(s, l.at(l.len - 0.05F)).rotZ(l.ang).color(b);
		box(s, 0.12F, 0.06F, 0.065F, 0.02F, 0.02F);
		to(s, l.at(l.len + 0.03F)).rotZ(l.ang).color(b);
		box(s, 0.32F, 0.04F, 0.06F, 0.015F, 0.015F);
		for (float side : new float[] { -0.33F, 0.33F }) to(s, l.at(l.len + 0.03F, side, 0)).color(b).blob(0.06F, 0.06F, 0.06F);
		s.at(0, 0, 0).color(a).tube(new float[][] { l.at(l.len + 0.07F), l.at(l.len + 0.46F) }, new float[] { 0.05F, 0.05F });
		for (int k = 0; k < 4; k++) to(s, l.at(l.len + 0.12F + k * 0.095F)).rotZ(l.ang).color(shade(a, 0.65F)).torus(0.052F, 0.014F, 0, 360);
		to(s, l.at(l.len + 0.53F)).color(b).ellipsoid(0.08F, 0.08F, 0.08F);
		to(s, l.at(l.len + 0.53F, 0, 0.075F)).color(0xD7263D).blob(0.03F, 0.03F, 0.015F);
	}

	/** A sheathed katana: a curved black saya with gold fittings and a red cord, a round tsuba, a cord-wrapped grip. */
	private static void katana(Smooth s, T t, int a, int b) {
		sling(s, t, 0x2A1A14, GOLD);
		Line l = Line.of(t.cx - 0.66F, t.cy - 1.02F, t.cx + 0.36F, t.cy + 0.74F, t.bk + 0.2F);
		float[][] saya = new float[7][];
		float[] radii = new float[7];
		for (int i = 0; i < 7; i++) {
			float f = i / 6F;
			saya[i] = l.at(l.len * f, -0.09F * (float) Math.sin(Math.PI * f), 0);
			radii[i] = 0.055F + 0.012F * f;
		}
		s.at(0, 0, 0).color(a).tube(saya, radii);
		to(s, saya[0]).color(GOLD).ellipsoid(0.065F, 0.065F, 0.065F);
		to(s, l.at(l.len - 0.03F)).rotZ(l.ang).color(GOLD).torus(0.068F, 0.016F, 0, 360);
		for (float d : new float[] { 0.32F, 0.38F }) to(s, l.at(l.len - d)).rotZ(l.ang).color(b).torus(0.07F, 0.014F, 0, 360);
		to(s, l.at(l.len - 0.45F, 0.07F, 0)).color(a).blob(0.035F, 0.035F, 0.03F);
		s.at(0, 0, 0).color(b).tube(new float[][] { l.at(l.len - 0.45F, 0.09F, 0.02F), l.at(l.len - 0.7F, 0.18F, 0.05F), l.at(l.len - 0.95F, 0.12F, 0.04F) }, new float[] { 0.016F, 0.016F, 0.014F });
		to(s, l.at(l.len + 0.02F)).rotZ(l.ang).color(0x2B2B2B).lathe(new float[] { 0, 0.15F, 0.15F, 0 }, new float[] { -0.02F, -0.02F, 0.02F, 0.02F }, 18);
		to(s, l.at(l.len + 0.02F)).rotZ(l.ang).color(GOLD).torus(0.15F, 0.012F, 0, 360);
		s.at(0, 0, 0).color(b).tube(new float[][] { l.at(l.len + 0.05F), l.at(l.len + 0.5F) }, new float[] { 0.052F, 0.052F });
		for (int k = 0; k < 5; k++)
			to(s, l.at(l.len + 0.1F + k * 0.085F, 0, 0.048F)).rotZ(l.ang).rotX(90).color(0xEDE6D6).scale(0.035F, 1, 0.045F).lathe(new float[] { 0, 1, 0 }, new float[] { -0.008F, 0, 0.008F }, 4);
		to(s, l.at(l.len + 0.53F)).rotZ(l.ang).color(GOLD);
		box(s, 0.055F, 0.03F, 0.055F, 0.015F, 0.02F);
	}

	/** A Viking shield: wooden planks with two painted quarters, a brass rim, a domed boss and rivets. */
	private static void roundShield(Smooth s, T t, int a, int b) {
		sling(s, t, LEATHER, b);
		float x = t.cx, y = t.cy, z0 = t.bk + 0.08F, face = z0 + 0.12F;
		s.at(x, y, z0).rotX(90).color(a).lathe(new float[] { 0, 1.0F, 1.04F, 1.0F, 0 }, new float[] { 0, 0, 0.06F, 0.12F, 0.12F }, 32);
		for (float a0 : new float[] { 0, 180 })
			s.at(x, y, face).rotX(90).color(0xA8322D).lathe(new float[] { 0, 0.97F, 0.97F, 0 }, new float[] { 0, 0, 0.006F, 0.006F }, 8, a0, a0 + 90);
		for (float px : new float[] { -0.6F, -0.3F, 0, 0.3F, 0.6F }) {
			float h = (float) Math.sqrt(1 - px * px) * 0.97F;
			s.at(0, 0, 0).color(shade(a, 0.55F)).tube(new float[][] { { x + px, y - h, face + 0.01F }, { x + px, y + h, face + 0.01F } }, new float[] { 0.012F, 0.012F }, false, 4);
		}
		s.at(x, y, z0 + 0.065F).rotX(90).color(b).torus(1.02F, 0.06F, 0, 360); // (not flush with the shield's back: no flicker)
		s.at(x, y, face).rotX(90).color(b).lathe(new float[] { 0, 0.3F, 0.3F, 0.22F, 0.1F, 0 }, new float[] { 0, 0, 0.03F, 0.12F, 0.18F, 0.19F }, 20);
		s.at(x, y, face + 0.015F).rotX(90).color(shade(b, 0.8F)).torus(0.32F, 0.025F, 0, 360);
		for (int k = 0; k < 10; k++) {
			double ang = k * Math.PI / 5;
			s.at(x + (float) Math.cos(ang) * 0.88F, y + (float) Math.sin(ang) * 0.88F, face + 0.02F).color(b).blob(0.035F, 0.035F, 0.025F);
		}
		for (int k = 0; k < 6; k++) {
			double ang = k * Math.PI / 3 + 0.5;
			s.at(x + (float) Math.cos(ang) * 0.42F, y + (float) Math.sin(ang) * 0.42F, face + 0.02F).color(b).blob(0.03F, 0.03F, 0.02F);
		}
	}

	/** A knight's kite shield: curved, rounded at the top and pointed below, a silver rim, a white cross and rivets. */
	private static void kiteShield(Smooth s, T t, int a, int b) {
		sling(s, t, LEATHER, METAL);
		Smooth.Surface sh = (u, v, o) -> {
			float uu = u * 2 - 1, w = 0.8F * (v < 0.35F ? 1 : (float) Math.pow(1 - (v - 0.35F) / 0.65F, 0.85));
			float y = t.cy + 1.0F - v * 2.15F + 0.14F * (1 - uu * uu) * (float) Math.pow(1 - v, 6);
			o.set(t.cx + uu * w, y, t.bk + 0.12F + 0.16F * (1 - uu * uu) * (1 - 0.5F * v));
		};
		s.at(0, 0, 0).color(a).surface(sh, 14, 16);
		List<float[]> rim = new ArrayList<>();
		Vector3f p = new Vector3f();
		for (int i = 12; i >= 0; i--) {
			sh.at(0, i / 12F, p);
			rim.add(new float[] { p.x, p.y, p.z + 0.01F });
		}
		for (int i = 1; i <= 8; i++) {
			sh.at(i / 8F, 0, p);
			rim.add(new float[] { p.x, p.y, p.z + 0.01F });
		}
		for (int i = 1; i <= 12; i++) {
			sh.at(1, i / 12F, p);
			rim.add(new float[] { p.x, p.y, p.z + 0.01F });
		}
		float[][] r = rim.toArray(new float[0][]);
		s.at(0, 0, 0).color(METAL).tube(r, fill(r.length, 0.045F));
		decal(s, sh, 0.44F, 0.56F, 0.06F, 0.84F, 0.012F, b);
		decal(s, sh, 0.12F, 0.88F, 0.2F, 0.31F, 0.016F, b);
		for (float[] q : new float[][] { { 0.1F, 0.1F }, { 0.9F, 0.1F }, { 0.12F, 0.5F }, { 0.88F, 0.5F }, { 0.5F, 0.94F } }) {
			sh.at(q[0], q[1], p);
			s.at(p.x, p.y, p.z + 0.02F).color(METAL).blob(0.035F, 0.035F, 0.025F);
		}
	}

	/** A painted patch on a surface: the part u0..u1, v0..v1 of it, lifted out by 'lift'. */
	private static void decal(Smooth s, Smooth.Surface f, float u0, float u1, float v0, float v1, float lift, int color) {
		s.at(0, 0, lift).color(color).surface((u, v, o) -> f.at(u0 + (u1 - u0) * u, v0 + (v1 - v0) * v, o), 3, 8);
	}

	// ---- gear ----

	/**
	 * A jetpack: two tanks with nose cones, bands, fins, nozzles and flames, a back plate with a gauge, a fuel pipe
	 * with a valve.
	 */
	private static void jetpack(Smooth s, T t, int a, int b) {
		float z = t.bk + 0.36F;
		straps(s, t, t.cy + 0.6F, t.cy - 0.7F, t.bk + 0.15F, DARK, METAL, true);
		s.at(t.cx, t.cy + 0.05F, t.bk + 0.12F).color(0x3A3F48);
		box(s, 0.55F, 0.75F, 0.07F, 0.05F, 0.05F);
		for (float side : new float[] { -1, 1 }) {
			float x = t.cx + side * 0.36F, y = t.cy - 0.55F;
			s.at(x, y, z).color(a).lathe(new float[] { 0, 0.24F, 0.27F, 0.27F, 0.24F, 0.13F, 0 }, new float[] { 0, 0.01F, 0.08F, 1.0F, 1.12F, 1.26F, 1.32F }, 22);
			for (float yy : new float[] { 0.22F, 0.86F }) s.at(x, y + yy, z).color(b).torus(0.275F, 0.03F, 0, 360);
			s.at(x, y + 0.54F, z).color(b).lathe(new float[] { 0.27F, 0.278F, 0.278F, 0.27F }, new float[] { -0.08F, -0.08F, 0.08F, 0.08F }, 22);
			s.at(x, y - 0.24F, z).color(0x55595F).lathe(new float[] { 0, 0.2F, 0.12F, 0 }, new float[] { 0, 0, 0.24F, 0.24F }, 16);
			s.at(x, y - 0.25F, z).rotX(180).color(0xFF8A1F).cone(0.15F, 0.5F, 12);
			s.at(x, y - 0.25F, z).rotX(180).color(0xFFE45A).cone(0.08F, 0.32F, 10);
			s.at(x + side * 0.29F, y + 0.25F, z).rotZ(side * 12).color(b);
			box(s, 0.025F, 0.24F, 0.12F, 0.1F, 0.02F);
		}
		s.at(0, 0, 0).color(METAL).tube(new float[][] { { t.cx - 0.36F, t.cy + 0.3F, z }, { t.cx + 0.36F, t.cy + 0.3F, z } }, new float[] { 0.05F, 0.05F });
		s.at(t.cx, t.cy + 0.3F, z + 0.08F).rotX(90).color(b).torus(0.09F, 0.018F, 0, 360);
		gauge(s, t.cx, t.cy + 0.62F, t.bk + 0.2F);
	}

	/** A round gauge facing out from the back: white face, dark rim, red needle. */
	private static void gauge(Smooth s, float x, float y, float z) {
		s.at(x, y, z).rotX(90).color(WHITE).lathe(new float[] { 0, 0.11F, 0.11F, 0 }, new float[] { 0, 0, 0.03F, 0.03F }, 16);
		s.at(x, y, z + 0.02F).rotX(90).color(DARK).torus(0.11F, 0.018F, 0, 360);
		s.at(x, y, z + 0.035F).rotZ(-40).move(0, 0.04F, 0).color(0xD7263D);
		box(s, 0.008F, 0.05F, 0.005F, 0.002F, 0.002F);
	}

	/**
	 * An oxygen or chem tank: a capsule with bands, a valve with a hand wheel, a hose over the shoulder; a gauge, or a
	 * glowing window and a hazard sign.
	 */
	private static void tank(Smooth s, T t, String id, int a, int b) {
		boolean chem = id.contains("chem");
		float x = t.cx, y = t.cy - 0.65F, z = t.bk + 0.4F, vy = y + 1.34F;
		straps(s, t, t.cy + 0.5F, t.cy - 0.6F, t.bk + 0.15F, DARK, METAL, true);
		s.at(x, y, z).color(a).lathe(new float[] { 0, 0.34F, 0.37F, 0.37F, 0.33F, 0.14F, 0 }, new float[] { 0, 0.01F, 0.1F, 1.08F, 1.22F, 1.32F, 1.34F }, 24);
		for (float yy : new float[] { 0.3F, 0.85F }) s.at(x, y + yy, z).color(b).torus(0.372F, 0.03F, 0, 360);
		s.at(0, 0, 0).color(METAL).tube(new float[][] { { x, vy - 0.02F, z }, { x, vy + 0.18F, z } }, new float[] { 0.05F, 0.05F });
		s.at(x, vy + 0.2F, z).color(b).torus(0.11F, 0.022F, 0, 360);
		for (int k = 0; k < 2; k++)
			s.at(0, 0, 0).color(b).tube(new float[][] { { x - (k == 0 ? 0.11F : 0), vy + 0.2F, z - (k == 1 ? 0.11F : 0) }, { x + (k == 0 ? 0.11F : 0), vy + 0.2F, z + (k == 1 ? 0.11F : 0) } },
				new float[] { 0.015F, 0.015F }, false, 6);
		s.at(0, 0, 0).color(DARK).tube(new float[][] { { x + 0.05F, vy + 0.08F, z }, { x + 0.3F, vy + 0.12F, z - 0.12F }, { t.cx + 0.62F, t.top + 0.12F, (t.bk + t.fr) / 2 + 0.1F },
			{ t.cx + 0.6F, t.top + 0.08F, t.fr - 0.02F }, { t.cx + 0.4F, t.cy + 0.45F, t.fr - 0.09F } }, new float[] { 0.04F, 0.04F, 0.04F, 0.04F, 0.04F });
		s.at(t.cx + 0.4F, t.cy + 0.42F, t.fr - 0.1F).color(DARK).ellipsoid(0.07F, 0.06F, 0.06F);
		if (!chem) {
			gauge(s, x, y + 0.98F, z + 0.37F);
			return;
		}
		s.at(x, y + 0.58F, z + 0.36F).rotX(90).color(b).lens(0.12F, 0.3F, 0.02F);
		for (int k = 0; k < 4; k++) s.at(x - 0.04F + 0.06F * (k % 2), y + 0.38F + k * 0.12F, z + 0.385F).color(WHITE).blob(0.022F, 0.022F, 0.012F);
		s.at(x, y + 1.0F, z + 0.33F).rotX(90).color(GOLD).lathe(new float[] { 0, 0.13F, 0.13F, 0 }, new float[] { 0, 0, 0.02F, 0.02F }, 16);
		for (int k = 0; k < 3; k++) s.at(x, y + 1.0F, z + 0.355F).rotZ(k * 120).move(0, 0.065F, 0).rotX(90).color(0x16161A).lens(0.045F, 0.05F, 0.01F);
		s.at(x, y + 1.0F, z + 0.36F).rotX(90).color(0x16161A).lens(0.02F, 0.02F, 0.01F);
	}

	/** The Robot Battery: a battery box with + and - terminals, glowing charge bars, a lightning bolt and a cable. */
	private static void battery(Smooth s, T t, int a, int b) {
		float z = t.bk + 0.3F, face = z + 0.24F;
		straps(s, t, t.cy + 0.45F, t.cy - 0.7F, t.bk + 0.12F, DARK, METAL, true);
		s.at(t.cx, t.cy - 0.1F, z).color(a);
		box(s, 0.55F, 0.72F, 0.24F, 0.06F, 0.06F);
		for (float yy : new float[] { 0.45F, -0.7F }) {
			s.at(t.cx, t.cy + yy, z).color(DARK);
			box(s, 0.555F, 0.04F, 0.245F, 0.01F, 0.01F);
		}
		for (float side : new float[] { -1, 1 })
			s.at(t.cx + side * 0.28F, t.cy + 0.62F, z).color(side > 0 ? 0xD7263D : 0x1C1C22).lathe(new float[] { 0, 0.1F, 0.1F, 0.07F, 0 }, new float[] { 0, 0, 0.12F, 0.15F, 0.15F }, 14);
		for (int k = 0; k < 4; k++) {
			s.at(t.cx - 0.15F, t.cy - 0.55F + k * 0.22F, face + 0.01F).color(k < 3 ? b : shade(b, 0.35F));
			box(s, 0.22F, 0.07F, 0.015F, 0.01F, 0.01F);
		}
		float[][] bolt = { { 0.36F, 0.3F }, { 0.24F, 0.02F }, { 0.34F, 0.0F }, { 0.22F, -0.35F } };
		for (int k = 0; k < 3; k++)
			s.at(0, 0, 0).color(b).tube(new float[][] { { t.cx + bolt[k][0], t.cy + bolt[k][1], face + 0.015F }, { t.cx + bolt[k + 1][0], t.cy + bolt[k + 1][1], face + 0.015F } },
				new float[] { 0.03F, 0.03F });
		s.at(0, 0, 0).color(0xD7263D).tube(new float[][] { { t.cx + 0.28F, t.cy + 0.77F, z }, { t.cx + 0.45F, t.cy + 0.95F, z - 0.05F }, { t.cx + 0.62F, t.top + 0.1F, (t.bk + t.fr) / 2 },
			{ t.cx + 0.58F, t.top + 0.06F, t.fr - 0.03F }, { t.cx + 0.42F, t.cy + 0.5F, t.fr - 0.07F } }, new float[] { 0.03F, 0.03F, 0.03F, 0.03F, 0.03F });
		s.at(t.cx + 0.42F, t.cy + 0.47F, t.fr - 0.08F).color(METAL).blob(0.05F, 0.05F, 0.04F);
	}

	// ---- music, boards and odds and ends ----

	/**
	 * A guitar body's outline round its centre, as (x, y) points going round (y towards the neck): a double-cutaway
	 * electric.
	 */
	private static final float[][] BODY = polar(new float[][] { { 0, -0.56F }, { 0.3F, -0.52F }, { 0.47F, -0.38F }, { 0.52F, -0.18F }, { 0.47F, 0.02F },
		{ 0.38F, 0.15F }, { 0.4F, 0.3F }, { 0.44F, 0.45F }, { 0.38F, 0.6F }, { 0.28F, 0.63F }, { 0.2F, 0.52F }, { 0.1F, 0.42F }, { -0.1F, 0.42F },
		{ -0.2F, 0.58F }, { -0.3F, 0.74F }, { -0.4F, 0.7F }, { -0.46F, 0.52F }, { -0.42F, 0.32F }, { -0.4F, 0.15F }, { -0.48F, 0 }, { -0.53F, -0.2F },
		{ -0.47F, -0.4F }, { -0.3F, -0.53F } }, 0, 0.05F);
	/** The headstock's outline round its centre: a paddle flaring out on the treble side. */
	private static final float[][] HEAD = polar(new float[][] { { -0.07F, -0.22F }, { 0.07F, -0.22F }, { 0.09F, -0.08F }, { 0.13F, 0.06F }, { 0.15F, 0.17F },
		{ 0.1F, 0.25F }, { -0.02F, 0.25F }, { -0.08F, 0.12F }, { -0.09F, 0 } }, 0.02F, 0);

	/**
	 * An electric guitar: a smooth double-cutaway body with rounded edges, a white pickguard, three black pickups, a
	 * tremolo bridge with saddles, three knobs and a switch; a maple neck with a rosewood fretboard, 17 frets, dot
	 * inlays and a bone nut; a shaped headstock with six tuners; six strings; strap buttons and a strap.
	 */
	private static void guitar(Smooth s, T t, int a, int b) {
		sling(s, t, 0x1C1C22, METAL);
		Line l = Line.turned(t.cx - 0.28F, t.cy - 0.45F, t.bk + 0.2F, -35);
		float d = 0.085F, top = d + 0.005F;
		slab(s, l, 0, 0.05F, 0, BODY, 1, d, 0.05F, 60, a);
		slab(s, l, -0.06F, 0.06F, top + 0.006F, BODY, 0.6F, 0.007F, 0.006F, 40, WHITE);
		for (float[] q : new float[][] { { 0, 0.33F, 0 }, { 0, 0.13F, 0 }, { 0, -0.09F, 8 } }) {
			to(s, l.at(q[1], q[0], top + 0.022F)).rotZ(l.ang - q[2]).color(0x1C1C22);
			box(s, 0.15F, 0.042F, 0.012F, 0.008F, 0.008F);
			for (int k = 0; k < 6; k++) to(s, l.at(q[1] + (k - 2.5F) * 0.05F * (float) Math.sin(Math.toRadians(q[2])), (k - 2.5F) * 0.05F, top + 0.035F)).rotX(90).color(METAL)
				.lens(0.012F, 0.012F, 0.004F);
		}
		to(s, l.at(-0.29F, 0, top + 0.016F)).rotZ(l.ang).color(METAL);
		box(s, 0.17F, 0.075F, 0.01F, 0.006F, 0.006F);
		for (int k = 0; k < 6; k++) {
			to(s, l.at(-0.27F, (k - 2.5F) * 0.052F, top + 0.03F)).rotZ(l.ang).color(0xC9CED6);
			box(s, 0.02F, 0.03F, 0.01F, 0.004F, 0.004F);
		}
		for (float[] q : new float[][] { { 0.24F, -0.2F }, { 0.29F, -0.33F }, { 0.32F, -0.46F } })
			to(s, l.at(q[1], q[0], top + 0.012F)).rotZ(l.ang).rotX(90).color(b).lathe(new float[] { 0, 0.045F, 0.045F, 0.035F, 0 }, new float[] { 0, 0, 0.035F, 0.045F, 0.045F }, 14);
		to(s, l.at(-0.04F, 0.24F, top + 0.03F)).color(b).blob(0.02F, 0.02F, 0.03F);
		// The neck: maple, a rosewood fretboard with frets and dots, a bone nut.
		float nut = 1.8F, bridge = -0.27F, scale = nut - bridge;
		to(s, l.at(1.1F, 0, d)).rotZ(l.ang).color(0xE0C08A);
		box(s, 0.07F, 0.72F, 0.035F, 0.01F, 0.01F);
		to(s, l.at(1.12F, 0, d + 0.041F)).rotZ(l.ang).color(0x3B2412);
		box(s, 0.068F, 0.68F, 0.006F, 0.003F, 0.003F);
		for (int n = 1; n <= 17; n++) {
			float y = nut - scale * (1 - (float) Math.pow(2, -n / 12.0));
			to(s, l.at(y, 0, d + 0.049F)).rotZ(l.ang).color(0xC9CED6);
			box(s, 0.062F, 0.005F, 0.004F, 0.002F, 0.002F); // (a little narrower than the fretboard: no shared sides)
			if (n == 3 || n == 5 || n == 7 || n == 9 || n == 15) {
				float mid = nut - scale * (1 - (float) Math.pow(2, -(n - 0.5) / 12.0));
				to(s, l.at(mid, 0, d + 0.048F)).rotX(90).color(0xF0EAD8).lens(0.017F, 0.017F, 0.004F);
			} else if (n == 12) for (float side : new float[] { -0.035F, 0.035F }) {
				float mid = nut - scale * (1 - (float) Math.pow(2, -11.5 / 12.0));
				to(s, l.at(mid, side, d + 0.048F)).rotX(90).color(0xF0EAD8).lens(0.015F, 0.015F, 0.004F);
			}
		}
		to(s, l.at(nut, 0, d + 0.05F)).rotZ(l.ang).color(0xF0EAD8);
		box(s, 0.07F, 0.012F, 0.01F, 0.003F, 0.003F);
		// The headstock with six tuners down its side.
		slab(s, l, 0, nut + 0.24F, d - 0.005F, HEAD, 1, 0.025F, 0.02F, 36, a);
		for (int k = 0; k < 6; k++) {
			float y = nut + 0.06F + k * 0.07F, x = -0.04F + k * 0.012F;
			to(s, l.at(y, x, d + 0.03F)).rotZ(l.ang).rotX(90).color(METAL).lathe(new float[] { 0, 0.018F, 0.018F, 0 }, new float[] { 0, 0, 0.03F, 0.03F }, 8);
			to(s, l.at(y, -0.13F, d - 0.005F)).rotZ(l.ang).color(METAL);
			box(s, 0.035F, 0.018F, 0.012F, 0.005F, 0.005F);
		}
		// Strings from the bridge saddles to the tuners.
		for (int k = 0; k < 6; k++)
			s.at(0, 0, 0).color(0xE6E9EE).tube(new float[][] { l.at(bridge, (k - 2.5F) * 0.052F, top + 0.045F), l.at(nut, (k - 2.5F) * 0.022F, d + 0.062F),
				l.at(nut + 0.06F + (5 - k) * 0.07F, -0.04F + (5 - k) * 0.012F, d + 0.055F) }, new float[] { 0.005F, 0.005F, 0.005F }, false, 4);
		for (float[] q : new float[][] { { -0.56F, 0 }, { 0.79F, -0.3F } }) to(s, l.at(q[0], q[1], 0)).color(METAL).blob(0.03F, 0.03F, 0.03F);
	}

	/**
	 * Points (x, y) as {angle in degrees, radius} round (cx, cy), sorted by angle (the shape must be star-shaped round
	 * that centre).
	 */
	private static float[][] polar(float[][] pts, float cx, float cy) {
		float[][] out = new float[pts.length][];
		for (int i = 0; i < pts.length; i++)
			out[i] = new float[] { (float) Math.toDegrees(Math.atan2(pts[i][1] - cy, pts[i][0] - cx)), (float) Math.hypot(pts[i][0] - cx, pts[i][1] - cy) };
		Arrays.sort(out, (p, q) -> Float.compare(p[0], q[0]));
		return out;
	}

	/** An outline's radius at an angle (degrees, -180..180), between its points. */
	private static float radius(float[][] o, float deg) {
		int n = o.length;
		for (int i = 0; i + 1 < n; i++)
			if (deg >= o[i][0] && deg <= o[i + 1][0]) return lerp(o[i][1], o[i + 1][1], (deg - o[i][0]) / (o[i + 1][0] - o[i][0]));
		// Between the last point and the first, across +-180 degrees.
		float a0 = o[n - 1][0], a1 = o[0][0] + 360, x = deg < o[0][0] ? deg + 360 : deg;
		return lerp(o[n - 1][1], o[0][1], (x - a0) / (a1 - a0));
	}

	/**
	 * A flat slab with an outline (scaled by k) round (lx, ly) on the line's frame, faces at +-d (out from lz) and a
	 * rounded edge of width 'round': one surface going round (u) and across - top face, edge, bottom face (v).
	 */
	private static void slab(Smooth s, Line l, float lx, float ly, float lz, float[][] o, float k, float d, float round, int nu, int color) {
		s.at(0, 0, 0).color(color).surface((u, v, out) -> {
			float deg = u * 360 - 180, r = radius(o, deg) * k, inner = Math.max(0, r - round), rr, z;
			if (v < 0.25F) {
				rr = inner * v / 0.25F;
				z = d;
			} else if (v > 0.75F) {
				rr = inner * (1 - v) / 0.25F;
				z = -d;
			} else {
				double ang = Math.PI * (v - 0.25F) / 0.5F;
				rr = inner + (r - inner) * (float) Math.sin(ang);
				z = d * (float) Math.cos(ang);
			}
			double th = Math.toRadians(deg);
			float[] p = l.at(ly + rr * (float) Math.sin(th), lx + rr * (float) Math.cos(th), lz + z);
			out.set(p[0], p[1], p[2]);
		}, nu, 12);
	}

	/**
	 * A skateboard: grip tape inside, a painted underside with a stripe and logo, kicked-up ends, trucks and four
	 * wheels.
	 */
	private static void skateboard(Smooth s, T t, int a, int b) {
		sling(s, t, DARK, METAL);
		float z = t.bk + 0.16F;
		Smooth.Surface deck = (u, v, o) -> {
			float uu = u * 2 - 1, vv = v * 2 - 1, end = Math.max(0, Math.abs(vv) - 0.72F) / 0.28F;
			float w = 0.42F * (float) Math.sqrt(Math.max(0, 1 - end * end));
			o.set(t.cx + uu * w, t.cy + vv * 1.3F, z + 0.18F * end * end + 0.025F * uu * uu);
		};
		s.at(0, 0, 0).color(0x1E1E22).surface(deck, 6, 24);
		s.at(0, 0, 0.03F).color(a).surface(deck, 6, 24);
		decal(s, deck, 0.38F, 0.62F, 0.12F, 0.88F, 0.036F, b);
		s.at(t.cx, t.cy, z + 0.075F).rotX(90).color(WHITE).lens(0.15F, 0.15F, 0.012F);
		s.at(t.cx, t.cy, z + 0.085F).rotX(90).color(b).lens(0.08F, 0.08F, 0.012F);
		for (float vv : new float[] { -0.62F, 0.62F }) {
			float y = t.cy + vv * 1.3F;
			s.at(t.cx, y, z + 0.07F).color(METAL);
			box(s, 0.12F, 0.09F, 0.025F, 0.01F, 0.01F);
			s.at(0, 0, 0).color(0xB8BEC6).tube(new float[][] { { t.cx - 0.36F, y, z + 0.14F }, { t.cx + 0.36F, y, z + 0.14F } }, new float[] { 0.035F, 0.035F });
			s.at(t.cx, y, z + 0.12F).color(0xB8BEC6).blob(0.07F, 0.05F, 0.05F);
			for (float side : new float[] { -1, 1 }) {
				s.at(t.cx + side * 0.36F, y, z + 0.14F).rotZ(90).color(0xF4E8C8)
					.lathe(new float[] { 0, 0.11F, 0.125F, 0.125F, 0.11F, 0 }, new float[] { -0.07F, -0.07F, -0.055F, 0.055F, 0.07F, 0.07F }, 16);
				s.at(t.cx + side * 0.43F, y, z + 0.14F).rotZ(-side * 90).color(b).lathe(new float[] { 0, 0.05F, 0.05F, 0 }, new float[] { 0, 0, 0.008F, 0.008F }, 12);
			}
		}
	}

	/**
	 * A money bag: a burlap sack tied with rope, a ruffled top with coins peeking out, a green dollar sign and a patch.
	 */
	private static void moneyBag(Smooth s, T t, int a, int b) {
		sling(s, t, ROPE, -1);
		float x = t.cx, y = t.cy - 0.9F, z = t.bk + 0.5F;
		float[] pr = { 0, 0.45F, 0.62F, 0.6F, 0.42F, 0.17F, 0.15F }, py = { 0, 0.02F, 0.35F, 0.75F, 1.0F, 1.15F, 1.2F };
		s.at(x, y, z).scale(1, 1, 0.75F).color(a).lathe(pr, py, 24);
		s.at(x, y, z).scale(1, 1, 0.75F).color(shade(a, 1.08F)).lathe(new float[] { 0.15F, 0.22F, 0.3F, 0.27F }, new float[] { 1.2F, 1.28F, 1.38F, 1.42F }, 12);
		s.at(x, y + 1.17F, z).scale(1, 1, 0.75F).color(ROPE).torus(0.165F, 0.035F, 0, 360);
		for (float side : new float[] { -1, 1 })
			s.at(0, 0, 0).color(ROPE).tube(new float[][] { { x + side * 0.1F, y + 1.15F, z + 0.12F }, { x + side * 0.16F, y + 0.95F, z + 0.2F }, { x + side * 0.12F, y + 0.8F, z + 0.22F } },
				new float[] { 0.025F, 0.025F, 0.02F });
		for (int k = 0; k < 3; k++) s.at(x - 0.1F + k * 0.1F, y + 1.4F + 0.03F * (k % 2), z + 0.02F * k).rotX(60 - 25 * k).rotZ(15 * k - 15).color(GOLD)
			.lathe(new float[] { 0, 0.1F, 0.1F, 0 }, new float[] { 0, 0, 0.025F, 0.025F }, 14);
		// The dollar sign follows the sack's front.
		float[][] sPath = { { 0.13F, 0.17F }, { 0.02F, 0.24F }, { -0.12F, 0.17F }, { -0.1F, 0.04F }, { 0.08F, -0.03F }, { 0.13F, -0.15F }, { 0.0F, -0.24F }, { -0.13F, -0.17F } };
		float cy = 0.6F;
		float[][] dollar = new float[sPath.length][];
		for (int i = 0; i < sPath.length; i++) dollar[i] = new float[] { x + sPath[i][0], y + cy + sPath[i][1], z + sackZ(pr, py, sPath[i][0], cy + sPath[i][1]) + 0.025F };
		s.at(0, 0, 0).color(b).tube(dollar, fill(dollar.length, 0.035F));
		s.at(0, 0, 0).color(b).tube(new float[][] { { x, y + cy + 0.33F, z + sackZ(pr, py, 0, cy + 0.33F) + 0.025F }, { x, y + cy, z + sackZ(pr, py, 0, cy) + 0.035F },
			{ x, y + cy - 0.33F, z + sackZ(pr, py, 0, cy - 0.33F) + 0.025F } }, new float[] { 0.025F, 0.025F, 0.025F });
		s.at(x - 0.32F, y + 0.3F, z + sackZ(pr, py, -0.32F, 0.3F) - 0.005F).rotY(-28).rotX(90).color(shade(a, 1.18F)).lens(0.12F, 0.1F, 0.015F);
	}

	/** How far out (z) the money bag's front is at (lx, ly) on it. */
	private static float sackZ(float[] pr, float[] py, float lx, float ly) {
		float r = pr[pr.length - 1];
		for (int i = 0; i < py.length - 1; i++)
			if (ly >= py[i] && ly <= py[i + 1]) r = lerp(pr[i], pr[i + 1], (ly - py[i]) / (py[i + 1] - py[i]));
		return 0.75F * (float) Math.sqrt(Math.max(0, r * r - lx * lx));
	}

	/**
	 * A violin case: smooth and violin-shaped, with a gold zip all round, gold latches, a nameplate, studs and a carry
	 * handle with a name tag.
	 */
	private static void violinCase(Smooth s, T t, int a, int b) {
		sling(s, t, 0x1C1C22, b);
		Line l = Line.turned(t.cx + 0.05F, t.cy - 0.35F, t.bk + 0.24F, -30);
		float[] py = { -0.48F, -0.45F, -0.34F, -0.1F, 0.15F, 0.3F, 0.42F, 0.6F, 0.8F, 0.98F, 1.1F, 1.3F, 1.62F, 1.74F, 1.76F };
		float[] pr = { 0, 0.18F, 0.36F, 0.45F, 0.42F, 0.33F, 0.31F, 0.36F, 0.36F, 0.27F, 0.19F, 0.17F, 0.16F, 0.09F, 0 };
		to(s, l.at(0, 0, 0)).rotZ(l.ang).scale(1, 1, 0.36F).color(a).lathe(pr, py, 24);
		List<float[]> zip = new ArrayList<>();
		for (int i = 0; i < py.length; i++) zip.add(l.at(py[i], pr[i] + 0.004F, 0));
		for (int i = py.length - 2; i >= 0; i--) zip.add(l.at(py[i], -pr[i] - 0.004F, 0));
		float[][] z = zip.toArray(new float[0][]);
		s.at(0, 0, 0).color(b).tube(z, fill(z.length, 0.016F), false, 6);
		for (float[] q : new float[][] { { 0.0F, 0.45F }, { 0.7F, 0.37F }, { 1.2F, 0.18F } }) {
			to(s, l.at(q[0], q[1], 0)).rotZ(l.ang).color(b);
			box(s, 0.035F, 0.06F, 0.06F, 0.01F, 0.01F);
		}
		s.at(0, 0, 0).color(DARK).tube(new float[][] { l.at(0.25F, 0.33F, 0), l.at(0.3F, 0.45F, 0), l.at(0.4F, 0.48F, 0), l.at(0.5F, 0.45F, 0), l.at(0.55F, 0.34F, 0) },
			new float[] { 0.03F, 0.03F, 0.03F, 0.03F, 0.03F });
		s.at(0, 0, 0).color(0xE8E0C8).tube(new float[][] { l.at(0.4F, 0.48F, 0), l.at(0.33F, 0.52F, 0.06F), l.at(0.27F, 0.5F, 0.12F) }, new float[] { 0.008F, 0.008F, 0.008F }, false, 4);
		to(s, l.at(0.22F, 0.5F, 0.14F)).rotZ(l.ang + 20).color(0xE8E0C8);
		box(s, 0.06F, 0.04F, 0.008F, 0.004F, 0.004F);
		to(s, l.at(0.05F, 0, 0.165F)).rotZ(l.ang).rotX(90).color(b).lens(0.11F, 0.055F, 0.01F);
		for (float[] q : new float[][] { { -0.3F, -0.2F }, { -0.3F, 0.2F }, { 0.7F, -0.2F }, { 0.7F, 0.2F }, { 1.45F, 0 } })
			to(s, l.at(q[0], q[1], 0.36F * (float) Math.sqrt(Math.max(0, sq(radius(py, pr, q[0])) - sq(q[1]))) + 0.008F)).color(b).blob(0.028F, 0.028F, 0.016F);
	}

	/** The violin case's half width at a point along it. */
	private static float radius(float[] py, float[] pr, float y) {
		for (int i = 0; i < py.length - 1; i++) if (y >= py[i] && y <= py[i + 1]) return lerp(pr[i], pr[i + 1], (y - py[i]) / (py[i + 1] - py[i]));
		return 0;
	}

	private static float sq(float v) {
		return v * v;
	}

	/** A lasso: a coil of rope with a honda knot, leather ties round the coil and a frayed tail, held by a strap. */
	private static void lasso(Smooth s, T t, int a, int b) {
		sling(s, t, b, METAL);
		float x = t.cx + 0.42F, y = t.cy - 0.35F, z = t.bk + 0.1F;
		for (int k = 0; k < 5; k++)
			s.at(x + k * 0.012F, y - k * 0.015F, z + 0.03F + k * 0.03F).rotX(90).color(k % 2 == 0 ? a : shade(a, 0.88F)).torus(0.42F - k * 0.025F, 0.036F, 0, 360);
		s.at(x - 0.05F, y + 0.4F, z + 0.2F).rotX(90).color(shade(a, 0.8F)).torus(0.07F, 0.025F, 0, 360);
		for (float side : new float[] { -1, 1 }) s.at(x + side * 0.39F, y, z + 0.09F).color(b).torus(0.1F, 0.03F, 0, 360);
		float[] end = { x - 0.42F, y - 1.0F, z + 0.16F };
		s.at(0, 0, 0).color(a).tube(new float[][] { { x - 0.3F, y - 0.25F, z + 0.16F }, { x - 0.42F, y - 0.6F, z + 0.15F }, { x - 0.35F, y - 0.85F, z + 0.18F }, end },
			new float[] { 0.035F, 0.035F, 0.035F, 0.03F });
		for (int k = -1; k <= 1; k++)
			s.at(0, 0, 0).color(shade(a, 1.1F)).tube(new float[][] { end, { end[0] + k * 0.05F, end[1] - 0.12F, end[2] + 0.02F } }, new float[] { 0.012F, 0.004F }, false, 4);
	}

	/**
	 * A wizard's staff: a gnarled shaft with a leather grip and gold rings, three branches twisting round a glowing
	 * crystal, sparkles.
	 */
	private static void staff(Smooth s, T t, int a, int b) {
		sling(s, t, 0x4B2A6A, GOLD);
		Line l = Line.of(t.cx - 0.55F, t.cy - 1.05F, t.cx + 0.48F, t.cy + 1.15F, t.bk + 0.2F);
		float[][] pts = new float[9][];
		float[] radii = new float[9];
		for (int i = 0; i < 9; i++) {
			pts[i] = l.at(l.len * i / 8F, 0.03F * (float) Math.sin(i * 1.9), 0.02F * (float) Math.cos(i * 1.3));
			radii[i] = i % 3 == 1 ? 0.062F : 0.048F;
		}
		s.at(0, 0, 0).color(a).tube(pts, radii);
		for (int k = 0; k < 3; k++) to(s, l.at(l.len * 0.45F + k * 0.07F)).rotZ(l.ang).color(LEATHER).torus(0.058F, 0.016F, 0, 360);
		for (float d : new float[] { 0.15F, 0.27F }) to(s, l.at(l.len - d)).rotZ(l.ang).color(GOLD).torus(0.055F, 0.016F, 0, 360);
		float[] c = l.at(l.len + 0.3F);
		for (int k = 0; k < 3; k++) {
			double ang = Math.toRadians(k * 120);
			float ox = (float) Math.cos(ang) * 0.16F, oz = (float) Math.sin(ang) * 0.16F;
			s.at(0, 0, 0).color(shade(a, 1.1F)).tube(new float[][] { l.at(l.len - 0.03F), l.at(l.len + 0.12F, ox, oz), l.at(l.len + 0.42F, ox * 0.9F, oz * 0.9F),
				l.at(l.len + 0.58F, ox * 0.25F, oz * 0.25F) }, new float[] { 0.04F, 0.035F, 0.028F, 0.012F });
		}
		to(s, c).rotZ(l.ang).color(b).lathe(new float[] { 0, 0.13F, 0.16F, 0 }, new float[] { -0.22F, -0.06F, 0.12F, 0.36F }, 6);
		for (int k = 0; k < 4; k++) {
			double ang = Math.toRadians(k * 90 + 30);
			to(s, l.at(l.len + 0.3F + 0.12F * (k % 2), (float) Math.cos(ang) * 0.32F, (float) Math.sin(ang) * 0.32F)).color(k % 2 == 0 ? b : WHITE).blob(0.035F, 0.035F, 0.035F);
		}
	}

	/**
	 * A pitchfork: a wooden handle with an end knob, a metal ferrule with rivets, a crossbar and four curved, pointed
	 * tines.
	 */
	private static void pitchfork(Smooth s, T t, int a, int b) {
		sling(s, t, 0x3B5E8C, METAL);
		Line l = Line.of(t.cx - 0.5F, t.cy - 1.05F, t.cx + 0.42F, t.cy + 0.95F, t.bk + 0.2F);
		s.at(0, 0, 0).color(a).tube(new float[][] { l.at(0), l.at(l.len * 0.5F, 0.01F, 0), l.at(l.len) }, new float[] { 0.045F, 0.047F, 0.045F });
		to(s, l.at(-0.03F)).color(shade(a, 0.8F)).ellipsoid(0.065F, 0.065F, 0.065F);
		s.at(0, 0, 0).color(b).tube(new float[][] { l.at(l.len - 0.02F), l.at(l.len + 0.2F) }, new float[] { 0.058F, 0.05F });
		for (float side : new float[] { -1, 1 }) to(s, l.at(l.len + 0.08F, side * 0.05F, 0.03F)).color(shade(b, 0.7F)).blob(0.016F, 0.016F, 0.012F);
		s.at(0, 0, 0).color(b).tube(new float[][] { l.at(l.len + 0.22F, -0.25F, 0), l.at(l.len + 0.19F, 0, 0), l.at(l.len + 0.22F, 0.25F, 0) }, new float[] { 0.035F, 0.04F, 0.035F });
		for (float w : new float[] { -0.24F, -0.08F, 0.08F, 0.24F })
			s.at(0, 0, 0).color(b).tube(new float[][] { l.at(l.len + 0.21F, w, 0), l.at(l.len + 0.45F, w * 1.05F, 0.02F), l.at(l.len + 0.75F, w, 0.07F), l.at(l.len + 0.95F, w * 0.95F, 0.13F) },
				new float[] { 0.03F, 0.028F, 0.022F, 0 });
	}

	/** Balloons: five on curly strings tied with a ribbon bow at the right shoulder, each with a knot and a shine. */
	private static void balloons(Smooth s, T t, int a, int b) {
		float tx = t.cx + 0.6F, ty = t.top - 0.12F, tz = t.bk + 0.1F;
		s.at(0, 0, 0).color(0xD7263D).tube(new float[][] { { tx, ty, tz }, { t.cx + 0.62F, t.top + 0.04F, (t.bk + t.fr) / 2 }, { t.cx + 0.58F, t.top - 0.12F, t.fr - 0.04F } },
			new float[] { 0.03F, 0.03F, 0.03F });
		float[][] at = { { 0.25F, 1.75F, 0.35F }, { 0.85F, 2.05F, 0.25F }, { 1.3F, 1.6F, 0.5F }, { 0.6F, 1.45F, 0.75F }, { 1.05F, 2.4F, 0.6F } };
		int[] colors = { a, b, 0xF2C230, 0x3BB273, 0xB46BFF };
		for (int k = 0; k < at.length; k++) {
			float bx = t.cx + at[k][0], by = t.top + at[k][1], bz = t.bk + at[k][2];
			s.at(bx, by, bz).color(colors[k]).lathe(new float[] { 0, 0.09F, 0.27F, 0.33F, 0.3F, 0.18F, 0 }, new float[] { -0.42F, -0.4F, -0.22F, 0.02F, 0.24F, 0.38F, 0.42F }, 18);
			s.at(bx, by - 0.4F, bz).rotX(180).color(colors[k]).cone(0.05F, 0.07F, 8);
			for (float side : new float[] { -1, 1 }) s.at(bx - 0.12F, by + 0.16F, bz + side * 0.26F).color(WHITE).blob(0.05F, 0.08F, 0.03F);
			float[][] str = new float[6][];
			for (int i = 0; i < 6; i++) {
				float f = i / 5F;
				str[i] = new float[] { lerp(bx, tx, f) + (i % 2 == 0 ? 0.04F : -0.04F) * (1 - f), lerp(by - 0.47F, ty, f), lerp(bz, tz, f) };
			}
			s.at(0, 0, 0).color(0xE6E6EA).tube(str, fill(6, 0.008F), false, 4);
		}
		for (float side : new float[] { -1, 1 }) s.at(tx + side * 0.07F, ty, tz + 0.04F).rotZ(side * 30).rotX(90).color(0xD7263D).lens(0.07F, 0.04F, 0.02F);
		s.at(tx, ty, tz + 0.05F).color(0xA81E2E).blob(0.03F, 0.03F, 0.025F);
	}

	/**
	 * A frying pan: a cast-iron pan facing out with a fried egg in it, a wooden handle with rivets and a hanging hole.
	 */
	private static void pan(Smooth s, T t, int a, int b) {
		sling(s, t, LEATHER, METAL);
		float x = t.cx, y = t.cy + 0.15F, z = t.bk + 0.12F;
		s.at(x, y, z).rotX(90).color(a).lathe(new float[] { 0, 0.55F, 0.6F, 0.63F, 0.6F, 0.56F, 0 }, new float[] { 0, 0, 0.03F, 0.18F, 0.2F, 0.07F, 0.07F }, 30);
		s.at(x, y, z + 0.072F).rotX(90).color(shade(a, 1.35F)).lathe(new float[] { 0, 0.54F, 0 }, new float[] { 0, 0, 0.003F }, 30);
		s.at(x + 0.08F, y + 0.06F, z + 0.085F).rotX(90).color(WHITE).lens(0.26F, 0.21F, 0.012F);
		s.at(x - 0.08F, y - 0.05F, z + 0.085F).rotX(90).color(WHITE).lens(0.18F, 0.16F, 0.012F);
		s.at(x + 0.05F, y + 0.03F, z + 0.1F).color(0xFFC21F).blob(0.09F, 0.09F, 0.045F);
		s.at(0, 0, 0).color(a).tube(new float[][] { { x, y - 0.6F, z + 0.1F }, { x, y - 0.82F, z + 0.12F } }, new float[] { 0.05F, 0.05F });
		s.at(x, y - 1.12F, z + 0.12F).color(b);
		box(s, 0.065F, 0.3F, 0.045F, 0.04F, 0.02F);
		for (float d : new float[] { 0.9F, 1.0F }) s.at(x, y - d, z + 0.165F).color(METAL).blob(0.018F, 0.018F, 0.01F);
		s.at(x, y - 1.32F, z + 0.168F).rotX(90).color(0x1C1C22).torus(0.035F, 0.012F, 0, 360);
	}

	// ---- helpers ----

	/**
	 * A line on the back to build things along (a sword, a staff...): from (x, y) in direction (ux, uy), at depth z;
	 * ang turns +y along it.
	 */
	private record Line(float x, float y, float z, float ux, float uy, float len, float ang) {
		static Line of(float x0, float y0, float x1, float y1, float z) {
			float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy), ux = dx / len, uy = dy / len;
			return new Line(x0, y0, z, ux, uy, len, (float) Math.toDegrees(Math.atan2(-ux, uy)));
		}

		/** A frame at (x, y, z) turned ang degrees (local y = along, local x = 'side'). */
		static Line turned(float x, float y, float z, float ang) {
			double r = Math.toRadians(ang);
			return new Line(x, y, z, (float) -Math.sin(r), (float) Math.cos(r), 0, ang);
		}

		float[] at(float d) {
			return at(d, 0, 0);
		}

		/** The point d along it, 'side' to its right, dz further out from the back. */
		float[] at(float d, float side, float dz) {
			return new float[] { x + ux * d + uy * side, y + uy * d - ux * side, z + dz };
		}
	}

	private static Smooth to(Smooth s, float[] p) {
		return s.at(p[0], p[1], p[2]);
	}

	/**
	 * A box with bevelled bottom (b0) and top (b1) edges at the current position and turn: half sizes hx, hy, hz
	 * (4-sided lathe: crisp upright edges).
	 */
	private static void box(Smooth s, float hx, float hy, float hz, float b0, float b1) {
		float r = 1.4142F, m = Math.min(hx, hz), k0 = r * Math.max(0.15F, 1 - b0 / m), k1 = r * Math.max(0.15F, 1 - b1 / m);
		s.scale(hx, 1, hz).rotY(45).lathe(new float[] { 0, k0, r, r, k1, 0 }, new float[] { -hy, -hy, -hy + b0, hy - b1, hy, hy }, 4);
	}

	/** A point a fraction f along a polyline (by segment, near enough for a few even segments). */
	private static float[] along(float[][] pts, float f) {
		float g = Math.max(0, Math.min(0.9999F, f)) * (pts.length - 1);
		int i = (int) g;
		float k = g - i;
		return new float[] { lerp(pts[i][0], pts[i + 1][0], k), lerp(pts[i][1], pts[i + 1][1], k), lerp(pts[i][2], pts[i + 1][2], k) };
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
	}

	private static float[] fill(int n, float v) {
		float[] r = new float[n];
		Arrays.fill(r, v);
		return r;
	}

	private static int shade(int rgb, float k) {
		int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)), bl = Math.min(255, Math.round((rgb & 255) * k));
		return r << 16 | g << 8 | bl;
	}
}
