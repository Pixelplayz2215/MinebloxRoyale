package gg.minebloxroyale.client.avatar;

import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * The 46 gliders (gliders sheet), drawn above a gliding player in Roblox root space (studs, y up from the hips, front
 * -Z). Nine shapes, each built like the real thing (creator: polish and more detail on every glider): a swept wing with
 * ribs and fins, an umbrella with ribs and a scalloped hem, a parachute with seams and lines, a kite with spars, a
 * bridle and a bow tail, a delta with a keel and fins, a bat with finger bones and membranes, a hot-air balloon with
 * load tapes and a burner, a leaf with veins and a stem, and Dominus wings (feathers and a Dominus hood). All hang from
 * the same frame the player holds with both hands raised (RobloxAvatarRenderer). On top of that each glider has its own
 * decoration (the sheet's "deco"): a skull, a crown, flames, stars, jet engines, a dragon head and so on, on the top
 * and the underside.
 */
final class Gliders {
	private static final float Y = 5.3F; // studs above the hips
	/** The frame the hands hold: grips at the raised hands, a crossbar over the head. */
	private static final float GRIP_X = 1.11F, GRIP_Y = 2.95F, BAR_X = 1.0F, BAR_Y = 3.7F, BAR_Z = -0.22F;
	private static final int DARK = 0x2E3138, METAL = 0x9AA2AD, GRIP = 0x18181C, WOOD = 0x6B4423, LINE = 0xE6E1D3, GOLD = 0xF2C230, WHITE = 0xF4F6FA,
		STAR = 0xFFF3B0;

	/** A point on a glider's top surface: u -1..1 left to right, v -1..1 front to back. */
	private interface Canopy {
		void top(float u, float v, Vector3f out);
	}

	private Gliders() {
	}

	/** The player's chosen glider; bots (and players who never picked) get one of the free ones. */
	static GliderDef of(Map<String, String> outfit, int entityId) {
		String id = outfit == null ? null : outfit.get("glider");
		GliderDef g = id == null ? null : Sheets.GLIDERS_BY_ID.get(id);
		if (g != null) return g;
		List<GliderDef> free = Sheets.GLIDERS.stream().filter(x -> x.unlock().equals("default")).toList();
		// No glider picked (bots, new players): one of the free ones (bots get a different one each).
		return !free.isEmpty() ? free.get(Math.floorMod(entityId, free.size())) : Sheets.GLIDERS.get(0);
	}

	static void draw(Smooth s, GliderDef g) {
		s.caps(0.045F); // lots of thin lines: no round caps on them
		int a = g.color(), b = g.color2();
		String deco = g.deco();
		Canopy c = switch (g.shape()) {
			case "umbrella" -> umbrella(s, a, b, deco);
			case "parachute" -> parachute(s, a, b, deco);
			case "kite" -> kite(s, a, b);
			case "delta" -> delta(s, a, b, deco);
			case "bat" -> bat(s, a, b, deco);
			case "balloon" -> balloon(s, a, b);
			case "leaf" -> leaf(s, a, b, deco);
			case "dominus" -> dominus(s, a, b, deco);
			default -> wing(s, a, b, deco);
		};
		handles(s);
		decorate(s, g, c, a, b);
	}

	// ---- the frame ----

	/** Rubber grips in the raised hands and a crossbar over the head joining them. */
	private static void handles(Smooth s) {
		for (float side : new float[] { -1, 1 }) {
			s.at(0, 0, 0).color(METAL).tube(new float[][] { { side * GRIP_X, GRIP_Y - 0.25F, BAR_Z }, { side * (GRIP_X + BAR_X) / 2, BAR_Y - 0.25F, BAR_Z },
				{ side * BAR_X, BAR_Y, BAR_Z } }, new float[] { 0.045F, 0.045F, 0.045F });
			s.at(0, 0, 0).color(GRIP).tube(new float[][] { { side * GRIP_X, GRIP_Y - 0.3F, BAR_Z }, { side * GRIP_X, GRIP_Y + 0.25F, BAR_Z } }, new float[] { 0.08F, 0.08F });
		}
		s.at(0, 0, 0).color(METAL).tube(new float[][] { { -BAR_X, BAR_Y, BAR_Z }, { BAR_X, BAR_Y, BAR_Z } }, new float[] { 0.045F, 0.045F });
	}

	/** Struts from the canopy (at x = +-spread, height y) down to the ends of the crossbar. */
	private static void struts(Smooth s, float spread, float y, int color) {
		for (float side : new float[] { -1, 1 })
			s.at(0, 0, 0).color(color).tube(new float[][] { { side * spread, y, 0 }, { side * BAR_X, BAR_Y, BAR_Z } }, new float[] { 0.04F, 0.035F });
	}

	// ---- the nine shapes ----

	/** A swept, curved wing: top and bottom skins, a rounded leading edge, ribs, a spine, tip fins and a pod. */
	private static Canopy wing(Smooth s, int a, int b, String deco) {
		boolean neon = deco.equals("neon");
		int skin = neon ? b : a, trim = neon ? a : b;
		Canopy top = (u, v, o) -> {
			float au = Math.abs(u), x = u * 2.25F;
			float zl = -0.8F + 0.45F * au * au, zt = 0.8F - 0.2F * au;
			float t = (v + 1) / 2, z = zl + (zt - zl) * t;
			o.set(x, Y + 0.18F * au * au + 0.14F * (float) Math.sin(Math.PI * t) * (1 - 0.5F * au), z);
		};
		s.at(0, 0, 0).color(skin).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 24, 8);
		s.at(0, -0.07F, 0).color(shade(skin, 0.8F)).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 20, 6);
		edge(s, top, -1, 0.075F, trim);
		edge(s, top, 1, 0.035F, shade(trim, 0.85F));
		for (float u : new float[] { -0.75F, -0.4F, 0.4F, 0.75F }) rib(s, top, u, 0.025F, shade(skin, neon ? 1F : 0.72F), neon ? a : -1);
		rib(s, top, 0, 0.05F, trim, -1);
		for (float side : new float[] { -1, 1 }) {
			// Tip fins: small upright plates in the trim colour.
			Vector3f f0 = new Vector3f(), f1 = new Vector3f();
			top.top(side, -1, f0);
			top.top(side, 1, f1);
			final Vector3f p0 = f0, p1 = f1;
			s.at(0, 0, 0).color(trim).surface((u, v, o) -> o.set(p0.x, p0.y + (p1.y - p0.y) * u + 0.3F * v * (float) Math.sin(Math.PI * (0.3 + 0.7 * u)), p0.z + (p1.z - p0.z) * u), 6, 3);
		}
		s.at(0, Y - 0.1F, 0.05F).color(trim).blob(0.24F, 0.14F, 0.78F);
		s.at(0, Y - 0.12F, -0.68F).color(shade(trim, 0.7F)).blob(0.12F, 0.08F, 0.12F);
		struts(s, 0.7F, Y - 0.05F, DARK);
		return top;
	}

	/** A leading or trailing edge (v = -1 or 1) as a tube following the wing. */
	private static void edge(Smooth s, Canopy c, float v, float r, int color) {
		float[][] pts = new float[13][];
		float[] radii = new float[13];
		Vector3f p = new Vector3f();
		for (int i = 0; i < 13; i++) {
			c.top(-1 + i / 6F, v, p);
			pts[i] = new float[] { p.x, p.y - 0.035F, p.z };
			radii[i] = r * (1 - 0.35F * Math.abs(-1 + i / 6F));
		}
		s.at(0, 0, 0).color(color).tube(pts, radii);
	}

	/** A rib along the chord at u, just above the skin (glow: a second thin line in that colour, -1 = none). */
	private static void rib(Smooth s, Canopy c, float u, float r, int color, int glow) {
		float[][] pts = new float[7][];
		Vector3f p = new Vector3f();
		for (int i = 0; i < 7; i++) {
			c.top(u, -1 + i / 3F, p);
			pts[i] = new float[] { p.x, p.y + 0.02F, p.z };
		}
		s.at(0, 0, 0).color(color).tube(pts, new float[] { r, r, r, r, r, r, r });
		if (glow < 0) return;
		// Neon: a bright line along the top of the rib.
		for (float[] q : pts) q[1] += r * 0.9F;
		float g = r * 0.55F;
		s.at(0, 0, 0).color(glow).tube(pts, new float[] { g, g, g, g, g, g, g });
	}

	/** An umbrella: panels, ribs and stretchers, a scalloped hem with rib tips, a finial, and a shaft to the frame. */
	private static Canopy umbrella(Smooth s, int a, int b, String deco) {
		float cy = Y - 0.4F, R = 1.75F, H = 0.85F;
		boolean ornate = deco.equals("ornate") || deco.equals("crown");
		int ribColor = ornate ? GOLD : METAL;
		for (int i = 0; i < 8; i++) s.at(0, cy, 0).scale(R, H, R).color(i % 2 == 0 ? a : b).panel(6, 90, 8, 4, i * 45, (i + 1) * 45);
		float rimY = cy + H * (float) Math.sin(Math.toRadians(6)), rimR = R * (float) Math.cos(Math.toRadians(6));
		for (int i = 0; i < 8; i++) {
			double ang = Math.toRadians(i * 45), mid = Math.toRadians(i * 45 + 22.5);
			// Rib along the panel seam, from the top down to a tip past the hem.
			float[][] rib = new float[6][];
			for (int k = 0; k < 6; k++) {
				double lat = Math.toRadians(90 - k * 16.8);
				float r = (float) (R * 1.012 * Math.cos(lat)), y = (float) (cy + H * 1.012 * Math.sin(lat));
				rib[k] = new float[] { (float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r };
			}
			s.at(0, 0, 0).color(ribColor).tube(rib, new float[] { 0.02F, 0.022F, 0.022F, 0.022F, 0.022F, 0.02F });
			s.at((float) Math.cos(ang) * (rimR + 0.06F), rimY - 0.04F, (float) Math.sin(ang) * (rimR + 0.06F)).color(ornate ? GOLD : b).blob(0.06F, 0.06F, 0.06F);
			// Scalloped hem: each panel's edge droops a little between the ribs.
			s.at((float) Math.cos(mid) * rimR * 0.97F, rimY - 0.07F, (float) Math.sin(mid) * rimR * 0.97F).rotY(-(float) Math.toDegrees(mid) - 90).color(i % 2 == 0 ? a : b)
				.blob(0.62F, 0.06F, 0.1F);
			// Stretcher from the runner on the shaft up to the rib.
			s.at(0, 0, 0).color(METAL).tube(new float[][] { { 0, cy - 0.55F, 0 }, { (float) Math.cos(ang) * R * 0.55F, cy + H * 0.8F, (float) Math.sin(ang) * R * 0.55F } },
				new float[] { 0.014F, 0.014F });
		}
		// Finial on top, the shaft, the runner, and a yoke from the shaft to the crossbar.
		s.at(0, cy + H, 0).color(ornate ? GOLD : b).cone(0.07F, 0.3F, 10);
		s.at(0, cy + H + 0.32F, 0).color(ornate ? 0xD7263D : b).blob(0.08F, 0.08F, 0.08F);
		s.at(0, 0, 0).color(METAL).tube(new float[][] { { 0, cy + H, 0 }, { 0, BAR_Y + 0.35F, BAR_Z * 0.5F } }, new float[] { 0.045F, 0.04F });
		s.at(0, cy - 0.55F, 0).color(DARK).blob(0.09F, 0.12F, 0.09F);
		for (float side : new float[] { -1, 1 })
			s.at(0, 0, 0).color(ornate ? GOLD : WOOD).tube(new float[][] { { 0, BAR_Y + 0.35F, BAR_Z * 0.5F }, { side * 0.45F, BAR_Y + 0.12F, BAR_Z }, { side * BAR_X, BAR_Y, BAR_Z } },
				new float[] { 0.055F, 0.05F, 0.045F });
		return (u, v, o) -> dome(u, v, cy, R, H, 6, o);
	}

	/** A point on a dome canopy (centre y cy, radius R, height H, hem at latitude rimLat) for decorations. */
	private static void dome(float u, float v, float cy, float R, float H, float rimLat, Vector3f o) {
		float r = Math.min(1, (float) Math.sqrt(u * u + v * v));
		double lat = Math.toRadians(90 - r * (88 - rimLat)), lon = Math.atan2(v, u);
		o.set((float) (R * Math.cos(lat) * Math.cos(lon)), (float) (cy + H * Math.sin(lat)), (float) (R * Math.cos(lat) * Math.sin(lon)));
	}

	/** A parachute: gores with seams, a skirt band, a vent at the top, suspension lines and risers to the frame. */
	private static Canopy parachute(Smooth s, int a, int b, String deco) {
		float cy = Y + 0.5F, R = 2.45F, H = 1.05F, rimLat = 14;
		int[] rainbow = { 0xD7263D, 0xF28C28, 0xF2E23A, 0x2EB84B, 0x2F6BD8, 0x7A4FC9 };
		for (int i = 0; i < 12; i++) {
			int col = deco.equals("rainbow") ? rainbow[i % 6] : i % 2 == 0 ? a : b;
			s.at(0, cy, 0).scale(R, H, R).color(col).panel(rimLat, 84, 8, 3, i * 30, (i + 1) * 30);
		}
		int seam = shade(deco.equals("rainbow") ? WHITE : b, 0.7F);
		float rimY = cy + H * (float) Math.sin(Math.toRadians(rimLat)), rimR = R * (float) Math.cos(Math.toRadians(rimLat));
		for (int i = 0; i < 12; i++) {
			double ang = Math.toRadians(i * 30);
			float[][] seamPts = new float[6][];
			for (int k = 0; k < 6; k++) {
				double lat = Math.toRadians(84 - k * (84 - rimLat) / 5);
				seamPts[k] = new float[] { (float) (R * 1.008 * Math.cos(lat) * Math.cos(ang)), (float) (cy + H * 1.008 * Math.sin(lat)), (float) (R * 1.008 * Math.cos(lat) * Math.sin(ang)) };
			}
			s.at(0, 0, 0).color(seam).tube(seamPts, new float[] { 0.016F, 0.016F, 0.016F, 0.016F, 0.016F, 0.016F });
			// Suspension line from the hem down to the riser on its side.
			float lx = (float) Math.cos(ang) * rimR, lz = (float) Math.sin(ang) * rimR, side = lx >= 0 ? 1 : -1;
			s.at(0, 0, 0).color(LINE).tube(new float[][] { { lx, rimY - 0.02F, lz }, { side * 0.85F, BAR_Y + 0.9F, BAR_Z * 0.5F } }, new float[] { 0.012F, 0.012F });
		}
		// Skirt band round the hem, the vent at the top.
		s.at(0, rimY - 0.01F, 0).color(shade(b, 0.85F)).torus(rimR, 0.045F, 0, 360);
		float ventY = cy + H * (float) Math.sin(Math.toRadians(84)), ventR = R * (float) Math.cos(Math.toRadians(84));
		s.at(0, ventY, 0).color(seam).torus(ventR, 0.035F, 0, 360);
		s.at(0, ventY - 0.05F, 0).color(DARK).lens(ventR, ventR, 0.02F);
		// Risers: the lines gather into a strap on each side down to the crossbar.
		for (float side : new float[] { -1, 1 }) {
			s.at(0, 0, 0).color(DARK).tube(new float[][] { { side * 0.85F, BAR_Y + 0.9F, BAR_Z * 0.5F }, { side * BAR_X, BAR_Y, BAR_Z } }, new float[] { 0.05F, 0.04F });
			s.at(side * 0.85F, BAR_Y + 0.9F, BAR_Z * 0.5F).color(METAL).torus(0.07F, 0.02F, 0, 360);
		}
		return (u, v, o) -> dome(u, v, cy, R, H, rimLat, o);
	}

	/** A diamond kite: billowing sail with a centre panel, bound edges, cross spars, a bridle and a tail of bows. */
	private static Canopy kite(Smooth s, int a, int b) {
		Vector3f n = new Vector3f(0, Y + 0.05F, -1.8F), l = new Vector3f(-1.4F, Y + 0.2F, -0.3F), r = new Vector3f(1.4F, Y + 0.2F, -0.3F), t = new Vector3f(0, Y - 0.05F, 1.8F);
		// The sail as a square (fu, fv): (0,0) the nose, (1,0) the right corner, (1,1) the tail, (0,1) the left corner.
		Canopy square = (fu, fv, o) -> {
			Vector3f p = bilinear(n, r, t, l, fv, fu);
			o.set(p.x, p.y + 0.14F * (float) (Math.sin(Math.PI * fu) * Math.sin(Math.PI * fv)), p.z);
		};
		// Decorations: u left to right, v nose to tail.
		Canopy sail = (u, v, o) -> square.top(Math.max(0, Math.min(1, (1 + u + v) / 2)), Math.max(0, Math.min(1, (1 - u + v) / 2)), o);
		s.at(0, 0, 0).color(a).surface(square::top, 12, 12);
		s.at(0, 0.012F, 0).color(b).surface((u, v, o) -> square.top(0.275F + 0.45F * u, 0.275F + 0.45F * v, o), 6, 6);
		Vector3f[] corners = { n, r, t, l, n };
		for (int i = 0; i < 4; i++) {
			Vector3f p0 = corners[i], p1 = corners[i + 1];
			s.at(0, 0, 0).color(b).tube(new float[][] { { p0.x, p0.y, p0.z }, { (p0.x + p1.x) / 2, (p0.y + p1.y) / 2 + 0.06F, (p0.z + p1.z) / 2 }, { p1.x, p1.y, p1.z } },
				new float[] { 0.035F, 0.035F, 0.035F });
		}
		s.at(0, 0, 0).color(WOOD).tube(new float[][] { { n.x, n.y + 0.03F, n.z }, { 0, Y + 0.2F, 0 }, { t.x, t.y + 0.03F, t.z } }, new float[] { 0.03F, 0.03F, 0.03F });
		s.at(0, 0, 0).color(WOOD).tube(new float[][] { { l.x, l.y + 0.03F, l.z }, { 0, Y + 0.24F, -0.3F }, { r.x, r.y + 0.03F, r.z } }, new float[] { 0.03F, 0.03F, 0.03F });
		// Bridle: from the nose, the cross and the tail end to a tow ring, then to the crossbar.
		float[] tow = { 0, Y - 0.85F, -0.1F };
		for (Vector3f p : new Vector3f[] { n, new Vector3f(0, Y + 0.15F, -0.3F), t })
			s.at(0, 0, 0).color(LINE).tube(new float[][] { { p.x, p.y, p.z }, tow }, new float[] { 0.01F, 0.01F });
		s.at(tow[0], tow[1], tow[2]).color(METAL).torus(0.07F, 0.02F, 0, 360);
		for (float side : new float[] { -1, 1 })
			s.at(0, 0, 0).color(LINE).tube(new float[][] { tow, { side * BAR_X * 0.8F, BAR_Y, BAR_Z } }, new float[] { 0.012F, 0.012F });
		// Tail streaming back, with bows in both colours.
		float[][] tail = new float[8][];
		for (int i = 0; i < 8; i++) tail[i] = new float[] { (float) Math.sin(i * 1.1) * 0.22F, t.y - 0.06F * i, t.z + 0.5F * i };
		s.at(0, 0, 0).color(b).tube(tail, new float[] { 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.015F });
		for (int k = 1; k < 8; k += 2) {
			float[] p = tail[k];
			int col = k % 4 == 1 ? a : b;
			s.at(p[0] - 0.12F, p[1], p[2]).rotY(20).color(col).blob(0.14F, 0.04F, 0.08F);
			s.at(p[0] + 0.12F, p[1], p[2]).rotY(-20).color(col).blob(0.14F, 0.04F, 0.08F);
			s.at(p[0], p[1], p[2]).color(shade(col, 0.8F)).blob(0.05F, 0.05F, 0.05F);
		}
		return sail;
	}

	private static Vector3f bilinear(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float s, float t) {
		// a = (0,0), b = (1,0), c = (1,1), d = (0,1) in (t, s) - used as nose, right, tail, left
		Vector3f ab = new Vector3f(a).lerp(b, t), dc = new Vector3f(d).lerp(c, t);
		// Diamond: mix so the four corners are the nose (s=0,t=..), the tail (s=1) and the two sides (t=0/1 at s=0.5).
		Vector3f mid = new Vector3f(ab).lerp(dc, s);
		return mid;
	}

	/**
	 * A delta wing: folded sail, keel, leading-edge trims and twin tail fins (paper plane: creases instead of metal).
	 */
	private static Canopy delta(Smooth s, int a, int b, String deco) {
		boolean paper = deco.equals("paper"), cracks = deco.equals("cracks");
		int skin = cracks ? b : a, trim = cracks ? a : b;
		Canopy top = (u, v, o) -> {
			float t = (v + 1) / 2, z = -2.1F + 3.1F * t, w = 1.95F * t;
			o.set(u * w, Y - 0.06F + 0.18F * Math.abs(u) * t, z);
		};
		s.at(0, 0, 0).color(skin).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 12, 10);
		s.at(0, -0.05F, 0).color(shade(skin, 0.8F)).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 8, 6);
		// Keel under the fold, and the leading edges.
		s.at(0, 0, 0).color(shade(skin, 0.75F)).surface((u, v, o) -> o.set(0, Y - 0.06F - 0.42F * u * (float) Math.sin(Math.PI * Math.min(1, v * 1.1)), -1.6F + 2.4F * v), 2, 8);
		for (float side : new float[] { -1, 1 }) {
			Vector3f p0 = new Vector3f(), p1 = new Vector3f(), p2 = new Vector3f();
			top.top(0, -1, p0);
			top.top(side, 0, p1);
			top.top(side, 1, p2);
			s.at(0, 0, 0).color(trim).tube(new float[][] { { p0.x, p0.y, p0.z }, { p1.x, p1.y, p1.z }, { p2.x, p2.y, p2.z } }, new float[] { 0.02F, paper ? 0.025F : 0.04F, 0.045F });
			if (!paper) {
				// Twin fins near the back.
				final float fx = side * 0.75F;
				s.at(0, 0, 0).color(trim).surface((u, v, o) -> o.set(fx, Y + 0.02F + 0.5F * u * v, 0.1F + 0.85F * v), 3, 5);
			}
		}
		if (paper) {
			// Creases: the folds of a paper plane.
			for (float u : new float[] { -0.55F, 0.55F }) rib(s, top, u, 0.012F, shade(skin, 0.82F), -1);
		}
		struts(s, 0.6F, Y - 0.05F, paper ? shade(a, 0.7F) : DARK);
		return top;
	}

	/** A bat: membranes with a scalloped trailing edge between finger bones, a body with ears and eyes. */
	private static Canopy bat(Smooth s, int a, int b, String deco) {
		int bone = shade(a, 0.55F), skin = a;
		int eyes = deco.equals("fangs") ? 0xFF2A2A : deco.equals("frost") ? 0x7FD8FF : b;
		for (float side : new float[] { -1, 1 }) {
			Vector3f shoulder = new Vector3f(side * 0.25F, Y + 0.02F, -0.3F), wrist = new Vector3f(side * 1.3F, Y + 0.25F, -0.4F);
			Vector3f[] tips = { new Vector3f(side * 2.5F, Y + 0.1F, -0.55F), new Vector3f(side * 2.6F, Y - 0.02F, 0.3F), new Vector3f(side * 1.95F, Y - 0.06F, 1.0F),
				new Vector3f(side * 0.3F, Y, 0.65F) };
			s.at(0, 0, 0).color(bone).tube(new float[][] { { shoulder.x, shoulder.y, shoulder.z }, { wrist.x, wrist.y, wrist.z } }, new float[] { 0.06F, 0.05F });
			for (int k = 0; k < 3; k++)
				s.at(0, 0, 0).color(bone).tube(new float[][] { { wrist.x, wrist.y, wrist.z }, { (wrist.x + tips[k].x) / 2, (wrist.y + tips[k].y) / 2 + 0.06F, (wrist.z + tips[k].z) / 2 },
					{ tips[k].x, tips[k].y, tips[k].z } }, new float[] { 0.04F, 0.03F, 0.012F });
			s.at(wrist.x, wrist.y + 0.05F, wrist.z).color(bone).cone(0.05F, 0.2F, 8); // thumb claw
			// Membranes: each between two bones, the edge scalloped inwards, sagging a little in the middle.
			for (int k = 0; k < 3; k++) {
				final Vector3f p0 = tips[k], p1 = tips[k + 1], w = k == 2 ? new Vector3f(side * 0.35F, Y + 0.02F, -0.2F) : wrist;
				s.at(0, 0, 0).color(skin).surface((u, v, o) -> {
					Vector3f e = new Vector3f(p0).lerp(p1, v);
					float scallop = 0.32F * (float) Math.sin(Math.PI * v);
					e.lerp(w, scallop);
					o.set(w).lerp(e, u);
					o.y -= 0.14F * u * (float) Math.sin(Math.PI * v);
				}, 6, 6);
			}
			// Leading membrane between shoulder, wrist and the first finger.
			final Vector3f t0 = tips[0];
			s.at(0, 0, 0).color(shade(skin, 0.9F)).surface((u, v, o) -> o.set(shoulder).lerp(wrist, u).lerp(new Vector3f(wrist).lerp(t0, u), v * 0.5F), 4, 3);
			// Ear.
			s.at(side * 0.13F, Y + 0.2F, -0.82F).rotZ(-side * 12).color(skin).cone(0.09F, 0.26F, 8);
			s.at(side * 0.09F, Y + 0.08F, -1.0F).color(eyes).blob(0.045F, 0.045F, 0.03F);
		}
		s.at(0, Y, -0.05F).color(shade(skin, 0.85F)).blob(0.3F, 0.22F, 0.62F);
		s.at(0, Y + 0.04F, -0.78F).color(shade(skin, 0.9F)).blob(0.2F, 0.18F, 0.22F);
		s.at(0, Y + 0.0F, -0.98F).color(shade(skin, 0.7F)).blob(0.07F, 0.05F, 0.05F); // nose
		struts(s, 0.35F, Y - 0.1F, DARK);
		return (u, v, o) -> o.set(u * 2.2F, Y - 0.04F * Math.abs(u), v * 0.8F + 0.1F);
	}

	/** A hot-air balloon: gores with load tapes, crown, skirt, a burner with its flame, and ropes to the frame. */
	private static Canopy balloon(Smooth s, int a, int b) {
		float cy = Y + 1.45F, RX = 1.45F, RY = 1.7F, lat0 = -58;
		for (int i = 0; i < 12; i++) s.at(0, cy, 0).scale(RX, RY, RX).color(i % 2 == 0 ? a : b).panel(lat0, 86, 12, 3, i * 30, (i + 1) * 30);
		for (int i = 0; i < 12; i++) {
			double ang = Math.toRadians(i * 30);
			float[][] tape = new float[8][];
			for (int k = 0; k < 8; k++) {
				double lat = Math.toRadians(86 - k * (86 - lat0) / 7);
				tape[k] = new float[] { (float) (RX * 1.006 * Math.cos(lat) * Math.cos(ang)), (float) (cy + RY * 1.006 * Math.sin(lat)), (float) (RX * 1.006 * Math.cos(lat) * Math.sin(ang)) };
			}
			s.at(0, 0, 0).color(shade(b, 0.7F)).tube(tape, new float[] { 0.016F, 0.016F, 0.016F, 0.016F, 0.016F, 0.016F, 0.016F, 0.016F });
		}
		float topY = cy + RY * (float) Math.sin(Math.toRadians(86)), topR = RX * (float) Math.cos(Math.toRadians(86));
		s.at(0, topY, 0).color(shade(b, 0.8F)).lens(topR * 1.4F, topR * 1.4F, 0.06F);
		float mouthY = cy + RY * (float) Math.sin(Math.toRadians(lat0)), mouthR = RX * (float) Math.cos(Math.toRadians(lat0));
		// Skirt: a short cone below the mouth, and its rim.
		s.at(0, 0, 0).color(shade(b, 0.9F)).lathe(new float[] { mouthR * 0.98F, mouthR * 0.8F }, new float[] { mouthY, mouthY - 0.42F }, 18);
		s.at(0, mouthY - 0.42F, 0).color(shade(b, 0.7F)).torus(mouthR * 0.8F, 0.03F, 0, 360);
		// Burner hanging in the mouth, with its flame.
		float by = mouthY - 0.62F;
		s.at(0, 0, 0).color(METAL).lathe(new float[] { 0, 0.2F, 0.22F, 0.16F, 0 }, new float[] { by - 0.12F, by - 0.12F, by, by + 0.08F, by + 0.08F }, 14);
		s.at(0, by + 0.25F, 0).color(0xFF8A1F).blob(0.12F, 0.24F, 0.12F);
		s.at(0, by + 0.2F, 0).color(0xFFE45A).blob(0.07F, 0.15F, 0.07F);
		for (float side : new float[] { -1, 1 })
			for (float fz : new float[] { -1, 1 }) {
				float rx = side * mouthR * 0.78F, rz = fz * mouthR * 0.55F;
				s.at(0, 0, 0).color(0xC9A66B).tube(new float[][] { { rx, mouthY - 0.4F, rz }, { side * BAR_X, BAR_Y, BAR_Z } }, new float[] { 0.018F, 0.018F });
				s.at(0, 0, 0).color(0xC9A66B).tube(new float[][] { { rx, mouthY - 0.4F, rz }, { side * 0.22F, by, 0 } }, new float[] { 0.012F, 0.012F });
			}
		return (u, v, o) -> {
			double lon = Math.atan2(v, u), lat = Math.toRadians(70 - 120 * Math.min(1, Math.sqrt(u * u + v * v)));
			o.set((float) (RX * Math.cos(lat) * Math.cos(lon)), (float) (cy + RY * Math.sin(lat)), (float) (RX * Math.cos(lat) * Math.sin(lon)));
		};
	}

	/** A leaf: cupped blade (lobed for the maple), midrib and veins, a curling stem. */
	private static Canopy leaf(Smooth s, int a, int b, String deco) {
		boolean maple = deco.equals("maple");
		Canopy top = (u, v, o) -> {
			float t = (v + 1) / 2;
			float w = 1.0F * (float) Math.pow(Math.sin(Math.PI * Math.pow(t, 0.8)), 0.85);
			if (maple) w *= 1 + 0.32F * (float) Math.max(0, Math.sin(t * Math.PI * 5 - 0.6)) - 0.12F;
			float x = u * w;
			o.set(x, Y + 0.22F * u * u - 0.32F * (1 - t) * (1 - t) + 0.05F * (float) Math.sin(Math.PI * t), -2.1F + 4.0F * t);
		};
		s.at(0, 0, 0).color(a).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 10, maple ? 30 : 16);
		s.at(0, -0.04F, 0).color(shade(a, 0.85F)).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 6, 10);
		rib(s, top, 0, 0.04F, b, -1);
		for (int k = 1; k <= 5; k++) {
			float v0 = -0.75F + k * 0.27F;
			for (float side : new float[] { -1, 1 }) {
				Vector3f p0 = new Vector3f(), p1 = new Vector3f(), p2 = new Vector3f();
				top.top(0, v0, p0);
				top.top(side * 0.5F, v0 - 0.12F, p1);
				top.top(side * 0.92F, v0 - 0.2F, p2);
				s.at(0, 0, 0).color(b).tube(new float[][] { { p0.x, p0.y + 0.02F, p0.z }, { p1.x, p1.y + 0.02F, p1.z }, { p2.x, p2.y + 0.015F, p2.z } }, new float[] { 0.022F, 0.016F, 0.008F });
			}
		}
		s.at(0, 0, 0).color(shade(b, 0.9F)).tube(new float[][] { { 0, Y + 0.05F, 1.9F }, { 0, Y - 0.02F, 2.35F }, { 0.12F, Y - 0.14F, 2.6F }, { 0.08F, Y - 0.3F, 2.68F } },
			new float[] { 0.06F, 0.05F, 0.04F, 0.03F });
		struts(s, 0.55F, Y - 0.05F, DARK);
		return top;
	}

	/**
	 * Dominus wings, one free for each Dominus hat in the Locker (creator), in that hat's colours: feathered wings
	 * (flight feathers fanning out longer towards the tips, a row of trim-coloured coverts over them, a feather shaft
	 * down each) with the Dominus hood at the front - its dark face, trimmed rim and crest, and feather ears, or horns
	 * for Infernus and Rex, as on the hat. Each has its own touch (deco): Empyreus a halo, Frigidus icicles, Aureus
	 * gems, Infernus glowing eyes and embers, Rex a jewel and stars, Vespertilio glowing eyes and veins.
	 */
	private static Canopy dominus(Smooth s, int a, int b, String deco) {
		Canopy top = (u, v, o) -> {
			float au = Math.abs(u), t = (v + 1) / 2;
			float zl = -0.5F + 0.3F * au, zt = 0.3F + 0.05F * au;
			o.set(u * 2.35F, Y + 0.42F * au * (float) Math.sqrt(au) + 0.07F * (float) Math.sin(Math.PI * t), zl + (zt - zl) * t);
		};
		s.at(0, 0, 0).color(a).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 20, 5);
		s.at(0, -0.06F, 0).color(shade(a, 0.78F)).surface((u, v, o) -> top.top(u * 2 - 1, v * 2 - 1, o), 16, 4);
		edge(s, top, -1, 0.08F, b);
		int tip = switch (deco) {
			case "frigidus" -> WHITE;
			case "infernus" -> 0xFF8A1F;
			case "vespertilio" -> 0x9B4DFF;
			default -> -1;
		};
		Vector3f p = new Vector3f();
		for (float side : new float[] { -1, 1 }) {
			// Flight feathers along the back edge, longer towards the tip and fanning out.
			for (int i = 0; i < 12; i++) {
				float au = 0.1F + 0.9F * i / 11F;
				top.top(side * au, 0.8F, p);
				feather(s, p, side * (6 + 40 * au * au), 0.5F + 0.6F * au * au, 0.13F + 0.03F * au, i % 2 == 0 ? a : shade(a, 0.9F), tip, shade(b, 0.85F));
			}
			// Coverts: shorter feathers in the trim colour over the roots of the flight feathers.
			for (int i = 0; i < 11; i++) {
				float au = 0.08F + 0.84F * i / 10F;
				top.top(side * au, 0.3F, p);
				p.y += 0.02F;
				feather(s, p, side * (4 + 30 * au * au), 0.4F + 0.12F * au, 0.14F, i % 2 == 0 ? b : shade(b, 0.9F), -1, -1);
			}
		}
		s.at(0, Y - 0.08F, 0).color(shade(a, 0.85F)).blob(0.2F, 0.1F, 0.5F);
		// The Dominus hood at the front: dark face with a trimmed rim, a crest over the top, and a drape down the back.
		float hy = Y + 0.3F, hz = -0.42F, fz = hz - 0.39F;
		s.at(0, hy, hz).color(a).blob(0.4F, 0.46F, 0.4F);
		s.at(0, hy - 0.18F, hz + 0.3F).color(a).blob(0.34F, 0.3F, 0.42F);
		s.at(0, hy - 0.02F, fz).rotX(90).color(0x07070B).lens(0.25F, 0.31F, 0.04F);
		s.at(0, hy - 0.02F, fz - 0.005F).rotX(90).scale(1, 1, 1.24F).color(b).torus(0.27F, 0.035F, 0, 360);
		float[][] crest = new float[7][];
		for (int i = 0; i <= 6; i++) {
			double t = Math.toRadians(25 + 130 * i / 6.0);
			crest[i] = new float[] { 0, hy + 0.05F + (float) Math.sin(t) * 0.47F, hz - (float) Math.cos(t) * 0.42F };
		}
		s.at(0, 0, 0).color(b).tube(crest, new float[] { 0.045F, 0.05F, 0.055F, 0.055F, 0.05F, 0.045F, 0.04F });
		boolean horns = deco.equals("infernus") || deco.equals("rex");
		for (float side : new float[] { -1, 1 }) {
			float x = side * 0.36F;
			if (horns)
				s.at(0, 0, 0).color(b).tube(new float[][] { { x, hy + 0.1F, hz }, { x + side * 0.2F, hy + 0.28F, hz + 0.12F }, { x + side * 0.24F, hy + 0.6F, hz + 0.28F },
					{ x + side * 0.14F, hy + 0.85F, hz + 0.4F } }, new float[] { 0.1F, 0.08F, 0.045F, 0 });
			else for (int k = 0; k < 3; k++)
				s.at(x, hy + 0.08F + 0.12F * k, hz + 0.12F).rotY(-side * 35).rotZ(side * (12 + 20 * k)).move(side * 0.3F, 0, 0).rotX(90).color(b).lens(0.36F - 0.05F * k, 0.07F, 0.03F);
		}
		int eyes = deco.equals("infernus") ? 0xFF8A1F : deco.equals("vespertilio") ? 0xB46BFF : -1;
		if (eyes >= 0) for (float side : new float[] { -1, 1 }) s.at(side * 0.09F, hy + 0.03F, fz - 0.04F).rotX(90).color(eyes).lens(0.055F, 0.028F, 0.02F);
		switch (deco) {
			case "empyreus" -> {
				s.at(0, hy + 0.72F, hz + 0.08F).rotX(-15).color(GOLD).torus(0.28F, 0.03F, 0, 360); // a halo over the hood
				for (float side : new float[] { -1, 1 }) {
					sparkle(s, top, side * 0.5F, -0.3F, 0.13F, 0xFFF6C8);
					sparkle(s, top, side * 0.82F, -0.1F, 0.1F, 0xFFF6C8);
				}
			}
			case "frigidus" -> {
				// Icicles under the front edge, frost on each wing.
				for (int i = 0; i < 10; i++) {
					float u = -0.9F + i * 0.2F;
					if (Math.abs(u) < 0.25F) continue;
					top.top(u, -0.95F, p);
					s.at(p.x, p.y - 0.06F, p.z).rotX(180).color(0xE6F7FF).cone(0.04F, 0.14F + 0.1F * (i % 3), 8);
				}
				for (float side : new float[] { -1, 1 })
					for (boolean under : new boolean[] { false, true })
						for (int k = 0; k < 3; k++) spot(s, top, side * 0.6F, -0.4F, 0.03F, under).rotY(k * 60).color(WHITE).lens(0.18F, 0.025F, 0.02F);
			}
			case "aureus" -> {
				// Jewels along the front edge and a shine on each wing.
				for (int i = 0; i < 6; i++) {
					top.top((i < 3 ? -1 : 1) * (0.3F + 0.25F * (i % 3)), -0.7F, p);
					s.at(p.x, p.y + 0.05F, p.z).color(i % 2 == 0 ? 0xD7263D : 0x2F6BD8).blob(0.06F, 0.05F, 0.06F);
				}
				for (int i = 0; i < 4; i++) sparkle(s, top, -0.75F + i * 0.5F, -0.2F, 0.12F, WHITE);
			}
			case "infernus" -> {
				// Embers drifting off the burning feather tips.
				for (int i = 0; i < 8; i++) {
					float u = (i % 2 == 0 ? -1 : 1) * (0.3F + 0.18F * (i / 2));
					top.top(u, 1, p);
					s.at(p.x + u * 0.25F, p.y - 0.1F - 0.05F * (i % 3), p.z + 0.75F + 0.12F * (i % 3)).color(i % 3 == 0 ? 0xFFD23A : 0xFF6A1F).blob(0.045F, 0.045F, 0.045F);
				}
			}
			case "rex" -> {
				// A jewel set in the hood over the face, and stars on the wings.
				s.at(0, hy + 0.36F, fz + 0.1F).color(b).blob(0.07F, 0.08F, 0.06F);
				s.at(0, hy + 0.36F, fz + 0.1F).rotX(70).color(GOLD).torus(0.085F, 0.022F, 0, 360);
				for (float side : new float[] { -1, 1 }) {
					star(s, top, side * 0.6F, -0.3F, 0.16F, STAR);
				}
			}
			case "vespertilio" -> {
				// Glowing veins across the dark wings.
				for (float side : new float[] { -1, 1 }) {
					stripe(s, top, side * 0.15F, -0.6F, side * 0.85F, 0.2F, 0.025F, b);
					stripe(s, top, side * 0.35F, -0.65F, side * 0.55F, 0.45F, 0.02F, b);
				}
			}
			default -> {
			}
		}
		struts(s, 0.55F, Y - 0.04F, shade(b, 0.8F));
		return top;
	}

	/**
	 * A feather lying back from its root at the given turn (degrees, + towards +x), drooping a little; an optional
	 * coloured tip and shaft (-1 = none).
	 */
	private static void feather(Smooth s, Vector3f root, float yaw, float len, float w, int color, int tip, int shaft) {
		double r = Math.toRadians(yaw);
		float dx = (float) Math.sin(r), dz = (float) Math.cos(r);
		s.at(root.x + dx * len * 0.5F, root.y - 0.015F, root.z + dz * len * 0.5F).rotY(yaw).rotX(7).color(color).lens(w, len * 0.5F, 0.022F);
		if (tip >= 0) s.at(root.x + dx * len * 0.5F, root.y - 0.015F, root.z + dz * len * 0.5F).rotY(yaw).rotX(7).move(0, 0.006F, len * 0.36F).color(tip).lens(w * 0.75F, len * 0.15F, 0.024F);
		if (shaft >= 0)
			s.at(root.x + dx * len * 0.5F, root.y - 0.015F, root.z + dz * len * 0.5F).rotY(yaw).rotX(7).color(shaft).tube(new float[][] { { 0, 0.02F, -len * 0.5F }, { 0, 0.02F, len * 0.42F } },
				new float[] { 0.014F, 0.008F }, false, 4);
	}

	// ---- decorations ----

	/**
	 * Moves s to a point on the canopy (u, v), `lift` along its surface normal, turned so its own y is that normal;
	 * under = the underside.
	 */
	private static Smooth spot(Smooth s, Canopy c, float u, float v, float lift, boolean under) {
		Vector3f p = new Vector3f(), a = new Vector3f(), b = new Vector3f();
		c.top(u, v, p);
		float e = 0.03F;
		c.top(u + e, v, a);
		c.top(u - e, v, b);
		Vector3f du = new Vector3f(a).sub(b);
		c.top(u, v + e, a);
		c.top(u, v - e, b);
		Vector3f n = new Vector3f(a).sub(b).cross(du);
		if (n.lengthSquared() < 1e-10F) n.set(0, 1, 0);
		n.normalize();
		if (n.y < 0) n.negate();
		if (under) n.negate();
		float lt = under ? lift + 0.08F : lift;
		return s.at(p.x + n.x * lt, p.y + n.y * lt, p.z + n.z * lt).align(n.x, n.y, n.z);
	}

	/** A flat spot of colour on the canopy, on top and underneath. */
	private static void disc(Smooth s, Canopy c, float u, float v, float r, int color) {
		for (boolean under : new boolean[] { false, true }) spot(s, c, u, v, 0.02F, under).color(color).lens(r, r, 0.02F);
	}

	/** A four-pointed sparkle star, on top and underneath. */
	private static void sparkle(Smooth s, Canopy c, float u, float v, float r, int color) {
		for (boolean under : new boolean[] { false, true }) {
			spot(s, c, u, v, 0.03F, under).color(color).lens(r, r * 0.22F, 0.02F);
			spot(s, c, u, v, 0.03F, under).rotY(90).color(color).lens(r, r * 0.22F, 0.02F);
			spot(s, c, u, v, 0.035F, under).color(WHITE).lens(r * 0.25F, r * 0.25F, 0.025F);
		}
	}

	/** A five-pointed star, on top and underneath. */
	private static void star(Smooth s, Canopy c, float u, float v, float r, int color) {
		for (boolean under : new boolean[] { false, true }) {
			for (int k = 0; k < 5; k++) spot(s, c, u, v, 0.03F, under).rotY(k * 72).move(0, 0, -r * 0.5F).color(color).lens(r * 0.2F, r * 0.55F, 0.02F);
			spot(s, c, u, v, 0.032F, under).color(color).lens(r * 0.4F, r * 0.4F, 0.022F);
		}
	}

	/** A stripe from (u0, v0) to (u1, v1) on the canopy, on top and underneath. */
	private static void stripe(Smooth s, Canopy c, float u0, float v0, float u1, float v1, float r, int color) {
		for (boolean under : new boolean[] { false, true }) {
			// (9 points along it, so it follows the canopy's curve instead of cutting through it between points)
			float[][] pts = new float[9][];
			float[] radii = new float[9];
			for (int i = 0; i < 9; i++) {
				float t = i / 8F;
				Vector3f p = new Vector3f(), n = new Vector3f(0, 1, 0);
				c.top(u0 + (u1 - u0) * t, v0 + (v1 - v0) * t, p);
				// (A thick stripe is lifted until its flattened underside clears the canopy: level with it, the two
				// flickered when seen from below.)
				float lift = under ? -0.1F : Math.max(0.03F, r * 0.35F + 0.008F);
				pts[i] = new float[] { p.x + n.x * lift, p.y + n.y * lift, p.z + n.z * lift };
				radii[i] = r;
			}
			s.at(0, 0, 0).color(color).scale(1, 0.35F, 1).tube(flatten(pts), radii, false, 6);
		}
	}

	/** (tube points are scaled by 0.35 in y by the caller's transform: undo it for the positions) */
	private static float[][] flatten(float[][] pts) {
		for (float[] p : pts) p[1] /= 0.35F;
		return pts;
	}

	private static void decorate(Smooth s, GliderDef g, Canopy c, int a, int b) {
		switch (g.deco()) {
			case "chevron" -> {
				for (float side : new float[] { -1, 1 }) {
					stripe(s, c, 0, -0.3F, side * 0.7F, 0.45F, 0.07F, b);
					stripe(s, c, 0, 0.15F, side * 0.55F, 0.8F, 0.045F, b);
				}
			}
			case "stripes" -> {
				for (float u : new float[] { -0.62F, -0.52F, 0.52F, 0.62F }) stripe(s, c, u, -0.9F, u, 0.9F, 0.04F, b);
			}
			case "dots" -> {
				float[][] at = { { -0.8F, -0.2F }, { -0.55F, 0.4F }, { -0.3F, -0.5F }, { 0, 0.3F }, { 0.3F, -0.5F }, { 0.55F, 0.4F }, { 0.8F, -0.2F }, { -0.15F, -0.05F }, { 0.15F, -0.05F } };
				for (float[] p : at) disc(s, c, p[0], p[1], 0.12F, b);
			}
			case "stars" -> {
				float[][] at = { { -0.7F, 0 }, { -0.35F, -0.4F }, { 0, 0.15F }, { 0.35F, -0.4F }, { 0.7F, 0 }, { -0.45F, 0.5F }, { 0.45F, 0.5F } };
				for (int i = 0; i < at.length; i++) star(s, c, at[i][0], at[i][1], i % 2 == 0 ? 0.22F : 0.15F, STAR);
			}
			case "flames" -> {
				// Flames licking back from the trailing edge, and flame shapes on the wing.
				for (int i = 0; i < 7; i++) {
					float u = -0.75F + i * 0.25F;
					Vector3f p = new Vector3f();
					c.top(u, 1, p);
					float len = 0.45F + 0.2F * (float) Math.abs(Math.sin(i * 1.7));
					s.at(0, 0, 0).color(0xFF6A1F).tube(new float[][] { { p.x, p.y - 0.03F, p.z }, { p.x + 0.06F, p.y - 0.06F, p.z + len * 0.5F }, { p.x - 0.03F, p.y - 0.08F, p.z + len } },
						new float[] { 0.07F, 0.05F, 0 });
					s.at(0, 0, 0).color(0xFFD23A).tube(new float[][] { { p.x, p.y - 0.03F, p.z + 0.02F }, { p.x + 0.03F, p.y - 0.05F, p.z + len * 0.45F } }, new float[] { 0.035F, 0 });
				}
				for (float side : new float[] { -1, 1 })
					for (int k = 0; k < 3; k++) stripe(s, c, side * (0.2F + 0.22F * k), 0.55F, side * (0.3F + 0.25F * k), -0.35F + 0.12F * k, 0.06F, k == 1 ? 0xFFD23A : b);
			}
			case "bolt" -> {
				for (float side : new float[] { -1, 1 }) {
					float[][] zig = { { side * 0.25F, -0.55F }, { side * 0.45F, -0.2F }, { side * 0.32F, -0.15F }, { side * 0.55F, 0.35F } };
					for (int k = 0; k < 3; k++) stripe(s, c, zig[k][0], zig[k][1], zig[k + 1][0], zig[k + 1][1], 0.06F, 0xFFE45A);
				}
			}
			case "skull" -> {
				// On the back and the front of the canopy, where everyone sees it (the top is the vent).
				skull(s, c, 0, 0.55F, 1);
				skull(s, c, 0, -0.55F, -1);
			}
			case "crown" -> crown(s, c, a, b);
			case "ornate" -> {
				for (int i = 0; i < 8; i++) {
					// Tassels hanging from the rib tips.
					double ang = Math.toRadians(i * 45);
					float x = (float) Math.cos(ang) * 1.8F, z = (float) Math.sin(ang) * 1.8F, y = Y - 0.4F + 0.85F * (float) Math.sin(Math.toRadians(6)) - 0.08F;
					s.at(0, 0, 0).color(GOLD).tube(new float[][] { { x, y, z }, { x, y - 0.28F, z } }, new float[] { 0.012F, 0.012F });
					s.at(x, y - 0.32F, z).color(0xD7263D).cone(0.05F, 0.12F, 8);
				}
				for (int i = 0; i < 6; i++) sparkle(s, c, (float) Math.cos(i * 1.05) * 0.55F, (float) Math.sin(i * 1.05) * 0.55F, 0.16F, 0xFFF6C8);
			}
			case "sprinkles" -> {
				int[] cols = { 0xFF4FD8, 0x29E3FF, 0xFFD21F, 0x7BF24D, 0xFF8A1F, WHITE };
				for (int i = 0; i < 18; i++) {
					float r = 0.15F + 0.75F * (float) ((i * 0.618) % 1), ang = i * 2.4F;
					for (boolean under : new boolean[] { false, true })
						spot(s, c, (float) Math.cos(ang) * r, (float) Math.sin(ang) * r, 0.03F, under).rotY(i * 47).color(cols[i % cols.length]).lens(0.11F, 0.035F, 0.025F);
				}
			}
			case "frost" -> {
				float[][] at = { { -0.6F, 0 }, { 0, -0.3F }, { 0.6F, 0 }, { -0.25F, 0.45F }, { 0.3F, 0.45F } };
				for (float[] p : at)
					for (boolean under : new boolean[] { false, true })
						for (int k = 0; k < 3; k++) spot(s, c, p[0], p[1], 0.03F, under).rotY(k * 60).color(WHITE).lens(0.22F, 0.03F, 0.02F);
				// Icicles hanging from the front edge.
				for (int i = 0; i < 9; i++) {
					Vector3f p = new Vector3f();
					c.top(-0.85F + i * 0.21F, -0.95F, p);
					s.at(p.x, p.y - 0.02F, p.z).rotX(180).color(0xCFF0FF).cone(0.045F, 0.16F + 0.12F * (i % 3), 8);
				}
			}
			case "bee" -> {
				for (float v : new float[] { -0.45F, -0.05F, 0.35F }) stripe(s, c, -0.95F + Math.abs(v) * 0.6F, v, 0.95F - Math.abs(v) * 0.6F, v, 0.09F, b);
				Vector3f nose = new Vector3f();
				c.top(0, -1, nose);
				for (float side : new float[] { -1, 1 })
					s.at(0, 0, 0).color(b).tube(new float[][] { { nose.x, nose.y, nose.z }, { side * 0.15F, nose.y + 0.3F, nose.z - 0.2F }, { side * 0.3F, nose.y + 0.45F, nose.z - 0.15F } },
						new float[] { 0.025F, 0.02F, 0.02F });
				for (float side : new float[] { -1, 1 }) s.at(side * 0.3F, nose.y + 0.47F, nose.z - 0.15F).color(b).blob(0.06F, 0.06F, 0.06F);
				for (float side : new float[] { -1, 1 })
					for (boolean under : new boolean[] { false, true }) spot(s, c, side * 0.45F, 0.1F, 0.04F, under).rotY(side * 20).color(0xEAF6FF).lens(0.32F, 0.16F, 0.02F); // wings
			}
			case "dragon" -> dragon(s, c, a, b);
			case "jet" -> {
				for (float side : new float[] { -1, 1 }) engine(s, side * 0.78F, Y - 0.24F, b);
			}
			case "camo" -> {
				int[] cols = { shade(a, 0.65F), shade(a, 1.25F), 0x5C4A2E };
				for (int i = 0; i < 14; i++) {
					float r = 0.2F + 0.7F * (float) ((i * 0.618) % 1), ang = i * 2.4F;
					for (boolean under : new boolean[] { false, true })
						spot(s, c, (float) Math.cos(ang) * r, (float) Math.sin(ang) * r, 0.025F, under).rotY(i * 33).color(cols[i % 3]).lens(0.32F, 0.17F, 0.018F);
				}
			}
			case "clouds" -> {
				for (int i = 0; i < 5; i++) {
					double ang = Math.toRadians(i * 72 + 20);
					float x = (float) Math.cos(ang) * 1.55F, z = (float) Math.sin(ang) * 1.55F, y = Y + 0.6F + 0.25F * (i % 2);
					for (int k = 0; k < 4; k++)
						s.at(x + (k - 1.5F) * 0.2F * (float) -Math.sin(ang), y + (k % 2) * 0.1F, z + (k - 1.5F) * 0.2F * (float) Math.cos(ang)).color(WHITE).blob(0.22F, 0.17F, 0.22F);
				}
			}
			case "party" -> {
				int[] cols = { 0xFFD21F, 0x29E3FF, 0x7BF24D, WHITE, 0xFF8A1F };
				for (int i = 0; i < 16; i++) {
					double lon = i * 2.4, lat = Math.toRadians(-30 + 90 * ((i * 0.618) % 1));
					float x = (float) (1.47 * Math.cos(lat) * Math.cos(lon)), y = (float) (Y + 1.45F + 1.72 * Math.sin(lat)), z = (float) (1.47 * Math.cos(lat) * Math.sin(lon));
					s.at(x, y, z).align(x / 1.45F, (y - Y - 1.45F) / 1.7F, z / 1.45F).color(cols[i % cols.length]).lens(0.13F, 0.13F, 0.02F);
				}
				// Streamers from the skirt.
				for (int i = 0; i < 4; i++) {
					double ang = Math.toRadians(45 + i * 90);
					float x = (float) Math.cos(ang) * 0.7F, z = (float) Math.sin(ang) * 0.7F, y = Y + 1.45F - 1.45F - 0.4F;
					s.at(0, 0, 0).color(cols[i]).tube(new float[][] { { x, y, z }, { x * 1.1F + 0.1F, y - 0.35F, z * 1.1F }, { x * 1.2F - 0.1F, y - 0.7F, z * 1.2F } }, new float[] { 0.03F, 0.025F, 0.015F });
				}
			}
			case "banner" -> {
				float y = Y + 1.45F;
				s.at(0, y, 0).color(GOLD).scale(1, 1, 1).torus(1.47F, 0.07F, 0, 360);
				for (int i = 0; i < 4; i++) {
					double ang = Math.toRadians(45 + i * 90);
					float x = (float) Math.cos(ang) * 0.95F, z = (float) Math.sin(ang) * 0.95F;
					s.at(x, BAR_Y + 0.9F, z * 0.6F).color(0xC9A66B).blob(0.13F, 0.16F, 0.13F); // sandbags on the ropes
				}
			}
			case "neon" -> {
				// Glowing stripes along the wing (the wing itself is the dark colour, the lines are the neon one).
				for (float side : new float[] { -1, 1 }) stripe(s, c, side * 0.12F, -0.7F, side * 0.9F, 0.2F, 0.035F, a);
				stripe(s, c, -0.9F, 0.55F, 0.9F, 0.55F, 0.03F, a);
			}
			case "fangs" -> {
				for (float side : new float[] { -1, 1 }) s.at(side * 0.07F, Y - 0.1F, -0.95F).rotX(180).color(WHITE).cone(0.035F, 0.14F, 8);
				// A high collar behind the head, in the dark red.
				s.at(0, 0, 0).color(shade(a, 1.2F)).surface((u, v, o) -> o.set((u * 2 - 1) * 0.42F, Y + 0.05F + 0.4F * v, -0.55F + 0.15F * v - 0.2F * (u * 2 - 1) * (u * 2 - 1)), 6, 3);
			}
			case "waves" -> {
				for (int i = 0; i < 12; i++) {
					double ang = i * Math.PI / 6;
					for (boolean under : new boolean[] { false, true })
						spot(s, c, (float) Math.cos(ang) * 0.72F, (float) Math.sin(ang) * 0.72F, 0.025F, under).rotY(-(float) Math.toDegrees(ang)).color(b == WHITE || b == 0xF4F4F4 ? WHITE : b)
							.lens(0.08F, 0.3F, 0.02F);
				}
			}
			case "face" -> {
				for (boolean under : new boolean[] { false, true }) {
					for (float side : new float[] { -1, 1 }) spot(s, c, side * 0.18F, -0.15F, 0.03F, under).color(0x1C1C22).lens(0.11F, 0.16F, 0.025F);
					spot(s, c, 0, 0.35F, 0.03F, under).color(0x1C1C22).lens(0.09F, 0.11F, 0.025F);
				}
				// Wisps trailing behind, like a ghost's tail.
				for (int i = 0; i < 3; i++) {
					Vector3f p = new Vector3f();
					c.top(-0.4F + 0.4F * i, 1, p);
					s.at(0, 0, 0).color(shade(a, 0.95F)).tube(new float[][] { { p.x, p.y - 0.03F, p.z }, { p.x + 0.12F, p.y - 0.1F, p.z + 0.45F }, { p.x - 0.1F, p.y - 0.18F, p.z + 0.9F } },
						new float[] { 0.09F, 0.06F, 0 });
				}
			}
			case "cracks" -> {
				float[][] lines = { { -0.6F, -0.2F, -0.25F, 0.1F }, { -0.25F, 0.1F, -0.4F, 0.6F }, { 0.1F, -0.6F, 0.35F, 0 }, { 0.35F, 0, 0.6F, 0.55F }, { -0.05F, 0.3F, 0.2F, 0.8F } };
				for (int k = 0; k < lines.length; k++) stripe(s, c, lines[k][0], lines[k][1], lines[k][2], lines[k][3], 0.045F, k % 2 == 0 ? 0xFFD23A : a);
			}
			case "panels" -> {
				for (float v : new float[] { -0.3F, 0.3F }) stripe(s, c, -0.9F, v, 0.9F, v, 0.015F, b);
				for (float u : new float[] { -0.5F, 0.5F }) stripe(s, c, u, -0.6F, u, 0.95F, 0.015F, b);
				for (float side : new float[] { -1, 1 }) disc(s, c, side * 0.3F, -0.55F, 0.08F, 0x101012);
			}
			case "facets" -> {
				float[][] at = { { -0.55F, 0.4F }, { 0, -0.2F }, { 0.55F, 0.4F }, { -0.25F, 0.6F }, { 0.25F, 0.6F } };
				for (float[] p : at) {
					spot(s, c, p[0], p[1], 0.02F, false).color(b).lathe(new float[] { 0, 0.14F, 0 }, new float[] { 0, 0.06F, 0.22F }, 6);
					spot(s, c, p[0], p[1], 0.02F, true).color(b).lathe(new float[] { 0, 0.14F, 0 }, new float[] { 0, 0.06F, 0.22F }, 6);
				}
				for (int i = 0; i < 4; i++) sparkle(s, c, -0.7F + i * 0.47F, 0.1F, 0.12F, WHITE);
			}
			case "galaxy" -> {
				for (int i = 0; i < 10; i++) {
					float u = -0.85F + 1.7F * (float) ((i * 0.618) % 1), v = -0.8F + 1.6F * (float) ((i * 0.382 + 0.2) % 1);
					if (i % 3 == 0) star(s, c, u, v, 0.14F, STAR);
					else sparkle(s, c, u, v, 0.1F, i % 2 == 0 ? b : WHITE);
				}
				// A ringed planet riding on top.
				Vector3f p = new Vector3f();
				c.top(0, 0.1F, p);
				s.at(p.x, p.y + 0.3F, p.z).color(b).blob(0.2F, 0.2F, 0.2F);
				s.at(p.x, p.y + 0.3F, p.z).rotZ(18).color(STAR).torus(0.33F, 0.025F, 0, 360);
			}
			case "legendary" -> {
				for (float side : new float[] { -1, 1 }) {
					stripe(s, c, 0, -0.35F, side * 0.75F, 0.4F, 0.08F, b);
					stripe(s, c, 0, 0.05F, side * 0.6F, 0.75F, 0.05F, WHITE);
				}
				for (int i = 0; i < 6; i++) sparkle(s, c, -0.8F + i * 0.32F, i % 2 == 0 ? -0.55F : 0.65F, 0.13F, 0xFFF6C8);
				star(s, c, 0, -0.55F, 0.22F, WHITE);
			}
			case "drips" -> {
				for (int i = 0; i < 8; i++) {
					Vector3f p = new Vector3f();
					c.top((i % 2 == 0 ? -0.6F : 0.6F) * (0.5F + 0.5F * (i % 3) / 2F), -0.7F + i * 0.2F, p);
					float len = 0.15F + 0.1F * (i % 3);
					s.at(0, 0, 0).color(i % 2 == 0 ? b : 0x9BF24D).tube(new float[][] { { p.x, p.y - 0.04F, p.z }, { p.x, p.y - 0.04F - len, p.z } }, new float[] { 0.02F, 0.035F });
					s.at(p.x, p.y - 0.08F - len, p.z).color(i % 2 == 0 ? b : 0x9BF24D).blob(0.055F, 0.075F, 0.055F);
				}
				for (int i = 0; i < 5; i++) disc(s, c, -0.5F + i * 0.25F, (i % 2 == 0 ? 0.3F : -0.35F), 0.08F, 0x9BF24D);
			}
			default -> {
			}
		}
	}

	/**
	 * A white skull with crossbones on the canopy, top and underneath (pirate); dir: which way its forehead points
	 * (towards smaller v = 1).
	 */
	private static void skull(Smooth s, Canopy c, float u, float v, float dir) {
		for (boolean under : new boolean[] { false, true }) {
			for (float side : new float[] { -1, 1 }) {
				spot(s, c, u, v, 0.025F, under).rotY(side * 45).color(WHITE).lens(0.7F, 0.07F, 0.02F);
				for (float end : new float[] { -1, 1 })
					spot(s, c, u, v, 0.03F, under).rotY(side * 45).move(end * 0.68F, 0, 0).color(WHITE).lens(0.1F, 0.1F, 0.025F);
			}
			spot(s, c, u, v - 0.06F * dir, 0.05F, under).color(WHITE).lens(0.36F, 0.32F, 0.03F);
			spot(s, c, u, v + 0.17F * dir, 0.05F, under).color(WHITE).lens(0.22F, 0.14F, 0.03F);
			for (float side : new float[] { -1, 1 }) spot(s, c, u + side * 0.06F, v - 0.06F * dir, 0.075F, under).color(0x1C1C22).lens(0.09F, 0.1F, 0.02F);
			spot(s, c, u, v + 0.05F * dir, 0.075F, under).color(0x1C1C22).lens(0.04F, 0.05F, 0.02F);
		}
	}

	/** A gold crown standing on top of the glider, with jewels, and a crown badge underneath. */
	private static void crown(Smooth s, Canopy c, int a, int b) {
		Vector3f p = new Vector3f();
		c.top(0, 0.05F, p);
		float y = p.y + 0.05F;
		s.at(p.x, y, p.z).color(GOLD).lathe(new float[] { 0.3F, 0.34F, 0.34F, 0.3F }, new float[] { 0, 0.02F, 0.16F, 0.18F }, 20);
		for (int k = 0; k < 5; k++) {
			double ang = Math.toRadians(k * 72);
			float x = p.x + (float) Math.cos(ang) * 0.3F, z = p.z + (float) Math.sin(ang) * 0.3F;
			s.at(x, y + 0.16F, z).color(GOLD).cone(0.07F, 0.2F, 8);
			s.at(x, y + 0.38F, z).color(WHITE).lens(0.035F, 0.035F, 0.035F);
			s.at(p.x + (float) Math.cos(ang + 0.63) * 0.335F, y + 0.09F, p.z + (float) Math.sin(ang + 0.63) * 0.335F).color(k % 2 == 0 ? 0xD7263D : 0x2F6BD8).blob(0.04F, 0.04F, 0.03F);
		}
		for (int k = 0; k < 3; k++) spot(s, c, -0.18F + k * 0.18F, 0.05F, 0.02F, true).color(GOLD).lens(0.07F, 0.18F, 0.02F);
		spot(s, c, 0, 0.18F, 0.025F, true).color(GOLD).lens(0.3F, 0.06F, 0.02F);
	}

	/** A dragon's head at the kite's nose (horns, eyes, snout) and spines along its spine. */
	private static void dragon(Smooth s, Canopy c, int a, int b) {
		Vector3f nose = new Vector3f();
		c.top(0, -1, nose);
		s.at(nose.x, nose.y + 0.12F, nose.z - 0.15F).color(a).blob(0.26F, 0.2F, 0.32F);
		s.at(nose.x, nose.y + 0.06F, nose.z - 0.48F).color(shade(a, 0.9F)).blob(0.16F, 0.12F, 0.22F);
		for (float side : new float[] { -1, 1 }) {
			s.at(0, 0, 0).color(b).tube(new float[][] { { side * 0.14F, nose.y + 0.25F, nose.z - 0.05F }, { side * 0.24F, nose.y + 0.45F, nose.z + 0.15F }, { side * 0.22F, nose.y + 0.55F, nose.z + 0.4F } },
				new float[] { 0.05F, 0.03F, 0 });
			s.at(side * 0.13F, nose.y + 0.2F, nose.z - 0.36F).color(0xFFD23A).blob(0.05F, 0.04F, 0.04F);
			s.at(side * 0.06F, nose.y + 0.08F, nose.z - 0.69F).color(0x1C1C22).lens(0.025F, 0.02F, 0.02F);
		}
		for (int k = 0; k < 6; k++) {
			Vector3f p = new Vector3f();
			c.top(0, -0.6F + k * 0.3F, p);
			s.at(p.x, p.y + 0.03F, p.z).rotX(-25).color(b).cone(0.07F, 0.2F - 0.02F * k, 6);
		}
	}

	/** A jet engine pod lying along z under the wing: intake, body, glowing exhaust. */
	private static void engine(Smooth s, float x, float y, int b) {
		s.at(x, y, 0.1F).rotX(90).color(METAL).lathe(new float[] { 0, 0.15F, 0.18F, 0.18F, 0.14F, 0 }, new float[] { -0.62F, -0.62F, -0.5F, 0.45F, 0.6F, 0.6F }, 14);
		s.at(x, y, -0.53F).rotX(90).color(0x16161A).lathe(new float[] { 0, 0.13F, 0 }, new float[] { -0.02F, 0, 0.02F }, 14);
		s.at(x, y, 0.72F).color(0xFF8A1F).blob(0.11F, 0.11F, 0.14F);
		s.at(x, y, 0.82F).color(0xFFE45A).blob(0.06F, 0.06F, 0.1F);
		s.at(x, y + 0.08F, 0.3F).color(b).blob(0.04F, 0.16F, 0.25F); // pylon
	}

	private static int shade(int rgb, float k) {
		int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)), bl = Math.min(255, Math.round((rgb & 255) * k));
		return r << 16 | g << 8 | bl;
	}
}
