package gg.minebloxroyale.client.mixin;

import gg.minebloxroyale.client.ClientState;
import java.util.Collection;
import net.minecraft.client.gui.spectator.categories.TeleportToPlayerMenuCategory;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Spectating (eliminated): the hotbar's "Teleport to Player" menu lists only players in your party, never any other
 * real player (creator). The server refuses teleports to anyone else too (SpectatorLockMixin).
 */
@Mixin(TeleportToPlayerMenuCategory.class)
public abstract class SpectatorPlayersMixin {
	@ModifyVariable(method = "<init>(Ljava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;"), argsOnly = true, ordinal = 0)
	private Collection<PlayerInfo> minebloxroyale$partyOnly(Collection<PlayerInfo> profiles) {
		return profiles.stream().filter(p -> ClientState.inMyParty(p.getProfile().id())).toList();
	}
}
