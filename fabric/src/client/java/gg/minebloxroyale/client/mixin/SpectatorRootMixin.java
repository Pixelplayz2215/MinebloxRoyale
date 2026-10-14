package gg.minebloxroyale.client.mixin;

import java.util.List;
import net.minecraft.client.gui.spectator.RootSpectatorMenuCategory;
import net.minecraft.client.gui.spectator.SpectatorMenuItem;
import net.minecraft.client.gui.spectator.categories.TeleportToTeamMenuCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spectating: no "Teleport to Team Member" page. Every real player is on the same scoreboard team (their blue names),
 * so it would list everyone; the party-only player page (SpectatorPlayersMixin) is the only way to go to someone.
 */
@Mixin(RootSpectatorMenuCategory.class)
public abstract class SpectatorRootMixin {
	@Shadow
	@Final
	private List<SpectatorMenuItem> items;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void minebloxroyale$noTeamPage(CallbackInfo ci) {
		items.removeIf(i -> i instanceof TeleportToTeamMenuCategory);
	}
}
