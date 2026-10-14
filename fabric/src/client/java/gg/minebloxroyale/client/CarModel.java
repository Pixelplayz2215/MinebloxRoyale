package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import gg.minebloxroyale.match.CarBlockEntity;
import java.util.Random;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Parked cars in the reboot van's and Battle Bus's smooth Roblox "plastic" style (creator: "modeled the same way as the
 * reboot van and battle bus"; no Fortnite asset is used): a sedan, a sports car with a stripe and a spoiler, a pickup
 * with an open bed, a tall SUV with a roof rack and a spare wheel, a little hatchback, a police car with a light bar and
 * a taxi with its roof sign. Rounded bodies and cabins, dark tinted glass, round wheels with hubcaps, headlights that
 * glow, tail lights, bumpers and mirrors. Block units, nose along +Z, origin on the ground in the middle (the invisible
 * car blocks the server puts there carry it).
 * <p>
 * A car looks more damaged as its durability runs down (creator; the stages picked from mockups), each stage keeping the
 * damage before it: 2 scuffed (a cracked windscreen, dents in a door and the bonnet, scratches to bare metal), 3 damaged
 * (cracked side and rear windows, the front bumper hanging at one end, a mirror snapped off, a hubcap gone, a dented
 * roof, more dents and scratches), 4 nearly wrecked (every window smashed, the driver's door hanging open, the bonnet
 * buckled up over the engine, a headlight out; the engine smokes, CarBlockEntity.smoke). Left and right are the car's
 * own, sitting in it facing the nose: +X is its left (the driver's side), -X its right.
 */
public final class CarModel {
	private static final int MIRROR = 0xFFD3E6F5, GLASS = 0xFF34495C, TIRE = 0xFF232327, HUB = 0xFFC9CDD4, LIGHT = 0xFFFFF1B8, RED = 0xFFE03A3A, GREY = 0xFF8E97A3,
		DARK = 0xFF1B1E24, BLUE_LIGHT = 0xFF3A7BFF, AMBER = 0xFFF5A623, WHITE = 0xFFF2F4F8, BED = 0xFF3A3F47, FULL_BRIGHT = 0xF000F0;
	// Damage: bare metal in scratches, the dark inside of the car, glass shards, cracks, a dead headlight, a bare hub.
	private static final int BARE = 0xFFB9C0C8, INSIDE = 0xFF15181D, SHARD = 0xFF6F8AA6, CRACK = 0xFFE8F1F8, DEAD_LIGHT = 0xFF4A4F57, BARE_HUB = 0xFF3A3D42,
		NUT = 0xFF8E959E;

	private CarModel() {
	}

	/** The body: a rounded box on the centre line (centre height cy, half sizes hx, hy, hz, corner radius r). */
	private record Body(float cy, float hx, float hy, float hz, float r) {
		float top() {
			return cy + hy;
		}

		/**
		 * The point of the body's surface in the direction of (x, y, z) from its core, lifted `lift` off it, and the surface
		 * normal there: dents and scratches follow the rounded body.
		 */
		void surface(float x, float y, float z, float lift, Vector3f out, Vector3f normal) {
			float qy = y - cy;
			float ix = clamp(x, hx - r), iy = clamp(qy, hy - r), iz = clamp(z, hz - r);
			normal.set(x - ix, qy - iy, z - iz);
			if (normal.lengthSquared() < 1e-12F) normal.set(x >= 0 ? 1 : -1, 0, 0);
			normal.normalize();
			out.set(ix + normal.x * (r + lift), iy + normal.y * (r + lift) + cy, iz + normal.z * (r + lift));
		}
	}

	/** The cabin's shape (see cabin()). */
	private record Cabin(float y0, float y1, float z0, float z1, float zt0, float zt1, float hx0, float hx1, boolean twoDoors) {
	}

	/** What's painted on the driver's door (front left), so it goes with the door when it hangs open. */
	private enum Door { PLAIN, POLICE, TAXI }

	/** stage: 1 new, 2 scuffed, 3 damaged, 4 nearly wrecked (CarBlockEntity.stage). */
	public static void draw(PoseStack.Pose pose, VertexConsumer vc, int light, CarBlockEntity.Kind kind, int color, int trim, int stage) {
		switch (kind) {
			case SPORTS -> sports(pose, vc, light, color, trim, stage);
			case PICKUP -> pickup(pose, vc, light, color, stage);
			case SUV -> suv(pose, vc, light, color, stage);
			case HATCHBACK -> hatchback(pose, vc, light, color, stage);
			case POLICE -> {
				Cabin c = sedan(pose, vc, light, color, stage, Door.POLICE, trim);
				// White doors, a light bar (red and blue) and a push bar. (Hanging open, the driver's door takes its white
				// with it.)
				for (float s : new float[] { -1, 1 }) {
					if (stage >= 4 && s > 0) {
						float d0 = doorStart(c);
						box(pose, vc, light, 1.13F, 0.66F, (-1.0F + d0) / 2, 0.02F, 0.24F, (d0 + 1.0F) / 2, 0, trim);
					} else {
						box(pose, vc, light, s * 1.13F, 0.66F, -0.05F, 0.02F, 0.24F, 0.95F, 0, trim);
					}
				}
				box(pose, vc, light, 0, 1.6F, -0.15F, 0.62F, 0.06F, 0.16F, 0, DARK);
				box(pose, vc, light, -0.32F, 1.71F, -0.15F, 0.28F, 0.06F, 0.13F, 0, RED);
				box(pose, vc, FULL_BRIGHT, 0.32F, 1.71F, -0.15F, 0.28F, 0.06F, 0.13F, 0, BLUE_LIGHT);
				box(pose, vc, light, 0, 0.55F, 2.42F, 0.7F, 0.2F, 0.06F, 0, DARK);
			}
			case TAXI -> {
				Cabin c = sedan(pose, vc, light, color, stage, Door.TAXI, trim);
				// A checker stripe along both sides and the roof sign. (Hanging open, the driver's door takes its squares
				// with it.)
				float d0 = doorStart(c), d1 = c.z1 - 0.05F;
				for (float s : new float[] { -1, 1 })
					for (int i = 0; i < 12; i++) {
						float z = -1.65F + i * 0.3F;
						if (stage >= 4 && s > 0 && z + 0.15F > d0 && z - 0.15F < d1) continue;
						box(pose, vc, light, s * 1.13F, 0.82F, z, 0.02F, 0.06F, 0.15F, 0, i % 2 == 0 ? DARK : WHITE);
					}
				box(pose, vc, light, 0, 1.66F, -0.15F, 0.38F, 0.12F, 0.12F, 0, WHITE);
				box(pose, vc, FULL_BRIGHT, 0, 1.66F, -0.15F, 0.3F, 0.08F, 0.13F, 0, AMBER);
			}
			default -> sedan(pose, vc, light, color, stage, Door.PLAIN, trim);
		}
	}

	// ---- the cars ----

	private static Cabin sedan(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int stage, Door door, int trim) {
		Body b = body(pose, vc, light, 0.64F, 1.12F, 0.3F, 2.28F, color);
		Cabin c = cabin(pose, vc, light, color, stage, new Cabin(0.9F, 1.52F, -1.25F, 0.95F, -0.95F, 0.3F, 1.0F, 0.86F, true));
		front(pose, vc, light, stage, 2.28F, 0.66F, 1.12F);
		back(pose, vc, light, -2.28F, 0.7F, 1.12F);
		wheels(pose, vc, light, stage, 1.45F, -1.45F, 1.02F, 0.36F);
		damage(pose, vc, light, stage, b, c, color, door, trim);
		return c;
	}

	private static void sports(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int trim, int stage) {
		Body b = body(pose, vc, light, 0.52F, 1.16F, 0.27F, 2.3F, color);
		Cabin c = cabin(pose, vc, light, color, stage, new Cabin(0.76F, 1.17F, -1.35F, 0.75F, -0.95F, 0F, 0.92F, 0.74F, false));
		// Twin racing stripes nose to tail: bonnet, roof, boot.
		for (float s : new float[] { -0.22F, 0.22F }) {
			if (stage < 4) box(pose, vc, light, s, 0.8F, 1.5F, 0.1F, 0.01F, 0.75F, 0, trim); // (under the buckled bonnet once it's up)
			box(pose, vc, light, s, 1.18F, -0.475F, 0.1F, 0.01F, 0.47F, 0, trim);
			box(pose, vc, light, s, 0.8F, -1.8F, 0.1F, 0.01F, 0.45F, 0, trim);
		}
		// Spoiler on two posts, side vents.
		for (float s : new float[] { -0.7F, 0.7F }) box(pose, vc, light, s, 0.92F, -2.05F, 0.05F, 0.14F, 0.05F, 0, DARK);
		box(pose, vc, light, 0, 1.08F, -2.1F, 1.08F, 0.04F, 0.2F, -8, trim);
		for (float s : new float[] { -1, 1 }) box(pose, vc, light, s * 1.15F, 0.55F, 0.5F, 0.02F, 0.08F, 0.35F, 0, DARK);
		front(pose, vc, light, stage, 2.3F, 0.52F, 1.16F);
		back(pose, vc, light, -2.3F, 0.56F, 1.16F);
		wheels(pose, vc, light, stage, 1.45F, -1.5F, 1.06F, 0.34F);
		damage(pose, vc, light, stage, b, c, color, Door.PLAIN, trim);
	}

	private static void pickup(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int stage) {
		Body b = body(pose, vc, light, 0.72F, 1.15F, 0.36F, 2.35F, color);
		Cabin c = cabin(pose, vc, light, color, stage, new Cabin(1.05F, 1.8F, -0.2F, 1.2F, -0.18F, 0.78F, 1.04F, 0.96F, false));
		// The open bed behind the cab: a dark floor with low walls and a tailgate.
		box(pose, vc, light, 0, 1.09F, -1.25F, 1.0F, 0.02F, 1.0F, 0, BED);
		for (float s : new float[] { -1, 1 }) BusModel.roundedBox(pose, vc, light, s * 1.08F, 1.22F, -1.25F, 0.07F, 0.16F, 1.08F, 0.05F, color);
		BusModel.roundedBox(pose, vc, light, 0, 1.22F, -2.28F, 1.15F, 0.16F, 0.07F, 0.05F, color);
		BusModel.roundedBox(pose, vc, light, 0, 1.22F, -0.22F, 1.08F, 0.16F, 0.06F, 0.05F, color);
		front(pose, vc, light, stage, 2.35F, 0.78F, 1.15F);
		back(pose, vc, light, -2.35F, 0.85F, 1.15F);
		wheels(pose, vc, light, stage, 1.5F, -1.5F, 1.05F, 0.42F);
		damage(pose, vc, light, stage, b, c, color, Door.PLAIN, 0);
	}

	private static void suv(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int stage) {
		Body b = body(pose, vc, light, 0.74F, 1.15F, 0.36F, 2.3F, color);
		Cabin c = cabin(pose, vc, light, color, stage, new Cabin(1.07F, 1.88F, -2.05F, 1.3F, -1.95F, 0.85F, 1.06F, 0.98F, true));
		// Roof rails, a spare wheel on the back door.
		for (float s : new float[] { -0.75F, 0.75F }) box(pose, vc, light, s, 1.93F, -0.55F, 0.05F, 0.05F, 1.25F, 0, DARK);
		// (Facing backwards, its tyre, hub and cap stacked outwards like the road wheels.)
		disc(pose, vc, light, 0, 1.25F, -2.42F, 0.36F, 0.09F, false, TIRE);
		disc(pose, vc, light, 0, 1.25F, -2.535F, 0.2F, 0.02F, false, HUB);
		disc(pose, vc, light, 0, 1.25F, -2.57F, 0.07F, 0.012F, false, DARK);
		front(pose, vc, light, stage, 2.3F, 0.8F, 1.15F);
		back(pose, vc, light, -2.3F, 0.85F, 1.15F);
		wheels(pose, vc, light, stage, 1.5F, -1.45F, 1.05F, 0.42F);
		damage(pose, vc, light, stage, b, c, color, Door.PLAIN, 0);
	}

	private static void hatchback(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int stage) {
		Body b = body(pose, vc, light, 0.62F, 1.08F, 0.3F, 1.95F, color);
		Cabin c = cabin(pose, vc, light, color, stage, new Cabin(0.89F, 1.53F, -1.75F, 0.95F, -1.55F, 0.35F, 0.94F, 0.82F, true));
		front(pose, vc, light, stage, 1.95F, 0.64F, 1.08F);
		back(pose, vc, light, -1.95F, 0.72F, 1.08F);
		wheels(pose, vc, light, stage, 1.2F, -1.2F, 0.98F, 0.34F);
		damage(pose, vc, light, stage, b, c, color, Door.PLAIN, 0);
	}

	// ---- parts ----

	/** The rounded body (corner radius 0.22). */
	private static Body body(PoseStack.Pose pose, VertexConsumer vc, int light, float cy, float hx, float hy, float hz, int color) {
		BusModel.roundedBox(pose, vc, light, 0, cy, 0, hx, hy, hz, 0.22F, color);
		return new Body(cy, hx, hy, hz, Math.min(0.22F, Math.min(hx, Math.min(hy, hz))));
	}

	/**
	 * The cabin, in the body colour: a roof on sloping sides, its base from z0 (back) to z1 (front) at height y0, its roof
	 * from zt0 to zt1 at height y1, half as wide as hx0 at the base and hx1 at the roof. The windscreen and rear window lie
	 * flat on the front and back slopes and the side windows on the sides, inside a frame of pillars (two side windows
	 * with a pillar between them when `twoDoors`), so glass and roof meet in one clean edge (creator: the glued-on
	 * windscreen of the first cars didn't meet the roof properly). The side mirrors hang off its front pillars. Damaged,
	 * the glass cracks and then smashes, and the roof gets a dent.
	 */
	private static Cabin cabin(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int stage, Cabin k) {
		float y0 = k.y0, y1 = k.y1, z0 = k.z0, z1 = k.z1, zt0 = k.zt0, zt1 = k.zt1, hx0 = k.hx0, hx1 = k.hx1;
		Vector3f c = new Vector3f(0, (y0 + y1) / 2, (z0 + z1 + zt0 + zt1) / 4);
		Vector3f bl = new Vector3f(-hx0, y0, z1), br = new Vector3f(hx0, y0, z1), tl = new Vector3f(-hx1, y1, zt1), tr = new Vector3f(hx1, y1, zt1);
		Vector3f bbl = new Vector3f(hx0, y0, z0), bbr = new Vector3f(-hx0, y0, z0), btl = new Vector3f(hx1, y1, zt0), btr = new Vector3f(-hx1, y1, zt0);
		// Roof, front slope, back slope, the two sides.
		Vector3f[][] faces = { { tl, tr, btl, btr }, { bl, br, tr, tl }, { bbl, bbr, btr, btl }, { bbr, bl, tl, btr }, { br, bbl, btl, tr } };
		for (Vector3f[] f : faces) quad(pose, vc, light, f[0], f[1], f[2], f[3], c, 0, 1, 0, 1, color);
		mirrors(pose, vc, light, color, stage, y0, y1, z1, zt1, hx0, hx1);
		// The windows: windscreen, rear window, then each side's (two a side with two doors).
		int seed = 0;
		window(pose, vc, light, stage, faces[1], c, 0.07F, 0.93F, 0.1F, 0.9F, stage >= 2, seed++); // windscreen
		window(pose, vc, light, stage, faces[2], c, 0.09F, 0.91F, 0.12F, 0.88F, stage >= 3, seed++); // rear window
		for (int side = 0; side < 2; side++) {
			Vector3f[] f = faces[3 + side];
			if (k.twoDoors) {
				window(pose, vc, light, stage, f, c, 0.06F, 0.47F, 0.14F, 0.86F, stage >= 3 && side == 0, seed++);
				window(pose, vc, light, stage, f, c, 0.53F, 0.94F, 0.14F, 0.86F, stage >= 3 && side == 1, seed++);
			} else {
				window(pose, vc, light, stage, f, c, 0.08F, 0.92F, 0.14F, 0.86F, stage >= 3 && side == 1, seed++);
			}
		}
		if (stage >= 3) roofDent(pose, vc, light, k, color);
		return k;
	}

	/** One window: glass (cracked if `cracked`), or smashed when nearly wrecked. */
	private static void window(PoseStack.Pose pose, VertexConsumer vc, int light, int stage, Vector3f[] f, Vector3f c, float u0, float u1, float v0, float v1,
		boolean cracked, int seed) {
		if (stage >= 4) {
			smashed(pose, vc, light, f, c, u0, u1, v0, v1, 40 + seed);
			return;
		}
		quad(pose, vc, light, f[0], f[1], f[2], f[3], c, u0, u1, v0, v1, GLASS);
		if (cracked) cracks(pose, vc, light, f, c, u0, u1, v0, v1, 7 + seed, seed == 0 ? 1F : 0.8F);
	}

	/**
	 * Part of the face a-b-c-d (a, b along the bottom, d, c along the top): u from u0 to u1 across it, v from v0 to v1 up
	 * it. Wound to face away from the centre c; anything less than the whole face (a window) sits a hair proud of it.
	 */
	private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, Vector3f a, Vector3f b, Vector3f cc, Vector3f d, Vector3f c, float u0, float u1,
		float v0, float v1, int color) {
		Vector3f[] p = { at(a, b, cc, d, u0, v0), at(a, b, cc, d, u1, v0), at(a, b, cc, d, u1, v1), at(a, b, cc, d, u0, v1) };
		Vector3f n = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[0])).normalize();
		Vector3f mid = new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).mul(0.25F);
		if (n.dot(new Vector3f(mid).sub(c)) < 0) {
			n.negate();
			p = new Vector3f[] { p[0], p[3], p[2], p[1] };
		}
		float proud = u0 > 0 || v0 > 0 ? 0.012F : 0;
		for (Vector3f q : p)
			vc.addVertex(pose, q.x + n.x * proud, q.y + n.y * proud, q.z + n.z * proud).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(pose, n.x, n.y, n.z);
	}

	/** The point u across and v up the face a-b-c-d. */
	private static Vector3f at(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u, float v) {
		Vector3f bottom = new Vector3f(a).lerp(b, u), top = new Vector3f(d).lerp(c, u);
		return bottom.lerp(top, v);
	}

	/** Nose: glowing headlights at the corners (the left one out when nearly wrecked), a dark grille, a grey bumper. */
	private static void front(PoseStack.Pose pose, VertexConsumer vc, int light, int stage, float z, float y, float hx) {
		for (float s : new float[] { -1, 1 }) {
			if (stage >= 4 && s > 0) box(pose, vc, light, s * (hx - 0.28F), y + 0.08F, z, 0.2F, 0.08F, 0.04F, 0, DEAD_LIGHT);
			else box(pose, vc, FULL_BRIGHT, s * (hx - 0.28F), y + 0.08F, z, 0.2F, 0.08F, 0.04F, 0, LIGHT);
		}
		box(pose, vc, light, 0, y + 0.02F, z + 0.01F, 0.42F, 0.1F, 0.03F, 0, DARK);
		PoseStack.Pose bumper = pose;
		if (stage >= 3) { // the bumper hangs from its right end, its left end down
			bumper = pose.copy();
			bumper.rotateAround(Axis.ZP.rotationDegrees(-10), -hx + 0.12F, y - 0.2F, z + 0.04F);
		}
		BusModel.roundedBox(bumper, vc, light, 0, y - 0.2F, z + 0.04F, hx - 0.02F, 0.08F, 0.07F, 0.05F, GREY);
	}

	/** Tail: red lights at the corners, a grey bumper, a number plate. */
	private static void back(PoseStack.Pose pose, VertexConsumer vc, int light, float z, float y, float hx) {
		for (float s : new float[] { -1, 1 }) box(pose, vc, light, s * (hx - 0.25F), y + 0.06F, z, 0.18F, 0.08F, 0.04F, 0, RED);
		box(pose, vc, light, 0, y - 0.02F, z - 0.01F, 0.22F, 0.07F, 0.02F, 0, WHITE);
		BusModel.roundedBox(pose, vc, light, 0, y - 0.22F, z - 0.04F, hx - 0.02F, 0.08F, 0.07F, 0.05F, GREY);
	}

	/**
	 * Four round wheels (front and back axle z, the tyres' centre x, radius): a tyre whose outer face stands just out
	 * from the body, a silver hub disc and a dark centre cap stacked clear in front of it. (The old hubcap was a small
	 * cylinder through the tyre's side, its face a hair from the tyre's: the two flickered against each other, creator.)
	 * Damaged, the front left one has lost its hubcap: a bare hub and its wheel nuts.
	 */
	private static void wheels(PoseStack.Pose pose, VertexConsumer vc, int light, int stage, float zf, float zb, float x, float r) {
		float tyre = 0.15F, out = x + tyre; // the tyre's outer face
		for (float z : new float[] { zf, zb })
			for (float s : new float[] { -1, 1 }) {
				disc(pose, vc, light, s * x, r, z, r, tyre, true, TIRE);
				if (stage >= 3 && s > 0 && z == zf) {
					disc(pose, vc, light, s * (out + 0.01F), r, z, r * 0.42F, 0.012F, true, BARE_HUB); // out - 0.002 .. out + 0.022
					for (int i = 0; i < 5; i++) {
						double a = i * Math.PI * 2 / 5;
						disc(pose, vc, light, s * (out + 0.025F), r + (float) Math.cos(a) * r * 0.25F, z + (float) Math.sin(a) * r * 0.25F, r * 0.05F, 0.01F, true, NUT);
					}
					continue;
				}
				disc(pose, vc, light, s * (out + 0.025F), r, z, r * 0.58F, 0.02F, true, HUB); // out + 0.005 .. out + 0.045
				disc(pose, vc, light, s * (out + 0.06F), r, z, r * 0.2F, 0.012F, true, DARK); // out + 0.048 .. out + 0.072
			}
	}

	/** A wheel-like disc: a cylinder (centre, radius, half its width) with flat faces, its axis along X or else Z. */
	private static void disc(PoseStack.Pose pose, VertexConsumer vc, int light, float cx, float cy, float cz, float radius, float hw, boolean alongX, int color) {
		int seg = 20;
		Vector3f p = new Vector3f();
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			// Tread: {along the axis, cos, sin} for each corner.
			float[][] tread = { { -hw, c0, s0 }, { hw, c0, s0 }, { hw, c1, s1 }, { -hw, c1, s1 } };
			for (float[] t : tread) discVertex(pose, vc, light, p, cx, cy, cz, t[0], t[1] * radius, t[2] * radius, 0, t[1], t[2], alongX, color);
			for (float side : new float[] { -1, 1 }) {
				float[][] cap = side > 0 ? new float[][] { { 0, 0 }, { c0, s0 }, { c1, s1 }, { 0, 0 } } : new float[][] { { 0, 0 }, { c1, s1 }, { c0, s0 }, { 0, 0 } };
				for (float[] c : cap) discVertex(pose, vc, light, p, cx, cy, cz, side * hw, c[0] * radius, c[1] * radius, side, 0, 0, alongX, color);
			}
		}
	}

	/** One vertex of disc(): (a, u, v) = along the axis and across it; (na, nu, nv) the normal the same way. */
	private static void discVertex(PoseStack.Pose pose, VertexConsumer vc, int light, Vector3f p, float cx, float cy, float cz, float a, float u, float v, float na,
		float nu, float nv, boolean alongX, int color) {
		if (alongX) p.set(cx + a, cy + u, cz + v);
		else p.set(cx + u, cy + v, cz + a);
		float nx = alongX ? na : nu, ny = alongX ? nu : nv, nz = alongX ? nv : na;
		vc.addVertex(pose, p.x, p.y, p.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/**
	 * Side mirrors at the foot of the front pillars, placed from the cabin's own shape (creator: the first ones floated
	 * off the car): a short arm out of the cabin side, a housing in the body colour, and the silvery mirror glass on its
	 * back, facing the driver, with a glint across it. Damaged, the left one has snapped off at its arm.
	 */
	private static void mirrors(PoseStack.Pose pose, VertexConsumer vc, int light, int color, int stage, float y0, float y1, float z1, float zt1, float hx0, float hx1) {
		float y = y0 + 0.13F, t = (y - y0) / (y1 - y0);
		float side = hx0 + (hx1 - hx0) * t, front = z1 + (zt1 - z1) * t, z = front - 0.12F;
		for (float s : new float[] { -1, 1 }) {
			if (stage >= 3 && s > 0) {
				box(pose, vc, light, s * (side + 0.03F), y, z, 0.05F, 0.022F, 0.022F, 0, DARK); // the snapped-off arm
				continue;
			}
			box(pose, vc, light, s * (side + 0.06F), y, z, 0.08F, 0.025F, 0.025F, 0, DARK); // arm, from just inside the cabin
			box(pose, vc, light, s * (side + 0.2F), y + 0.03F, z, 0.07F, 0.07F, 0.045F, 0, color); // housing
			box(pose, vc, light, s * (side + 0.2F), y + 0.03F, z - 0.05F, 0.055F, 0.055F, 0.006F, 0, MIRROR); // the glass, facing back
			box(pose, vc, light, s * (side + 0.2F), y + 0.065F, z - 0.0585F, 0.045F, 0.008F, 0.0025F, 0, WHITE); // a glint across its top
		}
	}

	// ---- damage ----

	/** Where the driver's door starts (towards the back); it ends just behind the windscreen's foot. */
	private static float doorStart(Cabin c) {
		return c.z1 - (c.twoDoors ? 1.05F : 1.25F);
	}

	/**
	 * Dents and scratches by stage; nearly wrecked, the driver's door hangs open (with what's painted on it) and the
	 * bonnet is buckled up in a ridge with the dark engine bay under it. Everything sits clear of the surfaces under it
	 * (dents 0.006-0.008, scratches 0.011, the doorway 0.014): nothing lies on the same plane as anything else.
	 */
	private static void damage(PoseStack.Pose pose, VertexConsumer vc, int light, int stage, Body b, Cabin c, int color, Door door, int trim) {
		if (stage < 2) return;
		float hx = b.hx, cy = b.cy, hz = b.hz, top = b.top(), z0 = c.z0, z1 = c.z1;
		Vector3f ex = new Vector3f(1, 0, 0), ey = new Vector3f(0, 1, 0), ez = new Vector3f(0, 0, 1);
		// Scuffed: the driver's door and the bonnet dented, scratches down the left side. (Nearly wrecked, that door hangs
		// open and the bonnet is buckled: they go with them.)
		if (stage < 4) {
			dent(pose, vc, light, b, hx, cy + 0.02F, z1 - 0.55F, 0.24F, 0.13F, ez, ey, color, 1);
			dent(pose, vc, light, b, 0.35F, top, z1 + 0.6F, 0.25F, 0.22F, ex, ez, color, 2);
			float[][] scratches = { { z1 - 1.0F, cy - 0.02F }, { z1 - 0.7F, cy + 0.06F }, { z1 - 0.2F, cy - 0.06F } };
			for (int k = 0; k < scratches.length; k++) {
				float zs = scratches[k][0], ys = scratches[k][1] + 0.03F * k;
				scratch(pose, vc, light, b, new float[][] { { hx, ys, zs }, { hx, ys - 0.02F, zs + 0.35F }, { hx, ys - 0.05F, zs + 0.7F } });
			}
		}
		if (stage >= 3) {
			dent(pose, vc, light, b, -hx, cy, z0 + 0.4F, 0.26F, 0.14F, ez, ey, color, 3); // right rear
			dent(pose, vc, light, b, hx, cy - 0.04F, hz - 0.55F, 0.2F, 0.12F, ez, ey, color, 4); // left front wing
			if (stage < 4) dent(pose, vc, light, b, -0.4F, top, hz - 0.5F, 0.2F, 0.18F, ex, ez, color, 5); // bonnet, other side
			float[][] scratches = { { z0 + 0.2F, cy + 0.02F }, { z0 + 0.6F, cy - 0.04F } };
			for (float[] sc : scratches)
				scratch(pose, vc, light, b, new float[][] { { -hx, sc[1], sc[0] }, { -hx, sc[1] - 0.03F, sc[0] + 0.4F }, { -hx, sc[1] - 0.07F, sc[0] + 0.8F } });
		}
		if (stage >= 4) {
			openDoor(pose, vc, light, b, c, color, door, trim);
			buckledBonnet(pose, vc, light, b, c, color);
		}
	}

	/**
	 * A dent: a shallow hollow on the body round (x, y, z), rx by ry along the in-surface directions u and v, lit from
	 * above (its upper inside slope in shadow, the lower one catching the light), its outline a little irregular.
	 */
	private static void dent(PoseStack.Pose pose, VertexConsumer vc, int light, Body b, float x, float y, float z, float rx, float ry, Vector3f u, Vector3f v,
		int color, long seed) {
		Random rnd = new Random(seed);
		int rings = 3, seg = 10;
		float[] k = new float[seg];
		for (int i = 0; i < seg; i++) k[i] = 1 + 0.25F * (rnd.nextFloat() * 2 - 1);
		Vector3f p = new Vector3f(), n = new Vector3f(), sum = new Vector3f();
		Vector3f[] pts = new Vector3f[4];
		for (int i = 0; i < rings; i++)
			for (int j = 0; j < seg; j++) {
				int[][] corners = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
				sum.zero();
				for (int q = 0; q < 4; q++) {
					int ri = corners[q][0], sj = corners[q][1] % seg;
					double a = sj * Math.PI * 2 / seg;
					float f = (float) ri / rings * k[sj];
					float ca = (float) Math.cos(a) * rx * f, sa = (float) Math.sin(a) * ry * f;
					b.surface(x + u.x * ca + v.x * sa, y + u.y * ca + v.y * sa, z + u.z * ca + v.z * sa, 0.006F + 0.002F * (rings - ri) / rings, p, n);
					pts[q] = new Vector3f(p);
					sum.add(n);
				}
				double mid = (j + 0.5) * Math.PI * 2 / seg;
				float fade = 1 - (float) i / rings, sin = (float) Math.sin(mid);
				int col = shade(color, 1 + (sin > 0 ? -0.3F : 0.16F) * Math.abs(sin) * (0.5F + 0.5F * fade));
				emit(pose, vc, light, pts, sum.normalize(), col);
			}
	}

	/** A scratch down to bare metal: a thin line along the body through the given points. */
	private static void scratch(PoseStack.Pose pose, VertexConsumer vc, int light, Body b, float[][] pts) {
		Vector3f p0 = new Vector3f(), n0 = new Vector3f(), p1 = new Vector3f(), n1 = new Vector3f();
		for (int i = 0; i + 1 < pts.length; i++) {
			b.surface(pts[i][0], pts[i][1], pts[i][2], 0.011F, p0, n0);
			b.surface(pts[i + 1][0], pts[i + 1][1], pts[i + 1][2], 0.011F, p1, n1);
			Vector3f side = new Vector3f(p1).sub(p0).cross(n0).normalize().mul(0.007F);
			emit(pose, vc, light, new Vector3f[] { new Vector3f(p0).sub(side), new Vector3f(p1).sub(side), new Vector3f(p1).add(side), new Vector3f(p0).add(side) }, n0, BARE);
		}
	}

	/** A dent in the roof (a little irregular, shaded like a hollow lit from above). */
	private static void roofDent(PoseStack.Pose pose, VertexConsumer vc, int light, Cabin c, int color) {
		Random rnd = new Random(5);
		float cz = (c.zt0 + c.zt1) / 2 + 0.1F, cx = 0.15F;
		Vector3f up = new Vector3f(0, 1, 0);
		Vector3f[] rim = new Vector3f[10];
		for (int j = 0; j < 10; j++) {
			double a = j * Math.PI * 2 / 10;
			float k = 1 + 0.25F * (rnd.nextFloat() * 2 - 1);
			rim[j] = new Vector3f(cx + 0.28F * (float) Math.cos(a) * k, c.y1 + 0.006F, cz + 0.22F * (float) Math.sin(a) * k);
		}
		for (int j = 0; j < 10; j++) {
			float cos = (float) Math.cos((j + 0.5) * Math.PI * 2 / 10);
			Vector3f next = rim[(j + 1) % 10];
			emit(pose, vc, light, new Vector3f[] { new Vector3f(cx, c.y1 + 0.007F, cz), rim[j], next, next }, up, shade(color, 1 + (cos > 0 ? -0.25F : 0.12F) * Math.abs(cos)));
		}
	}

	/** Cracks in a window: a starburst round an impact point, with a broken ring round it. */
	private static void cracks(PoseStack.Pose pose, VertexConsumer vc, int light, Vector3f[] f, Vector3f c, float u0, float u1, float v0, float v1, long seed,
		float size) {
		Random rnd = new Random(seed);
		Vector3f n = paneNormal(f, c);
		float cu = 0.3F + 0.4F * rnd.nextFloat(), cv = 0.35F + 0.35F * rnd.nextFloat();
		for (int k = 0; k < 8; k++) {
			double ang = k * Math.PI * 2 / 8 + (rnd.nextFloat() * 2 - 1) * 0.3;
			float len = size * (0.18F + 0.22F * rnd.nextFloat()), mid = len * (0.4F + 0.2F * rnd.nextFloat());
			double kink = ang + (rnd.nextFloat() * 2 - 1) * 0.4;
			float x1 = cu + (float) Math.cos(ang) * mid, y1 = cv + (float) Math.sin(ang) * mid * 1.4F;
			float x2 = x1 + (float) Math.cos(kink) * (len - mid), y2 = y1 + (float) Math.sin(kink) * (len - mid) * 1.4F;
			crackLine(pose, vc, light, f, n, u0, u1, v0, v1, cu, cv, clip(x1), clip(y1));
			crackLine(pose, vc, light, f, n, u0, u1, v0, v1, clip(x1), clip(y1), clip(x2), clip(y2));
		}
		float ring = size * 0.09F;
		for (int k = 0; k < 7; k++) {
			double a0 = k * Math.PI * 2 / 8, a1 = (k + 1) * Math.PI * 2 / 8;
			crackLine(pose, vc, light, f, n, u0, u1, v0, v1, cu + (float) Math.cos(a0) * ring, cv + (float) Math.sin(a0) * ring * 1.4F, cu + (float) Math.cos(a1) * ring,
				cv + (float) Math.sin(a1) * ring * 1.4F);
		}
	}

	private static float clip(float t) {
		return Math.max(0.02F, Math.min(0.98F, t));
	}

	/** One crack: a thin white line on the glass from (a0, b0) to (a1, b1) across the pane (0..1), just off its surface. */
	private static void crackLine(PoseStack.Pose pose, VertexConsumer vc, int light, Vector3f[] f, Vector3f n, float u0, float u1, float v0, float v1, float a0, float b0,
		float a1, float b1) {
		Vector3f p0 = pane(f, u0, u1, v0, v1, a0, b0).add(new Vector3f(n).mul(0.02F)), p1 = pane(f, u0, u1, v0, v1, a1, b1).add(new Vector3f(n).mul(0.02F));
		Vector3f side = new Vector3f(p1).sub(p0).cross(n);
		if (side.lengthSquared() < 1e-12F) return;
		side.normalize().mul(0.006F);
		emit(pose, vc, light, new Vector3f[] { new Vector3f(p0).sub(side), new Vector3f(p1).sub(side), new Vector3f(p1).add(side), new Vector3f(p0).add(side) }, n, CRACK);
	}

	/** A smashed window: a dark hole where the glass was, with jagged shards left round the frame. */
	private static void smashed(PoseStack.Pose pose, VertexConsumer vc, int light, Vector3f[] f, Vector3f c, float u0, float u1, float v0, float v1, long seed) {
		Random rnd = new Random(seed);
		Vector3f n = paneNormal(f, c), hole = new Vector3f(n).mul(0.004F), lift = new Vector3f(n).mul(0.012F);
		emit(pose, vc, light, new Vector3f[] { pane(f, u0, u1, v0, v1, 0, 0).add(hole), pane(f, u0, u1, v0, v1, 1, 0).add(hole), pane(f, u0, u1, v0, v1, 1, 1).add(hole),
			pane(f, u0, u1, v0, v1, 0, 1).add(hole) }, n, INSIDE);
		for (int e = 0; e < 4; e++)
			for (int k = 0; k < 3; k++) {
				float t0 = k / 3F + 0.05F * rnd.nextFloat(), t1 = (k + 1) / 3F - 0.05F * rnd.nextFloat(), depth = 0.12F + 0.18F * rnd.nextFloat(), tm = (t0 + t1) / 2;
				float[][] uv = switch (e) {
					case 0 -> new float[][] { { t0, 0 }, { t1, 0 }, { tm + 0.1F * (rnd.nextFloat() - 0.5F), depth } };
					case 1 -> new float[][] { { 1, t0 }, { 1, t1 }, { 1 - depth, tm } };
					case 2 -> new float[][] { { t1, 1 }, { t0, 1 }, { tm, 1 - depth } };
					default -> new float[][] { { 0, t1 }, { 0, t0 }, { depth, tm } };
				};
				Vector3f tip = pane(f, u0, u1, v0, v1, uv[2][0], uv[2][1]).add(lift);
				emit(pose, vc, light, new Vector3f[] { pane(f, u0, u1, v0, v1, uv[0][0], uv[0][1]).add(lift), pane(f, u0, u1, v0, v1, uv[1][0], uv[1][1]).add(lift), tip,
					new Vector3f(tip) }, n, SHARD);
			}
	}

	/** The point (a, b) across a window that covers u0..u1, v0..v1 of the cabin face f. */
	private static Vector3f pane(Vector3f[] f, float u0, float u1, float v0, float v1, float a, float b) {
		return at(f[0], f[1], f[2], f[3], u0 + (u1 - u0) * a, v0 + (v1 - v0) * b);
	}

	/** The outward normal of the cabin face f (away from the cabin's centre c). */
	private static Vector3f paneNormal(Vector3f[] f, Vector3f c) {
		Vector3f n = new Vector3f(f[1]).sub(f[0]).cross(new Vector3f(f[3]).sub(f[0])).normalize();
		Vector3f mid = new Vector3f(f[0]).add(f[1]).add(f[2]).add(f[3]).mul(0.25F);
		return n.dot(mid.sub(c)) < 0 ? n.negate() : n;
	}

	/** Nearly wrecked: the driver's door hangs wide open on its front hinge, a dark doorway where it was. */
	private static void openDoor(PoseStack.Pose pose, VertexConsumer vc, int light, Body b, Cabin c, int color, Door door, int trim) {
		float d0 = doorStart(c), d1 = c.z1 - 0.05F, ylo = b.cy - 0.2F, yhi = c.y0 + 0.02F;
		Vector3f p = new Vector3f(), n = new Vector3f(), sum = new Vector3f();
		int gy = 6, gz = 8;
		for (int i = 0; i < gy; i++)
			for (int j = 0; j < gz; j++) {
				Vector3f[] q = new Vector3f[4];
				int[][] corners = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
				sum.zero();
				for (int k = 0; k < 4; k++) {
					b.surface(b.hx, ylo + (yhi - ylo) * corners[k][0] / gy, d0 + (d1 - d0) * corners[k][1] / gz, 0.014F, p, n);
					q[k] = new Vector3f(p);
					sum.add(n);
				}
				emit(pose, vc, light, q, sum.normalize(), INSIDE);
			}
		float hingeX = b.hx + 0.02F, midY = (ylo + yhi) / 2, len = d1 - d0, halfY = (yhi - ylo) / 2 + 0.02F;
		PoseStack.Pose swung = pose.copy();
		swung.rotateAround(Axis.YP.rotationDegrees(-65), hingeX, midY, d1);
		box(swung, vc, light, hingeX, midY, d1 - len / 2, 0.035F, halfY, len / 2, 0, door == Door.POLICE ? trim : color);
		box(swung, vc, light, hingeX + 0.04F, midY + 0.08F, d1 - len * 0.82F, 0.006F, 0.025F, 0.07F, 0, GREY); // the handle
		if (door == Door.TAXI) // the checker squares that were on it
			for (int i = 0; i < 12; i++) {
				float z = -1.65F + i * 0.3F, from = Math.max(z - 0.15F, d0), to = Math.min(z + 0.15F, d1);
				if (to > from) box(swung, vc, light, hingeX + 0.041F, 0.82F, (from + to) / 2, 0.006F, 0.06F, (to - from) / 2, 0, i % 2 == 0 ? DARK : WHITE);
			}
	}

	/** Nearly wrecked: the bonnet buckled up into a ridge across it, the dark engine bay showing under its sides. */
	private static void buckledBonnet(PoseStack.Pose pose, VertexConsumer vc, int light, Body b, Cabin c, int color) {
		float top = b.top(), za = c.z1 + 0.12F, zb = b.hz - 0.12F, zm = za + (zb - za) * 0.55F, rise = 0.2F, w = b.hx - 0.18F;
		float[][] plates = { { za, top, zm, top + rise }, { zm, top + rise, zb, top } };
		for (float[] pl : plates) {
			float midZ = (pl[0] + pl[2]) / 2, midY = (pl[1] + pl[3]) / 2 + 0.02F;
			float len = (float) Math.hypot(pl[2] - pl[0], pl[3] - pl[1]), ang = (float) Math.toDegrees(Math.atan2(pl[3] - pl[1], pl[2] - pl[0]));
			PoseStack.Pose tilted = pose.copy();
			tilted.rotateAround(Axis.XP.rotationDegrees(-ang), 0, midY, midZ);
			box(tilted, vc, light, 0, midY, midZ, w, 0.022F, len / 2 + 0.02F, 0, shade(color, 0.92F));
		}
		for (float s : new float[] { -1, 1 }) { // (a little inside the plates' sides: not on the same plane)
			float x = s * (w - 0.012F);
			Vector3f apex = new Vector3f(x, top + rise, zm);
			emit(pose, vc, light, new Vector3f[] { new Vector3f(x, top + 0.01F, za), new Vector3f(x, top + 0.01F, zb), apex, new Vector3f(apex) }, new Vector3f(s, 0, 0), INSIDE);
		}
	}

	/** One quad (four corners, one normal, one colour). */
	private static void emit(PoseStack.Pose pose, VertexConsumer vc, int light, Vector3f[] p, Vector3f n, int color) {
		for (Vector3f q : p)
			vc.addVertex(pose, q.x, q.y, q.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
	}

	private static int shade(int argb, float k) {
		int r = Math.min(255, Math.round((argb >> 16 & 255) * k)), g = Math.min(255, Math.round((argb >> 8 & 255) * k)), bl = Math.min(255, Math.round((argb & 255) * k));
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static float clamp(float v, float lim) {
		return Math.max(-lim, Math.min(lim, v));
	}

	// ---- geometry ----

	/** Corner order of each face, counter-clockwise seen from outside (as Minecraft's own faces), and its normal. */
	private static final float[][][] FACES = {
		{ { -1, -1, 1 }, { -1, -1, -1 }, { 1, -1, -1 }, { 1, -1, 1 }, { 0, -1, 0 } }, // down
		{ { -1, 1, -1 }, { -1, 1, 1 }, { 1, 1, 1 }, { 1, 1, -1 }, { 0, 1, 0 } }, // up
		{ { 1, 1, -1 }, { 1, -1, -1 }, { -1, -1, -1 }, { -1, 1, -1 }, { 0, 0, -1 } }, // north
		{ { -1, 1, 1 }, { -1, -1, 1 }, { 1, -1, 1 }, { 1, 1, 1 }, { 0, 0, 1 } }, // south
		{ { -1, 1, -1 }, { -1, -1, -1 }, { -1, -1, 1 }, { -1, 1, 1 }, { -1, 0, 0 } }, // west
		{ { 1, 1, 1 }, { 1, -1, 1 }, { 1, -1, -1 }, { 1, 1, -1 }, { 1, 0, 0 } }, // east
	};

	/**
	 * A plain box (half sizes hx, hy, hz) centred on (cx, cy, cz), tipped by `pitch` degrees about the X axis through its
	 * centre (positive leans its top back towards -Z: windscreens).
	 */
	static void box(PoseStack.Pose pose, VertexConsumer vc, int light, float cx, float cy, float cz, float hx, float hy, float hz, float pitch, int color) {
		float c = (float) Math.cos(Math.toRadians(pitch)), s = (float) Math.sin(Math.toRadians(pitch));
		Vector3f p = new Vector3f(), n = new Vector3f();
		for (float[][] f : FACES) {
			float[] fn = f[4];
			n.set(fn[0], fn[1] * c + fn[2] * s, -fn[1] * s + fn[2] * c);
			for (int i = 0; i < 4; i++) {
				float x = f[i][0] * hx, y = f[i][1] * hy, z = f[i][2] * hz;
				p.set(cx + x, cy + y * c + z * s, cz - y * s + z * c);
				vc.addVertex(pose, p.x, p.y, p.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
			}
		}
	}
}
