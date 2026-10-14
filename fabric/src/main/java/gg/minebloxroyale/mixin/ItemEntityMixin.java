package gg.minebloxroyale.mixin;

import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.Loadout;
import gg.minebloxroyale.match.Reboot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fortnite pickups: rarity upgrades swap guns; with all five item slots full, other guns and heals stay on the ground.
 * Reboot cards go to the offhand (teammates only); knocked fighters pick nothing up.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$fullHotbar(Player player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp)) return;
		ItemEntity self = (ItemEntity) (Object) this;
		// Reboot cards: only their team picks them up, into the offhand even with a full inventory (creator).
		if (Reboot.isCard(self.getItem())) {
			Reboot.tryPickUp(sp, self);
			ci.cancel();
			return;
		}
		if (Knocked.is(sp)) {
			ci.cancel();
			return;
		}
		// A better-rarity copy of a gun you carry replaces it (old one dropped); otherwise a full hotbar picks nothing
		// up.
		if (Loadout.tryUpgrade(sp, self) || !Loadout.canPickUp(sp, self.getItem())) ci.cancel();
	}
}
