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
 */
public final class Mesh {
	public static final int FULL_BRIGHT = 0xF000F0;
	private static final Map<String, Mesh> CACHE = new ConcurrentHashMap<>();

	private final int count;
	private final float[] pos;
	private final byte[] nrm;
	private final int[] rgb;
	private final byte[] flags;
	/** minX, minY, minZ, maxX, maxY, maxZ. */
	public final float[] bounds = new float[6];

	private Mesh(int count, float[] pos, byte[] nrm, int[] rgb, byte[] flags) {
		this.count = count;
		this.pos = pos;
		this.nrm = nrm;
		this.rgb = rgb;
		this.flags = flags;
		if (count == 0) return;
		Arrays.fill(bounds, 0, 3, Float.MAX_VALUE);
		Arrays.fill(bounds, 3, 6, -Float.MAX_VALUE);
		for (int i = 0; i < count; i++)
			for (int k = 0; k < 3; k++) {
				bounds[k] = Math.min(bounds[k], pos[i * 3 + k]);
				bounds[k + 3] = Math.max(bounds[k + 3], pos[i * 3 + k]);
			}
	}

	public static Mesh get(String id) {
		return CACHE.computeIfAbsent(id, Mesh::load);
	}

	public boolean isEmpty() {
		return count == 0;
	}

	private static Mesh load(String id) {
		try (InputStream raw = Mesh.class.getResourceAsStream("/assets/minebloxroyale/meshes/" + id + ".brm")) {
			if (raw == null) {
				MinebloxRoyale.LOG.warn("BR: no mesh {}", id);
				return new Mesh(0, new float[0], new byte[0], new int[0], new byte[0]);
			}
			ByteBuffer in;
			try (GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw))) {
				in = ByteBuffer.wrap(gz.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
			}
			byte[] magic = new byte[4];
			in.get(magic);
			if (magic[0] != 'B' || magic[1] != 'R' || magic[2] != 'M' || magic[3] != '1' && magic[3] != '2') throw new IOException("not a mesh file");
			return magic[3] == '1' ? brm1(in) : brm2(in);
		} catch (IOException | RuntimeException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't read mesh {}", id, e);
			return new Mesh(0, new float[0], new byte[0], new int[0], new byte[0]);
		}
	}

	/** Every corner in full. */
	private static Mesh brm1(ByteBuffer in) {
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
		return new Mesh(n, pos, nrm, rgb, flags);
	}

	/**
	 * Each different corner once, field by field as changes from the one before; then which one each corner is (0 = the
	 * next not used yet, n = n before that). See fabric/tools/blender/brm.py.
	 */
	private static Mesh brm2(ByteBuffer in) {
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
		return new Mesh(n, pos, nrm, rgb, flags);
	}

	/**
	 * Draws it: each corner through `local` (null: as it is), then the pose. tintA, tintB: the colours (0xRRGGBB) of
	 * tint channels 1 and 2. Triangles go out as quads with the last corner twice (entity render types draw quads).
	 */
	public void draw(PoseStack.Pose pose, @Nullable Matrix4f local, VertexConsumer vc, int light, int overlay, int tintA, int tintB) {
		Matrix3f nm = local == null ? null : new Matrix3f(local).invert().transpose();
		Vector3f p = new Vector3f(), n = new Vector3f();
		for (int t = 0; t < count; t += 3)
			for (int k = 0; k < 4; k++) {
				int i = t + Math.min(k, 2);
				p.set(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
				n.set(nrm[i * 3] / 127F, nrm[i * 3 + 1] / 127F, nrm[i * 3 + 2] / 127F);
				if (local != null) {
					local.transformPosition(p);
					nm.transform(n).normalize();
				}
				int f = flags[i];
				int c = rgb[i];
				if ((f & 3) == 1) c = multiply(c, tintA);
				else if ((f & 3) == 2) c = multiply(c, tintB);
				vc.addVertex(pose, p.x, p.y, p.z).setColor(0xFF000000 | c).setUv(0.5F, 0.5F).setOverlay(overlay).setLight((f & 4) != 0 ? FULL_BRIGHT : light)
					.setNormal(pose, n.x, n.y, n.z);
			}
	}

	private static int multiply(int a, int b) {
		int r = ((a >> 16 & 0xFF) * (b >> 16 & 0xFF)) / 255, g = ((a >> 8 & 0xFF) * (b >> 8 & 0xFF)) / 255, bl = ((a & 0xFF) * (b & 0xFF)) / 255;
		return r << 16 | g << 8 | bl;
	}
}
