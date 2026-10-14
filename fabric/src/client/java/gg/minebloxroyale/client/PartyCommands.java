package gg.minebloxroyale.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gg.minebloxroyale.net.Net;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Parties (creator's choice): /party invite <name>, /party accept [name], /party leave and /party (who's in it). They
 * run in the player's own game (server commands are locked) and send the request to the room, which checks and answers.
 */
public final class PartyCommands {
	private PartyCommands() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("party")
			.executes(c -> send(c.getSource(), "party:list"))
			.then(ClientCommands.literal("invite")
				.then(ClientCommands.argument("name", StringArgumentType.word()).suggests((c, b) -> players(b))
					.executes(c -> send(c.getSource(), "party:invite:" + StringArgumentType.getString(c, "name")))))
			.then(ClientCommands.literal("accept")
				.executes(c -> send(c.getSource(), "party:accept"))
				.then(ClientCommands.argument("name", StringArgumentType.word()).suggests((c, b) -> players(b))
					.executes(c -> send(c.getSource(), "party:accept:" + StringArgumentType.getString(c, "name")))))
			.then(ClientCommands.literal("leave").executes(c -> send(c.getSource(), "party:leave")))));
	}

	private static int send(FabricClientCommandSource src, String action) {
		if (src.getClient().getConnection() == null) {
			src.sendError(Component.literal("Join a world first."));
			return 0;
		}
		ClientPlayNetworking.send(new Net.Action(action));
		return 1;
	}

	/** Everyone else in this room, for Tab completion. */
	private static CompletableFuture<Suggestions> players(SuggestionsBuilder b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && mc.player != null)
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
				String name = info.getProfile().name();
				if (!name.equals(mc.player.getName().getString()) && name.toLowerCase(Locale.ROOT).startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) b.suggest(name);
			}
		return b.buildFuture();
	}
}
