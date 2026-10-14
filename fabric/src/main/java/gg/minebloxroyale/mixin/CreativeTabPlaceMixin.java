package gg.minebloxroyale.mixin;

import gg.minebloxroyale.ModItems;
import net.fabricmc.fabric.impl.creativetab.FabricCreativeModeTabImpl;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mineblox Royale's creative tab sits beside Spawn Eggs on the first page (creator). Fabric moves every mod's tab to
 * later pages each time the tabs are built; this runs once they're built, so the tab goes back to its place.
 */
@Mixin(CreativeModeTabs.class)
public abstract class CreativeTabPlaceMixin {
	@Inject(method = "tryRebuildTabContents", at = @At("RETURN"))
	private static void minebloxroyale$besideSpawnEggs(FeatureFlagSet flags, boolean ops, HolderLookup.Provider registries, CallbackInfoReturnable<Boolean> cir) {
		((FabricCreativeModeTabImpl) ModItems.TAB).fabric_setPage(0);
		CreativeModeTabAccessor tab = (CreativeModeTabAccessor) ModItems.TAB;
		tab.minebloxroyale$setRow(CreativeModeTab.Row.BOTTOM);
		tab.minebloxroyale$setColumn(5); // Spawn Eggs is column 4 of the bottom row
	}
}
