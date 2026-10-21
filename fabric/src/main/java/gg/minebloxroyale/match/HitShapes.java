package gg.minebloxroyale.match;

/**
 * The shapes a player's shot is tested against (Hitboxes): Minecraft's column and, for a standing body, the blocky Roblox
 * body's own parts, turned the way it faces. Plain numbers only (no game classes), so it can be checked on its own.
 */
final class HitShapes {
	/** Blocks per stud (the avatar's size, RobloxAvatarRenderer.S). */
	private static final double S = 0.36;
	/** Added round each body part: a bullet is a line, a little leeway as in Fortnite. */
	private static final double PAD = 0.06;
	/** The standing body's parts, feet at 0, facing +Z: {half width, bottom, top, half depth} (avatar_parts). */
	static final double[][] PARTS = {
		{ 1 * S, 0, 2 * S, 0.5 * S }, // legs
		{ 2 * S, 2 * S, 4 * S, 0.5 * S }, // torso with the arms at its sides
		{ 0.6 * S, 4 * S, 5.2 * S, 0.6 * S }, // head
	};
	/** A hit at or above this height over the feet of a standing body is on the head (the neck: 4 studs). */
	static final double NECK = 4 * S - 0.04;

	/** Where a fighter was one tick: feet, body yaw, standing (the Roblox body) or not (crouching, crawling, in a car). */
	record Sample(long tick, double x, double y, double z, float yaw, boolean standing, double width, double height, double eye) {
	}

	private HitShapes() {
	}

	/** On the head: for a standing body, at the neck or above; otherwise near the eyes (the old rule). */
	static boolean head(Sample s, double above) {
		return s.standing() ? above >= NECK : above > s.eye() - 0.3;
	}

	/** Where along o + t * d (t from 0 to 1) the line first meets this fighter, or -1. */
	static double rayBody(double ox, double oy, double oz, double dx, double dy, double dz, Sample s) {
		double hw = s.width() / 2;
		double best = rayBox(ox - s.x(), oy - s.y(), oz - s.z(), dx, dy, dz, -hw, 0, -hw, hw, s.height(), hw);
		if (!s.standing()) return best;
		// The body's parts, turned with it: its right (cos, 0, sin) and its front (-sin, 0, cos), as Minecraft faces.
		double yaw = Math.toRadians(s.yaw()), c = Math.cos(yaw), n = Math.sin(yaw);
		double px = ox - s.x(), py = oy - s.y(), pz = oz - s.z();
		double lx = px * c + pz * n, lz = -px * n + pz * c, ldx = dx * c + dz * n, ldz = -dx * n + dz * c;
		for (double[] part : PARTS) {
			double t = rayBox(lx, py, lz, ldx, dy, ldz, -part[0] - PAD, part[1] - PAD, -part[3] - PAD, part[0] + PAD, part[2] + PAD, part[3] + PAD);
			if (t >= 0 && (best < 0 || t < best)) best = t;
		}
		return best;
	}

	/** Where along o + t * d (t from 0 to 1) the line first meets the box, 0 if it starts inside, or -1. */
	static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz, double x0, double y0, double z0, double x1, double y1,
		double z1) {
		double near = 0, far = 1;
		double[] o = { ox, oy, oz }, d = { dx, dy, dz }, lo = { x0, y0, z0 }, hi = { x1, y1, z1 };
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1e-12) {
				if (o[i] < lo[i] || o[i] > hi[i]) return -1;
				continue;
			}
			double a = (lo[i] - o[i]) / d[i], b = (hi[i] - o[i]) / d[i];
			if (a > b) {
				double t = a;
				a = b;
				b = t;
			}
			near = Math.max(near, a);
			far = Math.min(far, b);
			if (near > far) return -1;
		}
		return near;
	}
}
