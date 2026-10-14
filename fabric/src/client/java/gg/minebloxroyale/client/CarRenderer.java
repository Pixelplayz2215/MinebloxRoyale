package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.match.CarBlockEntity;
import java.util.Locale;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a parked car on its core block, nose the way the car faces: the Blender model of its kind and damage stage
 * (fabric/tools/blender/cars.py), in its paint and trim colours; the lighter copy past FAR blocks.
 */
public class CarRenderer implements BlockEntityRenderer<CarBlockEntity, CarRenderer.State> {
	private static final double FAR = 40;

	public static class State extends BlockEntityRenderState {
		CarBlockEntity.Kind kind = CarBlockEntity.Kind.SEDAN;
		int color, trim, stage = 1;
		float yaw;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CarBlockEntity car, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(car, state, partialTicks, cameraPosition, breakProgress);
		state.kind = car.kind();
		state.color = car.color();
		state.trim = car.trim();
		state.yaw = car.facing().toYRot();
		state.stage = car.stage(); // (how damaged it looks)
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean far = camera.pos.distanceToSqr(Vec3.atCenterOf(state.blockPos)) > FAR * FAR;
		Mesh mesh = Mesh.get("car_" + state.kind.name().toLowerCase(Locale.ROOT) + "_" + state.stage + (far ? "_far" : ""));
		poseStack.pushPose();
		poseStack.translate(0.5F, 0F, 0.5F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> mesh.draw(pose, null, vc, state.lightCoords, OverlayTexture.NO_OVERLAY, state.color & 0xFFFFFF, state.trim & 0xFFFFFF));
		poseStack.popPose();
	}

	/** The car reaches 2.5 blocks past its core block: drawn even when the core itself is just off screen. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
