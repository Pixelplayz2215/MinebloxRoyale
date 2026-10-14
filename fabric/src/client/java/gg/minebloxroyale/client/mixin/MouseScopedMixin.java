package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.Aim;
import gg.minebloxroyale.client.ScopedSensitivity;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Scoped Sensitivity (creator): the mouse turns the view slower while looking through a sniper scope. */
@Mixin(MouseHandler.class)
public abstract class MouseScopedMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double minebloxroyale$scopedYaw(double dx) {
		return Aim.scoped() ? dx * ScopedSensitivity.factor() : dx;
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double minebloxroyale$scopedPitch(double dy) {
		return Aim.scoped() ? dy * ScopedSensitivity.factor() : dy;
	}
}
