package gg.minebloxroyale.mixin;

import java.util.SequencedMap;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunk writes still waiting to go to disk (only its size is read, by IslandPregen). */
@Mixin(IOWorker.class)
public interface IOWorkerAccessor {
	@Accessor("pendingWrites")
	SequencedMap<?, ?> getPendingWrites();
}
