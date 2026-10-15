package gg.minebloxroyale.client.avatar;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Elbows and knees (creator: "add knees and elbows so the animations are more animated"): each arm and leg bends in
 * its middle, exactly as fabric/tools/blender/bend.py bends them for the renders and the emote and move sheets.
 *
 * The bend turns the limb about an axis across its middle (its centre line keeps its length, so a bent leg is no longer
 * than a straight one): above a stretch round the middle nothing moves, below it the rest turns as one piece, and in
 * between each slice turns part of the way, so the outside of the bend is a smooth round corner and the inside is
 * squeezed. Elbows bend the forearm forward, knees bend the shin back. All in the limb's joint space (studs, y up, front
 * -Z, the joint at 0): the arms' middle is 0.5 below the shoulder joint, the legs' 1 below the hip joint.
 *
 * The deeper the bend, the longer that stretch (creator, test4: "behind the knees the textures overlap"): squeezed too
 * hard, the inside of a bend folds back over itself, two layers in one place, one seen from behind (lit darker since
 * 26.x), and they flicker. Turning a stretch of length L by a squeezes what sits d inside to 1 - a d / L of its length,
 * so L is at least a * INNER / SQUEEZE: never folded. A slight bend stays 2 * HALF long; a deep one reaches at most UP
 * above the middle (an elbow is only 0.5 below its shoulder joint) and the rest below. Meshes are cut every STEP across
 * where a limb can bend (cuts) so the curve shows smoothly.
 */
public final class Bend {
	static final float HALF = 0.25F, SQUEEZE = 0.9F, STEP = 0.1F;

	/** One limb's bend this frame: degrees (0 = straight) and which kind of limb. */
	public record Of(float degrees, boolean arm) {
		static final Of NONE = new Of(0, true);

		boolean none() {
			return degrees <= 0.01F;
		}

		float mid() {
			return arm ? -0.5F : -1.0F;
		}

		/** The turn of the far end (below the bend): what the hand, the foot and a held item follow. */
		Matrix4f lower() {
			Matrix4f m = new Matrix4f();
			if (none()) return m;
			return m.translate(0, mid(), 0).rotateX(angle(1)).translate(0, -mid(), 0);
		}

		private float angle(float w) {
			return (arm ? 1 : -1) * (float) Math.toRadians(degrees) * w;
		}

		/** The stretch that bends: how far above the middle (up) and below it, studs. */
		float up() {
			return Math.min(length() / 2, arm ? 0.45F : 0.9F);
		}

		float length() {
			return Math.max(2 * HALF, (float) Math.toRadians(degrees) * (arm ? 0.5F : 0.64F) / SQUEEZE);
		}

		/** Bends a point (and its normal, if not null) of the limb, in joint space. */
		void apply(Vector3f p, Vector3f n) {
			if (none()) return;
			float s = p.y - mid(), up = up(), length = length();
			if (s >= up) return;
			float w = s <= up - length ? 1 : (up - s) / length;
			float a = angle(w), c = (float) Math.cos(a), sn = (float) Math.sin(a);
			float y = mid() + s * c - p.z * sn, z = s * sn + p.z * c;
			p.y = y;
			p.z = z;
			if (n != null) {
				float ny = n.y * c - n.z * sn, nz = n.y * sn + n.z * c;
				n.y = ny;
				n.z = nz;
			}
		}
	}

	private Bend() {
	}

	/** Where to cut a limb's mesh (joint-space y): every STEP across where it can bend (bend.py cuts, REACH). */
	static float[] cuts(boolean arm) {
		float mid = arm ? -0.5F : -1.0F, hi = arm ? 0.45F : 0.9F, lo = -1.0F;
		int k = Math.round((hi - lo) / STEP);
		float[] out = new float[k + 1];
		for (int i = 0; i <= k; i++) out[i] = mid + hi - STEP * i;
		return out;
	}

	/**
	 * Triangle soup (3 corners each) cut across planes y = planes[i] (mesh space): each corner's attributes (stride
	 * values per corner, position first) interpolated along the cut edges. Returns the new soup.
	 */
	public static float[] cutSoup(float[] soup, int stride, float[] planes) {
		for (float y : planes) soup = cutSoup(soup, stride, y);
		return soup;
	}

	private static float[] cutSoup(float[] in, int stride, float plane) {
		int tri = 3 * stride;
		float[] out = new float[in.length * 3];
		int o = 0;
		float eps = 1e-6F;
		for (int t = 0; t < in.length; t += tri) {
			int[] side = new int[3];
			boolean up = false, down = false;
			for (int k = 0; k < 3; k++) {
				float y = in[t + k * stride + 1];
				side[k] = y > plane + eps ? 1 : y < plane - eps ? -1 : 0;
				up |= side[k] > 0;
				down |= side[k] < 0;
			}
			if (!(up && down)) {
				System.arraycopy(in, t, out, o, tri);
				o += tri;
				continue;
			}
			int r = 0; // the corner alone on its side of the plane first
			for (; r < 3; r++) {
				int sa = side[r], sb = side[(r + 1) % 3], sc = side[(r + 2) % 3];
				if (sa != 0 && sb != sa && sc != sa) break;
			}
			float[] a = corner(in, t, stride, r), b = corner(in, t, stride, (r + 1) % 3), c = corner(in, t, stride, (r + 2) % 3);
			int sb = side[(r + 1) % 3], sc = side[(r + 2) % 3];
			if (sb == 0 || sc == 0) { // one corner on the plane: two triangles
				if (sb == 0) {
					float[] m = lerp(a, c, plane);
					o = put(out, o, a, b, m);
					o = put(out, o, b, c, m);
				} else {
					float[] m = lerp(a, b, plane);
					o = put(out, o, a, m, c);
					o = put(out, o, m, b, c);
				}
				continue;
			}
			float[] m1 = lerp(a, b, plane), m2 = lerp(a, c, plane);
			o = put(out, o, a, m1, m2);
			o = put(out, o, m1, b, c);
			o = put(out, o, m1, c, m2);
		}
		return java.util.Arrays.copyOf(out, o);
	}

	private static float[] corner(float[] in, int t, int stride, int k) {
		return java.util.Arrays.copyOfRange(in, t + k * stride, t + (k + 1) * stride);
	}

	private static float[] lerp(float[] a, float[] b, float plane) {
		float f = (plane - a[1]) / (b[1] - a[1]);
		float[] m = new float[a.length];
		for (int i = 0; i < a.length; i++) m[i] = a[i] + (b[i] - a[i]) * f;
		return m;
	}

	private static int put(float[] out, int o, float[]... corners) {
		for (float[] c : corners) {
			System.arraycopy(c, 0, out, o, c.length);
			o += c.length;
		}
		return o;
	}
}
