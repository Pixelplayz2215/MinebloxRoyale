package gg.minebloxroyale.client.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunk saves go through Windows' write cache, as Minecraft already does on Mac and Linux. On Windows it waits for the
 * disk to confirm every single chunk write ("Sync Chunk Writes"), which a hard drive can't keep up with while an island
 * is generated: the saves piled up and island generation stalled for minutes (creator, on a USB hard drive). Closing
 * the world still flushes everything to the disk.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerWritesMixin {
	@Inject(method = "forceSynchronousWrites", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$cachedChunkWrites(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
