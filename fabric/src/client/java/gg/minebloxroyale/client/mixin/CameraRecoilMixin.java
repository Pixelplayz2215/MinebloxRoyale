package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.Aim;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Recoil (every gun since v2.0.0; first the sniper's, creator: stay scoped after a shot, "with a recoil kick"): the
 * view tilts up (and, for automatic guns, a little sideways) and settles back. Only the camera moves; where you aim
 * stays where it was.
 */
@Mixin(Camera.class)
public abstract class CameraRecoilMixin {
	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), index = 1)
	private float minebloxroyale$recoil(float xRot) {
		return xRot - Aim.recoil();
	}

	/** (Automatic guns also kick a little sideways.) */
	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), index = 0)
	private float minebloxroyale$recoilYaw(float yRot) {
		return yRot + Aim.recoilYaw();
	}
}
