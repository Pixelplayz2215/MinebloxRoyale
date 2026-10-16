package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.client.ClientState;
import gg.minebloxroyale.client.WorldVisuals;
import gg.minebloxroyale.client.Tracers;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.sheets.AvatarColorDef;
import gg.minebloxroyale.sheets.AvatarPartDef;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.EmoteKeyDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws an entity as the classic Roblox R6 character (avatar_parts sheet) instead of its Minecraft model, with elbows
 * and knees (Bend), moving as the moves and emote sheets say (Blender animations). Runs in the living-entity model space
 * (y down, front -Z, feet at y=1.501); Roblox studs (y up) map by a 180 degree turn about Z.
 */
public final class RobloxAvatarRenderer {
	public static final RenderStateDataKey<Integer> ENTITY_ID = RenderStateDataKey.create(() -> "minebloxroyale:entity_id");
	public static final RenderStateDataKey<String> COLORS = RenderStateDataKey.create(() -> "minebloxroyale:colors");
	public static final RenderStateDataKey<Boolean> AIMING = RenderStateDataKey.create(() -> "minebloxroyale:aiming");
	/** The gun in its hand (weapons sheet id; empty: none): where its shots leave from (muzzle). */
	public static final RenderStateDataKey<String> GUN = RenderStateDataKey.create(() -> "minebloxroyale:gun");
	/** Aiming down the sights (right mouse; bots while they're shooting): the gun raised from the hip into the aim. */
	public static final RenderStateDataKey<Boolean> ADS = RenderStateDataKey.create(() -> "minebloxroyale:ads");
	public static final RenderStateDataKey<Boolean> IN_BUS = RenderStateDataKey.create(() -> "minebloxroyale:in_bus");
	/** Not drawn this frame (your own avatar while the Locker shows its preview of you). */
	public static final RenderStateDataKey<Boolean> HIDDEN = RenderStateDataKey.create(() -> "minebloxroyale:hidden");
	public static final RenderStateDataKey<Boolean> GLIDING = RenderStateDataKey.create(() -> "minebloxroyale:gliding");
	/** Skydiving (falling from the Battle Bus, the glider not open): trailing a contrail from the hands. */
	public static final RenderStateDataKey<Boolean> SKYDIVING = RenderStateDataKey.create(() -> "minebloxroyale:skydiving");
	/** Riding in a car (v2.0.0, CarEntity): sitting, hands on the wheel. */
	public static final RenderStateDataKey<Boolean> SITTING = RenderStateDataKey.create(() -> "minebloxroyale:sitting");
	/** Getting into a car (2.0.0-test3): how far through (0..1), and whether through a door on the car's right. */
	public static final RenderStateDataKey<Float> CAR_ENTER = RenderStateDataKey.create(() -> "minebloxroyale:car_enter");
	public static final RenderStateDataKey<Boolean> CAR_ENTER_RIGHT = RenderStateDataKey.create(() -> "minebloxroyale:car_enter_right");
	/**
	 * Sitting in a car (2.0.0-test4): drawn this much smaller, about its feet, so it fits inside that car (CarEntity.Seat;
	 * a Roblox avatar sitting is taller than most cars' insides). 1 on foot.
	 */
	public static final RenderStateDataKey<Float> SEAT_SCALE = RenderStateDataKey.create(() -> "minebloxroyale:seat_scale");
	/** The Locker's preview of you (its glider and contrail kept apart from your avatar in the world). */
	public static final RenderStateDataKey<Boolean> PREVIEW = RenderStateDataKey.create(() -> "minebloxroyale:preview");
	/** The Locker's Shop (2.0.0-test2): the hovered item ({slot: id}), worn on the preview over the outfit. */
	public static final RenderStateDataKey<Map<String, String>> TRY_ON = RenderStateDataKey.create(() -> "minebloxroyale:try_on");
	/** Knocked down (team modes): drawn crawling, holding nothing. */
	public static final RenderStateDataKey<Boolean> KNOCKED = RenderStateDataKey.create(() -> "minebloxroyale:knocked");
	public static final RenderStateDataKey<Boolean> IS_PLAYER = RenderStateDataKey.create(() -> "minebloxroyale:is_player");
	/** How the entity is moving and what it is doing this frame: what picks its move. */
	public static final RenderStateDataKey<Motion> MOTION = RenderStateDataKey.create(() -> "minebloxroyale:motion");
	/** This frame's pose, worked out the first time it is needed (a fresh holder each frame). */
	public static final RenderStateDataKey<Pose[]> POSE = RenderStateDataKey.create(() -> "minebloxroyale:pose");
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
	/** Roblox root space (studs, y up from the hips) -> the avatar's model space, as the body meshes are drawn. */
	static final Matrix4f ROOT = new Matrix4f().translate(0, 1.501F - HIP * S, 0).scale(-S, -S, S);
	/** Blocks along the ground for one walk cycle (two steps) walking, running, sneaking and crawling. */
	private static final float WALK_STRIDE = 2.0F, RUN_STRIDE = 2.8F, CROUCH_STRIDE = 1.0F, CRAWL_STRIDE = 0.7F;
	/** Seconds a change of move (landing, starting an emote, kneeling to revive...) takes to blend in. */
	private static final float BLEND = 0.22F;
	/** How far out (degrees) the arms are at least, half-way into or out of the skydive (compose). */
	private static final float SKY_ARC = 60F;
	/** Seconds the arm takes to go from a pickaxe swing cut short into the next one. */
	private static final float SWING_BLEND = 0.1F;

	private RobloxAvatarRenderer() {
	}

	/**
	 * A pose (emote_keys and move_keys sheets; angles in degrees, bounce in studs): the arms swing forward (X) and out
	 * to their side (Z), the legs too; the whole body turns (bodyYaw) and lifts (bounce); above the hips the body twists
	 * (to its right), leans (forward) and tilts (to its right); the head nods (down), turns (to its right) and tilts (to
	 * its right); the elbows bend the forearms forward and the knees the shins back (0 = straight); each arm turns about
	 * its own length (Y: so the elbow can fold any way, e.g. across the face); the whole body, legs too, turns over
	 * about the hips (flip: forward, 180 = upside down); and steps sideways (shift, studs to its right: 2.0.0-test5, the
	 * Smooth Slide).
	 */
	public record Pose(float rightArmX, float rightArmZ, float leftArmX, float leftArmZ, float rightLegX, float leftLegX, float bodyYaw, float bounce,
		float rightLegZ, float leftLegZ, float headX, float headY, float headZ, float lean, float tilt, float twist, float rightElbow, float leftElbow,
		float rightKnee, float leftKnee, float rightArmY, float leftArmY, float flip, float shift) {
		static final Pose ZERO = of(new float[24]);
		static final int RAX = 0, RAZ = 1, LAX = 2, LAZ = 3, BOB = 7, HX = 10, HY = 11, HZ = 12, LEAN = 13, TWIST = 15, REL = 16, LEL = 17, RAY = 20, LAY = 21, SHIFT = 23;
		static final int[] ARMS = { RAX, RAZ, LAX, LAZ, REL, LEL, RAY, LAY }, RIGHT_ARM = { RAX, RAZ, REL, RAY };

		float[] values() {
			return new float[] { rightArmX, rightArmZ, leftArmX, leftArmZ, rightLegX, leftLegX, bodyYaw, bounce, rightLegZ, leftLegZ, headX, headY, headZ,
				lean, tilt, twist, rightElbow, leftElbow, rightKnee, leftKnee, rightArmY, leftArmY, flip, shift };
		}

		static Pose of(float[] v) {
			return new Pose(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17], v[18], v[19],
				v[20], v[21], v[22], v[23]);
		}

		Pose lerp(Pose b, float f) {
			if (f <= 0) return this;
			if (f >= 1) return b;
			float[] x = values(), y = b.values();
			for (int i = 0; i < x.length; i++) x[i] += toward(i, x[i], y[i]) * f;
			return of(x);
		}

		/** This pose with some of its channels moved part of the way (f) to another's. */
		Pose mix(Pose b, float f, int... channels) {
			if (f <= 0) return this;
			float[] x = values(), y = b.values();
			for (int i : channels) x[i] += toward(i, x[i], y[i]) * Math.min(1, f);
			return of(x);
		}

		/**
		 * How far channel i goes from a to b: angles the short way round (2.0.0-test6.1: an arm that turns on round, the
		 * Swim's a full turn a loop, comes out of the emote the near way, and the loop's end and start are one pose), bounce
		 * and shift (studs) straight.
		 */
		private static float toward(int i, float a, float b) {
			return i == BOB || i == SHIFT ? b - a : net.minecraft.util.Mth.wrapDegrees(b - a);
		}

		/** This pose with some of its channels moved by how far `to` is from `from`, times k. */
		Pose shift(Pose to, Pose from, float k, int... channels) {
			if (k == 0) return this;
			float[] x = values(), a = to.values(), b = from.values();
			for (int i : channels) x[i] += (a[i] - b[i]) * k;
			return of(x);
		}

		/** This pose with some of its channels changed by amounts (channel, amount, channel, amount...). */
		Pose add(float... pairs) {
			float[] x = values();
			for (int i = 0; i + 1 < pairs.length; i += 2) x[(int) pairs[i]] += pairs[i + 1];
			return of(x);
		}
	}

	/**
	 * What the entity is doing this frame (from the entity, when its render state is made). use: "heal" or "drink"
	 * while using a heal or shield; action: 1 reviving, 2 rebooting, 3 searching a chest (the server says who).
	 */
	public record Motion(boolean sprinting, boolean crouching, boolean onGround, boolean inWater, boolean swimming, boolean climbing, String use,
		int action) {
		public static final Motion STILL = new Motion(false, false, true, false, false, false, null, 0);
	}

	public static boolean submit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		if (!RobloxAssets.ready()) return false;
		boolean crawl = state.getDataOrDefault(KNOCKED, false) && !state.getDataOrDefault(IS_PLAYER, false);
		float seat = state.getDataOrDefault(SEAT_SCALE, 1F);
		boolean scaled = seat < 0.999F;
		if (scaled) { // (about the feet: 1.501 down in the model's space)
			poseStack.pushPose();
			poseStack.translate(0, 1.501F, 0);
			poseStack.scale(seat, seat, seat);
			poseStack.translate(0, -1.501F, 0);
		}
		if (crawl) {
			poseStack.pushPose();
			poseStack.mulPose(CRAWL);
		}
		try {
			return draw(state, poseStack, collector, light, overlay);
		} finally {
			if (crawl) poseStack.popPose();
			if (scaled) poseStack.popPose();
		}
	}

	/**
	 * Upside down (Breakdance Spin's headspin): the whole avatar lifted, as it turns over, by how far the hat or hair on
	 * its head reaches past the top of it, so that rests on the mat rather than going down through it (2.0.0-test6,
	 * creator: "Breakdance emote: head should not go through the mat"; the bare head is put on the mat by the emote's own
	 * bounce, emote_anims.py lift). Things on the ground (the mat) stay where they are.
	 */
	private static Pose restOnHat(Pose pose, LivingEntityRenderState state, Map<String, String> outfit) {
		if (outfit == null || outfit.isEmpty()) return pose;
		float down = -jointMatrix(Sheets.AVATAR_PARTS_BY_ID.get("head"), state, pose).m11(); // (the head's up, pointing down: 1 upside down)
		if (down <= 0.05F) return pose;
		float reach = Cosmetics.headReach(outfit);
		if (reach <= 0) return pose;
		float[] v = pose.values();
		v[Pose.BOB] += reach * Math.min(1F, down);
		return Pose.of(v);
	}

	private static boolean draw(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		AvatarColorDef colors = Sheets.AVATAR_COLORS_BY_ID.getOrDefault(state.getDataOrDefault(COLORS, "classic"), Sheets.AVATAR_COLORS.get(0));
		int entityId = state.getDataOrDefault(ENTITY_ID, -1);
		Pose pose = pose(state);
		String uuid = state.getDataOrDefault(PLAYER_UUID, "");
		Map<String, String> outfit = uuid.isEmpty() ? null : Cosmetics.OUTFITS.get(UUID.fromString(uuid));
		Map<String, String> tryOn = state.getDataOrDefault(TRY_ON, null);
		if (tryOn != null) {
			outfit = new HashMap<>(outfit == null ? Map.of() : outfit);
			outfit.putAll(tryOn);
		}
		CosmeticDef shirt = Cosmetics.worn(outfit, "shirt"), pants = Cosmetics.worn(outfit, "pants");
		pose = restOnHat(pose, state, outfit);
		Pose posed = pose;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(RobloxAssets.TEXTURE), (ps, vc) -> {
			for (AvatarPartDef part : Sheets.AVATAR_PARTS) {
				RobloxMesh mesh = RobloxAssets.mesh(part.id());
				if (mesh == null) continue;
				int color = 0xFF000000 | switch (part.colorSlot()) {
					case "head" -> colors.head();
					case "torso" -> shirt != null ? shirt.color() : colors.torso();
					case "arms" -> shirt != null ? shirt.color() : colors.arms();
					default -> pants != null ? pants.color() : colors.legs();
				};
				drawMesh(vc, ps, jointMatrix(part, state, posed), part, bend(part, posed), mesh, color, light, overlay);
			}
		});
		// Hats, hair, face accessories, back bling and shoes (Blender models; the lighter copies further away, so lots of
		// bots at once stay smooth; the Locker's preview always the full ones).
		boolean preview = state.getDataOrDefault(PREVIEW, false);
		double distSq = preview ? 0 : state.distanceToCameraSq;
		if (outfit != null && !outfit.isEmpty()) Cosmetics.submit(collector, poseStack, state, outfit, pose, distSq, light, overlay);
		submitProps(collector, poseStack, state, pose, entityId, distSq, light, overlay);
		// The glider (gliders sheet) above a gliding player, in root space: popping open, riding the air, put away again
		// on landing (Gliders).
		int track = preview ? Gliders.PREVIEW_TRACK : entityId;
		GliderDef glider = Gliders.of(outfit, entityId);
		Matrix4f gt = Gliders.transform(track, glider.id(), state.getDataOrDefault(GLIDING, false), state.bodyRot);
		if (gt != null) Gliders.submit(collector, poseStack, glider, gt, distSq, light, overlay);
		// (a gun in hand: where its bullets leave from, the end of its barrel: Tracers)
		if (!preview && state.getDataOrDefault(AIMING, false)) {
			Vector3f end = poseStack.last().pose().transformPosition(muzzle(state, state.getDataOrDefault(GUN, "")));
			var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
			Tracers.hand(entityId, cam.x + end.x, cam.y + end.y, cam.z + end.z);
		}
		// Contrails (v2.0.0): skydiving, from the hands; riding a glider, from its wingtips (Contrails).
		boolean sky = state.getDataOrDefault(SKYDIVING, false);
		if (preview) {
			if (sky) Contrails.preview(collector, poseStack, outfit, handTips(state, pose));
		} else if (sky) Contrails.record(entityId, outfit, poseStack.last().pose(), handTips(state, pose), false);
		else if (gt != null && Gliders.riding(track)) {
			Vector3f[] tips = Gliders.wingtips(glider);
			for (Vector3f t : tips) ROOT.transformPosition(gt.transformPosition(t));
			Contrails.record(entityId, outfit, poseStack.last().pose(), tips, true);
		}
		// Shirt and pants designs: painted pictures wrapped round the torso and legs (their own texture).
		if (outfit != null && !outfit.isEmpty()) Cosmetics.submitClothes(collector, poseStack, state, outfit, pose, light, overlay);
		return true;
	}

	/**
	 * Where the held item goes: the same frame Minecraft's arm gives ItemInHandLayer (model space, blocks, y down the
	 * arm, origin 2 px below the top of the arm), but following the Roblox arm and its forearm, so the gun moves exactly
	 * with the hand holding it.
	 */
	public static Matrix4f handMatrix(LivingEntityRenderState state, boolean rightArm) {
		AvatarPartDef part = Sheets.AVATAR_PARTS_BY_ID.get(rightArm ? "right_arm" : "left_arm");
		Pose pose = pose(state);
		Matrix4f joint = jointMatrix(part, state, pose).mul(bend(part, pose).lower());
		// Minecraft's arm pivot: 2 px below the arm's top, 1 px towards the body from its centre.
		float top = part.centerY() + 1F - part.pivotY();
		joint.translate(part.centerX() - part.pivotX() - Math.signum(part.centerX()) * (1F / 16F) / S, top - (2F / 16F) / S, part.centerZ() - part.pivotZ());
		// studs (y up) -> model space, and back for the local frame: T * F * joint * F^-1 (rotation + translation
		// only).
		return new Matrix4f().translate(0, 1.501F - HIP * S, 0).scale(-S, -S, S).mul(joint).scale(-1F / S, -1F / S, 1F / S);
	}

	/**
	 * Where the end of each gun's barrel is in its model (item pixels: x along the barrel to the muzzle, y up; z is 8,
	 * the middle): the front of the muzzle in fabric/tools/blender/items.py.
	 */
	private static final Map<String, float[]> MUZZLE = Map.of("pistol", new float[] { 12.62F, 10.55F }, "smg", new float[] { 15.54F, 9.15F },
		"assault_rifle", new float[] { 16.29F, 9.7F }, "pump_shotgun", new float[] { 15.94F, 9.55F }, "sniper", new float[] { 16.93F, 8.95F },
		"rocket_launcher", new float[] { 16.19F, 10.2F });

	/**
	 * The end of the barrel of the gun in the right hand, this frame (model space; 2.0.0-test5, creator: "Bullets appear
	 * to be shooting out of players hand rather than out of the gun itself"): the hand's frame (handMatrix), then exactly
	 * what Minecraft's ItemInHandLayer and the guns' item models do to it (thirdperson_righthand: turned 90 about y,
	 * moved 1.5 up and 2 along, 0.9 the size), then the muzzle point in the gun's model. Without a gun: the hand's tip.
	 */
	static Vector3f muzzle(LivingEntityRenderState state, String gun) {
		float[] m = MUZZLE.get(gun);
		if (m == null) return handTips(state, pose(state))[0];
		Matrix4f t = handMatrix(state, true).rotateX((float) Math.toRadians(-90)).rotateY((float) Math.toRadians(180)).translate(1 / 16F, 2 / 16F, -10 / 16F)
			.translate(0, 1.5F / 16F, 2 / 16F).rotateXYZ(0, (float) Math.toRadians(90), 0).scale(0.9F).translate(-0.5F, -0.5F, -0.5F);
		return t.transformPosition(new Vector3f(m[0] / 16F, m[1] / 16F, 0.5F));
	}

	/** The ends of both hands this frame (model space): where a skydiver's contrails stream from. */
	private static Vector3f[] handTips(LivingEntityRenderState state, Pose pose) {
		Vector3f[] out = new Vector3f[2];
		for (int i = 0; i < 2; i++) {
			AvatarPartDef part = Sheets.AVATAR_PARTS_BY_ID.get(i == 0 ? "right_arm" : "left_arm");
			Matrix4f joint = jointMatrix(part, state, pose).mul(bend(part, pose).lower());
			// (the arm's end: a stud below its centre, from its pivot)
			Vector3f tip = joint.transformPosition(new Vector3f(part.centerX() - part.pivotX(), part.centerY() - 1F - part.pivotY(), part.centerZ() - part.pivotZ()));
			out[i] = ROOT.transformPosition(tip);
		}
		return out;
	}

	/** Roblox root space -> a body part's joint (studs): its pivot, turned as the pose says, the head also to the look. */
	static Matrix4f jointMatrix(AvatarPartDef part, LivingEntityRenderState state, Pose p) {
		Matrix4f m = new Matrix4f();
		m.translate(p.shift(), p.bounce(), 0).rotateY(rad(p.bodyYaw())).rotateX(rad(-p.flip()));
		// Everything above the hips (the root) twists, leans and tilts together; the legs stay planted.
		if (!part.swing().equals("leg")) m.rotateY(rad(-p.twist())).rotateX(rad(-p.lean())).rotateZ(rad(-p.tilt()));
		m.translate(part.pivotX(), part.pivotY(), part.pivotZ());
		boolean right = part.centerX() > 0;
		switch (part.swing()) {
			// +x swings an arm or leg forward (towards -Z), +z raises it out to its own side.
			case "arm" -> m.rotateX(rad(right ? p.rightArmX() : p.leftArmX())).rotateZ(rad(right ? p.rightArmZ() : -p.leftArmZ()))
				.rotateY(rad(right ? p.rightArmY() : -p.leftArmY()));
			case "leg" -> m.rotateX(rad(right ? p.rightLegX() : p.leftLegX())).rotateZ(rad(right ? p.rightLegZ() : -p.leftLegZ()));
			case "head" -> m.rotateX(rad(-p.headX())).rotateY(rad(-p.headY())).rotateZ(rad(-p.headZ()))
				.rotateY(rad(-state.yRot)).rotateX(rad(-state.xRot));
			default -> {
			}
		}
		return m;
	}

	/** How much this part bends this frame: an arm at its elbow, a leg at its knee. */
	static Bend.Of bend(AvatarPartDef part, Pose p) {
		boolean right = part.centerX() > 0;
		return switch (part.swing()) {
			case "arm" -> new Bend.Of(right ? p.rightElbow() : p.leftElbow(), true);
			case "leg" -> new Bend.Of(right ? p.rightKnee() : p.leftKnee(), false);
			default -> Bend.Of.NONE;
		};
	}

	private static float rad(float deg) {
		return (float) Math.toRadians(deg);
	}

	private static void drawMesh(VertexConsumer vc, PoseStack.Pose pose, Matrix4f joint, AvatarPartDef part, Bend.Of bend, RobloxMesh mesh, int color, int light,
		int overlay) {
		float ox = part.centerX() - part.pivotX(), oy = part.centerY() - part.pivotY(), oz = part.centerZ() - part.pivotZ();
		boolean face = part.faceDecal();
		// Each corner bent, through its joint, from studs (y up, root at hips) to model space (y down, origin 1.501 above
		// the feet, turned 180 about Z: ROOT) and through the pose: one matrix for it (and one for its normal), worked out
		// once (2.0.0-test3, lag); the triangles in pairs, as quads (RobloxMesh.quads).
		Matrix4f m = new Matrix4f(pose.pose()).mul(ROOT).mul(joint);
		Matrix3f nm = new Matrix3f(pose.normal()).mul(new Matrix3f(ROOT).invert().transpose()).mul(new Matrix3f(joint).invert().transpose());
		Mesh.Deform deform = bend.deform();
		Vector3f p = new Vector3f(), n = new Vector3f();
		int[] q = mesh.quads;
		for (int i = 0; i < q.length; i++) {
			int idx = q[i];
			// Face decal per whole triangle: only where all three corners face forward. (Deciding per corner blended the
			// face texture across triangles round the side of the head, leaving thin black streaks there.)
			boolean front = face && mesh.forward[i / 4];
			float vx = mesh.pos[idx * 3], vy = mesh.pos[idx * 3 + 1], vz = mesh.pos[idx * 3 + 2];
			p.set(vx + ox, vy + oy, vz + oz);
			n.set(mesh.nrm[idx * 3], mesh.nrm[idx * 3 + 1], mesh.nrm[idx * 3 + 2]);
			if (deform != null) deform.apply(p, n);
			float nx = nm.m00 * n.x + nm.m10 * n.y + nm.m20 * n.z, ny = nm.m01 * n.x + nm.m11 * n.y + nm.m21 * n.z, nz = nm.m02 * n.x + nm.m12 * n.y + nm.m22 * n.z;
			float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (l > 1e-6F) {
				nx /= l;
				ny /= l;
				nz /= l;
			}
			// Face decal: front-facing head triangles get a planar projection of the face; everything else samples white.
			vc.addVertex(m.m00() * p.x + m.m10() * p.y + m.m20() * p.z + m.m30(), m.m01() * p.x + m.m11() * p.y + m.m21() * p.z + m.m31(),
				m.m02() * p.x + m.m12() * p.y + m.m22() * p.z + m.m32()).setColor(color)
				.setUv(front ? 0.5F + vx / 1.2F * -1F * 0.92F : 0.01F, front ? 0.5F - vy / 1.2F * 0.92F : 0.01F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
		}
	}

	// ---- which move, this frame ----

	/** Each avatar's going-on between frames: how far it has gone, how fast, and what it was doing (for blending). */
	private static final class Track {
		boolean started;
		double x, y, z;
		long nanos, seen;
		float phase, speed, vy, airTime;
		float run, crouch, air, aim, ads, use;
		String useMove = "heal", useWas = "heal", mode = "";
		float useSince = 1;
		Pose from = Pose.ZERO, last = Pose.ZERO;
		String fromMode = "";
		float since = 1;
		// the pickaxe: how far through a swing it was, and where a swing cut short left the arm and body
		float swing, swingAt = -1, swingTwist, swingLean, lastTwist, lastLean;
		Pose swingFrom = Pose.ZERO, ground = Pose.ZERO;
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static long lastSweep;

	/** This frame's pose for the entity (worked out once a frame and kept in its render state). */
	public static Pose pose(LivingEntityRenderState state) {
		Pose[] holder = state.getData(POSE);
		if (holder != null && holder[0] != null) return holder[0];
		Pose p = computePose(state);
		if (holder != null) holder[0] = p;
		return p;
	}

	private static Pose computePose(LivingEntityRenderState state) {
		// (the Locker's Contrails tab: you, skydiving as in a match, 2.0.0-test3)
		if (state.getDataOrDefault(PREVIEW, false) && state.getDataOrDefault(SKYDIVING, false)) return Moves.loop("skydive", state.ageInTicks / 20F);
		float swing = state instanceof ArmedEntityRenderState a ? a.swingAnimation : 0;
		int id = state.getDataOrDefault(ENTITY_ID, -1);
		Pose emote = state.getDataOrDefault(KNOCKED, false) ? null : emotePose(id, state.walkAnimationSpeed);
		Pose p = compose(new Frame(id, state.getDataOrDefault(MOTION, Motion.STILL), state.getDataOrDefault(AIMING, false),
			state.getDataOrDefault(ADS, false), state.getDataOrDefault(GLIDING, false), state.getDataOrDefault(SKYDIVING, false), state.getDataOrDefault(KNOCKED, false),
			state.getDataOrDefault(SITTING, false), emote, state.x, state.y, state.z, state.ageInTicks / 20F,
			state.xRot, swing), System.nanoTime());
		// Getting into a car: climbing in (its sitting goes on underneath, so it's seated once in)
		Float enter = state.getDataOrDefault(CAR_ENTER, null);
		if (enter != null && enter < 1) p = Moves.at(state.getDataOrDefault(CAR_ENTER_RIGHT, false) ? "car_enter_r" : "car_enter", enter);
		// Minecraft draws a crouching player 2 px lower; the crouch already stands its feet on the ground.
		return state instanceof AvatarRenderState a && a.isCrouching ? p.add(Pose.BOB, 2F / 16F / S) : p;
	}

	/**
	 * What picks an avatar's pose this frame: who (entity id), how it moves, what it holds and does, where it is,
	 * its age (seconds), where it looks (pitch, down +) and how far through a swing it is (0..1).
	 */
	record Frame(int id, Motion motion, boolean aiming, boolean ads, boolean gliding, boolean skydiving, boolean knocked, boolean sitting, Pose emote, double x,
		double y, double z, float time, float pitch, float swing) {
	}

	/** The pose for this frame (the avatar's track remembers how it got here; now: System.nanoTime()). */
	static Pose compose(Frame f, long now) {
		Motion m = f.motion();
		if (now - lastSweep > 10_000_000_000L) { // (forget avatars not seen for a while)
			lastSweep = now;
			TRACKS.values().removeIf(t -> now - t.seen > 30_000_000_000L);
		}
		Track tr = TRACKS.computeIfAbsent(f.id(), k -> new Track());
		tr.seen = now;
		float dt = tr.started ? Math.min(0.1F, (now - tr.nanos) / 1e9F) : 0;
		double dx = f.x() - tr.x, dy = f.y() - tr.y, dz = f.z() - tr.z;
		float along = (float) Math.sqrt(dx * dx + dz * dz);
		if (!tr.started || along > 4 || Math.abs(dy) > 4) along = 0; // (first seen, or teleported)
		if (!tr.started || Math.abs(dy) > 4) dy = 0;
		tr.started = true;
		tr.x = f.x();
		tr.y = f.y();
		tr.z = f.z();
		tr.nanos = now;
		if (dt > 0) {
			tr.speed = ease(tr.speed, along / dt, 12, dt);
			tr.vy = ease(tr.vy, (float) dy / dt, 12, dt);
		}
		boolean airborne = !m.onGround() && !m.inWater() && !m.climbing();
		tr.airTime = airborne ? tr.airTime + dt : 0;
		// (sprinting, or just going faster than a walk: bots don't always say they sprint)
		tr.run = ease(tr.run, m.sprinting() ? 1 : Math.max(0, Math.min(1, (tr.speed - 4.8F) / 1.2F)), 6, dt);
		tr.crouch = ease(tr.crouch, m.crouching() ? 1 : 0, 8, dt);
		tr.air = ease(tr.air, airborne && (tr.airTime > 0.12F || tr.vy > 2) ? 1 : 0, 12, dt);
		tr.aim = ease(tr.aim, f.aiming() ? 1 : 0, 10, dt);
		tr.ads = ease(tr.ads, f.ads() ? 1 : 0, 14, dt); // (raising the gun to aim takes about a sixth of a second)
		tr.use = ease(tr.use, m.use() != null ? 1 : 0, 8, dt);
		// (from healing straight to drinking or back: one into the other)
		if (m.use() != null && !m.use().equals(tr.useMove)) {
			tr.useWas = tr.useMove;
			tr.useMove = m.use();
			tr.useSince = 0;
		}
		tr.useSince += dt;
		float stride = lerp(lerp(WALK_STRIDE, RUN_STRIDE, tr.run), CROUCH_STRIDE, tr.crouch);
		tr.phase += along / (f.knocked() ? CRAWL_STRIDE : stride);

		String mode;
		Pose target;
		if (f.sitting()) { // in a car (v2.0.0)
			mode = "sit";
			target = Moves.loop("sit", f.time());
		} else if (f.emote() != null) {
			mode = "emote";
			target = f.emote();
		} else if (f.knocked()) {
			mode = "crawl";
			target = Moves.at("crawl", tr.phase);
		} else if (m.action() > 0) {
			mode = switch (m.action()) {
				case 1 -> "revive";
				case 2 -> "reboot";
				default -> "search";
			};
			target = Moves.loop(mode, f.time());
		} else if (f.gliding()) {
			mode = "glide";
			target = Moves.loop("glide", f.time());
		} else if (f.skydiving()) {
			// (2.0.0-test3, creator: "a new skydiving animation") Belly down, spread out and fluttering; looking down, head
			// first into a dive (Blender's skydive and dive moves), more the further down you look.
			mode = "skydive";
			float dive = Math.max(0, Math.min(1, (f.pitch() - 25) / 45F));
			dive = dive * dive * (3 - 2 * dive);
			// (the arms straighten and stop turning first as they come in to the dive: a turned, bent arm's square
			// corners would catch the body)
			Pose sky = Moves.loop("skydive", f.time()), down = Moves.loop("dive", f.time());
			target = sky.lerp(down, dive).mix(sky.lerp(down, Math.min(1, dive * 2)), 1, Pose.REL, Pose.LEL, Pose.RAY, Pose.LAY);
		} else if (m.swimming()) {
			mode = "swim";
			target = Moves.loop("swim", f.time());
		} else if (m.inWater() && !m.onGround()) {
			mode = "tread";
			target = Moves.loop("tread", f.time());
		} else if (m.climbing()) {
			mode = "climb";
			target = Moves.at("climb", (float) (f.y() / 1.2));
		} else {
			mode = "ground";
			target = ground(f, tr);
		}
		// A change of move blends in over BLEND seconds (from wherever the body was).
		if (!mode.equals(tr.mode)) {
			tr.from = tr.last;
			tr.since = 0;
			tr.fromMode = tr.mode;
			tr.mode = mode;
		}
		tr.since += dt;
		float k = Math.min(1, tr.since / BLEND);
		Pose out = tr.from.lerp(target, k * k * (3 - 2 * k));
		// (2.0.0-test3) Into or out of the skydive (out of the bus, or up onto the glider's grips), the arms go out wide
		// round the body on the way rather than straight across it.
		if (k < 1 && (mode.equals("skydive") || tr.fromMode.equals("skydive"))) {
			float wide = SKY_ARC * (float) Math.sin(Math.PI * k);
			out = out.add(Pose.RAZ, Math.max(0, wide - out.rightArmZ())).add(Pose.LAZ, Math.max(0, wide - out.leftArmZ()));
		}
		tr.last = out;
		return out;
	}

	/**
	 * On foot: standing, walking, running or sneaking (their cycles along the ground walked), in the air (from going up
	 * to falling fast), and on top of that the arms holding a gun where you look, healing or drinking, or swinging the
	 * pickaxe.
	 */
	private static Pose ground(Frame f, Track tr) {
		float time = f.time();
		float moving = Math.min(1, tr.speed / 2.2F);
		Pose loco = Moves.at("walk", tr.phase).lerp(Moves.at("run", tr.phase), tr.run);
		Pose stand = Moves.loop("idle", time).lerp(loco, moving);
		Pose low = Moves.loop("crouch", time).lerp(Moves.at("crouch_walk", tr.phase), moving);
		Pose base = stand.lerp(low, tr.crouch);
		// (jumping 8 blocks/s up -> 0, falling 10 down -> about 0.55, falling fast -> 1: spread out)
		base = base.lerp(Moves.at("air", (8 - tr.vy) / 32F), tr.air);
		if (tr.aim > 0) {
			// A gun where you look (the arms are in the leaning body: its lean taken off the look): carried at the hip,
			// raised into the aim with both hands when aiming down the sights (v2.0.0: Blender's hip and aim moves).
			float look = (f.pitch() - base.lean() + 90) / 180F;
			Pose hold = Moves.at("hip", look).lerp(Moves.at("aim", look), tr.ads);
			base = base.mix(hold, tr.aim, Pose.ARMS).add(Pose.TWIST, -base.twist() * tr.aim);
			// Each shot kicks: Blender's recoil move, how far it moves from its start, times the gun's kick.
			float[] shot = Tracers.lastShot(f.id());
			float span = Moves.seconds("recoil");
			if (shot != null && shot[0] >= 0 && shot[0] < span) {
				Pose r = Moves.at("recoil", shot[0] / span), r0 = Moves.at("recoil", 0);
				base = base.shift(r, r0, shot[1] * tr.aim, Pose.RAX, Pose.RAZ, Pose.LAX, Pose.LAZ, Pose.REL, Pose.LEL, Pose.LEAN, Pose.HX, Pose.TWIST);
			}
		}
		if (tr.use > 0) {
			Pose use = Moves.loop(tr.useMove, time);
			float k = tr.useSince / BLEND;
			if (k < 1) use = Moves.loop(tr.useWas, time).lerp(use, k * k * (3 - 2 * k));
			base = base.mix(use, tr.use * (1 - tr.aim), Pose.ARMS).add(Pose.HX, use.headX() * tr.use);
		}
		float swing = f.swing();
		// (a new swing before the last one ended, as holding the button down to mine does: from where that one was)
		if (swing < tr.swing - 0.05F && tr.mode.equals("ground")) {
			tr.swingFrom = tr.ground;
			tr.swingTwist = tr.lastTwist;
			tr.swingLean = tr.lastLean;
			tr.swingAt = f.time();
		}
		tr.swing = swing;
		tr.lastTwist = tr.lastLean = 0;
		if (swing > 0 && swing < 1 && tr.aim < 0.5F) {
			// The pickaxe: wound up, chopped down, followed through; the body turns and leans with it (eased in and out),
			// standing up and still only (a stepping thigh or a sneak's lean leaves no room: the hips would sink into the legs).
			Pose s = Moves.at("swing", swing), s0 = Moves.at("swing", 0);
			float w = Math.min(1, Math.min(swing, 1 - swing) * 6);
			float body = w * (1 - Math.max(moving, tr.crouch));
			tr.lastTwist = (s.twist() - s0.twist()) * body;
			tr.lastLean = (s.lean() - s0.lean()) * body;
			base = base.mix(s, w, Pose.RIGHT_ARM).add(Pose.TWIST, tr.lastTwist, Pose.LEAN, tr.lastLean);
		}
		float k = (f.time() - tr.swingAt) / SWING_BLEND;
		if (k >= 0 && k < 1) {
			float left = 1 - k * k * (3 - 2 * k);
			base = base.mix(tr.swingFrom, left, Pose.RIGHT_ARM).add(Pose.TWIST, tr.swingTwist * left, Pose.LEAN, tr.swingLean * left);
		}
		tr.ground = base;
		return base;
	}

	private static float ease(float from, float to, float rate, float dt) {
		return from + (to - from) * (1 - (float) Math.exp(-rate * dt));
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
	}

	// ---- emotes ----

	/** Each emote's props (emotes sheet: "model@where, ..."), read once: {model, where}. */
	private static final Map<String, List<String[]>> PROPS = new HashMap<>();

	/**
	 * What the emote being played brings out (2.0.0-test5, creator: Drum Solo's "Drum and Drumsticks", then the
	 * accessories for more emotes: a guitar, a top hat and cane, a lasso, boxing gloves, pom-poms, a cardboard mat and a
	 * boombox, a cape, glow sticks): Blender models (fabric/tools/blender/props.py), each in its own space: @ground the
	 * avatar's own (staying put while the body bounces), @body the upper body's, @head the head's, @right_hand /
	 * @left_hand a hand's below the elbow, following the forearm. "/n" after it spins the prop n times a loop round its
	 * own up-and-down line (2.0.0-test6, creator: "Animated Lasso on the emote": the lasso's loop turning about the
	 * forearm, the rope going round like a spoke).
	 */
	private static void submitProps(SubmitNodeCollector collector, PoseStack poseStack, LivingEntityRenderState state, Pose pose, int entityId, double distSq,
		int light, int overlay) {
		Map.Entry<String, Long> playing = ClientState.EMOTES.get(entityId);
		if (playing == null || state.getDataOrDefault(KNOCKED, false) || state.getDataOrDefault(SITTING, false)) return;
		EmoteDef def = Sheets.EMOTES_BY_ID.get(playing.getKey());
		if (def == null || def.props().equals("-")) return;
		List<String[]> props = PROPS.computeIfAbsent(def.id(), id -> {
			List<String[]> out = new ArrayList<>();
			for (String spec : def.props().split(",")) {
				String[] mw = spec.trim().split("@");
				if (mw.length != 2) continue;
				String[] wt = mw[1].split("/"); // (where, then how many turns it spins each loop: 2.0.0-test6, the lasso)
				out.add(new String[] { mw[0].trim(), wt[0].trim(), wt.length > 1 ? wt[1].trim() : "0" });
			}
			return out;
		});
		List<Matrix4f> frames = new ArrayList<>();
		float phase = ((System.currentTimeMillis() - playing.getValue()) / 1000F / def.loopSeconds()) % 1F; // (as emotePose times the dance)
		for (String[] prop : props) {
			Matrix4f m = new Matrix4f(ROOT);
			switch (prop[1]) {
				case "right_hand", "left_hand" -> {
					AvatarPartDef arm = Sheets.AVATAR_PARTS_BY_ID.get(prop[1].equals("right_hand") ? "right_arm" : "left_arm");
					m.mul(jointMatrix(arm, state, pose)).mul(bend(arm, pose).lower());
				}
				case "body" -> m.mul(jointMatrix(Sheets.AVATAR_PARTS_BY_ID.get("torso"), state, pose));
				case "head" -> m.mul(jointMatrix(Sheets.AVATAR_PARTS_BY_ID.get("head"), state, pose));
				default -> {
				}
			}
			float turns = Float.parseFloat(prop[2]);
			if (turns != 0) m.rotateY(phase * turns * (float) (Math.PI * 2)); // (round its own up-and-down line: the lasso's loop about the forearm)
			frames.add(m);
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (ps, vc) -> {
			for (int i = 0; i < props.size(); i++) Mesh.lod(props.get(i)[0], distSq, Cosmetics.AVATAR_SIZE * 2).draw(ps, frames.get(i), vc, light, overlay, 0xFFFFFF, 0xFFFFFF);
		});
	}

	/** Each emote's keyframes in order (emote_keys sheet), sorted once. */
	private static final Map<String, List<EmoteKeyDef>> KEYS = new HashMap<>();

	/**
	 * Interpolates the emote_keys sheet for an entity currently emoting. Moving cancels the emote. (The keys are the
	 * Blender animation sampled finely, fabric/tools/blender/emote_video.py, so straight lines between them follow its
	 * curves.)
	 */
	static Pose emotePose(int entityId, float walkSpeed) {
		Map.Entry<String, Long> e = ClientState.EMOTES.get(entityId);
		if (e == null) return null;
		if (walkSpeed > 0.25F) {
			ClientState.EMOTES.remove(entityId);
			return null;
		}
		EmoteDef def = Sheets.EMOTES_BY_ID.get(e.getKey());
		if (def == null) return null;
		List<EmoteKeyDef> keys = KEYS.computeIfAbsent(def.id(),
			id -> Sheets.EMOTE_KEYS.stream().filter(k -> k.emote().equals(id)).sorted(Comparator.comparingDouble(EmoteKeyDef::t)).toList());
		if (keys.isEmpty()) return null;
		float t = ((System.currentTimeMillis() - e.getValue()) / 1000F / def.loopSeconds()) % 1F;
		EmoteKeyDef a = keys.get(0), b = keys.get(keys.size() - 1);
		for (int i = 0; i + 1 < keys.size(); i++)
			if (t >= keys.get(i).t() && t <= keys.get(i + 1).t()) {
				a = keys.get(i);
				b = keys.get(i + 1);
				break;
			}
		float f = b.t() > a.t() ? (t - a.t()) / (b.t() - a.t()) : 0;
		return pose(a).lerp(pose(b), f);
	}

	private static Pose pose(EmoteKeyDef k) {
		return new Pose(k.rightArmX(), k.rightArmZ(), k.leftArmX(), k.leftArmZ(), k.rightLegX(), k.leftLegX(), k.bodyYaw(), k.bounce(), k.rightLegZ(),
			k.leftLegZ(), k.headX(), k.headY(), k.headZ(), k.lean(), k.tilt(), k.twist(), k.rightElbow(), k.leftElbow(), k.rightKnee(), k.leftKnee(),
			k.rightArmY(), k.leftArmY(), k.flip(), k.shift());
	}
}
