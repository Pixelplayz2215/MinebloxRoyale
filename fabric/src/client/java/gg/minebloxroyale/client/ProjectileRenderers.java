package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.match.GrenadeEntity;
import gg.minebloxroyale.match.RocketEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Rockets and grenades in flight (v2.0.0): their Blender models (the rocket from fabric/tools/blender/vehicles.py, a
 * block long, pointing the way it flies; the grenade the item's own model, tumbling as it goes).
 */
public final class ProjectileRenderers {
	private ProjectileRenderers() {
	}

	public static class State extends EntityRenderState {
		float yaw, pitch, spin;
	}

	public static class Rocket extends EntityRenderer<RocketEntity, State> {
		public Rocket(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public State createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(RocketEntity entity, State state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.yaw = entity.getYRot(partialTicks);
			state.pitch = entity.getXRot(partialTicks);
		}

		@Override
		public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
			poseStack.pushPose();
			poseStack.translate(0, 0.15F, 0);
			// the model points along +x: turned to face the flight (yaw about y, then pitch)
			poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw - 90F));
			poseStack.rotate(Axis.ZP.rotationDegrees(-state.pitch));
			poseStack.translate(-0.5F, 0, 0);
			Mesh mesh = Mesh.lod("rocket", state.distanceToCameraSq, 1F);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
				(pose, vc) -> mesh.draw(pose, null, vc, Mesh.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFF, 0xFFFFFF));
			poseStack.popPose();
			super.submit(state, poseStack, collector, camera);
		}
	}

	public static class Grenade extends EntityRenderer<GrenadeEntity, State> {
		public Grenade(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public State createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(GrenadeEntity entity, State state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.yaw = entity.getYRot();
			state.spin = entity.spin(partialTicks);
		}

		@Override
		public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
			int light = state.lightCoords;
			poseStack.pushPose();
			poseStack.translate(0, 0.15F, 0);
			poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
			poseStack.rotate(Axis.XP.rotationDegrees(state.spin));
			// the item model is in item pixels (0..16, upright): a third of a block across here, centred
			poseStack.scale(0.05F, 0.05F, 0.05F);
			poseStack.translate(-8F, -6.4F, -8F);
			Mesh mesh = Mesh.lod("grenade", state.distanceToCameraSq, 0.4F);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
				(pose, vc) -> mesh.draw(pose, null, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFF, 0xFFFFFF));
			poseStack.popPose();
			super.submit(state, poseStack, collector, camera);
		}
	}
}
