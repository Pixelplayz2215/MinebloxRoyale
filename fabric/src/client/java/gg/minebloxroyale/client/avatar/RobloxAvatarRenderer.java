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
	public static final RenderStateDataKey<Boolean> IN_BUS = RenderStateDataKey.create(() -> "minebloxroyale:in_bus");
	/** Not drawn this frame (your own avatar while the Locker shows its preview of you). */
	public static final RenderStateDataKey<Boolean> HIDDEN = RenderStateDataKey.create(() -> "minebloxroyale:hidden");
	public static final RenderStateDataKey<Boolean> GLIDING = RenderStateDataKey.create(() -> "minebloxroyale:gliding");
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

	private RobloxAvatarRenderer() {
	}

	/**
	 * A pose (emote_keys and move_keys sheets; angles in degrees, bounce in studs): the arms swing forward (X) and out
	 * to their side (Z), the legs too; the whole body turns (bodyYaw) and lifts (bounce); above the hips the body twists
	 * (to its right), leans (forward) and tilts (to its right); the head nods (down), turns (to its right) and tilts (to
	 * its right); the elbows bend the forearms forward and the knees the shins back (0 = straight).
	 */
	public record Pose(float rightArmX, float rightArmZ, float leftArmX, float leftArmZ, float rightLegX, float leftLegX, float bodyYaw, float bounce,
		float rightLegZ, float leftLegZ, float headX, float headY, float headZ, float lean, float tilt, float twist, float rightElbow, float leftElbow,
		float rightKnee, float leftKnee) {
		static final Pose ZERO = of(new float[20]);
		static final int RAX = 0, RAZ = 1, LAX = 2, LAZ = 3, BOB = 7, HX = 10, HY = 11, HZ = 12, LEAN = 13, TWIST = 15, REL = 16, LEL = 17;
		static final int[] ARMS = { RAX, RAZ, LAX, LAZ, REL, LEL }, RIGHT_ARM = { RAX, RAZ, REL };

		float[] values() {
			return new float[] { rightArmX, rightArmZ, leftArmX, leftArmZ, rightLegX, leftLegX, bodyYaw, bounce, rightLegZ, leftLegZ, headX, headY, headZ,
				lean, tilt, twist, rightElbow, leftElbow, rightKnee, leftKnee };
		}

		static Pose of(float[] v) {
			return new Pose(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17], v[18], v[19]);
		}

		Pose lerp(Pose b, float f) {
			if (f <= 0) return this;
			if (f >= 1) return b;
			float[] x = values(), y = b.values();
			for (int i = 0; i < x.length; i++) x[i] += (y[i] - x[i]) * f;
			return of(x);
		}

		/** This pose with some of its channels moved part of the way (f) to another's. */
		Pose mix(Pose b, float f, int... channels) {
			if (f <= 0) return this;
			float[] x = values(), y = b.values();
			for (int i : channels) x[i] += (y[i] - x[i]) * Math.min(1, f);
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
		Pose pose = pose(state);
		String uuid = state.getDataOrDefault(PLAYER_UUID, "");
		Map<String, String> outfit = uuid.isEmpty() ? null : Cosmetics.OUTFITS.get(UUID.fromString(uuid));
		CosmeticDef shirt = Cosmetics.worn(outfit, "shirt"), pants = Cosmetics.worn(outfit, "pants");
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
				drawMesh(vc, ps, jointMatrix(part, state, pose), part, bend(part, pose), mesh, color, light, overlay);
			}
		});
		// Hats, hair, face accessories, back bling and shoes (Blender models; the lighter copies far away, so lots of
		// bots at once stay smooth).
		if (outfit != null && !outfit.isEmpty()) Cosmetics.submit(collector, poseStack, state, outfit, pose, state.distanceToCameraSq > 24 * 24, light, overlay);
		// The glider (gliders sheet) above a gliding player, in root space.
		if (state.getDataOrDefault(GLIDING, false)) Gliders.submit(collector, poseStack, Gliders.of(outfit, entityId), state.distanceToCameraSq > 32 * 32, light, overlay);
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

	/** Roblox root space -> a body part's joint (studs): its pivot, turned as the pose says, the head also to the look. */
	static Matrix4f jointMatrix(AvatarPartDef part, LivingEntityRenderState state, Pose p) {
		Matrix4f m = new Matrix4f();
		m.translate(0, p.bounce(), 0).rotateY(rad(p.bodyYaw()));
		// Everything above the hips (the root) twists, leans and tilts together; the legs stay planted.
		if (!part.swing().equals("leg")) m.rotateY(rad(-p.twist())).rotateX(rad(-p.lean())).rotateZ(rad(-p.tilt()));
		m.translate(part.pivotX(), part.pivotY(), part.pivotZ());
		boolean right = part.centerX() > 0;
		switch (part.swing()) {
			// +x swings an arm or leg forward (towards -Z), +z raises it out to its own side.
			case "arm" -> m.rotateX(rad(right ? p.rightArmX() : p.leftArmX())).rotateZ(rad(right ? p.rightArmZ() : -p.leftArmZ()));
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
				p.set(vx + ox, vy + oy, vz + oz);
				n.set(mesh.nrm[idx * 3], mesh.nrm[idx * 3 + 1], mesh.nrm[idx * 3 + 2]);
				bend.apply(p, n);
				joint.transformPosition(p);
				joint.transformDirection(n);
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

	// ---- which move, this frame ----

	/** Each avatar's going-on between frames: how far it has gone, how fast, and what it was doing (for blending). */
	private static final class Track {
		boolean started;
		double x, y, z;
		long nanos, seen;
		float phase, speed, vy, airTime;
		float run, crouch, air, aim, use;
		String useMove = "heal", mode = "";
		Pose from = Pose.ZERO, last = Pose.ZERO;
		float since = 1;
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
		float swing = state instanceof ArmedEntityRenderState a ? a.swingAnimation : 0;
		int id = state.getDataOrDefault(ENTITY_ID, -1);
		Pose emote = state.getDataOrDefault(KNOCKED, false) ? null : emotePose(id, state.walkAnimationSpeed);
		Pose p = compose(new Frame(id, state.getDataOrDefault(MOTION, Motion.STILL), state.getDataOrDefault(AIMING, false),
			state.getDataOrDefault(GLIDING, false), state.getDataOrDefault(KNOCKED, false), emote, state.x, state.y, state.z, state.ageInTicks / 20F,
			state.xRot, swing), System.nanoTime());
		// Minecraft draws a crouching player 2 px lower; the crouch already stands its feet on the ground.
		return state instanceof AvatarRenderState a && a.isCrouching ? p.add(Pose.BOB, 2F / 16F / S) : p;
	}

	/**
	 * What picks an avatar's pose this frame: who (entity id), how it moves, what it holds and does, where it is,
	 * its age (seconds), where it looks (pitch, down +) and how far through a swing it is (0..1).
	 */
	record Frame(int id, Motion motion, boolean aiming, boolean gliding, boolean knocked, Pose emote, double x, double y, double z, float time,
		float pitch, float swing) {
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
		tr.use = ease(tr.use, m.use() != null ? 1 : 0, 8, dt);
		if (m.use() != null) tr.useMove = m.use();
		float stride = lerp(lerp(WALK_STRIDE, RUN_STRIDE, tr.run), CROUCH_STRIDE, tr.crouch);
		tr.phase += along / (f.knocked() ? CRAWL_STRIDE : stride);

		String mode;
		Pose target;
		if (f.emote() != null) {
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
			tr.mode = mode;
		}
		tr.since += dt;
		float k = Math.min(1, tr.since / BLEND);
		Pose out = tr.from.lerp(target, k * k * (3 - 2 * k));
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
			// Both hands on the gun where you look (the arms are in the leaning body: its lean taken off the look).
			Pose aim = Moves.at("aim", (f.pitch() - base.lean() + 90) / 180F);
			base = base.mix(aim, tr.aim, Pose.ARMS).add(Pose.TWIST, -base.twist() * tr.aim);
		}
		if (tr.use > 0) {
			Pose use = Moves.loop(tr.useMove, time);
			base = base.mix(use, tr.use * (1 - tr.aim), Pose.ARMS).add(Pose.HX, use.headX() * tr.use);
		}
		float swing = f.swing();
		if (swing > 0 && swing < 1 && tr.aim < 0.5F) {
			// The pickaxe: wound up, chopped down, followed through; the body turns with it (eased in and out).
			Pose s = Moves.at("swing", swing), s0 = Moves.at("swing", 0);
			float w = Math.min(1, Math.min(swing, 1 - swing) * 6);
			base = base.mix(s, w, Pose.RIGHT_ARM).add(Pose.TWIST, (s.twist() - s0.twist()) * w, Pose.LEAN, (s.lean() - s0.lean()) * w);
		}
		return base;
	}

	private static float ease(float from, float to, float rate, float dt) {
		return from + (to - from) * (1 - (float) Math.exp(-rate * dt));
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
	}

	// ---- emotes ----

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
			k.leftLegZ(), k.headX(), k.headY(), k.headZ(), k.lean(), k.tilt(), k.twist(), k.rightElbow(), k.leftElbow(), k.rightKnee(), k.leftKnee());
	}
}
