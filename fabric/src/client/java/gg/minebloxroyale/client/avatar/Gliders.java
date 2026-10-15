package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.client.WorldVisuals;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.GliderKeyDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The 46 gliders (gliders sheet), each a Blender model (fabric/tools/blender/gliders.py: nine shapes, each glider's own
 * decoration, bigger than before), drawn above a gliding player in Roblox root space on the frame its raised hands hold
 * (RobloxAvatarRenderer), in the glider's two colours; the lighter copy far away. v2.0.0 (creator: "glider animations.
 * Deploy annimation, actual gliding ... as well as putting gliders away"): it pops open out of the back, sways as it
 * rides the air, banks into turns, and folds back into the back on landing, as keyframed in Blender (glider_keys sheet,
 * fabric/tools/blender/glider_video.py).
 */
final class Gliders {
	/** The crossbar (gliders.py BAR_Y, BAR_Z): what the animations turn and scale about (root space, studs). */
	private static final float PIVOT_Y = 3.7F, PIVOT_Z = -0.22F;
	/** After popping open, the gliding sway eases in over this long (seconds). */
	private static final float SWAY_IN = 0.5F;
	/** Banking into a turn: degrees of roll per degree a second of turning, at most MAX_BANK either way. */
	private static final float BANK_PER = 0.12F, MAX_BANK = 18F;
	/** The sheet's channels, in this order. */
	private static final int SX = 0, SY = 1, SZ = 2, Y = 3, Z = 4, PITCH = 5, ROLL = 6, YAW = 7;
	private static final float[] REST = { 1, 1, 1, 0, 0, 0, 0, 0 };
	private static final Map<String, List<GliderKeyDef>> KEYS = new HashMap<>();
	/** The Locker's preview of you keeps its own track (so your avatar in the world doesn't put a glider away after). */
	static final int PREVIEW_TRACK = Integer.MIN_VALUE;
	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static long lastSweep;

	private Gliders() {
	}

	/** Each avatar's glider lately: out or not and since when (nanos), which glider, how far it banks. */
	private static final class Track {
		boolean open;
		long opened, closed = Long.MIN_VALUE, seen, yawAt;
		String glider = "";
		float yaw, bank;
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

	/**
	 * Where the glider is this frame (root space, studs: a transform of the model), or null when none is out: opening
	 * (deploy), gliding (the sway, eased in after the pop, and the bank into a turn) or put away just after (stow).
	 * gliding: out now; bodyYaw: the body's facing (degrees), for the bank.
	 */
	static @Nullable Matrix4f transform(int track, String gliderId, boolean gliding, float bodyYaw) {
		long now = System.nanoTime();
		if (now - lastSweep > 10_000_000_000L) { // (forget avatars not seen for half a minute)
			lastSweep = now;
			TRACKS.values().removeIf(t -> now - t.seen > 30_000_000_000L);
		}
		Track tr = TRACKS.computeIfAbsent(track, k -> new Track());
		tr.seen = now;
		if (gliding && (!tr.open || !tr.glider.equals(gliderId))) { // (a different glider picked in the Locker pops open too)
			tr.open = true;
			tr.opened = now;
			tr.glider = gliderId;
		} else if (!gliding && tr.open) {
			tr.open = false;
			tr.closed = now;
		}
		float dt = tr.yawAt == 0 ? 0 : (now - tr.yawAt) / 1e9F;
		if (dt > 0 && dt < 0.25F) {
			float rate = Mth.wrapDegrees(bodyYaw - tr.yaw) / dt; // (+: turning right)
			float want = tr.open ? Math.max(-MAX_BANK, Math.min(MAX_BANK, -rate * BANK_PER)) : 0;
			tr.bank += (want - tr.bank) * (1 - (float) Math.exp(-6 * dt));
		}
		tr.yaw = bodyYaw;
		tr.yawAt = now;
		float[] k;
		if (tr.open) {
			float s = (now - tr.opened) / 1e9F, deploy = seconds("deploy");
			if (s < deploy) k = sample("deploy", s / deploy);
			else {
				k = sample("glide", ((s - deploy) / seconds("glide")) % 1F);
				float in = Math.min(1, (s - deploy) / SWAY_IN);
				for (int i = 0; i < k.length; i++) k[i] = REST[i] + (k[i] - REST[i]) * in;
			}
		} else {
			float s = (now - tr.closed) / 1e9F, stow = seconds("stow");
			if (tr.closed == Long.MIN_VALUE || s >= stow) return null;
			k = sample("stow", s / stow);
		}
		if (k[SX] < 0.02F) return null; // (still tucked away)
		return new Matrix4f().translate(0, PIVOT_Y + k[Y], PIVOT_Z + k[Z]).rotateY(rad(k[YAW])).rotateX(rad(k[PITCH])).rotateZ(rad(k[ROLL] + tr.bank))
			.scale(k[SX], k[SY], k[SZ]).translate(0, -PIVOT_Y, -PIVOT_Z);
	}

	/** Fully open and riding the air (not opening, not being put away): when its wingtips trail contrails. */
	static boolean riding(int track) {
		Track tr = TRACKS.get(track);
		return tr != null && tr.open && (System.nanoTime() - tr.opened) / 1e9F > seconds("deploy") * 0.6F;
	}

	static void submit(SubmitNodeCollector collector, PoseStack poseStack, GliderDef g, Matrix4f t, boolean far, int light, int overlay) {
		Mesh mesh = Mesh.get(g.id() + (far ? "_far" : ""));
		Matrix4f local = new Matrix4f(RobloxAvatarRenderer.ROOT).mul(t);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> mesh.draw(pose, local, vc, light, overlay, g.color() & 0xFFFFFF, g.color2() & 0xFFFFFF));
	}

	/** The glider's two wingtips (root space, studs, before its animation): its furthest points out to either side. */
	static Vector3f[] wingtips(GliderDef g) {
		float[] t = Mesh.get(g.id()).sideTips();
		return new Vector3f[] { new Vector3f(t[0], t[1], t[2]), new Vector3f(t[3], t[4], t[5]) };
	}

	private static List<GliderKeyDef> keys(String anim) {
		return KEYS.computeIfAbsent(anim, a -> {
			List<GliderKeyDef> out = new ArrayList<>();
			for (GliderKeyDef r : Sheets.GLIDER_KEYS) if (r.anim().equals(a)) out.add(r);
			out.sort(Comparator.comparingDouble(GliderKeyDef::t));
			return out;
		});
	}

	private static float seconds(String anim) {
		List<GliderKeyDef> k = keys(anim);
		return k.isEmpty() ? 0.5F : k.get(0).seconds();
	}

	/** An animation's channels at t (0..1), straight between its sheet rows (as the Blender sampling was). */
	private static float[] sample(String anim, float t) {
		List<GliderKeyDef> k = keys(anim);
		if (k.size() < 2) return REST.clone();
		float x = Math.max(0, Math.min(1, t)) * (k.size() - 1);
		int i = Math.min(k.size() - 2, (int) x);
		float f = x - i;
		float[] a = channels(k.get(i)), b = channels(k.get(i + 1));
		for (int c = 0; c < a.length; c++) a[c] += (b[c] - a[c]) * f;
		return a;
	}

	private static float[] channels(GliderKeyDef r) {
		return new float[] { r.sx(), r.sy(), r.sz(), r.y(), r.z(), r.pitch(), r.roll(), r.yaw() };
	}

	private static float rad(float deg) {
		return (float) Math.toRadians(deg);
	}
}
