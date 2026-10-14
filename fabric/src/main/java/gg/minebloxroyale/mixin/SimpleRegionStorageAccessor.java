package gg.minebloxroyale.mixin;

import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The chunk storage's disk writer, so the island generator can see how many chunk writes are waiting (IslandPregen).
 */
@Mixin(SimpleRegionStorage.class)
public interface SimpleRegionStorageAccessor {
	@Accessor("worker")
	IOWorker getWorker();
}
