package gg.minebloxroyale.mixin;

import gg.minebloxroyale.match.IslandPregen;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A world closing while its next island is only half made drops that island (IslandPregen.abandonsChunk): its chunks
 * are pure world generation that nobody has played on, so they aren't written, and a new island is picked next time.
 * Writing them is what made closing take up to a minute and a half.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapSaveMixin {
	@Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$skipAbandonedIsland(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		if (IslandPregen.abandonsChunk(chunk.getPos().x(), chunk.getPos().z())) {
			chunk.tryMarkSaved();
			cir.setReturnValue(false);
		}
	}
}
