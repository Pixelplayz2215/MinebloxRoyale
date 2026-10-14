package gg.minebloxroyale.client.mixin;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No "press Enter to enable the narrator" welcome, not even on a brand-new install (creator: it showed on the first start
 * of a new CurseForge profile): it's marked as seen, and saved, before Minecraft picks its first screens. The narrator
 * stays off; Ctrl+B still turns it on.
 */
@Mixin(Gui.class)
public abstract class OnboardingSkipMixin {
	@Inject(method = "addInitialScreens", at = @At("HEAD"))
	private void minebloxroyale$noNarratorWelcome(List<Function<Runnable, Screen>> screens, CallbackInfoReturnable<Boolean> cir) {
		Options options = Minecraft.getInstance().options;
		if (options.onboardAccessibility) options.onboardingAccessibilityFinished();
	}
}
