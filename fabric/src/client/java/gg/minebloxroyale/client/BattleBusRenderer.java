package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.match.BattleBusEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Draws the Battle Bus entity with BusModel, facing its direction of travel. */
public class BattleBusRenderer extends EntityRenderer<BattleBusEntity, BattleBusRenderer.State> {
	public static class State extends EntityRenderState {
		float yaw;
	}

	public BattleBusRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BattleBusEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (pose, vc) -> BusModel.draw(pose, vc, 0xF000F0));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected boolean affectedByCulling(BattleBusEntity entity) {
		return false;
	}
}
