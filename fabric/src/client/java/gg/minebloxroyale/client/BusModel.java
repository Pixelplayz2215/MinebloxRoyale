package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Our own Battle Bus in a smooth Roblox "plastic" style (no Fortnite asset is used): rounded-edge body and windows,
 * round wheels and a smooth striped hot-air balloon, all with smooth normals so the game's lighting shades the curves.
 * Block units, front along +Z, origin at the cabin floor.
 */
public final class BusModel {
	private static final int BLUE = 0xFF2F6BD8, DARK_BLUE = 0xFF1E4797, WHITE = 0xFFF2F4F8, GLASS = 0xFF9FD8F2, TIRE = 0xFF26262B,
		HUB = 0xFFC9CDD4, LIGHT = 0xFFFFE07A, RED = 0xFFD8402F, ROPE = 0xFF6B5236, BALLOON_A = 0xFF3B86F0, BALLOON_B = 0xFFF5F7FA, BASKET = 0xFF8A5A2B;

	private BusModel() {
	}

	public static void draw(PoseStack.Pose pose, VertexConsumer vc, int light) {
		// Body: one big rounded block, a white roof cap, a darker skirt.
		roundedBox(pose, vc, light, 0, 1.5F, 0, 1.6F, 1.1F, 4.2F, 0.45F, BLUE);
		roundedBox(pose, vc, light, 0, 2.62F, 0, 1.5F, 0.16F, 4.05F, 0.14F, WHITE);
		roundedBox(pose, vc, light, 0, 0.55F, 0, 1.62F, 0.2F, 4.22F, 0.18F, DARK_BLUE);
		// Windows (both sides), windshield, back window.
		for (float z = -2.8F; z <= 2.2F; z += 1.25F) {
			roundedBox(pose, vc, light, 1.6F, 1.92F, z + 0.45F, 0.05F, 0.38F, 0.48F, 0.04F, GLASS);
			roundedBox(pose, vc, light, -1.6F, 1.92F, z + 0.45F, 0.05F, 0.38F, 0.48F, 0.04F, GLASS);
		}
		roundedBox(pose, vc, light, 0, 1.9F, 4.18F, 1.25F, 0.45F, 0.06F, 0.05F, GLASS);
		roundedBox(pose, vc, light, 0, 1.95F, -4.18F, 1.0F, 0.38F, 0.06F, 0.05F, GLASS);
		// Headlights, bumper, tail lights.
		roundedBox(pose, vc, light, -1.0F, 0.95F, 4.2F, 0.24F, 0.17F, 0.08F, 0.07F, LIGHT);
		roundedBox(pose, vc, light, 1.0F, 0.95F, 4.2F, 0.24F, 0.17F, 0.08F, 0.07F, LIGHT);
		roundedBox(pose, vc, light, 0, 0.55F, 4.35F, 1.5F, 0.12F, 0.12F, 0.1F, WHITE);
		roundedBox(pose, vc, light, -1.15F, 1.05F, -4.2F, 0.2F, 0.15F, 0.08F, 0.06F, RED);
		roundedBox(pose, vc, light, 1.15F, 1.05F, -4.2F, 0.2F, 0.15F, 0.08F, 0.06F, RED);
		// Round wheels with hubcaps.
		for (float z : new float[] { -2.7F, 2.6F })
			for (float side : new float[] { -1, 1 }) {
				wheel(pose, vc, light, side * 1.55F, 0.42F, z, 0.5F, 0.32F, TIRE);
				wheel(pose, vc, light, side * 1.72F, 0.42F, z, 0.24F, 0.06F, HUB);
			}
		// Ropes up to the basket, then the balloon.
		for (float x : new float[] { -1.3F, 1.3F })
			for (float z : new float[] { -3.6F, 3.6F })
				roundedBox(pose, vc, light, x, 4.5F, z, 0.05F, 1.75F, 0.05F, 0.04F, ROPE);
		roundedBox(pose, vc, light, 0, 6.3F, 0, 1.5F, 0.18F, 3.8F, 0.15F, BASKET);
		balloon(pose, vc, light, 0, 10.8F, 0, 4.3F, 12);
	}

	/**
	 * A box with rounded edges: each face is a grid pushed out onto a rounded shape (inner box + radius), with the
	 * normal pointing away from the inner box, so it shades smoothly. hx/hy/hz are half sizes.
	 */
	static void roundedBox(PoseStack.Pose pose, VertexConsumer vc, int light, float cx, float cy, float cz, float hx, float hy, float hz, float r, int color) {
		r = Math.min(r, Math.min(hx, Math.min(hy, hz)));
		int n = 6;
		Vector3f p = new Vector3f(), nrm = new Vector3f();
		float[][] faces = { { 0, 0, 1 }, { 0, 0, -1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 } };
		for (float[] f : faces) {
			// Two in-face axes for this face.
			float[] u = f[0] != 0 ? new float[] { 0, 0, -f[0] } : f[1] != 0 ? new float[] { 1, 0, 0 } : new float[] { f[2], 0, 0 };
			float[] v = f[1] != 0 ? new float[] { 0, 0, f[1] } : new float[] { 0, 1, 0 };
			for (int i = 0; i < n; i++)
				for (int j = 0; j < n; j++) {
					float[][] corner = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
					if (f[1] < 0 || f[0] > 0 || f[2] < 0) corner = new float[][] { { i, j }, { i, j + 1 }, { i + 1, j + 1 }, { i + 1, j } };
					for (float[] c : corner) {
						float a = c[0] / n * 2 - 1, b = c[1] / n * 2 - 1;
						float x = (f[0] + u[0] * a + v[0] * b) * hx, y = (f[1] + u[1] * a + v[1] * b) * hy, z = (f[2] + u[2] * a + v[2] * b) * hz;
						float ix = clamp(x, hx - r), iy = clamp(y, hy - r), iz = clamp(z, hz - r);
						nrm.set(x - ix, y - iy, z - iz);
						if (nrm.lengthSquared() < 1e-8F) nrm.set(f[0], f[1], f[2]);
						nrm.normalize();
						p.set(ix + nrm.x * r + cx, iy + nrm.y * r + cy, iz + nrm.z * r + cz);
						vert(pose, vc, light, color, p, nrm);
					}
				}
		}
	}

	private static float clamp(float v, float lim) {
		return Math.max(-lim, Math.min(lim, v));
	}

	/** A wheel: cylinder along X with rounded rims. */
	static void wheel(PoseStack.Pose pose, VertexConsumer vc, int light, float cx, float cy, float cz, float radius, float halfWidth, int color) {
		int seg = 18;
		Vector3f p = new Vector3f(), nrm = new Vector3f();
		for (int s = 0; s < seg; s++) {
			double a0 = s * Math.PI * 2 / seg, a1 = (s + 1) * Math.PI * 2 / seg;
			float y0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0), y1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
			// Tread.
			float[][] tread = { { -halfWidth, y0, z0 }, { halfWidth, y0, z0 }, { halfWidth, y1, z1 }, { -halfWidth, y1, z1 } };
			for (float[] t : tread) {
				nrm.set(0, t[1], t[2]).normalize();
				p.set(cx + t[0], cy + t[1] * radius, cz + t[2] * radius);
				vert(pose, vc, light, color, p, nrm);
			}
			// Both side faces (as thin quads to the centre).
			for (float side : new float[] { -1, 1 }) {
				float x = cx + side * halfWidth;
				float[][] cap = side > 0 ? new float[][] { { 0, 0 }, { y0, z0 }, { y1, z1 }, { 0, 0 } } : new float[][] { { 0, 0 }, { y1, z1 }, { y0, z0 }, { 0, 0 } };
				for (float[] c : cap) {
					nrm.set(side, 0, 0);
					p.set(x, cy + c[0] * radius, cz + c[1] * radius);
					vert(pose, vc, light, color, p, nrm);
				}
			}
		}
	}

	/** A smooth balloon: a sphere stretched taller, narrowing at the bottom, with vertical stripes. */
	static void balloon(PoseStack.Pose pose, VertexConsumer vc, int light, float cx, float cy, float cz, float r, int stripes) {
		int rings = 14, perStripe = 2, seg = stripes * perStripe;
		Vector3f p = new Vector3f(), nrm = new Vector3f();
		for (int i = 0; i < rings; i++) {
			double t0 = Math.PI * i / rings, t1 = Math.PI * (i + 1) / rings;
			for (int s = 0; s < seg; s++) {
				double a0 = s * Math.PI * 2 / seg, a1 = (s + 1) * Math.PI * 2 / seg;
				int color = (s / perStripe) % 2 == 0 ? BALLOON_A : BALLOON_B;
				double[][] q = { { t0, a0 }, { t1, a0 }, { t1, a1 }, { t0, a1 } };
				for (double[] c : q) {
					double st = Math.sin(c[0]), ct = Math.cos(c[0]);
					// Narrower towards the bottom (a balloon, not a ball).
					double squeeze = ct < 0 ? 1 + ct * 0.35 : 1;
					nrm.set((float) (st * Math.cos(c[1])), (float) ct, (float) (st * Math.sin(c[1]))).normalize();
					p.set((float) (cx + st * Math.cos(c[1]) * r * squeeze), (float) (cy + ct * r * 1.15), (float) (cz + st * Math.sin(c[1]) * r * squeeze));
					vert(pose, vc, light, color, p, nrm);
				}
			}
		}
	}

	private static void vert(PoseStack.Pose pose, VertexConsumer vc, int light, int color, Vector3f p, Vector3f n) {
		vc.addVertex(pose, p.x, p.y, p.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
	}
}
