package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.media.FortniteMedia;
import gg.minebloxroyale.hub.HubBuilder;
import gg.minebloxroyale.sheets.HubPieceDef;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Hub: the parked Battle Bus and the Fortnite splash billboard. Match: the storm wall (StormVisuals), reboot vans,
 * loot beams over guns on the ground, waypoint beacons and Build mode's ghost piece.
 */
public final class WorldVisuals {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static Identifier white;

	private WorldVisuals() {
	}

	public static void submit(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack ps = ctx.poseStack();
		SubmitNodeCollector out = ctx.submitNodeCollector();
		if (!ClientState.inMatch()) {
			HubPieceDef bus = HubBuilder.piece("parked_bus");
			double bx = bus.x(), by = HubBuilder.hubY() + bus.y(), bz = bus.z();
			if (mc.player.position().distanceToSqr(bx, by, bz) < 160 * 160) {
				ps.pushPose();
				ps.translate(bx - cam.x, by - cam.y + 0.1, bz - cam.z);
				ps.rotate(Axis.YP.rotationDegrees(switch (bus.facing()) { case "east" -> 90; case "west" -> -90; case "north" -> 180; default -> 0; }));
				out.submitCustomGeometry(ps, RenderTypes.entityCutout(white()), (pose, vc) -> BusModel.draw(pose, vc, FULL_BRIGHT));
				ps.popPose();
			}
			Identifier splash = FortniteMedia.billboard();
			if (splash != null && HubBuilder.piece("splash_board") != null) picture(ps, out, cam, HubBuilder.piece("splash_board"), splash);
		} else {
			if (ClientState.hud.phase().equals("LIVE")) StormVisuals.draw(ps, out, cam);
			vans(mc, ps, out, cam);
			LootBeams.draw(ps, out, cam);
			Waypoint.beacon(ps, out, cam);
			BuildMode.preview(ps, out, cam);
		}
	}

	/** Team modes: the reboot vans within 200 blocks (VanModel), glowing green when ready and red while recharging. */
	private static void vans(Minecraft mc, PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		if (!ClientState.teamMatch()) return;
		for (var v : ClientState.squad.vans()) {
			if (mc.player.position().distanceToSqr(v.x(), v.y(), v.z()) > 200 * 200) continue;
			boolean ready = v.readyIn() == 0;
			ps.pushPose();
			ps.translate(v.x() - cam.x, v.y() - cam.y, v.z() - cam.z);
			ps.rotate(Axis.YP.rotationDegrees(v.yaw()));
			out.submitCustomGeometry(ps, RenderTypes.entityCutout(white()), (pose, vc) -> VanModel.draw(pose, vc, FULL_BRIGHT, ready));
			ps.popPose();
		}
	}

	/** A textured, full-bright rectangle on the front of a hub piece's frame. */
	private static void picture(PoseStack ps, SubmitNodeCollector out, Vec3 cam, HubPieceDef p, Identifier texture) {
		double cy = HubBuilder.hubY() + p.y();
		float hw = p.width() / 2F, hh = p.height() / 2F;
		double[][] c;
		switch (p.facing()) {
			case "south" -> { double z = p.z() + 0.02; c = new double[][] { { p.x() - hw, cy + hh, z }, { p.x() - hw, cy - hh, z }, { p.x() + hw, cy - hh, z }, { p.x() + hw, cy + hh, z } }; }
			case "north" -> { double z = p.z() + 0.98; c = new double[][] { { p.x() + hw, cy + hh, z }, { p.x() + hw, cy - hh, z }, { p.x() - hw, cy - hh, z }, { p.x() - hw, cy + hh, z } }; }
			case "west" -> { double x = p.x() + 0.98; c = new double[][] { { x, cy + hh, p.z() - hw }, { x, cy - hh, p.z() - hw }, { x, cy - hh, p.z() + hw }, { x, cy + hh, p.z() + hw } }; }
			default -> { double x = p.x() + 0.02; c = new double[][] { { x, cy + hh, p.z() + hw }, { x, cy - hh, p.z() + hw }, { x, cy - hh, p.z() - hw }, { x, cy + hh, p.z() - hw } }; }
		}
		final double[][] corners = c;
		float[][] uv = { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
		out.submitCustomGeometry(ps, RenderTypes.text(texture), (pose, vc) -> {
			for (int i = 0; i < 4; i++)
				vc.addVertex(pose, (float) (corners[i][0] - cam.x), (float) (corners[i][1] - cam.y), (float) (corners[i][2] - cam.z))
					.setColor(0xFFFFFFFF).setUv(uv[i][0], uv[i][1]).setLight(FULL_BRIGHT);
		});
	}

	/** A flat-coloured quad, both windings (Build mode's preview, waypoint beams). */
	static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x0, double z0, double x1, double z1, double y0, double y1, float nx, float nz, int color) {
		v(vc, pose, cam, x0, y1, z0, color, nx, nz);
		v(vc, pose, cam, x0, y0, z0, color, nx, nz);
		v(vc, pose, cam, x1, y0, z1, color, nx, nz);
		v(vc, pose, cam, x1, y1, z1, color, nx, nz);
		// Back face, so the wall is visible from inside the storm too.
		v(vc, pose, cam, x1, y1, z1, color, -nx, -nz);
		v(vc, pose, cam, x1, y0, z1, color, -nx, -nz);
		v(vc, pose, cam, x0, y0, z0, color, -nx, -nz);
		v(vc, pose, cam, x0, y1, z0, color, -nx, -nz);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, double x, double y, double z, int color, float nx, float nz) {
		vc.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(color).setUv(0.5F, 0.5F)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, 0, nz);
	}

	static Identifier white() {
		if (white == null) {
			NativeImage img = new NativeImage(4, 4, false);
			img.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
			white = MinebloxRoyale.id("white");
			Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "minebloxroyale white", img));
		}
		return white;
	}
}
