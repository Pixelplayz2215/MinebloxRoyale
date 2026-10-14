package gg.minebloxroyale.mixin;

import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a creative tab sits (CreativeTabPlaceMixin moves Mineblox Royale's beside Spawn Eggs). */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
	@Mutable
	@Accessor("row")
	void minebloxroyale$setRow(CreativeModeTab.Row row);

	@Mutable
	@Accessor("column")
	void minebloxroyale$setColumn(int column);
}
