package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.match.LootChestBlock;
import gg.minebloxroyale.match.LootChestBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a loot chest (creator, v1.0.1: "a smoother texture built in Blender. And the chest should have an opening
 * animation"): the Blender models (fabric/tools/blender/chests.py), the wooden one or the blue rare one, front the way
 * the chest faces; the lighter copies past FAR blocks. When a chest is searched open, its lid swings up and back about
 * its hinge with a little bounce at the top; a chest already open when it comes into view is simply open.
 */
public class ChestRenderer implements BlockEntityRenderer<LootChestBlockEntity, ChestRenderer.State> {
	private static final double FAR = 24;
	/** The lid: how far it opens (degrees) and how long it takes (seconds). */
	private static final float OPEN_DEGREES = 105, OPEN_SECONDS = 0.45F;
	/** The body's hinge line (chests.py HINGE): the top of its back. */
	private static final float HINGE_Y = 0.46F, HINGE_Z = -0.29F;
	/** Each chest seen lately: {when its lid started opening (nanos; CLOSED, or ALREADY open when first seen), last seen}. */
	private static final Map<Long, long[]> SEEN = new HashMap<>();
	private static final long CLOSED = -1, ALREADY = Long.MIN_VALUE;
	private static long lastSweep;

	public static class State extends BlockEntityRenderState {
		boolean rare;
		float yaw, lid;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LootChestBlockEntity chest, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(chest, state, partialTicks, cameraPosition, breakProgress);
		BlockState block = chest.getBlockState();
		if (!(block.getBlock() instanceof LootChestBlock b)) return;
		state.rare = b.rare;
		state.yaw = block.getValue(LootChestBlock.FACING).toYRot();
		boolean open = block.getValue(LootChestBlock.OPEN);
		long now = System.nanoTime();
		long[] seen = SEEN.computeIfAbsent(chest.getBlockPos().asLong(), k -> new long[] { open ? ALREADY : CLOSED, now });
		if (!open) seen[0] = CLOSED;
		else if (seen[0] == CLOSED) seen[0] = now; // just searched open: swing from now
		seen[1] = now;
		float t = seen[0] == ALREADY ? 1 : !open ? 0 : Math.min(1, (now - seen[0]) / 1e9F / OPEN_SECONDS);
		state.lid = OPEN_DEGREES * bounce(t);
		if (now - lastSweep > 10_000_000_000L) { // (forget chests not seen for half a minute)
			lastSweep = now;
			SEEN.values().removeIf(s -> now - s[1] > 30_000_000_000L);
		}
	}

	/** Eased out with a small overshoot: quick, past open a touch, settling back. */
	private static float bounce(float t) {
		float c1 = 1.1F, c3 = c1 + 1, u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean far = camera.pos.distanceToSqr(Vec3.atCenterOf(state.blockPos)) > FAR * FAR;
		String kind = "chest_" + (state.rare ? "rare" : "loot"), lod = far ? "_far" : "";
		Mesh body = Mesh.get(kind + "_body" + lod), lid = Mesh.get(kind + "_lid" + lod);
		int light = state.lightCoords;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> body.draw(pose, null, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFF, 0xFFFFFF));
		poseStack.translate(0F, HINGE_Y, HINGE_Z);
		poseStack.rotate(Axis.XP.rotationDegrees(-state.lid)); // (front up and over the back)
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> lid.draw(pose, null, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFF, 0xFFFFFF));
		poseStack.popPose();
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
