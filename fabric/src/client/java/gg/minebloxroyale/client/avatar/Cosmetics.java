package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.AvatarPartDef;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Locker cosmetics on the Roblox avatar (cosmetics sheet): shirt and pants colours and prints, plus smooth, rounded
 * Roblox-style hats, hair, face and back accessories and shoes, drawn on the moving body parts. Looks only.
 * Head layout (studs, from the head mesh bounds): the eyes sit about 0.23 above the head's middle, so hats stop at
 * the brow (0.22 below the top) and glasses centre on the eyes.
 */
public final class Cosmetics {
	/** Every player's outfit, as the server last sent it: uuid -> slot -> cosmetic id. */
	public static final Map<UUID, Map<String, String>> OUTFITS = new HashMap<>();
	private static final Map<String, float[]> BOUNDS = new HashMap<>();

	private Cosmetics() {
	}

	/**
	 * A bot's outfit (synced with the bot as "slot:id,slot:id"): stored like a player's, parsed again only if it
	 * changed.
	 */
	private static final Map<UUID, String> BOT_RAW = new HashMap<>();

	public static void botOutfit(UUID id, String raw) {
		if (raw.equals(BOT_RAW.get(id))) return;
		BOT_RAW.put(id, raw);
		Map<String, String> outfit = new LinkedHashMap<>();
		for (String part : raw.split(",")) {
			String[] si = part.split(":", 2);
			if (si.length == 2) outfit.put(si[0], si[1]);
		}
		OUTFITS.put(id, outfit);
	}

	public static void receive(Net.LockerOutfit p) {
		Map<String, String> outfit = new LinkedHashMap<>();
		for (String part : p.outfit().split(",")) {
			String[] si = part.split(":", 2);
			if (si.length == 2) outfit.put(si[0], si[1]);
		}
		try {
			OUTFITS.put(UUID.fromString(p.uuid()), outfit);
		} catch (IllegalArgumentException ignored) {
		}
	}

	public static CosmeticDef worn(Map<String, String> outfit, String slot) {
		if (outfit == null) return null;
		String id = outfit.get(slot);
		CosmeticDef c = id == null ? null : Sheets.COSMETICS_BY_ID.get(id);
		return c != null && c.slot().equals(slot) ? c : null;
	}

	/** Mesh extents of a body part, relative to its joint: minX, minY, minZ, maxX, maxY, maxZ (studs). */
	private static float[] bounds(AvatarPartDef part) {
		return BOUNDS.computeIfAbsent(part.id(), id -> {
			RobloxMesh m = RobloxAssets.mesh(id);
			float[] b = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
			if (m == null) return new float[] { -0.5F, -1, -0.5F, 0.5F, 1, 0.5F };
			for (int i = 0; i < m.pos.length; i += 3)
				for (int k = 0; k < 3; k++) {
					b[k] = Math.min(b[k], m.pos[i + k]);
					b[k + 3] = Math.max(b[k + 3], m.pos[i + k]);
				}
			float ox = part.centerX() - part.pivotX(), oy = part.centerY() - part.pivotY(), oz = part.centerZ() - part.pivotZ();
			return new float[] { b[0] + ox, b[1] + oy, b[2] + oz, b[3] + ox, b[4] + oy, b[5] + oz };
		});
	}

	/** Every worn accessory, drawn in its body part's joint space so it moves with it. */
	static void draw(VertexConsumer vc, PoseStack.Pose pose, LivingEntityRenderState state, Map<String, String> outfit,
		RobloxAvatarRenderer.Pose emote, float swing, boolean aiming, int light, int overlay) {
		CosmeticDef hat = worn(outfit, "hat"), hair = worn(outfit, "hair"), face = worn(outfit, "face"), back = worn(outfit, "back"),
			shoes = worn(outfit, "shoes");
		// Full masks and helmets hide the haircut; any other hat sits on top of it (the hair's crown is left out).
		if (hat != null && hat.hideHair() || face != null && face.hideHair()) hair = null;
		for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
			boolean isHead = part.id().equals("head"), isLeg = part.swing().equals("leg"), isTorso = part.id().equals("torso");
			if (!(isHead && (hat != null || hair != null || face != null) || isLeg && shoes != null || isTorso && back != null)) continue;
			Matrix4f joint = RobloxAvatarRenderer.jointMatrix(part, state, emote, swing, aiming);
			Smooth s = new Smooth(vc, pose, joint, light, overlay);
			float[] e = bounds(part);
			if (isHead) {
				Head h = new Head(e);
				if (hair != null) hair(s, hair, h, hat);
				// Headphones, halos and ears go over an afro, not inside it: measure them against the afro instead of
				// the head.
				boolean overAfro = hat != null && hair != null && hair.shape().equals("afro") && List.of("halo", "bunny", "cat").contains(hat.shape());
				if (hat != null && hair != null && hair.shape().equals("afro") && hat.shape().equals("headphones")) afroHeadphones(s, hat, h);
				else if (hat != null && hair != null && hair.shape().equals("afro") && hat.shape().equals("tiara")) // round the afro's middle
					hat(s, hat, new Head(h.cx, h.cy, h.cz + 0.25F, h.hw * 1.38F, h.hh, h.hd * 1.38F, h.top + 0.3F, h.bottom, h.front, h.back, h.eye, h.brow));
				else if (hat != null) hat(s, hat, overAfro ? new Head(h.cx, h.cy + 0.12F, h.cz + 0.25F, h.hw * 1.45F, h.hh, h.hd * 1.45F, h.top + 0.8F, h.bottom,
					h.front, h.back, h.eye, h.brow) : h);
				if (face != null) face(s, face, h);
			} else if (isLeg) {
				if (shoes != null) shoes(s, shoes, e);
			} else {
				if (back != null) back(s, back, e);
			}
		}
	}

	/** The head's measurements (studs, joint space). */
	private record Head(float cx, float cy, float cz, float hw, float hh, float hd, float top, float bottom, float front, float back, float eye, float brow) {
		Head(float[] e) {
			this((e[0] + e[3]) / 2, (e[1] + e[4]) / 2, (e[2] + e[5]) / 2, (e[3] - e[0]) / 2, (e[4] - e[1]) / 2, (e[5] - e[2]) / 2, e[4], e[1], e[2], e[5],
				(e[1] + e[4]) / 2 + 0.23F, e[4] - 0.22F);
		}
	}

	/**
	 * A shell that follows the head's rounded-cylinder shape (grow = how much bigger than the head), from y0 up to y1
	 * with a rounded top edge (corner radius rc). closed = a rounded bottom too (full masks); angles a0..a1 pick part
	 * of the way round (front = 180..360).
	 */
	private static void shell(Smooth s, Head h, float grow, float y0, float y1, float rc, boolean closed, float a0, float a1, int color) {
		List<float[]> p = new ArrayList<>();
		float R = grow, rr = rc / h.hw;
		if (closed) {
			p.add(new float[] { 0, y0 });
			for (int i = 0; i <= 5; i++) {
				double t = Math.toRadians(-90 + 90 * i / 5.0);
				p.add(new float[] { R - rr + rr * (float) Math.cos(t), y0 + rc + rc * (float) Math.sin(t) });
			}
		} else p.add(new float[] { R, y0 });
		for (int i = 0; i <= 6; i++) {
			double t = Math.toRadians(90 * i / 6.0);
			p.add(new float[] { R - rr + rr * (float) Math.cos(t), y1 - rc + rc * (float) Math.sin(t) });
		}
		p.add(new float[] { 0, y1 });
		float[] r = new float[p.size()], y = new float[p.size()];
		for (int i = 0; i < p.size(); i++) {
			r[i] = p.get(i)[0];
			y[i] = p.get(i)[1];
		}
		s.at(h.cx, 0, h.cz).scale(h.hw, 1, h.hd).color(color).lathe(r, y, 32, a0, a1);
	}

	/**
	 * A piece that wraps part of the head (beards, cloth masks, the hockey mask): angles a0..a1 round it (front =
	 * 180..360), from y0 up to y1, standing out by 'grow' in the middle and tucked in to the head at all four edges,
	 * so no flat edge sticks out from the side.
	 */
	private static void wrap(Smooth s, Head h, float grow, float y0, float y1, float a0, float a1, int color) {
		s.at(0, 0, 0).color(color).surface((u, v, out) -> {
			double ang = Math.toRadians(a0 + (a1 - a0) * u);
			float edge = (float) Math.sqrt(Math.min(Math.sin(Math.PI * u), Math.sin(Math.PI * v)));
			float r = 1.006F + (grow - 1.006F) * edge;
			out.set(h.cx + (float) Math.cos(ang) * h.hw * r, y0 + (y1 - y0) * v, h.cz + (float) Math.sin(ang) * h.hd * r);
		}, 32, 12);
	}

	// ---- hats (head joint space: studs, y up, front -Z) ----

	private static void hat(Smooth s, CosmeticDef c, Head h) {
		float hw = h.hw, hd = h.hd, cx = h.cx, cz = h.cz, top = h.top, cy = h.cy, brow = h.brow;
		float oval = hd / hw;
		int a = c.color(), b = c.color2();
		switch (c.shape()) {
			case "tophat" -> {
				s.at(cx, top - 0.08F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.5F, 1.56F, 1.5F, 0 }, new float[] { 0, 0, 0.04F, 0.08F, 0.08F }, 32);
				s.at(cx, top - 0.04F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 0.82F, 0.85F, 0.85F, 0.81F, 0 }, new float[] { 0, 0, 0.04F, 1.0F, 1.04F, 1.04F }, 32);
				s.at(cx, top - 0.04F, cz).scale(hw, 1, hw * oval).color(b).lathe(new float[] { 0.862F, 0.862F }, new float[] { 0.1F, 0.32F }, 32);
			}
			case "cap", "backcap" -> {
				float dir = c.shape().equals("cap") ? 1 : -1; // backwards cap: bill at the back
				shell(s, h, 1.07F, brow, top + 0.08F, 0.3F, false, 0, 360, a);
				s.at(cx, brow + 0.02F, cz - dir * hd * 1.05F).color(a).ellipsoid(0.85F * hw, 0.035F, 0.5F);
				s.at(cx, top + 0.09F, cz).color(b).ellipsoid(0.07F, 0.04F, 0.07F);
				s.at(cx, brow + 0.17F, cz - dir * hd * 1.06F).color(b).ellipsoid(0.2F, 0.1F, 0.025F);
			}
			case "crown" -> {
				float base = top - 0.08F;
				s.at(cx, base, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0.98F, 1.06F, 1.06F, 0.98F, 0.98F }, new float[] { 0, 0, 0.3F, 0.3F, 0 }, 32);
				for (int i = 0; i < 6; i++) {
					double ang = Math.PI * 2 * i / 6 + Math.PI / 2;
					float px = cx + (float) Math.cos(ang) * 1.02F * hw, pz = cz + (float) Math.sin(ang) * 1.02F * hd;
					s.at(px, base + 0.29F, pz).color(a).lathe(new float[] { 0, 0.09F, 0.0F }, new float[] { 0, 0, 0.24F }, 12);
					s.at(px, base + 0.55F, pz).color(a).ellipsoid(0.045F, 0.045F, 0.045F);
				}
				s.at(cx, base + 0.15F, cz - 1.06F * hd).color(b).ellipsoid(0.09F, 0.09F, 0.045F);
				for (float side : new float[] { -1, 1 }) s.at(cx + side * 0.75F * hw, base + 0.15F, cz - 0.8F * hd).color(0x2E7BE8).ellipsoid(0.06F, 0.06F, 0.04F);
			}
			case "tiara" -> {
				// A tiara: a slim gold band hugging the front of the head (over the hair), highest over the forehead
				// and curving down to just above the ears, with five points rising from it (the middle one tallest,
				// with a pink gem).
				float y = top - 0.18F, r = 1.07F, dip = 0.22F;
				List<float[]> band = new ArrayList<>();
				for (int i = 0; i <= 24; i++) {
					double ang = Math.toRadians(180 + 180.0 * i / 24);
					float c2 = (float) (Math.cos(ang) * Math.cos(ang));
					band.add(new float[] { cx + (float) Math.cos(ang) * r * hw, y - dip * c2, cz + (float) Math.sin(ang) * r * hd });
				}
				float[] rad = new float[band.size()];
				Arrays.fill(rad, 0.032F);
				rad[0] = rad[rad.length - 1] = 0.02F;
				s.at(0, 0, 0).color(a).tube(band.toArray(new float[0][]), rad);
				float[][] points = { { 270, 0.36F }, { 247, 0.25F }, { 293, 0.25F }, { 224, 0.15F }, { 316, 0.15F } };
				for (float[] pt : points) {
					double ang = Math.toRadians(pt[0]);
					float px = cx + (float) Math.cos(ang) * r * hw, pz = cz + (float) Math.sin(ang) * r * hd;
					float py = y - dip * (float) (Math.cos(ang) * Math.cos(ang));
					s.at(px, py, pz).rotY(-pt[0] + 270).scale(1, 1, 0.45F).color(a).lathe(new float[] { 0, 0.08F, 0 }, new float[] { 0, 0, pt[1] }, 12);
					s.at(px, py + pt[1] + 0.025F, pz).color(pt[0] == 270 ? b : a).ellipsoid(0.04F, 0.04F, 0.04F);
				}
				s.at(cx, y + 0.06F, cz - r * hd - 0.02F).color(b).ellipsoid(0.065F, 0.065F, 0.03F);
			}
			case "cone" -> {
				float y0 = top - 0.1F;
				s.at(cx, y0, cz).scale(hw, 1, hw).color(a).lathe(new float[] { 0, 1.25F, 1.27F, 1.25F, 0 }, new float[] { 0, 0, 0.04F, 0.08F, 0.08F }, 32);
				s.at(cx, y0, cz).scale(hw, 1, hw).color(a).lathe(new float[] { 0, 1.0F, 0.06F, 0 }, new float[] { 0.08F, 0.08F, 1.45F, 1.47F }, 32);
				s.at(cx, y0, cz).scale(hw, 1, hw).color(b).lathe(new float[] { 0.725F, 0.505F }, new float[] { 0.5F, 0.82F }, 32);
			}
			case "headphones" -> {
				s.at(cx, cy, cz).rotX(90).color(a).torus(hw + 0.13F, 0.06F, 180, 360);
				for (float side : new float[] { -1, 1 }) {
					s.at(cx + side * (hw + 0.1F), cy, cz).rotZ(90).color(b)
						.lathe(new float[] { 0, 0.3F, 0.33F, 0.33F, 0.3F, 0 }, new float[] { -0.11F, -0.11F, -0.08F, 0.08F, 0.11F, 0.11F }, 24);
					s.at(cx + side * (hw + 0.1F), cy, cz).rotZ(90).color(a).torus(0.33F, 0.035F, 0, 360);
				}
			}
			case "viking" -> {
				shell(s, h, 1.09F, brow, top + 0.14F, 0.45F, false, 0, 360, a);
				s.at(cx, brow, cz).scale(1, 1, oval).color(a).torus(1.09F * hw, 0.06F, 0, 360);
				s.at(cx, cy, cz).rotX(90).color(a).torus(1.1F * hw + 0.02F, 0.045F, 180, 360);
				for (float side : new float[] { -1, 1 }) {
					float x = cx + side * hw;
					s.at(0, 0, 0).color(b).tube(new float[][] { { x - side * 0.05F, brow + 0.2F, cz }, { x + side * 0.25F, brow + 0.3F, cz }, { x + side * 0.42F, brow + 0.55F, cz },
						{ x + side * 0.45F, brow + 0.87F, cz }, { x + side * 0.36F, brow + 1.1F, cz } }, new float[] { 0.12F, 0.1F, 0.08F, 0.05F, 0 });
				}
			}
			case "fedora" -> {
				s.at(cx, brow + 0.04F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.55F, 1.66F, 1.55F, 0 }, new float[] { 0, 0, 0.1F, 0.05F, 0.05F }, 32);
				s.at(cx, brow + 0.04F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.03F, 1.03F, 0.88F, 0.55F, 0 }, new float[] { 0.03F, 0.03F, 0.2F, 0.66F, 0.7F, 0.56F }, 32);
				s.at(cx, brow + 0.04F, cz).scale(hw, 1, hw * oval).color(b).lathe(new float[] { 1.045F, 1.035F }, new float[] { 0.07F, 0.24F }, 32);
			}
			case "beanie" -> {
				shell(s, h, 1.08F, brow + 0.05F, top + 0.2F, 0.5F, false, 0, 360, a);
				s.at(cx, brow, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 1.04F, 1.13F, 1.15F, 1.13F, 1.04F }, new float[] { 0, 0, 0.11F, 0.22F, 0.22F }, 32);
				s.at(cx, top + 0.3F, cz).color(b).ellipsoid(0.18F, 0.18F, 0.18F);
			}
			case "cowboy" -> {
				s.at(cx, brow + 0.04F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.75F, 1.86F, 1.75F, 0 }, new float[] { 0, 0, 0.2F, 0.08F, 0.08F }, 32);
				s.at(cx, brow + 0.04F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.03F, 1.03F, 0.86F, 0.5F, 0.33F, 0 }, new float[] { 0.04F, 0.04F, 0.3F, 0.72F, 0.76F, 0.68F, 0.68F }, 32);
				s.at(cx, brow + 0.04F, cz).scale(hw, 1, hw * oval).color(b).lathe(new float[] { 1.045F, 1.035F }, new float[] { 0.1F, 0.26F }, 32);
			}
			case "halo" -> s.at(cx, top + 0.38F, cz).rotX(8).color(a).torus(0.55F, 0.055F, 0, 360);
			case "bunny" -> {
				s.at(cx, cy, cz).rotX(90).color(a).torus(hw + 0.04F, 0.035F, 180, 360);
				for (float side : new float[] { -1, 1 }) {
					s.at(cx + side * 0.3F, top + 0.42F, cz + 0.05F).rotZ(-side * 12).color(a).ellipsoid(0.13F, 0.5F, 0.07F);
					s.at(cx + side * 0.3F, top + 0.42F, cz - 0.02F).rotZ(-side * 12).color(b).ellipsoid(0.07F, 0.38F, 0.03F);
				}
			}
			case "cat" -> {
				s.at(cx, cy, cz).rotX(90).color(a).torus(hw + 0.04F, 0.035F, 180, 360);
				for (float side : new float[] { -1, 1 }) {
					s.at(cx + side * 0.62F * hw, top - 0.06F, cz).rotZ(-side * 15).scale(1, 1, 0.45F).color(a).lathe(new float[] { 0, 0.22F, 0 }, new float[] { 0, 0, 0.4F }, 16);
					s.at(cx + side * 0.62F * hw, top - 0.03F, cz - 0.06F).rotZ(-side * 15).scale(1, 1, 0.3F).color(b).lathe(new float[] { 0, 0.13F, 0 }, new float[] { 0, 0, 0.27F }, 16);
				}
			}
			case "witch" -> {
				s.at(cx, brow + 0.06F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.7F, 1.74F, 1.7F, 0 }, new float[] { 0, 0, 0.03F, 0.06F, 0.06F }, 32);
				s.at(0, 0, 0).color(a).tube(new float[][] { { cx, brow + 0.08F, cz }, { cx, top + 0.38F, cz }, { cx, top + 0.82F, cz + 0.06F }, { cx, top + 1.12F, cz + 0.26F }, { cx, top + 1.26F, cz + 0.56F } },
					new float[] { 1.03F * hw, 0.66F * hw, 0.38F * hw, 0.2F * hw, 0 }, false);
				s.at(cx, brow + 0.08F, cz).scale(hw, 1, hw).color(b).lathe(new float[] { 1.045F, 1.0F }, new float[] { 0.02F, 0.2F }, 32);
			}
			case "party" -> {
				s.at(cx + 0.08F, top - 0.08F, cz).rotZ(-10).color(a).lathe(new float[] { 0, 0.55F * hw, 0 }, new float[] { 0, 0, 1.0F }, 24);
				for (float y : new float[] { 0.25F, 0.58F })
					s.at(cx + 0.08F, top - 0.08F, cz).rotZ(-10).color(b).lathe(new float[] { 0.55F * hw * (1 - y) + 0.01F, 0.55F * hw * (0.88F - y) + 0.01F }, new float[] { y, y + 0.12F }, 24);
				s.at(cx + 0.08F, top - 0.08F, cz).rotZ(-10).move(0, 1.02F, 0).color(b).ellipsoid(0.12F, 0.12F, 0.12F);
			}
			case "chef" -> {
				shell(s, h, 1.05F, brow + 0.04F, top + 0.2F, 0.1F, false, 0, 360, a);
				s.at(cx, top + 0.45F, cz).color(a).ellipsoid(1.15F * hw, 0.36F, 1.15F * hd);
				for (int i = 0; i < 6; i++) {
					double ang = Math.PI * 2 * i / 6;
					s.at(cx + (float) Math.cos(ang) * 0.75F * hw, top + 0.45F, cz + (float) Math.sin(ang) * 0.75F * hd).color(a).ellipsoid(0.32F, 0.3F, 0.32F);
				}
				s.at(cx, brow + 0.12F, cz).scale(hw, 1, hw * oval).color(b).lathe(new float[] { 1.06F, 1.06F }, new float[] { 0, 0.06F }, 32);
			}
			case "tricorne" -> {
				s.at(cx, brow + 0.08F, cz).rotY(90).scale(hw, 1, hw).color(a).lathe(new float[] { 0, 1.75F, 1.85F, 1.75F, 0 }, new float[] { 0, 0, 0.3F, 0.14F, 0.14F }, 3);
				shell(s, h, 1.04F, brow + 0.1F, top + 0.16F, 0.4F, false, 0, 360, a);
				s.at(cx, brow + 0.38F, cz - hd * 1.05F).color(b).ellipsoid(0.12F, 0.12F, 0.04F);
				for (float side : new float[] { -1, 1 }) s.at(cx + side * 0.07F, brow + 0.26F, cz - hd * 1.05F).rotZ(side * 40).color(b).ellipsoid(0.13F, 0.03F, 0.03F);
			}
			case "police" -> {
				shell(s, h, 1.05F, brow + 0.02F, brow + 0.28F, 0.02F, false, 0, 360, a);
				s.at(cx, brow + 0.26F, cz + 0.03F).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.28F, 1.32F, 1.26F, 0 }, new float[] { 0, 0, 0.06F, 0.14F, 0.16F }, 32);
				s.at(cx, brow + 0.03F, cz - hd * 1.0F).rotX(-12).color(0x111114).ellipsoid(0.8F * hw, 0.035F, 0.4F);
				s.at(cx, brow + 0.17F, cz - hd * 1.07F).color(b).ellipsoid(0.1F, 0.1F, 0.03F);
			}
			case "space_helmet" -> {
				s.at(cx, cy + 0.04F, cz).color(a).ellipsoid(1.42F * hw, 1.3F * h.hh, 1.42F * hd);
				// The visor: a window cut from the front of the bubble itself, so it sits flush and can't stick out the
				// sides.
				s.at(cx, cy + 0.04F, cz).scale(1.43F * hw, 1.31F * h.hh, 1.43F * hd).color(b).sphere(-28, 42, 32, 215, 325);
				s.at(cx, h.bottom - 0.05F, cz).scale(1, 1, oval).color(0xB8C4D6).torus(1.25F * hw, 0.08F, 0, 360);
			}
			case "knight" -> {
				shell(s, h, 1.1F, h.bottom - 0.04F, top + 0.08F, 0.4F, false, 0, 360, a);
				s.at(cx, h.eye, cz - hd * 1.1F).color(0x111114).ellipsoid(0.75F * hw, 0.05F, 0.04F);
				for (int i = 0; i < 4; i++) s.at(cx + 0.18F * (i - 1.5F) * hw, h.cy - 0.22F, cz - hd * 1.1F).color(0x111114).ellipsoid(0.03F, 0.03F, 0.03F);
				s.at(0, 0, 0).color(b).tube(new float[][] { { cx, top + 0.06F, cz - 0.3F * hd }, { cx, top + 0.35F, cz }, { cx, top + 0.3F, cz + 0.5F * hd }, { cx, top - 0.05F, cz + 1.0F * hd } },
					new float[] { 0.06F, 0.12F, 0.12F, 0.04F });
			}
			case "fire_helmet" -> {
				shell(s, h, 1.09F, brow + 0.02F, top + 0.18F, 0.45F, false, 0, 360, a);
				s.at(cx, brow + 0.02F, cz + 0.15F).color(a).ellipsoid(1.4F * hw, 0.035F, 1.4F * hd);
				s.at(0, 0, 0).color(a).tube(new float[][] { { cx, brow + 0.12F, cz - hd * 1.05F }, { cx, top + 0.22F, cz }, { cx, brow + 0.25F, cz + hd * 1.08F } }, new float[] { 0.05F, 0.06F, 0.05F });
				s.at(cx, brow + 0.25F, cz - hd * 1.1F).color(b).ellipsoid(0.17F, 0.2F, 0.04F);
			}
			case "army" -> {
				shell(s, h, 1.14F, brow, top + 0.16F, 0.5F, false, 0, 360, a);
				s.at(cx, brow, cz).scale(1, 1, oval).color(b).torus(1.14F * hw, 0.05F, 0, 360);
			}
			case "pith" -> {
				shell(s, h, 1.05F, brow + 0.04F, top + 0.26F, 0.5F, false, 0, 360, a);
				s.at(cx, brow + 0.06F, cz).scale(hw, 1, hw * oval).color(a).lathe(new float[] { 0, 1.5F, 1.56F, 1.5F, 0 }, new float[] { -0.02F, -0.12F, -0.09F, -0.04F, 0.06F }, 32);
				s.at(cx, brow + 0.06F, cz).scale(hw, 1, hw * oval).color(b).lathe(new float[] { 1.07F, 1.06F }, new float[] { 0.04F, 0.16F }, 32);
			}
			case "robot_helmet" -> {
				// (Roomy enough that glasses stay inside its faceplate, and its underside a little below a full mask's,
				// so the two never lie on the same plane and flicker.)
				float y0 = h.bottom - 0.06F, y1 = top + 0.1F, H = y1 - y0, R = 1.66F, plate = R * 0.7071F;
				s.at(cx, y0, cz).rotY(45).scale(hw, 1, hd).color(a)
					.lathe(new float[] { 0, R - 0.08F, R, R, R - 0.08F, 0 }, new float[] { 0, 0, 0.06F, H - 0.06F, H, H }, 4);
				s.at(cx, h.eye, cz - hd * (plate + 0.009F)).color(b).ellipsoid(0.75F * hw, 0.13F, 0.03F);
				s.at(0, 0, 0).color(a).tube(new float[][] { { cx + 0.25F, y1, cz }, { cx + 0.3F, y1 + 0.4F, cz } }, new float[] { 0.03F, 0.03F });
				s.at(cx + 0.3F, y1 + 0.45F, cz).color(b).ellipsoid(0.08F, 0.08F, 0.08F);
			}
			case "dominus", "dominus_horns" -> {
				shell(s, h, 1.2F, h.bottom - 0.1F, top + 0.28F, 0.55F, false, 0, 360, a);
				s.at(cx, cy - 0.45F, cz + 0.45F * hd).color(a).ellipsoid(1.05F * hw, 0.55F, 0.6F * hd);
				// The dark void where the face would be, with a trimmed rim.
				float fz = cz - hd * 1.2F;
				s.at(cx, cy - 0.02F, fz).color(0x07070B).ellipsoid(0.72F * hw, 0.66F * h.hh, 0.06F);
				s.at(cx, cy - 0.02F, fz).rotX(90).scale(1, 1, 0.92F * h.hh / hw).color(b).torus(0.74F * hw, 0.05F, 0, 360);
				List<float[]> crest = new ArrayList<>();
				for (int i = 0; i <= 6; i++) {
					double t = Math.toRadians(25 + 130 * i / 6.0);
					crest.add(new float[] { cx, cy + 0.1F + (float) Math.sin(t) * (h.hh * 1.2F + 0.32F), cz - (float) Math.cos(t) * hd * 1.24F });
				}
				float[] cr = new float[crest.size()];
				Arrays.fill(cr, 0.06F);
				s.at(0, 0, 0).color(b).tube(crest.toArray(new float[0][]), cr);
				for (float side : new float[] { -1, 1 }) {
					if (c.shape().equals("dominus")) {
						for (int k = 0; k < 3; k++)
							s.at(cx + side * 1.18F * hw, cy + 0.12F + 0.14F * k, cz + 0.15F).rotY(-side * 35).rotZ(side * (12 + 20 * k)).move(side * 0.36F, 0, 0).color(b)
								.ellipsoid(0.42F - 0.06F * k, 0.08F, 0.035F);
					} else {
						float x = cx + side * 1.15F * hw;
						s.at(0, 0, 0).color(b).tube(new float[][] { { x, cy + 0.25F, cz }, { x + side * 0.25F, cy + 0.42F, cz + 0.15F }, { x + side * 0.3F, cy + 0.8F, cz + 0.32F },
							{ x + side * 0.18F, cy + 1.1F, cz + 0.45F } }, new float[] { 0.12F, 0.09F, 0.05F, 0 });
					}
				}
			}
			default -> {
			}
		}
	}

	/**
	 * The hair's base: a cap over the head whose lower edge goes smoothly from frontY (forehead) round to backY (nape),
	 * tucked in to the head along that edge so there is no ledge, with a rounded top edge (rc) up to y1.
	 */
	private static void hairCap(Smooth s, Head h, float grow, float frontY, float backY, float y1, float rc, int color) {
		s.at(0, 0, 0).color(color).surface((u, v, out) -> {
			double ang = u * Math.PI * 2;
			float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
			float t = (1 - sn) / 2; // 1 at the front (-Z), 0 at the back
			t = t * t * (3 - 2 * t);
			float y0 = Math.min(y1 - 0.08F, backY + (frontY - backY) * t), r, y; // (under a hat the cap is short)
			float rr = Math.min(rc, (y1 - y0) * 0.8F) / h.hw;
			if (v < 0.1F) { // the lower edge, tucked in against the head
				float k = v / 0.1F;
				r = 1.006F + (grow - 1.006F) * (float) Math.sin(k * Math.PI / 2);
				y = y0 + 0.05F * k;
			} else if (v < 0.55F) { // the sides
				r = grow;
				y = y0 + 0.05F + (y1 - rr * h.hw - y0 - 0.05F) * (v - 0.1F) / 0.45F;
			} else if (v < 0.85F) { // the rounded top edge
				double q = (v - 0.55F) / 0.3F * Math.PI / 2;
				r = grow - rr + rr * (float) Math.cos(q);
				y = y1 - rr * h.hw + rr * h.hw * (float) Math.sin(q);
			} else { // the top, to the middle
				r = (grow - rr) * (1 - (v - 0.85F) / 0.15F);
				y = y1;
			}
			out.set(h.cx + c * h.hw * r, y, h.cz + sn * h.hd * r);
		}, 48, 24);
	}

	/**
	 * Headphones over an afro: the cups on the ears, standing out past the afro's sides, and the band arching up from
	 * them round the outside of the afro (afro: ellipsoid at top + 0.1, radii 1.45 hw, 0.75, 1.45 hd, 0.25 back).
	 */
	private static void afroHeadphones(Smooth s, CosmeticDef c, Head h) {
		int a = c.color(), b = c.color2();
		float ax = 1.45F * h.hw + 0.1F, ay = 0.75F + 0.1F, acy = h.top + 0.1F, z = h.cz + 0.1F;
		float cupX = h.hw + 0.12F, cupY = h.cy + 0.02F;
		for (float side : new float[] { -1, 1 }) {
			s.at(h.cx + side * cupX, cupY, h.cz).rotZ(90).color(b)
				.lathe(new float[] { 0, 0.3F, 0.33F, 0.33F, 0.3F, 0 }, new float[] { -0.11F, -0.11F, -0.08F, 0.08F, 0.11F, 0.11F }, 24);
			s.at(h.cx + side * cupX, cupY, h.cz).rotZ(90).color(a).torus(0.33F, 0.035F, 0, 360);
		}
		// The band: up from the left cup to the afro's widest point, over the top, down to the right cup.
		List<float[]> pts = new ArrayList<>();
		pts.add(new float[] { h.cx - cupX, cupY + 0.3F, z });
		for (int i = 0; i <= 16; i++) {
			double t = Math.PI * i / 16; // 0 = left side, PI/2 = top, PI = right side
			pts.add(new float[] { h.cx - ax * (float) Math.cos(t), acy + ay * (float) Math.sin(t), z });
		}
		pts.add(new float[] { h.cx + cupX, cupY + 0.3F, z });
		float[] r = new float[pts.size()];
		Arrays.fill(r, 0.06F);
		s.at(0, 0, 0).color(a).tube(pts.toArray(new float[0][]), r);
	}

	/** Where a hat's lower edge sits on the head (hats that sit on top of the head: its top, minus the hair's 0.1). */
	private static float hatEdge(String shape, Head h) {
		return switch (shape) {
			case "cap", "backcap", "viking", "beanie", "army" -> h.brow;
			case "police", "fire_helmet" -> h.brow + 0.02F;
			case "chef", "pith", "fedora", "cowboy" -> h.brow + 0.04F;
			case "witch" -> h.brow + 0.06F;
			case "tricorne" -> h.brow; // (its crown rounds in fast: stop the hair low, the brim hides the rest)
			default -> h.top - 0.1F;
		};
	}

	// ---- hair (head joint space). Under a hat the hair's crown is left out and the cut reaches up into the hat ----

	private static void hair(Smooth s, CosmeticDef c, Head h, CosmeticDef hat) {
		// Headphones, halos, bunny and cat ears sit over the hair rather than on the head, so the hair stays whole.
		boolean underHat = hat != null && !List.of("headphones", "halo", "bunny", "cat", "tiara").contains(hat.shape());
		float hw = h.hw, hd = h.hd, cx = h.cx, cz = h.cz, top = h.top, cy = h.cy;
		int a = c.color(), b = c.color2();
		String shape = c.shape();
		// The base cut follows the head: short over the forehead (well above the eyes), down to ear level at the sides
		// and lower at the back, in one smooth curve (no steps or ledges).
		float front = h.top - 0.13F, back = shape.equals("buzz") ? cy - 0.05F : cy - 0.32F;
		// Under a hat the cut goes up into it (no bald band between the hair and the hat): just past the lower edge of
		// a hat that wraps the head (so it can't poke through higher up), or to the top for hats that sit on top. (Only
		// 0.03 past a wrapping hat's edge: helmets round in near their rim, so any higher pokes through.)
		float edge = underHat ? hatEdge(hat.shape(), h) : top;
		float hatTop = !underHat ? top + 0.05F : edge >= top - 0.1F ? top : edge + 0.03F;
		float grow = shape.equals("buzz") || underHat ? 1.025F : 1.045F;
		boolean brim = underHat && hatTop < top; // it stops inside a hat that wraps the head: keep it full up to there (no rounding)
		hairCap(s, h, grow, front, back, hatTop, brim ? 0.02F : underHat ? 0.3F : 0.4F, a);
		float tuck = hatTop;
		switch (shape) {
			case "long" -> {
				// Hangs down the back, sitting behind the back of the head (so none of the head shows through it).
				s.at(cx, cy - 0.45F, cz + 0.62F * hd).color(a).ellipsoid(1.06F * hw, 0.7F, 0.6F * hd);
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 1.0F * hw, cy - 0.2F, cz + 0.12F * hd).color(a).ellipsoid(0.17F, 0.6F, 0.42F * hd);
			}
			case "spiky" -> {
				if (!underHat) for (int i = 0; i < 7; i++) {
					double ang = Math.PI * 2 * i / 7;
					float px = cx + (float) Math.cos(ang) * 0.55F * hw, pz = cz + (float) Math.sin(ang) * 0.55F * hd;
					s.at(px, top + 0.02F, pz).rotZ((float) -Math.cos(ang) * 25).rotX((float) Math.sin(ang) * 25).color(a).lathe(new float[] { 0, 0.17F, 0 }, new float[] { 0, 0, 0.42F }, 10);
				}
			}
			case "mohawk" -> {
				if (!underHat) for (int i = 0; i < 6; i++)
					s.at(cx, top + 0.02F, cz - hd * 0.85F + i * hd * 0.34F).rotX(-20 + i * 8).scale(0.4F, 1, 1).color(a).lathe(new float[] { 0, 0.2F, 0 }, new float[] { 0, 0, 0.5F - Math.abs(i - 2.5F) * 0.05F }, 10);
			}
			case "bun" -> {
				if (!underHat) s.at(cx, top + 0.04F, cz + 0.45F * hd).color(a).ellipsoid(0.26F, 0.24F, 0.26F);
				else s.at(cx, cy + 0.1F, cz + 1.12F * hd).color(a).ellipsoid(0.24F, 0.24F, 0.2F);
			}
			case "ponytail" -> {
				s.at(cx, top - 0.18F, cz + 1.06F * hd).color(b).ellipsoid(0.1F, 0.1F, 0.08F);
				s.at(0, 0, 0).color(a).tube(new float[][] { { cx, top - 0.18F, cz + 1.08F * hd }, { cx, top - 0.35F, cz + 1.35F * hd }, { cx, cy - 0.3F, cz + 1.38F * hd }, { cx, cy - 0.7F, cz + 1.18F * hd } },
					new float[] { 0.14F, 0.13F, 0.1F, 0.02F });
			}
			case "afro" -> {
				if (!underHat) s.at(cx, top + 0.1F, cz + 0.25F).color(a).ellipsoid(1.45F * hw, 0.75F, 1.45F * hd);
			}
			case "pigtails" -> {
				for (float sd : new float[] { -1, 1 }) {
					s.at(cx + sd * 1.3F * hw, cy + 0.05F, cz + 0.2F).color(a).ellipsoid(0.25F, 0.28F, 0.25F);
					s.at(cx + sd * 1.08F * hw, cy + 0.08F, cz + 0.15F).color(b).ellipsoid(0.07F, 0.09F, 0.09F);
				}
			}
			case "swoop" -> {
				if (!underHat) s.at(cx + 0.12F, h.brow + 0.1F, cz - hd * 1.0F).rotZ(-22).rotX(-10).color(a).ellipsoid(0.62F * hw, 0.13F, 0.14F); // (a hat's brim would cut through it)
			}
			case "bob" -> {
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 1.0F * hw, cy - 0.12F, cz + 0.1F * hd).color(a).ellipsoid(0.2F, 0.45F, 0.5F * hd);
				s.at(cx, cy - 0.3F, cz + 0.5F * hd).color(a).ellipsoid(1.0F * hw, 0.4F, 0.5F * hd);
			}
			case "curly" -> {
				for (int i = 0; i < 12; i++) {
					double ang = Math.PI * 2 * i / 12;
					boolean tiara = hat != null && hat.shape().equals("tiara");
					if ((underHat || tiara) && Math.sin(ang) < -0.2) continue; // under a hat or tiara: curls at the sides and back only
					float yy = underHat ? Math.min(tuck, top) - 0.25F - (i % 2) * 0.12F : top - 0.12F - (i % 2) * 0.2F;
					s.at(cx + (float) Math.cos(ang) * 1.04F * hw, yy, cz + (float) Math.sin(ang) * 1.04F * hd).color(a).ellipsoid(0.17F, 0.17F, 0.17F);
				}
			}
			case "messy" -> {
				if (!underHat) for (int i = 0; i < 6; i++) {
					double ang = Math.PI * 2 * i / 6 + 0.4;
					s.at(cx + (float) Math.cos(ang) * 0.6F * hw, top + 0.08F, cz + (float) Math.sin(ang) * 0.6F * hd).rotZ(25 * (i % 2 == 0 ? 1 : -1)).rotX(20 * (i % 3 - 1))
						.color(a).ellipsoid(0.12F, 0.24F, 0.12F);
				}
			}
			default -> {
			}
		}
	}

	// ---- face accessories (head joint space) ----

	private static void face(Smooth s, CosmeticDef c, Head h) {
		float hw = h.hw, hd = h.hd, cx = h.cx, cz = h.cz, cy = h.cy, front = h.front, ey = h.eye;
		int a = c.color(), b = c.color2();
		switch (c.shape()) {
			case "shades", "shades_low" -> {
				float y = c.shape().equals("shades_low") ? cy + 0.1F : ey; // Cool Shades sit low on purpose
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 0.22F, y, front - 0.035F).color(b).ellipsoid(0.19F, 0.12F, 0.035F);
				glassesFrame(s, h, y, a, 0.4F);
			}
			case "aviators" -> {
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 0.22F, ey - 0.02F, front - 0.035F).rotZ(sd * 12).color(b).ellipsoid(0.17F, 0.14F, 0.035F);
				glassesFrame(s, h, ey + 0.06F, a, 0.38F);
			}
			case "glasses", "nerd" -> {
				float t = c.shape().equals("nerd") ? 0.045F : 0.026F;
				for (float sd : new float[] { -1, 1 }) {
					s.at(cx + sd * 0.2F, ey, front - 0.035F).rotX(90).scale(1, 1, c.shape().equals("nerd") ? 0.8F : 1).color(a).torus(0.15F, t, 0, 360);
				}
				glassesFrame(s, h, ey, a, 0.35F);
			}
			case "goggles" -> {
				for (float sd : new float[] { -1, 1 }) {
					s.at(cx + sd * 0.21F, ey, front - 0.02F).rotX(-90).color(a).lathe(new float[] { 0, 0.17F, 0.17F, 0 }, new float[] { 0, 0, 0.08F, 0.08F }, 20);
					s.at(cx + sd * 0.21F, ey, front - 0.105F).color(b).ellipsoid(0.15F, 0.15F, 0.01F);
				}
				s.at(cx, ey, cz).scale(1, 1, hd / hw).color(a).torus(hw * 1.04F, 0.04F, 0, 360);
			}
			case "monocle" -> {
				s.at(cx + 0.2F, ey, front - 0.035F).rotX(90).color(a).torus(0.15F, 0.025F, 0, 360);
				s.at(0, 0, 0).color(a).tube(new float[][] { { cx + 0.33F, ey - 0.06F, front - 0.03F }, { cx + 0.4F, cy - 0.25F, front - 0.03F }, { cx + 0.45F, h.bottom - 0.05F, front + 0.05F } },
					new float[] { 0.012F, 0.012F, 0.012F });
			}
			case "mustache" -> {
				for (float sd : new float[] { -1, 1 }) {
					s.at(cx + sd * 0.13F, cy - 0.04F, front - 0.025F).rotZ(sd * 15).color(a).ellipsoid(0.15F, 0.06F, 0.045F);
					s.at(cx + sd * 0.28F, cy + 0.01F, front - 0.015F).color(a).ellipsoid(0.055F, 0.055F, 0.04F);
				}
			}
			case "beard", "long_beard" -> {
				wrap(s, h, 1.1F, h.bottom + 0.04F, cy - 0.04F, 175, 365, a);
				s.at(cx, h.bottom + 0.06F, front + 0.1F).color(a).ellipsoid(0.55F * hw, 0.18F, 0.16F); // chin
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 0.13F, cy - 0.04F, front - 0.03F).rotZ(sd * 12).color(a).ellipsoid(0.16F, 0.06F, 0.05F);
				if (c.shape().equals("long_beard")) s.at(cx, h.bottom - 0.45F, front + 0.1F).color(a).ellipsoid(0.38F * hw, 0.55F, 0.16F);
			}
			case "eyepatch" -> {
				s.at(cx + 0.2F, ey, front - 0.03F).color(a).ellipsoid(0.15F, 0.13F, 0.03F);
				// The strap goes round the head, hugging it (tilted, higher over the patch side).
				s.at(cx, ey + 0.08F, cz).rotZ(-14).scale(1, 1, hd / hw).color(a).torus(hw * 1.03F, 0.018F, 0, 360);
			}
			case "ski_mask", "ninja_mask" -> {
				shell(s, h, 1.05F, h.bottom - 0.04F, h.top + 0.04F, 0.3F, true, 0, 360, a);
				if (c.shape().equals("ninja_mask")) s.at(cx, ey, front - 0.035F).color(b).ellipsoid(0.72F * hw, 0.12F, 0.03F);
				for (float sd : new float[] { -1, 1 }) {
					if (c.shape().equals("ski_mask")) s.at(cx + sd * 0.2F, ey, front - 0.035F).color(b).ellipsoid(0.11F, 0.09F, 0.025F);
					s.at(cx + sd * 0.2F, ey, front - 0.055F).color(0x111114).ellipsoid(0.045F, 0.06F, 0.015F);
				}
			}
			case "hockey_mask" -> {
				wrap(s, h, 1.09F, h.bottom + 0.04F, h.top - 0.06F, 190, 350, a);
				for (float sd : new float[] { -1, 1 }) {
					s.at(cx + sd * 0.2F, ey, front - 0.055F).color(0x111114).ellipsoid(0.12F, 0.08F, 0.02F);
					s.at(cx + sd * 0.26F, ey + 0.2F, front - 0.05F).rotZ(sd * 30).color(b).ellipsoid(0.12F, 0.025F, 0.02F);
				}
				for (int i = 0; i < 6; i++) s.at(cx + (i % 3 - 1) * 0.12F, cy - 0.2F - (i / 3) * 0.1F, front - 0.055F).color(0x111114).ellipsoid(0.025F, 0.025F, 0.015F);
			}
			case "surgical", "bandana" -> {
				wrap(s, h, 1.06F, h.bottom + 0.06F, cy + 0.04F, 180, 360, a);
				if (c.shape().equals("surgical")) {
					for (float sd : new float[] { -1, 1 })
						s.at(0, 0, 0).color(b).tube(new float[][] { { cx + sd * hw * 1.0F, cy, front + 0.25F }, { cx + sd * hw * 1.05F, ey, cz + 0.1F } }, new float[] { 0.015F, 0.015F });
				} else {
					s.at(cx, h.bottom - 0.02F, front + 0.02F).color(a).ellipsoid(0.25F, 0.16F, 0.08F);
					for (int i = 0; i < 5; i++) s.at(cx + (i - 2) * 0.13F, cy - 0.2F + (i % 2) * 0.08F, front - 0.05F).color(b).ellipsoid(0.025F, 0.025F, 0.01F);
				}
			}
			case "domino" -> {
				s.at(cx, ey, cz).scale(hw, 1, hd).color(a).lathe(new float[] { 1.04F, 1.04F }, new float[] { -0.1F, 0.1F }, 24, 200, 340);
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 0.2F, ey, front - 0.045F).color(0xF4F4F4).ellipsoid(0.08F, 0.06F, 0.012F);
			}
			case "nose" -> s.at(cx, cy + 0.05F, front - 0.07F).color(a).ellipsoid(0.11F, 0.11F, 0.11F);
			case "visor" -> {
				s.at(cx, ey, cz).scale(hw, 1, hd).color(a).lathe(new float[] { 1.05F, 1.05F }, new float[] { -0.09F, 0.09F }, 24, 180, 360);
				s.at(cx, ey + 0.02F, front - 0.055F).color(b).ellipsoid(0.55F * hw, 0.02F, 0.01F);
			}
			case "blush" -> {
				for (float sd : new float[] { -1, 1 }) s.at(cx + sd * 0.33F, cy - 0.05F, front - 0.012F).color(a).ellipsoid(0.11F, 0.06F, 0.015F);
			}
			case "freckles" -> {
				float[][] dots = { { -0.36F, 0.02F }, { -0.28F, -0.05F }, { -0.4F, -0.08F }, { 0.36F, 0.02F }, { 0.28F, -0.05F }, { 0.4F, -0.08F } };
				for (float[] d : dots) s.at(cx + d[0], cy + d[1], front - 0.012F).color(a).ellipsoid(0.025F, 0.025F, 0.01F);
			}
			default -> {
			}
		}
	}

	/** Bridge and arms of a pair of glasses at height y; reach = how far out the lenses go. */
	private static void glassesFrame(Smooth s, Head h, float y, int color, float reach) {
		float cx = h.cx, front = h.front, hw = h.hw;
		s.at(0, 0, 0).color(color).tube(new float[][] { { cx - 0.07F, y + 0.03F, front - 0.04F }, { cx, y + 0.06F, front - 0.05F }, { cx + 0.07F, y + 0.03F, front - 0.04F } },
			new float[] { 0.022F, 0.022F, 0.022F });
		for (float sd : new float[] { -1, 1 })
			s.at(0, 0, 0).color(color).tube(new float[][] { { h.cx + sd * reach, y + 0.03F, front - 0.03F }, { h.cx + sd * (hw - 0.02F), y + 0.04F, front + 0.08F },
				{ h.cx + sd * (hw + 0.01F), y + 0.03F, h.cz + 0.15F } }, new float[] { 0.022F, 0.022F, 0.022F });
	}

	// ---- back accessories (torso joint space) ----

	/** Back bling: drawn by BackBling (wings, capes, packs, weapons and more, each with its own detail). */
	private static void back(Smooth s, CosmeticDef c, float[] e) {
		BackBling.draw(s, c, e);
	}

	// ---- shoes (leg joint space) ----

	/**
	 * A shoe shell that wraps the square Roblox leg: a rounded square (superellipse) around the leg from y0 up to y1,
	 * closed underneath with a rounded edge, open on top. The front stretches out by 'toe' at the sole and slopes back
	 * to the leg at the top, so it reads as a shoe, not a sleeve. grow: how far outside the leg (1.08 clears its
	 * corners).
	 */
	private static void shoeShell(Smooth s, float[] e, float grow, float y0, float y1, float toe, int color) {
		float cx = (e[0] + e[3]) / 2, cz = (e[2] + e[5]) / 2, hx = (e[3] - e[0]) / 2 * grow, hz = (e[5] - e[2]) / 2 * grow;
		float k = Math.min(0.08F, (y1 - y0) * 0.4F); // rounded bottom edge
		s.at(0, 0, 0).color(color).surface((u, v, out) -> {
			double ang = u * Math.PI * 2, c = Math.cos(ang), sn = Math.sin(ang);
			float px = (float) (Math.signum(c) * Math.pow(Math.abs(c), 0.2)), pz = (float) (Math.signum(sn) * Math.pow(Math.abs(sn), 0.2));
			float r, y;
			if (v < 0.2F) { // the sole, from the middle out
				r = v / 0.2F * (1 - k / hx);
				y = y0;
			} else if (v < 0.45F) { // the rounded bottom edge
				double t = (v - 0.2F) / 0.25F * Math.PI / 2;
				r = 1 - k / hx + k / hx * (float) Math.sin(t);
				y = y0 + k - k * (float) Math.cos(t);
			} else { // the sides, up to the top
				r = 1;
				y = y0 + k + (y1 - y0 - k) * (v - 0.45F) / 0.55F;
			}
			float f = (y - y0) / (y1 - y0), front = pz < 0 ? 1 + (toe - 1) * (1 - f * f) : 1;
			out.set(cx + px * hx * r, y, cz + pz * hz * r * front);
		}, 40, 14);
	}

	private static void shoes(Smooth s, CosmeticDef c, float[] e) {
		float lcx = (e[0] + e[3]) / 2, lcz = (e[2] + e[5]) / 2, bottom = e[1], hw = (e[3] - e[0]) / 2, hd = (e[5] - e[2]) / 2;
		int a = c.color(), b = c.color2();
		switch (c.shape()) {
			case "boots" -> {
				shoeShell(s, e, 1.12F, bottom - 0.02F, bottom + 0.1F, 1.32F, b); // sole
				shoeShell(s, e, 1.08F, bottom + 0.06F, bottom + 0.7F, 1.28F, a);
				shoeShell(s, e, 1.13F, bottom + 0.58F, bottom + 0.7F, 1.0F, b); // cuff
			}
			case "loafers" -> {
				shoeShell(s, e, 1.12F, bottom - 0.02F, bottom + 0.07F, 1.4F, 0x1C1C22);
				shoeShell(s, e, 1.08F, bottom + 0.04F, bottom + 0.24F, 1.36F, a);
				s.at(lcx, bottom + 0.2F, lcz - hd * 1.08F - 0.12F).color(b).ellipsoid(hw * 0.45F, 0.03F, 0.05F);
			}
			case "clown" -> {
				shoeShell(s, e, 1.2F, bottom - 0.02F, bottom + 0.12F, 2.3F, b);
				shoeShell(s, e, 1.14F, bottom + 0.08F, bottom + 0.42F, 2.15F, a);
				s.at(lcx, bottom + 0.36F, lcz - hd * 1.14F - 0.3F).color(b).ellipsoid(0.14F, 0.12F, 0.12F);
			}
			default -> { // sneakers
				float y0 = bottom + 0.05F, y1 = bottom + 0.44F, toe = 1.36F;
				shoeShell(s, e, 1.12F, bottom - 0.02F, bottom + 0.09F, 1.4F, b); // sole
				shoeShell(s, e, 1.08F, y0, y1, toe, a);
				for (int i = 0; i < 3; i++) { // laces across the sloping front of the foot, sitting on its surface
					float y = y1 - 0.06F - i * 0.07F, f = (y - y0) / (y1 - y0);
					s.at(lcx, y, lcz - hd * 1.08F * (1 + (toe - 1) * (1 - f * f)) + 0.005F).color(b).ellipsoid(hw * 0.5F, 0.022F, 0.02F);
				}
			}
		}
	}

	// ---- shirts and pants: a painted design (ClothingTextures) wrapped round the torso / each leg ----

	/**
	 * Draws the worn shirt's and pants' designs as pictures on the torso and legs, just outside the body (the parts
	 * themselves are already coloured with the items' main colours).
	 */
	static void submitClothes(SubmitNodeCollector collector, PoseStack poseStack, LivingEntityRenderState state,
		Map<String, String> outfit, RobloxAvatarRenderer.Pose emote, float swing, boolean aiming, int light, int overlay) {
		CosmeticDef shirt = worn(outfit, "shirt"), pants = worn(outfit, "pants");
		if (shirt == null && pants == null) return;
		for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
			boolean isLeg = part.swing().equals("leg"), isTorso = part.id().equals("torso");
			CosmeticDef c = isTorso ? shirt : isLeg ? pants : null;
			if (c == null) continue;
			Matrix4f joint = RobloxAvatarRenderer.jointMatrix(part, state, emote, swing, aiming);
			float[] e = bounds(part);
			boolean outerPlusX = part.centerX() >= 0;
			Identifier tex = isTorso ? ClothingTextures.shirt(c) : ClothingTextures.pants(c, outerPlusX);
			float[] widths = isTorso ? new float[] { 96, 48, 96, 48 } : new float[] { 48, 48, 48, 48 };
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(tex),
				(pose, vc) -> wrap(vc, pose, joint, e, widths, light, overlay));
		}
	}

	/**
	 * The four side faces of a part's box (just outside it), each mapped to its strip of the picture: front, +X, back,
	 * -X.
	 */
	private static void wrap(VertexConsumer vc, PoseStack.Pose pose, Matrix4f joint, float[] e, float[] widths, int light, int overlay) {
		float d = 0.012F, x0 = e[0] - d, y0 = e[1], z0 = e[2] - d, x1 = e[3] + d, y1 = e[4], z1 = e[5] + d;
		float total = 0;
		for (float w : widths) total += w;
		// Each face's two vertical edges as seen from outside, left then right: front (-Z), +X side, back (+Z), -X
		// side.
		float[][][] faces = { { { x1, z0 }, { x0, z0 } }, { { x1, z1 }, { x1, z0 } }, { { x0, z1 }, { x1, z1 } }, { { x0, z0 }, { x0, z1 } } };
		float[][] normals = { { 0, 0, -1 }, { 1, 0, 0 }, { 0, 0, 1 }, { -1, 0, 0 } };
		float u = 0;
		Vector3f p = new Vector3f(), n = new Vector3f();
		for (int f = 0; f < 4; f++) {
			float ua = u / total, ub = (u + widths[f]) / total;
			u += widths[f];
			// The model mapping mirrors x, so the picture's left edge goes on the face's right-hand edge.
			float[][] corner = { { faces[f][1][0], y1, faces[f][1][1], ua, 0 }, { faces[f][1][0], y0, faces[f][1][1], ua, 1 },
				{ faces[f][0][0], y0, faces[f][0][1], ub, 1 }, { faces[f][0][0], y1, faces[f][0][1], ub, 0 } };
			joint.transformDirection(normals[f][0], normals[f][1], normals[f][2], n);
			for (int pass = 0; pass < 2; pass++) // both windings, so it shows whichever way the mirror turns it
				for (int k = 0; k < 4; k++) {
					float[] c = corner[pass == 0 ? k : 3 - k];
					joint.transformPosition(c[0], c[1], c[2], p);
					vc.addVertex(pose, -p.x * RobloxAvatarRenderer.S, 1.501F - (p.y + RobloxAvatarRenderer.HIP) * RobloxAvatarRenderer.S, p.z * RobloxAvatarRenderer.S)
						.setColor(0xFFFFFFFF).setUv(c[3], c[4]).setOverlay(overlay).setLight(light).setNormal(pose, -n.x, -n.y, n.z);
				}
		}
	}
}
