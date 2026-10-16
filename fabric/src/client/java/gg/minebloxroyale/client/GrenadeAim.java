package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.match.GrenadeEntity;
import gg.minebloxroyale.match.Knocked;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The grenade's aim arc (2.0.0-test4, creator: "Grenades should be aimable with a blue aimtrail that the grenade
 * follows"): while you aim with a grenade in your hand (right mouse held: 2.0.0-test5, creator: "the grenade aim arc
 * should only be visible when aim button is held"), a blue line from your hand along exactly where it will fly if you
 * throw it now (towards the crosshair, Aim), with a ring where it first lands. The arc is a copy of the grenade flown ahead of
 * time with the grenade's own throw and flight (GrenadeEntity.launch, step), so the real one follows it, bounces and
 * all; after its first landing the line goes faint along its bounces and roll to where it will go off.
 */
public final class GrenadeAim {
	private static final int BLUE = 0x3D8BFF, CORE = 0xCFE6FF;
	/** The line fades in over this much of its length from your hand (blocks). */
	private static final double FADE_IN = 1.2;
	private static final float RING = 0.4F;
	private static GrenadeEntity sim;

	private GrenadeAim() {
	}

	/** Is a grenade's arc to be shown now: aiming (right mouse) with one in your hand, on foot, not building, knocked or watching. */
	private static boolean showing(Minecraft mc) {
		var p = mc.player;
		return p != null && mc.level != null && p.getMainHandItem().is(ModItems.GRENADE) && mc.options.keyUse.isDown() && mc.gui.screen() == null
			&& !BuildMode.active() && p.getVehicle() == null && !p.isSpectator()
			&& !Knocked.is(p);
	}

	/** The arc (WorldVisuals: camera-relative). */
	public static void draw(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (!showing(mc)) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 eye = mc.player.getEyePosition(pt);
		Vec3[] go = GrenadeEntity.launch(eye, Crosshair.aimPoint(mc, 60), mc.player.getViewVector(pt));
		List<Vec3> path = new ArrayList<>();
		int[] land = fly(mc.level, go[0], go[1], path);
		if (path.size() < 2) return;
		int first = land[0];
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			double along = 0;
			for (int i = 0; i + 1 < path.size(); i++) {
				Vec3 a = path.get(i), b = path.get(i + 1);
				double len = a.distanceTo(b);
				if (len < 1e-4) continue;
				float fa = (float) Math.min(1, along / FADE_IN), fb = (float) Math.min(1, (along + len) / FADE_IN);
				along += len;
				Vector3f ra = rel(a, cam), rb = rel(b, cam);
				if (first < 0 || i < first) {
					Tracers.band(vc, pose, ra, rb, 0.09F, BLUE, (int) (fa * 0xB0), (int) (fb * 0xB0));
					Tracers.band(vc, pose, ra, rb, 0.03F, CORE, (int) (fa * 0xFF), (int) (fb * 0xFF));
				} else Tracers.band(vc, pose, ra, rb, 0.05F, BLUE, 0x50, 0x50); // (bouncing on and rolling, faint)
			}
			if (first >= 0) ring(vc, pose, rel(path.get(first), cam), land[1]);
		});
	}

	/**
	 * Flies a copy of the grenade from `start` at `speed` until it would go off (or lies still), adding where it is each
	 * tick to `path`. Returns {the point where it first lands (-1 if it never does), the axis it landed across (0 x, 1 y,
	 * 2 z)}.
	 */
	private static int[] fly(ClientLevel level, Vec3 start, Vec3 speed, List<Vec3> path) {
		if (sim == null || sim.level() != level) sim = new GrenadeEntity(GrenadeEntity.TYPE, level);
		sim.snapTo(start.x, start.y, start.z, 0F, 0F);
		sim.setDeltaMovement(speed);
		sim.setOnGround(false);
		sim.resetFallDistance(); // (each throw starts afresh, as a new grenade does)
		int fuse = (int) MinebloxRoyale.setting("grenadeFuseTicks");
		int first = -1, axis = 1;
		path.add(start);
		for (int t = 0; t < fuse; t++) {
			int hit = sim.step();
			path.add(sim.position());
			if (first < 0 && hit != 0) {
				first = path.size() - 1;
				axis = (hit & GrenadeEntity.HIT_Y) != 0 ? 1 : (hit & GrenadeEntity.HIT_X) != 0 ? 0 : 2;
			}
			if (first >= 0 && sim.onGround() && sim.getDeltaMovement().lengthSqr() < 1e-5) break; // (lying still)
		}
		return new int[] { first, axis };
	}

	/** A ring lying flat on what it lands on (across the axis it hit). */
	private static void ring(VertexConsumer vc, PoseStack.Pose pose, Vector3f at, int axis) {
		Vector3f u = axis == 0 ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0), w = axis == 2 ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
		Vector3f c = new Vector3f(at).add(0, axis == 1 ? 0.03F : 0, 0); // (just above the ground)
		int n = 28, col = 0xC0 << 24 | BLUE;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
			Vector3f o0 = dir(u, w, a0), o1 = dir(u, w, a1);
			Tracers.quad(vc, pose, new Vector3f(c).fma(RING - 0.05F, o0), new Vector3f(c).fma(RING, o0), new Vector3f(c).fma(RING, o1), new Vector3f(c).fma(RING - 0.05F, o1), col,
				col, col, col);
		}
	}

	private static Vector3f dir(Vector3f u, Vector3f w, double a) {
		return new Vector3f(u).mul((float) Math.cos(a)).add(new Vector3f(w).mul((float) Math.sin(a)));
	}

	private static Vector3f rel(Vec3 p, Vec3 cam) {
		return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
	}
}
