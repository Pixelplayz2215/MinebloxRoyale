package gg.minebloxroyale.mixin;

import gg.minebloxroyale.match.Parties;
import gg.minebloxroyale.match.Teams;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eliminated players (spectators) only go to, or watch, players in their own party (creator: the hotbar teleport menu
 * lists only your party, never any other real player). Each player's game already lists only their party
 * (SpectatorMenuMixin); this also refuses a teleport or a watch request for anyone else. Bots can still be watched.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpectatorLockMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
	private void minebloxroyale$partyTeleportOnly(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			Entity target = packet.getEntity(level);
			if (target != null) {
				if (!allowed(target)) ci.cancel();
				return;
			}
		}
	}

	@Inject(method = "handleSpectatorAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
	private void minebloxroyale$partyWatchOnly(ServerboundSpectatorActionPacket packet, CallbackInfo ci) {
		if (packet.spectateEntityId().isEmpty()) return;
		Entity target = player.level().getEntity(packet.spectateEntityId().getAsInt());
		if (target != null && !allowed(target)) ci.cancel();
	}

	private boolean allowed(Entity target) {
		if (!(target instanceof ServerPlayer other) || other == player) return true;
		return Parties.sameParty(player.getUUID(), other.getUUID()) || Teams.sameTeam(player, other);
	}
}
