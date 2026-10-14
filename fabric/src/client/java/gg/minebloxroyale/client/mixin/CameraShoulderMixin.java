package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.match.BattleBusEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fortnite's over-the-shoulder camera: after Minecraft pulls the third-person camera back, shift it right and up a
 * little.
 */
@Mixin(Camera.class)
public abstract class CameraShoulderMixin {
	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	public abstract boolean isDetached();

	@Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0, shift = At.Shift.AFTER))
	private void minebloxroyale$shoulder(float partialTicks, CallbackInfo ci) {
		if (!isDetached() || entity == null) return;
		// In the Battle Bus: look at the bus from behind and above, like Fortnite.
		if (entity.getVehicle() instanceof BattleBusEntity) move(-9.0F, 4.0F, 0.0F);
		else move(0.0F, 0.2F, 1.1F);
	}
}
