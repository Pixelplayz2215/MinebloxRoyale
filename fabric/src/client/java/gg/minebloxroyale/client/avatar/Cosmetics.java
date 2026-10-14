package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.client.WorldVisuals;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.AvatarPartDef;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
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
 * Locker cosmetics on the Roblox avatar (cosmetics sheet): shirt and pants colours and prints, plus hats, hair, face
 * and back accessories and shoes (Blender models, like real Roblox catalog items), drawn on the moving body parts.
 * Looks only.
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

	/** The head, leg and torso the Blender models were made on (fabric/tools/blender: cosmetics.py, backbling.py), in joint space. */
	private static final float[] HEAD = { -0.5989F, -0.0012F, -0.5989F, 0.5989F, 1.2012F, 0.5989F }, LEG = { -0.5F, -2, -0.5F, 0.5F, 0, 0.5F },
		TORSO = { -1, -1, -0.5F, 1, 1, 0.5F };
	/** Hats worn over the hair rather than on the head: the haircut stays whole (over an afro they have their own, bigger model). */
	private static final List<String> OVER_HAIR = List.of("headphones", "halo", "bunny", "cat", "tiara");

	private record Piece(String mesh, Matrix4f local, CosmeticDef c) {
	}

	/**
	 * Every worn accessory (hats, hair, face, back bling, shoes): Blender models (fabric/tools/blender/cosmetic_meshes.py,
	 * restyled like real Roblox catalog items), each on its body part's joint so it moves with it, in the item's two
	 * colours; the lighter copies far away.
	 */
	static void submit(SubmitNodeCollector collector, PoseStack poseStack, LivingEntityRenderState state, Map<String, String> outfit,
		RobloxAvatarRenderer.Pose emote, float swing, boolean aiming, boolean far, int light, int overlay) {
		CosmeticDef hat = worn(outfit, "hat"), hair = worn(outfit, "hair"), face = worn(outfit, "face"), back = worn(outfit, "back"),
			shoes = worn(outfit, "shoes");
		// Full masks and helmets hide the haircut.
		if (hat != null && hat.hideHair() || face != null && face.hideHair()) hair = null;
		List<Piece> pieces = new ArrayList<>();
		for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
			boolean isHead = part.id().equals("head"), isLeg = part.swing().equals("leg"), isTorso = part.id().equals("torso");
			if (isHead && (hat != null || hair != null || face != null)) {
				Matrix4f m = place(part, state, emote, swing, aiming, HEAD);
				if (hair != null) pieces.add(new Piece(hairMesh(hair, hat), m, hair));
				if (hat != null) pieces.add(new Piece("hat_" + hat.shape() + (hair != null && hair.shape().equals("afro") && OVER_HAIR.contains(hat.shape()) ? "_afro" : ""), m, hat));
				if (face != null) pieces.add(new Piece("face_" + face.shape(), m, face));
			} else if (isLeg && shoes != null) pieces.add(new Piece("shoes_" + shoes.shape(), place(part, state, emote, swing, aiming, LEG), shoes));
			else if (isTorso && back != null) pieces.add(new Piece(back.id(), place(part, state, emote, swing, aiming, TORSO), back));
		}
		if (pieces.isEmpty()) return;
		String lod = far ? "_far" : "";
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (pose, vc) -> {
			for (Piece p : pieces) Mesh.get(p.mesh() + lod).draw(pose, p.local(), vc, light, overlay, p.c().color() & 0xFFFFFF, p.c().color2() & 0xFFFFFF);
		});
	}

	/**
	 * Model space for a body part's accessory: the part's joint (moving with it), with the model, made on the default
	 * part ('made'), fitted to this part's actual extents.
	 */
	private static Matrix4f place(AvatarPartDef part, LivingEntityRenderState state, RobloxAvatarRenderer.Pose emote, float swing, boolean aiming, float[] made) {
		float[] e = bounds(part);
		return new Matrix4f(RobloxAvatarRenderer.ROOT).mul(RobloxAvatarRenderer.jointMatrix(part, state, emote, swing, aiming))
			.translate((e[0] + e[3]) / 2, (e[1] + e[4]) / 2, (e[2] + e[5]) / 2)
			.scale((e[3] - e[0]) / (made[3] - made[0]), (e[4] - e[1]) / (made[4] - made[1]), (e[5] - e[2]) / (made[5] - made[2]))
			.translate(-(made[0] + made[3]) / 2, -(made[1] + made[4]) / 2, -(made[2] + made[5]) / 2);
	}

	/**
	 * The haircut's model for the hat worn with it: under a hat its crown is left out and it reaches just up into the hat,
	 * which depends on where the hat's lower edge sits (cosmetics.py CUTS: the brow, a little above it, or the top).
	 */
	private static String hairMesh(CosmeticDef hair, CosmeticDef hat) {
		if (hair.shape().equals("curly") && hat != null && hat.shape().equals("tiara")) return "hair_curly_tiara"; // (curls at the sides and back only)
		String cut = hat == null || OVER_HAIR.contains(hat.shape()) ? "full" : switch (hat.shape()) {
			case "cap", "backcap", "viking", "beanie", "army", "tricorne" -> "b0";
			case "police", "fire_helmet" -> "b2";
			case "chef", "pith", "fedora", "cowboy" -> "b4";
			case "witch" -> "b6";
			default -> "top";
		};
		return "hair_" + hair.shape() + "_" + cut;
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
