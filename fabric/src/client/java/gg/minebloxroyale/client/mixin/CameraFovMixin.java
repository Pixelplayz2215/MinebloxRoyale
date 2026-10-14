package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.Aim;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aim-down-sights zoom (weapons sheet adsZoom). */
@Mixin(Camera.class)
public abstract class CameraFovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void minebloxroyale$adsZoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float zoom = Aim.zoom(partialTicks);
		if (zoom != 1F) cir.setReturnValue(cir.getReturnValueF() / zoom);
	}
}
