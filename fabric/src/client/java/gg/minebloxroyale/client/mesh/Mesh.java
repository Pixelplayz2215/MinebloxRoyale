package gg.minebloxroyale.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.MinebloxRoyale;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A model made in Blender (v1.0.1, creator: "remade using Blender"; fabric/tools/blender): triangles with a colour per
 * corner (its material, with soft shadows baked in) and flags saying which corners take one of the two tint colours (a
 * gun's rarity, a car's paint and trim, a hat's colours) and which glow (drawn full bright). Loaded once from
 * assets/minebloxroyale/meshes/<id>.brm (brlib.export writes them; brm.py describes the format); an id with no file
 * draws nothing.
 *
 * Far away the lighter copies are drawn (lod): <id>_far.brm and <id>-low.brm, a third and a tenth or less of the
 * triangles (tools/blender/lod.py; 2.0.0-test3, creator: "we need to reduce the lag").
 */
public final class Mesh {
	public static final int FULL_BRIGHT = 0xF000F0;
	/**
	 * Past these many times its size away (about its diagonal), a model is drawn from its _far, then its -low copy: the
	 * -low copy keeps to within 1.5% of the model's size (lod.py), under a pixel there on a 1080p screen.
	 */
	private static final float FAR_AT = 6, LOW_AT = 14;
	private static final Map<String, Mesh> CACHE = new ConcurrentHashMap<>(), CUT = new ConcurrentHashMap<>();

	/** Its file's id ("hat_crown-low"). */
	public final String id;
	private final int count;
	private final float[] pos, nrm;
	private final int[] rgb;
	private final byte[] flags;
	/**
	 * The corners to send, four a quad (entity render types draw quads, each as the triangles 0 1 2 and 2 3 0): two
	 * triangles sharing an edge with the same corners on it, or one triangle with its last corner twice.
	 */
	private final int[] quads;
	/** minX, minY, minZ, maxX, maxY, maxZ. */
	public final float[] bounds = new float[6];

	private Mesh(String id, int count, float[] pos, byte[] nrm, int[] rgb, byte[] flags) {
		faceOut(count, pos, nrm, rgb, flags);
		this.id = id;
		this.count = count;
		this.pos = pos;
		this.nrm = new float[count * 3];
		for (int i = 0; i < count; i++) {
			float x = nrm[i * 3], y = nrm[i * 3 + 1], z = nrm[i * 3 + 2], l = (float) Math.sqrt(x * x + y * y + z * z);
			if (l < 1e-6F) l = 1;
			this.nrm[i * 3] = x / l;
			this.nrm[i * 3 + 1] = y / l;
			this.nrm[i * 3 + 2] = z / l;
		}
		this.rgb = rgb;
		this.flags = flags;
		int[] ids = new int[count];
		Map<Corner, Integer> seen = new HashMap<>();
		for (int i = 0; i < count; i++) {
			Corner c = new Corner(Float.floatToIntBits(pos[i * 3]), Float.floatToIntBits(pos[i * 3 + 1]), Float.floatToIntBits(pos[i * 3 + 2]),
				nrm[i * 3] << 16 | (nrm[i * 3 + 1] & 0xFF) << 8 | nrm[i * 3 + 2] & 0xFF, rgb[i], flags[i]);
			ids[i] = seen.computeIfAbsent(c, k -> seen.size());
		}
		this.quads = pairQuads(ids, null);
		if (count == 0) return;
		Arrays.fill(bounds, 0, 3, Float.MAX_VALUE);
		Arrays.fill(bounds, 3, 6, -Float.MAX_VALUE);
		for (int i = 0; i < count; i++)
			for (int k = 0; k < 3; k++) {
				bounds[k] = Math.min(bounds[k], pos[i * 3 + k]);
				bounds[k + 3] = Math.max(bounds[k + 3], pos[i * 3 + k]);
			}
	}

	/**
	 * Turns every triangle wound against its own normals the right way round (swapping two corners). Since 26.x a face
	 * seen from behind is lit as if facing away, so such a triangle came out dark from both sides (a few rims and brims:
	 * the tricorne's, the pith helmet's...); Blender, which made them, lights by the normals alone.
	 */
	private static void faceOut(int count, float[] pos, byte[] nrm, int[] rgb, byte[] flags) {
		for (int t = 0; t + 2 < count; t += 3) {
			int a = t * 3, b = a + 3, c = a + 6;
			float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
			float vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
			float gx = uy * vz - uz * vy, gy = uz * vx - ux * vz, gz = ux * vy - uy * vx;
			float nx = nrm[a] + nrm[b] + nrm[c], ny = nrm[a + 1] + nrm[b + 1] + nrm[c + 1], nz = nrm[a + 2] + nrm[b + 2] + nrm[c + 2];
			if (gx * nx + gy * ny + gz * nz >= 0) continue;
			for (int k = 0; k < 3; k++) {
				float fp = pos[b + k];
				pos[b + k] = pos[c + k];
				pos[c + k] = fp;
				byte fn = nrm[b + k];
				nrm[b + k] = nrm[c + k];
				nrm[c + k] = fn;
			}
			int fr = rgb[t + 1];
			rgb[t + 1] = rgb[t + 2];
			rgb[t + 2] = fr;
			byte ff = flags[t + 1];
			flags[t + 1] = flags[t + 2];
			flags[t + 2] = ff;
		}
	}

	/**
	 * Pairs triangles into quads (2.0.0-test3, lag): a triangle a b c and its neighbour across the edge c-a, wound c d a,
	 * with the same corners on that edge, are the quad a b c d, drawn as exactly those two triangles: four corners for
	 * two triangles, not eight. The rest go alone (a b c c). vid: which corner each triangle's corner is (3 a triangle;
	 * the same number for corners alike in every way: place, normal, colour...); group (null: all one): only triangles of
	 * the same group pair up. Returns the quads' corners (as places in vid), four a quad.
	 */
	public static int[] pairQuads(int[] vid, int @Nullable [] group) {
		int tris = vid.length / 3;
		Map<Long, Integer> open = new HashMap<>(); // edge from -> to (by corner id): an unpaired triangle wound along it
		boolean[] paired = new boolean[tris];
		int[] out = new int[tris * 4];
		int n = 0;
		for (int t = 0; t < tris; t++) {
			int pairedWith = -1, edge = 0;
			for (int k = 0; k < 3 && pairedWith < 0; k++) {
				Integer u = open.get(key(vid[t * 3 + (k + 1) % 3], vid[t * 3 + k]));
				if (u != null && !paired[u] && (group == null || group[u] == group[t])) {
					pairedWith = u;
					edge = k;
				}
			}
			if (pairedWith < 0) {
				for (int k = 0; k < 3; k++) open.put(key(vid[t * 3 + k], vid[t * 3 + (k + 1) % 3]), t);
				continue;
			}
			// t's edge k -> k+1 is u's the other way: c = t[k], a = t[k + 1], b = t[k + 2]; d is u's corner off the edge.
			int u = pairedWith;
			paired[t] = paired[u] = true;
			int c = t * 3 + edge, a = t * 3 + (edge + 1) % 3, b = t * 3 + (edge + 2) % 3, d = -1;
			for (int k = 0; k < 3; k++)
				if (vid[u * 3 + k] != vid[a] && vid[u * 3 + k] != vid[c]) d = u * 3 + k;
			if (d < 0) { // (a degenerate neighbour: each on its own)
				paired[t] = paired[u] = false;
				continue;
			}
			out[n++] = a;
			out[n++] = b;
			out[n++] = c;
			out[n++] = d;
		}
		for (int t = 0; t < tris; t++) {
			if (paired[t]) continue;
			out[n++] = t * 3;
			out[n++] = t * 3 + 1;
			out[n++] = t * 3 + 2;
			out[n++] = t * 3 + 2;
		}
		return Arrays.copyOf(out, n);
	}

	private record Corner(int x, int y, int z, int n, int rgb, byte flags) {
	}

	private static long key(int from, int to) {
		return (long) from << 32 | to & 0xFFFFFFFFL;
	}

	public static Mesh get(String id) {
		return CACHE.computeIfAbsent(id, k -> load(k, true));
	}

	/** The model with this id, or null if there's none (no warning). */
	public static @Nullable Mesh optional(String id) {
		Mesh m = CACHE.computeIfAbsent(id, k -> load(k, false));
		return m.isEmpty() ? null : m;
	}

	/**
	 * The copy of model `id` to draw `distSq` (blocks squared) from the camera, for a model about `size` blocks across
	 * (corner to corner):
	 * its -low copy past LOW_AT times that away, its _far copy past FAR_AT times, else itself (falling back to the
	 * nearer one where a copy doesn't exist).
	 */
	public static Mesh lod(String id, double distSq, float size) {
		double d = Math.sqrt(distSq) / size;
		if (d > LOW_AT) {
			Mesh m = optional(id + "-low");
			if (m != null) return m;
		}
		if (d > FAR_AT) {
			Mesh m = optional(id + "_far");
			if (m != null) return m;
		}
		return get(id);
	}

	public boolean isEmpty() {
		return count == 0;
	}

	private static Mesh load(String id, boolean warn) {
		try (InputStream raw = Mesh.class.getResourceAsStream("/assets/minebloxroyale/meshes/" + id + ".brm")) {
			if (raw == null) {
				if (warn) MinebloxRoyale.LOG.warn("BR: no mesh {}", id);
				return new Mesh(id, 0, new float[0], new byte[0], new int[0], new byte[0]);
			}
			ByteBuffer in;
			try (GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw))) {
				in = ByteBuffer.wrap(gz.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
			}
			byte[] magic = new byte[4];
			in.get(magic);
			if (magic[0] != 'B' || magic[1] != 'R' || magic[2] != 'M' || magic[3] != '1' && magic[3] != '2') throw new IOException("not a mesh file");
			return magic[3] == '1' ? brm1(id, in) : brm2(id, in);
		} catch (IOException | RuntimeException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't read mesh {}", id, e);
			return new Mesh(id, 0, new float[0], new byte[0], new int[0], new byte[0]);
		}
	}

	/** Every corner in full. */
	private static Mesh brm1(String id, ByteBuffer in) {
		int n = in.getInt();
		float[] pos = new float[n * 3];
		byte[] nrm = new byte[n * 3];
		int[] rgb = new int[n];
		byte[] flags = new byte[n];
		for (int i = 0; i < n; i++) {
			for (int k = 0; k < 3; k++) pos[i * 3 + k] = in.getFloat();
			in.get(nrm, i * 3, 3);
			rgb[i] = (in.get() & 0xFF) << 16 | (in.get() & 0xFF) << 8 | in.get() & 0xFF;
			flags[i] = in.get();
		}
		return new Mesh(id, n, pos, nrm, rgb, flags);
	}

	/**
	 * Each different corner once, field by field as changes from the one before; then which one each corner is (0 = the
	 * next not used yet, n = n before that). See fabric/tools/blender/brm.py.
	 */
	private static Mesh brm2(String id, ByteBuffer in) {
		int nv = in.getInt(), n = in.getInt();
		float[] lo = new float[3], hi = new float[3];
		for (int k = 0; k < 3; k++) lo[k] = in.getFloat();
		for (int k = 0; k < 3; k++) hi[k] = in.getFloat();
		int[][] col = new int[10][nv];
		for (int f = 0; f < 10; f++) {
			int prev = 0;
			for (int i = 0; i < nv; i++) {
				prev = f < 3 ? (prev + (in.getShort() & 0xFFFF)) & 0xFFFF : (prev + (in.get() & 0xFF)) & 0xFF;
				col[f][i] = prev;
			}
		}
		float[] pos = new float[n * 3];
		byte[] nrm = new byte[n * 3];
		int[] rgb = new int[n];
		byte[] flags = new byte[n];
		int seen = 0;
		for (int i = 0; i < n; i++) {
			int code = nv <= 65535 ? in.getShort() & 0xFFFF : in.getInt();
			int v = code == 0 ? seen++ : seen - code;
			for (int k = 0; k < 3; k++) {
				pos[i * 3 + k] = lo[k] + col[k][v] / 65535F * (hi[k] - lo[k]);
				nrm[i * 3 + k] = (byte) col[3 + k][v];
			}
			rgb[i] = col[6][v] << 16 | col[7][v] << 8 | col[8][v];
			flags[i] = (byte) col[9][v];
		}
		return new Mesh(id, n, pos, nrm, rgb, flags);
	}

	/**
	 * Draws it: each corner through `local` (null: as it is), then the pose, as one matrix (and one for the normals).
	 * tintA, tintB: the colours (0xRRGGBB) of tint channels 1 and 2.
	 */
	public void draw(PoseStack.Pose pose, @Nullable Matrix4f local, VertexConsumer vc, int light, int overlay, int tintA, int tintB) {
		Matrix4f m = local == null ? pose.pose() : new Matrix4f(pose.pose()).mul(local);
		Matrix3f nm = local == null ? pose.normal() : new Matrix3f(pose.normal()).mul(new Matrix3f(local).invert().transpose());
		for (int q : quads) {
			float x = pos[q * 3], y = pos[q * 3 + 1], z = pos[q * 3 + 2];
			float nx = nrm[q * 3], ny = nrm[q * 3 + 1], nz = nrm[q * 3 + 2];
			vertex(vc, q, m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
				m.m02() * x + m.m12() * y + m.m22() * z + m.m32(), nm.m00 * nx + nm.m10 * ny + nm.m20 * nz, nm.m01 * nx + nm.m11 * ny + nm.m21 * nz,
				nm.m02 * nx + nm.m12 * ny + nm.m22 * nz, light, overlay, tintA, tintB);
		}
	}

	private void vertex(VertexConsumer vc, int i, float x, float y, float z, float nx, float ny, float nz, int light, int overlay, int tintA, int tintB) {
		float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (l > 1e-6F) {
			nx /= l;
			ny /= l;
			nz /= l;
		}
		int f = flags[i], c = rgb[i];
		if ((f & 3) == 1) c = multiply(c, tintA);
		else if ((f & 3) == 2) c = multiply(c, tintB);
		vc.addVertex(x, y, z).setColor(0xFF000000 | c).setUv(0.5F, 0.5F).setOverlay(overlay).setLight((f & 4) != 0 ? FULL_BRIGHT : light).setNormal(nx, ny, nz);
	}

	/**
	 * This mesh fitted into a limb's joint space (`fit`: moved and stretched there, the normals with it) and cut across
	 * planes y = planes[i] (its own space), so the limb's bend (Bend) shows smoothly on it; made once per key (the mesh
	 * and the limb it's on).
	 */
	public Mesh fitted(String key, Matrix4f fit, float[] planes) {
		return CUT.computeIfAbsent(key, k -> {
			float[] soup = new float[count * 10];
			for (int i = 0; i < count; i++) {
				for (int c = 0; c < 3; c++) {
					soup[i * 10 + c] = pos[i * 3 + c];
					soup[i * 10 + 3 + c] = nrm[i * 3 + c];
				}
				soup[i * 10 + 6] = rgb[i] >> 16 & 0xFF;
				soup[i * 10 + 7] = rgb[i] >> 8 & 0xFF;
				soup[i * 10 + 8] = rgb[i] & 0xFF;
				soup[i * 10 + 9] = flags[i];
			}
			soup = gg.minebloxroyale.client.avatar.Bend.cutSoup(soup, 10, planes);
			int n = soup.length / 10;
			Matrix3f nf = new Matrix3f(fit).invert().transpose();
			Vector3f p = new Vector3f(), nv = new Vector3f();
			float[] ps = new float[n * 3];
			byte[] nr = new byte[n * 3], fl = new byte[n];
			int[] col = new int[n];
			for (int i = 0; i < n; i++) {
				fit.transformPosition(p.set(soup[i * 10], soup[i * 10 + 1], soup[i * 10 + 2]));
				nf.transform(nv.set(soup[i * 10 + 3], soup[i * 10 + 4], soup[i * 10 + 5]));
				if (nv.lengthSquared() > 1e-12F) nv.normalize();
				ps[i * 3] = p.x;
				ps[i * 3 + 1] = p.y;
				ps[i * 3 + 2] = p.z;
				nr[i * 3] = (byte) Math.round(nv.x * 127F);
				nr[i * 3 + 1] = (byte) Math.round(nv.y * 127F);
				nr[i * 3 + 2] = (byte) Math.round(nv.z * 127F);
				col[i] = Math.round(soup[i * 10 + 6]) << 16 | Math.round(soup[i * 10 + 7]) << 8 | Math.round(soup[i * 10 + 8]);
				fl[i] = (byte) Math.round(soup[i * 10 + 9]);
			}
			return new Mesh(k, n, ps, nr, col, fl);
		});
	}

	/** A bend of the limb a mesh is worn on: applied in that limb's joint space. */
	public interface Deform {
		void apply(Vector3f p, Vector3f n);
	}

	/**
	 * Draws a fitted mesh on a bending limb: each corner bent in the limb's joint space, then through `joint` (the limb's
	 * joint, in model space) and the pose, as one matrix (and one for the normals).
	 */
	public void drawBent(PoseStack.Pose pose, Deform bend, Matrix4f joint, VertexConsumer vc, int light, int overlay, int tintA, int tintB) {
		Matrix4f m = new Matrix4f(pose.pose()).mul(joint);
		Matrix3f nm = new Matrix3f(pose.normal()).mul(new Matrix3f(joint).invert().transpose());
		Vector3f p = new Vector3f(), n = new Vector3f();
		for (int q : quads) {
			p.set(pos[q * 3], pos[q * 3 + 1], pos[q * 3 + 2]);
			n.set(nrm[q * 3], nrm[q * 3 + 1], nrm[q * 3 + 2]);
			bend.apply(p, n);
			vertex(vc, q, m.m00() * p.x + m.m10() * p.y + m.m20() * p.z + m.m30(), m.m01() * p.x + m.m11() * p.y + m.m21() * p.z + m.m31(),
				m.m02() * p.x + m.m12() * p.y + m.m22() * p.z + m.m32(), nm.m00 * n.x + nm.m10 * n.y + nm.m20 * n.z, nm.m01 * n.x + nm.m11 * n.y + nm.m21 * n.z,
				nm.m02 * n.x + nm.m12 * n.y + nm.m22 * n.z, light, overlay, tintA, tintB);
		}
	}

	private static int multiply(int a, int b) {
		int r = ((a >> 16 & 0xFF) * (b >> 16 & 0xFF)) / 255, g = ((a >> 8 & 0xFF) * (b >> 8 & 0xFF)) / 255, bl = ((a & 0xFF) * (b & 0xFF)) / 255;
		return r << 16 | g << 8 | bl;
	}
}
