package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Smooth, rounded Roblox-style accessory shapes (hats, glasses, wings, shoes...) built from triangles with smooth
 * normals, in a body part's joint space (studs, y up, front -Z). Shapes: lathe (any round profile turned about Y),
 * ellipsoids and domes, tori and arcs, tubes along a path, and curved sheets. Faces are drawn on both sides, so
 * thin pieces (brims, capes) look right from below and behind.
 */
final class Smooth {
	interface Surface {
		void at(float u, float v, Vector3f out);
	}

	private final VertexConsumer vc;
	private final PoseStack.Pose pose;
	private final Matrix4f joint;
	private final int light, overlay;
	private Matrix4f local = new Matrix4f();
	private Matrix3f normals = new Matrix3f();
	private int color = 0xFFFFFFFF;
	/** Tubes thinner than this at an end get no round cap there (gliders: their many thin lines). */
	private float capMin = 0.005F;
	/** Level of detail for the avatar being drawn (1 = full; far avatars use fewer pieces). Set by the renderer. */
	static float detail = 1F;

	private static int seg(int n) {
		return detail >= 1F || n <= 6 ? n : Math.max(6, Math.round(n * detail)); // (small counts are shapes: 3 = triangle, 4 = diamond)
	}

	Smooth(VertexConsumer vc, PoseStack.Pose pose, Matrix4f joint, int light, int overlay) {
		this.vc = vc;
		this.pose = pose;
		this.joint = joint;
		this.light = light;
		this.overlay = overlay;
	}

	/** Starts a new piece at (x, y, z) in joint space, with no rotation or scale. */
	Smooth at(float x, float y, float z) {
		local = new Matrix4f().translate(x, y, z);
		return update();
	}

	/** Moves along the current (rotated) axes. */
	Smooth move(float x, float y, float z) {
		local.translate(x, y, z);
		return update();
	}

	Smooth rotX(float deg) {
		local.rotateX((float) Math.toRadians(deg));
		return update();
	}

	Smooth rotY(float deg) {
		local.rotateY((float) Math.toRadians(deg));
		return update();
	}

	Smooth rotZ(float deg) {
		local.rotateZ((float) Math.toRadians(deg));
		return update();
	}

	Smooth scale(float x, float y, float z) {
		local.scale(x, y, z);
		return update();
	}

	/** Turns the piece so its own up (y) points along (nx, ny, nz): decorations lying on a curved canopy. */
	Smooth align(float nx, float ny, float nz) {
		local.rotate(new Quaternionf().rotationTo(0, 1, 0, nx, ny, nz));
		return update();
	}

	/** Round caps only on tube ends at least this thick (thin lines don't need them: fewer pieces). */
	Smooth caps(float min) {
		capMin = min;
		return this;
	}

	Smooth color(int rgb) {
		color = 0xFF000000 | rgb;
		return this;
	}

	private Smooth update() {
		normals = new Matrix3f(local).invert().transpose();
		return this;
	}

	// ---- shapes ----

	/**
	 * Turns the profile (r[i], y[i]) about the Y axis. List the outline going round counter-clockwise in the (r, y)
	 * plane (e.g. bottom centre -> outwards -> up the side -> inwards at the top) so normals face outwards. Corners
	 * sharper than about 45 degrees stay crisp; gentler ones are smoothed.
	 */
	void lathe(float[] r, float[] y, int seg) {
		lathe(r, y, seg, 0, 360);
	}

	/** Like lathe, but only from angle a0 to a1 (degrees): wedges, for striped umbrellas and parachutes. */
	void lathe(float[] r, float[] y, int seg, float a0deg, float a1deg) {
		seg = seg(seg);
		int m = r.length;
		float[][] sn = new float[m - 1][];
		for (int j = 0; j < m - 1; j++) {
			float dr = r[j + 1] - r[j], dy = y[j + 1] - y[j], len = (float) Math.sqrt(dr * dr + dy * dy);
			sn[j] = len < 1e-6F ? (j > 0 ? sn[j - 1] : new float[] { 0, 1 }) : new float[] { dy / len, -dr / len };
		}
		for (int j = 0; j < m - 1; j++) {
			float[] na = join(j > 0 ? sn[j - 1] : null, sn[j]), nb = join(j + 1 < m - 1 ? sn[j + 1] : null, sn[j]);
			for (int i = 0; i < seg; i++) {
				double a0 = Math.toRadians(a0deg + (a1deg - a0deg) * i / seg), a1 = Math.toRadians(a0deg + (a1deg - a0deg) * (i + 1) / seg);
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				quad(r[j] * c0, y[j], r[j] * s0, na[0] * c0, na[1], na[0] * s0,
					r[j] * c1, y[j], r[j] * s1, na[0] * c1, na[1], na[0] * s1,
					r[j + 1] * c1, y[j + 1], r[j + 1] * s1, nb[0] * c1, nb[1], nb[0] * s1,
					r[j + 1] * c0, y[j + 1], r[j + 1] * s0, nb[0] * c0, nb[1], nb[0] * s0);
			}
		}
	}

	/** Smooth normal where two profile segments meet, unless the corner is sharp. */
	private static float[] join(float[] other, float[] own) {
		if (other == null || other[0] * own[0] + other[1] * own[1] < 0.7F) return own;
		float x = other[0] + own[0], y = other[1] + own[1], l = (float) Math.sqrt(x * x + y * y);
		return new float[] { x / l, y / l };
	}

	/** Part of a unit sphere from latitude lat0 to lat1 (degrees, -90 = bottom), scaled by the current transform. */
	void sphere(float lat0, float lat1, int seg) {
		sphere(lat0, lat1, seg, 0, 360);
	}

	void sphere(float lat0, float lat1, int seg, float a0, float a1) {
		seg = seg(seg);
		int rings = Math.max(3, Math.round((lat1 - lat0) / 180F * seg));
		float[] r = new float[rings + 1], y = new float[rings + 1];
		for (int i = 0; i <= rings; i++) {
			double lat = Math.toRadians(lat0 + (lat1 - lat0) * i / rings);
			r[i] = (float) Math.cos(lat);
			y[i] = (float) Math.sin(lat);
		}
		lathe(r, y, seg, a0, a1);
	}

	/** A small, cheap ellipsoid (few pieces): decorations, beads, flames. */
	void blob(float rx, float ry, float rz) {
		scale(rx, ry, rz);
		sphere(-90, 90, 8);
	}

	/**
	 * A flat lens (a disc a little thicker in the middle) of radii rx, rz and half-thickness h: decals on a surface.
	 */
	void lens(float rx, float rz, float h) {
		scale(rx, 1, rz);
		lathe(new float[] { 0, 1, 0 }, new float[] { -h, 0, h }, 12);
	}

	/**
	 * Part of a unit sphere with set numbers of rings and slices (panels of an umbrella or a parachute: few pieces
	 * each).
	 */
	void panel(float lat0, float lat1, int rings, int slices, float a0, float a1) {
		rings = Math.max(2, seg(rings));
		float[] r = new float[rings + 1], y = new float[rings + 1];
		for (int i = 0; i <= rings; i++) {
			double lat = Math.toRadians(lat0 + (lat1 - lat0) * i / rings);
			r[i] = (float) Math.cos(lat);
			y[i] = (float) Math.sin(lat);
		}
		lathe(r, y, slices, a0, a1);
	}

	/** A cone of radius r and height h standing on the current position (along y), with a base. */
	void cone(float r, float h, int seg) {
		lathe(new float[] { 0, r, 0 }, new float[] { 0, 0, h }, seg);
	}

	/** A whole ellipsoid with radii (rx, ry, rz) at the current position. */
	void ellipsoid(float rx, float ry, float rz) {
		scale(rx, ry, rz);
		sphere(-90, 90, 20);
	}

	/** A ring about the Y axis: centre radius R, tube radius t, from angle a0 to a1 (degrees; 0..360 = closed). */
	void torus(float R, float t, float a0, float a1) {
		int segU = Math.max(6, Math.round(Math.abs(a1 - a0) / 360F * seg(28))), segV = seg(10);
		for (int i = 0; i < segU; i++)
			for (int j = 0; j < segV; j++) {
				float[][] p = new float[4][], n = new float[4][];
				int[][] uv = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
				for (int k = 0; k < 4; k++) {
					double u = Math.toRadians(a0 + (a1 - a0) * uv[k][0] / segU), v = 2 * Math.PI * uv[k][1] / segV;
					float cu = (float) Math.cos(u), su = (float) Math.sin(u), cv = (float) Math.cos(v), sv = (float) Math.sin(v);
					p[k] = new float[] { (R + t * cv) * cu, t * sv, (R + t * cv) * su };
					n[k] = new float[] { cv * cu, sv, cv * su };
				}
				quad(p, n);
			}
	}

	/** A round tube through the points (current transform), radius per point (0 = pointed tip), round ends. */
	void tube(float[][] pts, float[] radii) {
		tube(pts, radii, true);
	}

	void tube(float[][] pts, float[] radii, boolean caps) {
		float thickest = 0;
		for (float r : radii) thickest = Math.max(thickest, r);
		tube(pts, radii, caps, thickest < 0.03F ? 6 : 10); // thin lines: 6 sides are plenty
	}

	/** A tube with a set number of sides (a flattened stripe needs few). */
	void tube(float[][] pts, float[] radii, boolean caps, int sides) {
		int n = pts.length, segV = seg(sides);
		Vector3f[] t = new Vector3f[n], nn = new Vector3f[n], bb = new Vector3f[n];
		for (int i = 0; i < n; i++) {
			float[] a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)];
			t[i] = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
			Vector3f ref = i == 0 ? (Math.abs(t[0].y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)) : nn[i - 1];
			nn[i] = new Vector3f(ref).sub(new Vector3f(t[i]).mul(ref.dot(t[i]))).normalize();
			bb[i] = new Vector3f(t[i]).cross(nn[i]).normalize();
		}
		for (int i = 0; i < n - 1; i++)
			for (int j = 0; j < segV; j++) {
				float[][] p = new float[4][], q = new float[4][];
				int[][] iv = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
				for (int k = 0; k < 4; k++) {
					int pi = iv[k][0];
					double v = 2 * Math.PI * iv[k][1] / segV;
					float cv = (float) Math.cos(v), sv = (float) Math.sin(v);
					float nx = nn[pi].x * cv + bb[pi].x * sv, ny = nn[pi].y * cv + bb[pi].y * sv, nz = nn[pi].z * cv + bb[pi].z * sv;
					p[k] = new float[] { pts[pi][0] + radii[pi] * nx, pts[pi][1] + radii[pi] * ny, pts[pi][2] + radii[pi] * nz };
					q[k] = new float[] { nx, ny, nz };
				}
				quad(p, q);
			}
		// Round caps where a tube ends with some thickness.
		for (int end : new int[] { 0, n - 1 })
			if (caps && radii[end] > capMin) {
				Matrix4f keep = new Matrix4f(local);
				local.translate(pts[end][0], pts[end][1], pts[end][2]).scale(radii[end]);
				update();
				sphere(-90, 90, 10);
				local = keep;
				update();
			}
	}

	/** A curved sheet: f(u, v) for u, v in 0..1, nu x nv quads; normals from the surface's slope. */
	void surface(Surface f, int nu, int nv) {
		nu = seg(nu);
		nv = seg(nv);
		Vector3f a = new Vector3f(), du = new Vector3f(), dv = new Vector3f();
		for (int i = 0; i < nu; i++)
			for (int j = 0; j < nv; j++) {
				float[][] p = new float[4][], n = new float[4][];
				float[][] uv = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
				for (int k = 0; k < 4; k++) {
					float u = uv[k][0] / nu, v = uv[k][1] / nv, e = 0.01F;
					f.at(u, v, a);
					f.at(Math.min(1, u + e), v, du);
					Vector3f du0 = new Vector3f();
					f.at(Math.max(0, u - e), v, du0);
					du.sub(du0);
					f.at(u, Math.min(1, v + e), dv);
					Vector3f dv0 = new Vector3f();
					f.at(u, Math.max(0, v - e), dv0);
					dv.sub(dv0);
					Vector3f nr = new Vector3f(du).cross(dv).normalize();
					p[k] = new float[] { a.x, a.y, a.z };
					n[k] = new float[] { nr.x, nr.y, nr.z };
				}
				quad(p, n);
			}
	}

	// ---- output ----

	private void quad(float x0, float y0, float z0, float nx0, float ny0, float nz0, float x1, float y1, float z1, float nx1, float ny1, float nz1,
		float x2, float y2, float z2, float nx2, float ny2, float nz2, float x3, float y3, float z3, float nx3, float ny3, float nz3) {
		quad(new float[][] { { x0, y0, z0 }, { x1, y1, z1 }, { x2, y2, z2 }, { x3, y3, z3 } },
			new float[][] { { nx0, ny0, nz0 }, { nx1, ny1, nz1 }, { nx2, ny2, nz2 }, { nx3, ny3, nz3 } });
	}

	private final Vector3f vp = new Vector3f(), vn = new Vector3f();

	/** One quad, front and back (back faces get flipped normals). */
	private void quad(float[][] p, float[][] n) {
		for (int k = 0; k < 4; k++) vertex(p[k], n[k], 1);
		for (int k = 3; k >= 0; k--) vertex(p[k], n[k], -1);
	}

	private void vertex(float[] p, float[] n, float side) {
		local.transformPosition(p[0], p[1], p[2], vp);
		joint.transformPosition(vp);
		normals.transform(n[0], n[1], n[2], vn);
		joint.transformDirection(vn);
		if (vn.lengthSquared() > 1e-12F) vn.normalize().mul(side);
		else vn.set(0, side, 0);
		// studs (y up, root at hips) -> model space (y down), the same mapping as the body meshes.
		vc.addVertex(pose, -vp.x * RobloxAvatarRenderer.S, 1.501F - (vp.y + RobloxAvatarRenderer.HIP) * RobloxAvatarRenderer.S, vp.z * RobloxAvatarRenderer.S)
			.setColor(color).setUv(0.01F, 0.01F).setOverlay(overlay).setLight(light).setNormal(pose, -vn.x, -vn.y, vn.z);
	}
}
