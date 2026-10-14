package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.client.ClientState;
import gg.minebloxroyale.sheets.AvatarColorDef;
import gg.minebloxroyale.sheets.AvatarPartDef;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.EmoteKeyDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws an entity as the classic Roblox R6 character (avatar_parts sheet) instead of its Minecraft model. Runs in the
 * living-entity model space (y down, front -Z, feet at y=1.501); Roblox studs (y up) map by a 180 degree turn about Z.
 */
public final class RobloxAvatarRenderer {
	public static final RenderStateDataKey<Integer> ENTITY_ID = RenderStateDataKey.create(() -> "minebloxroyale:entity_id");
	public static final RenderStateDataKey<String> COLORS = RenderStateDataKey.create(() -> "minebloxroyale:colors");
	public static final RenderStateDataKey<Boolean> AIMING = RenderStateDataKey.create(() -> "minebloxroyale:aiming");
	public static final RenderStateDataKey<Boolean> IN_BUS = RenderStateDataKey.create(() -> "minebloxroyale:in_bus");
	/** Not drawn this frame (your own avatar while the Locker shows its preview of you). */
	public static final RenderStateDataKey<Boolean> HIDDEN = RenderStateDataKey.create(() -> "minebloxroyale:hidden");
	public static final RenderStateDataKey<Boolean> GLIDING = RenderStateDataKey.create(() -> "minebloxroyale:gliding");
	/** Knocked down (team modes): drawn crawling, holding nothing. */
	public static final RenderStateDataKey<Boolean> KNOCKED = RenderStateDataKey.create(() -> "minebloxroyale:knocked");
	public static final RenderStateDataKey<Boolean> IS_PLAYER = RenderStateDataKey.create(() -> "minebloxroyale:is_player");
	/**
	 * Minecraft lays a crawling player flat itself (its swimming pose); a knocked bot gets the same turn here: the
	 * model space of a standing body (scale -1,-1,1 then 1.501 down) with the crawl turn (90 degrees forward, 1 down,
	 * 0.3 back) slipped in before it.
	 */
	private static final Matrix4f CRAWL;

	static {
		Matrix4f st = new Matrix4f().scale(-1, -1, 1).translate(0, -1.501F, 0);
		Matrix4f turn = new Matrix4f().rotateX((float) Math.toRadians(-90)).translate(0, -1F, 0.3F);
		CRAWL = new Matrix4f(st).invert().mul(turn).mul(st);
	}
	/** Player UUID (Locker outfit lookup); absent for bots. */
	public static final RenderStateDataKey<String> PLAYER_UUID = RenderStateDataKey.create(() -> "minebloxroyale:player_uuid");
	/** Blocks per stud: 5.2 studs tall -> about 1.87 blocks. */
	static final float S = 0.36F;
	static final float HIP = 2F; // studs from feet to the root (hips)

	private RobloxAvatarRenderer() {
	}

	/** The emote pose at this moment, or null. Angles in degrees. */
	record Pose(float rightArmX, float rightArmZ, float leftArmX, float leftArmZ, float rightLegX, float leftLegX, float bodyYaw, float bounce) {
	}

	public static boolean submit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		if (!RobloxAssets.ready()) return false;
		boolean crawl = state.getDataOrDefault(KNOCKED, false) && !state.getDataOrDefault(IS_PLAYER, false);
		if (crawl) {
			poseStack.pushPose();
			poseStack.mulPose(CRAWL);
		}
		try {
			return draw(state, poseStack, collector, light, overlay);
		} finally {
			if (crawl) poseStack.popPose();
		}
	}

	private static boolean draw(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		AvatarColorDef colors = Sheets.AVATAR_COLORS_BY_ID.getOrDefault(state.getDataOrDefault(COLORS, "classic"), Sheets.AVATAR_COLORS.get(0));
		int entityId = state.getDataOrDefault(ENTITY_ID, -1);
		boolean aiming = state.getDataOrDefault(AIMING, false);
		Pose emote = emotePose(entityId, state.walkAnimationSpeed);
		String uuid = state.getDataOrDefault(PLAYER_UUID, "");
		Map<String, String> outfit = uuid.isEmpty() ? null : Cosmetics.OUTFITS.get(UUID.fromString(uuid));
		CosmeticDef shirt = Cosmetics.worn(outfit, "shirt"), pants = Cosmetics.worn(outfit, "pants");
		float swing = (float) Math.cos(state.walkAnimationPos * 0.6662F) * 1.2F * Math.min(1F, state.walkAnimationSpeed);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(RobloxAssets.TEXTURE), (pose, vc) -> {
			for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
				RobloxMesh mesh = RobloxAssets.mesh(part.id());
				if (mesh == null) continue;
				int color = 0xFF000000 | switch (part.colorSlot()) {
					case "head" -> colors.head();
					case "torso" -> shirt != null ? shirt.color() : colors.torso();
					case "arms" -> shirt != null ? shirt.color() : colors.arms();
					default -> pants != null ? pants.color() : colors.legs();
				};
				Matrix4f m = partMatrix(part, state, emote, swing, aiming);
				drawMesh(vc, pose, m, mesh, part.faceDecal(), color, light, overlay);
			}
			// Accessories and gliders far away are drawn with fewer pieces (lots of gliding bots at once stay smooth).
			Smooth.detail = state.distanceToCameraSq < 20 * 20 ? 1F : state.distanceToCameraSq < 48 * 48 ? 0.5F : 0.25F;
			try {
				if (outfit != null && !outfit.isEmpty()) Cosmetics.draw(vc, pose, state, outfit, emote, swing, aiming, light, overlay);
				// The glider (gliders sheet) above a gliding player, in root space.
				if (state.getDataOrDefault(GLIDING, false)) Gliders.draw(new Smooth(vc, pose, new Matrix4f(), light, overlay), Gliders.of(outfit, entityId));
			} finally {
				Smooth.detail = 1F;
			}
		});
		// Shirt and pants designs: painted pictures wrapped round the torso and legs (their own texture).
		if (outfit != null && !outfit.isEmpty()) Cosmetics.submitClothes(collector, poseStack, state, outfit, emote, swing, aiming, light, overlay);
		return true;
	}

	/** Roblox root space -> part transform (studs), including walk swing, emote pose and head look. */
	static Matrix4f partMatrix(AvatarPartDef part, LivingEntityRenderState state, Pose emote, float swing, boolean aiming) {
		Matrix4f m = jointMatrix(part, state, emote, swing, aiming);
		return m.translate(part.centerX() - part.pivotX(), part.centerY() - part.pivotY(), part.centerZ() - part.pivotZ());
	}

	/**
	 * Where the held item goes: the same frame Minecraft's arm gives ItemInHandLayer (model space, blocks, y down the
	 * arm, origin 2 px below the top of the arm), but following the Roblox arm, so the gun moves exactly with the arm
	 * holding it.
	 */
	public static Matrix4f handMatrix(LivingEntityRenderState state, boolean rightArm) {
		AvatarPartDef part = Sheets.AVATAR_PARTS_BY_ID.get(rightArm ? "right_arm" : "left_arm");
		Pose emote = emotePose(state.getDataOrDefault(ENTITY_ID, -1), state.walkAnimationSpeed);
		float swing = (float) Math.cos(state.walkAnimationPos * 0.6662F) * 1.2F * Math.min(1F, state.walkAnimationSpeed);
		Matrix4f joint = jointMatrix(part, state, emote, swing, state.getDataOrDefault(AIMING, false));
		// Minecraft's arm pivot: 2 px below the arm's top, 1 px towards the body from its centre.
		float top = part.centerY() + 1F - part.pivotY();
		joint.translate(part.centerX() - part.pivotX() - Math.signum(part.centerX()) * (1F / 16F) / S, top - (2F / 16F) / S, part.centerZ() - part.pivotZ());
		// studs (y up) -> model space, and back for the local frame: T * F * joint * F^-1 (rotation + translation
		// only).
		return new Matrix4f().translate(0, 1.501F - HIP * S, 0).scale(-S, -S, S).mul(joint).scale(-1F / S, -1F / S, 1F / S);
	}

	static Matrix4f jointMatrix(AvatarPartDef part, LivingEntityRenderState state, Pose emote, float swing, boolean aiming) {
		Matrix4f m = new Matrix4f();
		float bounce = emote == null ? 0 : emote.bounce();
		float bodyYaw = emote == null ? 0 : emote.bodyYaw();
		m.translate(0, bounce, 0).rotateY((float) Math.toRadians(bodyYaw));
		m.translate(part.pivotX(), part.pivotY(), part.pivotZ());
		boolean right = part.centerX() > 0;
		switch (part.swing()) {
			case "arm" -> {
				float x, z;
				if (emote != null) {
					x = right ? emote.rightArmX() : emote.leftArmX();
					z = right ? emote.rightArmZ() : emote.leftArmZ();
				} else if (state.getDataOrDefault(GLIDING, false)) {
					// Gliding: both hands up on the glider's grips (Gliders), like Fortnite.
					x = 170;
					z = -15;
				} else if (aiming) {
					// Holding a gun: both arms point it where you look (left hand on the handguard); a small sway while
					// walking.
					float sway = (float) Math.toDegrees(swing) * 0.12F;
					x = (right ? 90 : 82) - state.xRot + sway;
					z = right ? 0 : -28;
				} else {
					x = (float) Math.toDegrees(swing * part.swingSign());
					z = 0;
				}
				// +x swings the arm forward (towards -Z), +z raises it out to its own side.
				m.rotateX((float) Math.toRadians(x)).rotateZ((float) Math.toRadians(right ? z : -z));
			}
			case "leg" -> {
				float x = emote != null ? (right ? emote.rightLegX() : emote.leftLegX()) : (float) Math.toDegrees(swing * part.swingSign());
				m.rotateX((float) Math.toRadians(x));
			}
			case "head" -> m.rotateY((float) Math.toRadians(-state.yRot)).rotateX((float) Math.toRadians(-state.xRot));
			default -> {
			}
		}
		return m;
	}

	private static void drawMesh(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, RobloxMesh mesh, boolean face, int color, int light, int overlay) {
		Vector3f p = new Vector3f(), n = new Vector3f();
		int[] t = mesh.tris;
		float[] u = new float[3], v = new float[3];
		Vector3f[] ps = { new Vector3f(), new Vector3f(), new Vector3f() };
		Vector3f[] ns = { new Vector3f(), new Vector3f(), new Vector3f() };
		for (int i = 0; i < t.length; i += 3) {
			// Face decal per whole triangle: only when all three corners face forward. (Deciding per corner blended the
			// face texture across triangles round the side of the head, leaving thin black streaks there.)
			boolean front = face;
			for (int k = 0; k < 3 && front; k++) front = mesh.nrm[t[i + k] * 3 + 2] < -0.3F;
			for (int k = 0; k < 3; k++) {
				int idx = t[i + k];
				float vx = mesh.pos[idx * 3], vy = mesh.pos[idx * 3 + 1], vz = mesh.pos[idx * 3 + 2];
				m.transformPosition(vx, vy, vz, p);
				m.transformDirection(mesh.nrm[idx * 3], mesh.nrm[idx * 3 + 1], mesh.nrm[idx * 3 + 2], n);
				// studs (y up, root at hips) -> model space (y down, origin 1.501 above the feet), turned 180 about Z.
				ps[k].set(-p.x * S, 1.501F - (p.y + HIP) * S, p.z * S);
				ns[k].set(-n.x, -n.y, n.z).normalize();
				// Face decal: front-facing head triangles get a planar projection of the face; everything else samples
				// white.
				if (front) {
					u[k] = 0.5F + vx / 1.2F * -1F * 0.92F;
					v[k] = 0.5F - vy / 1.2F * 0.92F;
				} else {
					u[k] = 0.01F;
					v[k] = 0.01F;
				}
			}
			// The entity render type draws quads: send each triangle as a quad with its last corner repeated.
			for (int k = 0; k < 4; k++) {
				int c = Math.min(k, 2);
				vc.addVertex(pose, ps[c].x, ps[c].y, ps[c].z).setColor(color).setUv(u[c], v[c]).setOverlay(overlay).setLight(light).setNormal(pose, ns[c].x, ns[c].y, ns[c].z);
			}
		}
	}

	/** Interpolates the emote_keys sheet for an entity currently emoting. Moving cancels the emote. */
	static Pose emotePose(int entityId, float walkSpeed) {
		Map.Entry<String, Long> e = ClientState.EMOTES.get(entityId);
		if (e == null) return null;
		if (walkSpeed > 0.25F) {
			ClientState.EMOTES.remove(entityId);
			return null;
		}
		EmoteDef def = Sheets.EMOTES_BY_ID.get(e.getKey());
		if (def == null) return null;
		float t = ((System.currentTimeMillis() - e.getValue()) / 1000F / def.loopSeconds()) % 1F;
		List<EmoteKeyDef> keys = Sheets.EMOTE_KEYS.stream().filter(k -> k.emote().equals(def.id())).sorted(Comparator.comparingDouble(EmoteKeyDef::t)).toList();
		EmoteKeyDef a = keys.get(0), b = keys.get(keys.size() - 1);
		for (int i = 0; i + 1 < keys.size(); i++)
			if (t >= keys.get(i).t() && t <= keys.get(i + 1).t()) {
				a = keys.get(i);
				b = keys.get(i + 1);
				break;
			}
		float f = b.t() > a.t() ? (t - a.t()) / (b.t() - a.t()) : 0;
		return new Pose(l(a.rightArmX(), b.rightArmX(), f), l(a.rightArmZ(), b.rightArmZ(), f), l(a.leftArmX(), b.leftArmX(), f), l(a.leftArmZ(), b.leftArmZ(), f),
			l(a.rightLegX(), b.rightLegX(), f), l(a.leftLegX(), b.leftLegX(), f), l(a.bodyYaw(), b.bodyYaw(), f), l(a.bounce(), b.bounce(), f));
	}

	private static float l(float a, float b, float f) {
		return a + (b - a) * f;
	}
}
