package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.Aim;
import gg.minebloxroyale.client.BuildMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With a gun in hand, left mouse is the trigger (Aim), not punching or mining. In build mode both mouse buttons belong
 * to BuildMode (left places, right switches material), so Minecraft's attack and use are skipped.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$noPunchWithGun(CallbackInfoReturnable<Boolean> cir) {
		if (Aim.heldGun() != null || BuildMode.active()) cir.setReturnValue(false);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$noMineWithGun(boolean down, CallbackInfo ci) {
		if (Aim.heldGun() != null || BuildMode.active()) ci.cancel();
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$noUseWhileBuilding(CallbackInfo ci) {
		if (BuildMode.active()) ci.cancel();
	}
}
