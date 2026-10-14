package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.MinebloxRoyale;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rooms take match.maxPlayers real players (creator: 16, four full squads); Minecraft caps a shared world at 8. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerPlayersMixin {
	@Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$roomSize(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(Math.round(MinebloxRoyale.setting("maxPlayers")));
	}
}
