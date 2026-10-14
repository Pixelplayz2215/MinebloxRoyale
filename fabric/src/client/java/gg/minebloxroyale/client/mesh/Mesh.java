package gg.minebloxroyale.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.MinebloxRoyale;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * assets/minebloxroyale/meshes/<id>.brm (brlib.export writes them); an id with no file draws nothing.
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
			DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
			byte[] magic = in.readNBytes(4);
			if (magic.length != 4 || magic[0] != 'B' || magic[1] != 'R' || magic[2] != 'M' || magic[3] != '1') throw new IOException("not a mesh file");
			int n = Integer.reverseBytes(in.readInt());
			float[] pos = new float[n * 3];
			byte[] nrm = new byte[n * 3];
			int[] rgb = new int[n];
			byte[] flags = new byte[n];
			for (int i = 0; i < n; i++) {
				for (int k = 0; k < 3; k++) pos[i * 3 + k] = Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
				in.readFully(nrm, i * 3, 3);
				rgb[i] = in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte();
				flags[i] = in.readByte();
			}
			return new Mesh(n, pos, nrm, rgb, flags);
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't read mesh {}", id, e);
			return new Mesh(0, new float[0], new byte[0], new int[0], new byte[0]);
		}
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
