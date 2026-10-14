package gg.minebloxroyale.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import gg.minebloxroyale.DevCommands;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.MatchManager;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No commands for players, in their own world or a friend's (creator: no OP commands or creative; the only commands are
 * the game's own /rc, /roomcode, /leave and /party, which run in the player's game, not here). Players are sent an
 * empty command list, so "/" only suggests those, and any server command they send anyway is refused, including the
 * ones keys such as F3+F4 send (/gamemode). The server itself (the mod's own time and weather commands) is unaffected.
 * The one exception is the development account (MinebloxDev, only in the development run): it may switch its own game
 * mode with /gamemode creative, adventure or survival (creator: to fly round an island looking for buildings), and use
 * the testing helpers, /br (DevCommands).
 */
@Mixin(Commands.class)
public abstract class CommandsLockMixin {
	@Shadow
	@Final
	private static ClientboundCommandsPacket.NodeInspector<CommandSourceStack> COMMAND_NODE_INSPECTOR;

	/** The game modes the development account may switch to. */
	private static final String[] DEV_MODES = { "creative", "adventure", "survival" };

	@Inject(method = "sendCommands", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$noCommandList(ServerPlayer player, CallbackInfo ci) {
		RootCommandNode<CommandSourceStack> root = new RootCommandNode<>();
		if (MinebloxRoyale.devAccount(player)) {
			// Only so "/" suggests them: /gamemode is run below, not by Minecraft (no cheats needed); /br is the testing
			// helpers' own command (DevCommands).
			LiteralArgumentBuilder<CommandSourceStack> gamemode = LiteralArgumentBuilder.literal("gamemode");
			for (String m : DEV_MODES) gamemode.then(LiteralArgumentBuilder.<CommandSourceStack>literal(m).executes(c -> 1));
			root.addChild(gamemode.build());
			var br = ((Commands) (Object) this).getDispatcher().getRoot().getChild("br");
			if (br != null) root.addChild(br);
		}
		player.connection.send(new ClientboundCommandsPacket(root, COMMAND_NODE_INSPECTOR));
		ci.cancel();
	}

	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void minebloxroyale$playersRunNoCommands(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo ci) {
		ServerPlayer player = command.getContext().getSource().getPlayer();
		if (player == null) return;
		if (MinebloxRoyale.devAccount(player)) {
			if (DevCommands.isDevCommand(commandString)) return; // the testing helpers run as usual
			if (devGameMode(player, commandString)) {
				ci.cancel();
				return;
			}
		}
		player.sendSystemMessage(Component.literal("Commands are off in Mineblox Royale. You can use /rc, /roomcode, /leave and /party.").withStyle(ChatFormatting.RED));
		ci.cancel();
	}

	/** The development account's "/gamemode creative|adventure|survival": true if this was one. */
	private static boolean devGameMode(ServerPlayer player, String command) {
		String[] a = command.trim().toLowerCase(Locale.ROOT).split("\\s+");
		if (a.length != 2 || !a[0].equals("gamemode")) return false;
		// Survival is "back to normal": in the hub that's adventure (the obby's checkpoints only count in adventure).
		boolean hub = MatchManager.get().inHub(player);
		GameType mode = switch (a[1]) {
			case "creative" -> GameType.CREATIVE;
			case "adventure" -> GameType.ADVENTURE;
			case "survival" -> hub ? GameType.ADVENTURE : GameType.SURVIVAL;
			default -> null;
		};
		if (mode == null) return false;
		player.setGameMode(mode);
		String name = mode.getName();
		player.sendSystemMessage(Component.literal("Game mode: " + name + (a[1].equals("survival") && hub ? " (the hub's normal mode)" : "") + " (development account)")
			.withStyle(ChatFormatting.GRAY));
		return true;
	}
}
