package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.item.MaterialStacks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A material slot holding more than 99 is one item that stands for them all (MaterialStacks), so the inventory screen
 * shows what it holds (up to 500) instead of Minecraft's own count (2.0.0).
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemCountMixin {
	@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$materialCount(Font font, ItemStack stack, int x, int y, @Nullable String countText, CallbackInfo ci) {
		if (countText != null || stack.isEmpty() || !MaterialStacks.is(stack)) return;
		int n = MaterialStacks.amount(stack);
		if (n == stack.getCount()) return;
		ci.cancel();
		((GuiGraphicsExtractor) (Object) this).itemDecorations(font, stack, x, y, String.valueOf(n));
	}
}
