package gg.minebloxroyale.client.avatar;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reads a Roblox .mesh file (binary "version 2.00" / "version 3.0x"): vertices (position, normal, uv) and triangles.
 * Only the first LOD's faces are used. Positions are in studs.
 */
public final class RobloxMesh {
	public final float[] pos;   // xyz per vertex
	public final float[] nrm;   // xyz per vertex
	public final float[] uv;    // uv per vertex
	public final int[] tris;    // 3 indices per triangle

	private RobloxMesh(float[] pos, float[] nrm, float[] uv, int[] tris) {
		this.pos = pos;
		this.nrm = nrm;
		this.uv = uv;
		this.tris = tris;
	}

	public static RobloxMesh parse(byte[] bytes) throws IOException {
		int nl = 0;
		while (nl < bytes.length && bytes[nl] != '\n') nl++;
		String version = new String(bytes, 0, nl, StandardCharsets.US_ASCII).trim();
		if (!version.startsWith("version 2.") && !version.startsWith("version 3.")) throw new IOException("unsupported mesh " + version);
		ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int h = nl + 1;
		int headerSize = b.getShort(h) & 0xFFFF;
		int vertexSize = b.get(h + 2) & 0xFF;
		int faceSize = b.get(h + 3) & 0xFF;
		int numVerts, numFaces, lodCount = 0, lodSize = 0;
		if (version.startsWith("version 2.")) {
			numVerts = b.getInt(h + 4);
			numFaces = b.getInt(h + 8);
		} else {
			lodSize = b.getShort(h + 4) & 0xFFFF;
			lodCount = b.getShort(h + 6) & 0xFFFF;
			numVerts = b.getInt(h + 8);
			numFaces = b.getInt(h + 12);
		}
		if (vertexSize < 36 || faceSize < 12 || numVerts <= 0 || numFaces <= 0) throw new IOException("bad mesh header");
		int p = h + headerSize;
		float[] pos = new float[numVerts * 3], nrm = new float[numVerts * 3], uv = new float[numVerts * 2];
		for (int i = 0; i < numVerts; i++, p += vertexSize) {
			for (int k = 0; k < 3; k++) {
				pos[i * 3 + k] = b.getFloat(p + 4 * k);
				nrm[i * 3 + k] = b.getFloat(p + 12 + 4 * k);
			}
			uv[i * 2] = b.getFloat(p + 24);
			uv[i * 2 + 1] = b.getFloat(p + 28);
		}
		int facesStart = p;
		int useFaces = numFaces;
		if (lodCount >= 2) {
			int lodAt = facesStart + numFaces * faceSize;
			int end = b.getInt(lodAt + lodSize); // LOD table: offsets into faces; [0]=0, [1]=end of LOD 0
			if (end > 0 && end <= numFaces) useFaces = end;
		}
		int[] tris = new int[useFaces * 3];
		for (int i = 0; i < useFaces; i++) {
			int q = facesStart + i * faceSize;
			for (int k = 0; k < 3; k++) {
				int idx = b.getInt(q + 4 * k);
				if (idx < 0 || idx >= numVerts) throw new IOException("face index out of range");
				tris[i * 3 + k] = idx;
			}
		}
		return new RobloxMesh(pos, nrm, uv, tris);
	}
}
