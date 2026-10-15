package gg.minebloxroyale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.minebloxroyale.client.mesh.Mesh;
import gg.minebloxroyale.match.CarBlockEntity;
import gg.minebloxroyale.match.CarEntity;
import java.util.Locale;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A car being driven (v2.0.0, CarEntity): the same Blender model as a parked car (fabric/tools/blender/cars.py), its kind
 * and damage stage in its paint and trim, turned the way it drives; the lighter copies further away (Mesh.lod).
 */
public class CarEntityRenderer extends EntityRenderer<CarEntity, CarEntityRenderer.State> {
	/** About how big a car is (blocks), for picking its lighter copies far away. */
	static final float SIZE = 5F;

	public static class State extends EntityRenderState {
		CarBlockEntity.Kind kind = CarBlockEntity.Kind.SEDAN;
		int color, trim, stage = 1;
		float yaw;
	}

	public CarEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.1F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CarEntity car, State state, float partialTicks) {
		super.extractRenderState(car, state, partialTicks);
		state.kind = car.kind();
		state.color = car.color();
		state.trim = car.trim();
		state.stage = car.stage();
		state.yaw = car.getYRot(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Mesh mesh = Mesh.lod("car_" + state.kind.name().toLowerCase(Locale.ROOT) + "_" + state.stage, state.distanceToCameraSq, SIZE);
		int light = state.lightCoords;
		poseStack.pushPose();
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WorldVisuals.white()),
			(pose, vc) -> mesh.draw(pose, null, vc, light, OverlayTexture.NO_OVERLAY, state.color & 0xFFFFFF, state.trim & 0xFFFFFF));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
