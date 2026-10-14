package gg.minebloxroyale.mixin;

import gg.minebloxroyale.match.Knocked;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A knocked player crawls (Minecraft's crawling pose: low, slow, 0.6 blocks tall), on the server and in every player's
 * game alike, so nobody's game snaps them back up between updates.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$knockedCrawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!Knocked.is(self)) return;
		self.setPose(Pose.SWIMMING);
		ci.cancel();
	}
}
