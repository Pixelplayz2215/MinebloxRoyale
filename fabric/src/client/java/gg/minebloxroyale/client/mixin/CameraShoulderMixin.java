package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.match.BattleBusEntity;
import gg.minebloxroyale.match.CarEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fortnite's over-the-shoulder camera: after Minecraft pulls the third-person camera back, shift it right and up a
 * little. Driving, it sits further back, centred behind the car.
 */
@Mixin(Camera.class)
public abstract class CameraShoulderMixin {
	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	public abstract boolean isDetached();

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	/** How far back the camera sits while driving (blocks; on foot Minecraft's 4). */
	private static final float CAR_DISTANCE = 7.0F;

	/**
	 * Driving (2.0.0-test3, creator, of boost trails: "Can't see in game due to the camera being too close when driving"):
	 * Minecraft pulls the camera further back, only as far as walls and hills let it, so the car and its trail show.
	 */
	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float minebloxroyale$carDistance(float distance) {
		return entity != null && entity.getVehicle() instanceof CarEntity ? Math.max(distance, CAR_DISTANCE) : distance;
	}

	/**
	 * A winner's moment (2.0.0-test5): while the Victory banner, the team's stage or the results card is up, the camera
	 * circles them (Solo) or sweeps across the stage (teams) instead (VictoryClient.camera).
	 */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void minebloxroyale$victory(float partialTicks, CallbackInfo ci) {
		double[] c = gg.minebloxroyale.client.VictoryClient.camera(partialTicks);
		if (c == null) return;
		setPosition(c[0], c[1], c[2]);
		setRotation((float) c[3], (float) c[4]);
	}

	@Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0, shift = At.Shift.AFTER))
	private void minebloxroyale$shoulder(float partialTicks, CallbackInfo ci) {
		if (!isDetached() || entity == null) return;
		// In the Battle Bus: look at the bus from behind and above, like Fortnite.
		if (entity.getVehicle() instanceof BattleBusEntity) move(-9.0F, 4.0F, 0.0F);
		else if (entity.getVehicle() instanceof CarEntity) move(0.0F, 0.9F, 0.0F); // (driving: centred, a little higher)
		else move(0.0F, 0.2F, 1.1F);
	}
}
