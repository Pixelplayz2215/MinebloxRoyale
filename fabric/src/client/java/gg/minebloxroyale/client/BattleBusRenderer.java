package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.match.BattleBusEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Draws the Battle Bus entity (its Blender model, fabric/tools/blender/vehicles.py), full bright, facing its direction of travel. */
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
		Mesh bus = Mesh.lod("battle_bus", state.distanceToCameraSq, WorldVisuals.BUS_SIZE);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()), (pose, vc) -> bus.draw(pose, null, vc, Mesh.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFF, 0xFFFFFF));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected boolean affectedByCulling(BattleBusEntity entity) {
		return false;
	}
}
