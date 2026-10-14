package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Fortnite's loot beams (creator: a gun's rarity should stand out more): a short pillar of light in the rarity colour
 * over every gun lying on the ground, bright at the bottom and fading out upwards, so you can tell a gold gun from a
 * grey one from across a room.
 */
public final class LootBeams {
	private static final double RANGE = 64, HEIGHT = 2.6;
	private static final int FULL_BRIGHT = 0xF000F0;

	private LootBeams() {
	}

	static void draw(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		List<double[]> beams = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ItemEntity item) || !(item.getItem().getItem() instanceof WeaponItem)) continue;
			if (e.distanceToSqr(mc.player) > RANGE * RANGE) continue;
			beams.add(new double[] { e.getX(), e.getY(), e.getZ(), Guns.rarity(item.getItem()).color() });
		}
		if (beams.isEmpty()) return;
		out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(WorldVisuals.white()), (pose, vc) -> {
			for (double[] b : beams) {
				int rgb = (int) b[3] & 0xFFFFFF;
				column(vc, pose, cam, b[0], b[1], b[2], 0.05, 0xC0, rgb);
				column(vc, pose, cam, b[0], b[1], b[2], 0.16, 0x48, rgb);
			}
		});
	}

	/** Four sides of a square pillar, alpha going from `alpha` at the bottom to nothing at the top. */
	private static void column(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x, double y, double z, double r, int alpha, int rgb) {
		int bottom = alpha << 24 | rgb, top = rgb;
		double[][] c = { { x - r, z - r }, { x + r, z - r }, { x + r, z + r }, { x - r, z + r } };
		for (int i = 0; i < 4; i++) {
			double[] a = c[i], b = c[(i + 1) % 4];
			float nx = (float) Math.signum((a[0] + b[0]) / 2 - x), nz = (float) Math.signum((a[1] + b[1]) / 2 - z);
			// Both windings, so the pillar shows from inside too.
			v(vc, pose, cam, a[0], y + HEIGHT, a[1], top, nx, nz);
			v(vc, pose, cam, a[0], y, a[1], bottom, nx, nz);
			v(vc, pose, cam, b[0], y, b[1], bottom, nx, nz);
			v(vc, pose, cam, b[0], y + HEIGHT, b[1], top, nx, nz);
			v(vc, pose, cam, b[0], y + HEIGHT, b[1], top, -nx, -nz);
			v(vc, pose, cam, b[0], y, b[1], bottom, -nx, -nz);
			v(vc, pose, cam, a[0], y, a[1], bottom, -nx, -nz);
			v(vc, pose, cam, a[0], y + HEIGHT, a[1], top, -nx, -nz);
		}
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x, double y, double z, int color, float nx, float nz) {
		vc.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(color).setUv(0.5F, 0.5F)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, 0, nz);
	}
}
