package gg.minebloxroyale.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import gg.minebloxroyale.client.avatar.RobloxAssets;
import gg.minebloxroyale.client.avatar.RobloxAvatarRenderer;
import gg.minebloxroyale.match.Reboot;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held items (guns) follow the Roblox arm that holds them instead of Minecraft's hidden arm. A reboot card in the
 * offhand is never drawn (creator: hidden in third person), and a knocked fighter holds nothing.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$hidden(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (Reboot.isCard(stack) || state.getDataOrDefault(RobloxAvatarRenderer.KNOCKED, false)) ci.cancel();
	}

	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private void minebloxroyale$robloxHand(ArmedModel<?> model, EntityRenderState state, HumanoidArm arm, PoseStack poseStack, Operation<Void> original) {
		if (state instanceof LivingEntityRenderState s && s.getData(RobloxAvatarRenderer.COLORS) != null && RobloxAssets.ready()) {
			poseStack.mulPose(RobloxAvatarRenderer.handMatrix(s, arm == HumanoidArm.RIGHT));
			return;
		}
		original.call(model, state, arm, poseStack);
	}
}
