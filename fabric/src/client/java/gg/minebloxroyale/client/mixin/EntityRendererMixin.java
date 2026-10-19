package gg.minebloxroyale.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.client.NameBadge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2.0.0) A player's badge on its own line over their name tag (NameBadge): drawn right after the name tag, from where
 * the name tag went (over the score line, if there is one).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void minebloxroyale$nameBadge(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
		String badge = state.getData(NameBadge.KEY);
		if (badge != null && !badge.isEmpty() && state.nameTag != null && state.nameTagAttachment != null)
			NameBadge.submit(badge, poseStack, collector, state.nameTagAttachment, offset, !state.isDiscrete, state.lightCoords, camera);
	}
}
