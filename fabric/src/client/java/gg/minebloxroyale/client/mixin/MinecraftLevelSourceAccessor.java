package gg.minebloxroyale.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (2.0.0) Where Minecraft keeps its worlds, changed while none is open (WorldStorage: the world on another drive). */
@Mixin(Minecraft.class)
public interface MinecraftLevelSourceAccessor {
	@Mutable
	@Accessor("levelSource")
	void minebloxroyale$setLevelSource(LevelStorageSource source);
}
