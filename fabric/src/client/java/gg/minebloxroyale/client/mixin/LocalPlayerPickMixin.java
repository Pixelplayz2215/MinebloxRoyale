package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.Crosshair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The block outline, mining, opening chests and hitting all follow the crosshair (Crosshair.interaction), not the head.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPickMixin {
	@Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
	private void minebloxroyale$crosshairPick(float a, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		HitResult h = Crosshair.interaction(Minecraft.getInstance(), self.blockInteractionRange(), self.entityInteractionRange());
		if (h != null) cir.setReturnValue(h);
	}
}
