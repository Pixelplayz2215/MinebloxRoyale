package gg.minebloxroyale.net;

import gg.minebloxroyale.MinebloxRoyale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * (2.0.0, creator: "I just don't want a v3 player to join a v4 player (vise versa) and someone report its broken")
 * Only the same version plays together. While a player joins, before anything else is sent, the host tells them its
 * version and they answer with theirs; if they differ, the player is turned away with what to do (update, or the
 * host does) instead of the game breaking later. A player whose game is older than 2.0.0 can't answer at all and is
 * turned away the same way. (The other way round, a 2.0.0 player at an older host: UpdateCheck, on the player's side.)
 */
public final class VersionCheck {
	private VersionCheck() {
	}

	public static final String GITHUB = "https://github.com/Pixelplayz2215/MinebloxRoyale/releases/latest";
	public static final String MELTY = "https://melty.gg/m/mineblox-royale";

	/** Each side's version: the host's first, then the player's answer. */
	public record Hello(String version) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = new Type<>(MinebloxRoyale.id("version"));
		public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.version), buf -> new Hello(buf.readUtf()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Joining waits on this until the player has answered. */
	private record Ask(String version) implements ConfigurationTask {
		static final Type TYPE = new Type(MinebloxRoyale.MOD_ID + ":version");

		@Override
		public void start(Consumer<Packet<?>> send) {
			send.accept(ServerConfigurationNetworking.createClientboundPacket(new Hello(version)));
		}

		@Override
		public Type type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(Hello.TYPE, Hello.CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			String mine = MinebloxRoyale.version();
			if (!ServerConfigurationNetworking.canSend(handler, Hello.TYPE)) {
				// (older than 2.0.0, which can't answer; a game without the mod couldn't join anyway)
				MinebloxRoyale.LOG.info("BR: turned away a player with Mineblox Royale older than 2.0.0 (this room: {})", mine);
				handler.disconnect(message(mine, "an older version", true));
				return;
			}
			handler.addTask(new Ask(mine));
		});
		ServerConfigurationNetworking.registerGlobalReceiver(Hello.TYPE, (p, ctx) -> {
			ServerConfigurationPacketListenerImpl handler = ctx.packetListener();
			String mine = MinebloxRoyale.version();
			if (!p.version().equals(mine)) {
				MinebloxRoyale.LOG.info("BR: turned away a player with Mineblox Royale {} (this room: {})", p.version(), mine);
				handler.disconnect(message(mine, "v" + p.version(), newer(mine, p.version())));
				return;
			}
			handler.completeTask(Ask.TYPE);
		});
	}

	/**
	 * Why a player can't join: the room's version, theirs, and who needs to update (the player, when the room's is
	 * newer; else the host), with where to get it.
	 */
	public static MutableComponent message(String room, String theirs, boolean playerUpdates) {
		MutableComponent m = Component.literal("Can't join: different versions of Mineblox Royale\n\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		m.append(Component.literal("This room is on v" + room + ". You have " + theirs + ".\n").withStyle(ChatFormatting.WHITE));
		m.append(Component.literal(playerUpdates ? "Update your game to v" + room + " to join.\n\n" : "Ask your friend to update to the newest version.\n\n")
			.withStyle(ChatFormatting.YELLOW));
		m.append(Component.literal("Get the newest version on Melty or GitHub:\n" + MELTY + "\n" + GITHUB).withStyle(ChatFormatting.GRAY));
		return m;
	}

	/** Whether version a is newer than b ("2.1.0" > "2.0.0"; a test build counts as its release's number). */
	public static boolean newer(String a, String b) {
		int[] x = parts(a), y = parts(b);
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
			if (p != q) return p > q;
		}
		return false;
	}

	/** A version's numbers ("v2.0.0-test6" -> 2, 0, 0). */
	public static int[] parts(String v) {
		String s = v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
		int cut = s.indexOf('-');
		if (cut >= 0) s = s.substring(0, cut);
		String[] bits = s.split("\\.");
		int[] out = new int[bits.length];
		for (int i = 0; i < bits.length; i++) {
			try {
				out[i] = Integer.parseInt(bits[i].replaceAll("[^0-9]", ""));
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}
}
