package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.ScopedSensitivity;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Scoped Sensitivity slider (creator), under Minecraft's own mouse settings. */
@Mixin(MouseSettingsScreen.class)
public abstract class MouseSettingsScreenMixin {
	@Inject(method = "addOptions", at = @At("TAIL"))
	private void minebloxroyale$scopedSensitivity(CallbackInfo ci) {
		((OptionsSubScreenAccessor) (Object) this).minebloxroyale$list().addBig(ScopedSensitivity.option());
	}
}
