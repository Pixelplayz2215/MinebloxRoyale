package gg.minebloxroyale.net;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which launcher each player plays from, for the badge by their name (2.0.0-test6.2, creator: "Icons next to players
 * names based on the launcher they're using ... (shows for everybody)"): each player's game says on joining
 * ("launcher:<id>", LauncherBadge on the client); the world tells everyone, and a newcomer gets everyone's. Each
 * player's country travels the same way, for the flag after their name (2.0.0, "flag:<CC>", CountryFlag on the client;
 * "flag:" while they hide it).
 */
public final class Launchers {
	/** The ids a game may send (the badges there are). */
	private static final List<String> KNOWN = List.of("melty", "prism", "multimc", "modrinth", "lunar", "curseforge", "atlauncher", "gdlauncher", "minecraft", "dev");
	private static final Map<UUID, String> BY_PLAYER = new HashMap<>();
	private static final Map<UUID, String> FLAGS = new HashMap<>();

	private Launchers() {
	}

	/** A player's game said which launcher it is (an unknown id clears it). */
	public static void set(ServerPlayer player, String id) {
		if (KNOWN.contains(id)) BY_PLAYER.put(player.getUUID(), id);
		else BY_PLAYER.remove(player.getUUID());
		sendAll(player.level().getServer());
	}

	/** A player's game said its country (two capital letters; anything else, as when they hide it, clears it). */
	public static void setFlag(ServerPlayer player, String cc) {
		if (cc.matches("[A-Z]{2}")) FLAGS.put(player.getUUID(), cc);
		else FLAGS.remove(player.getUUID());
		sendAll(player.level().getServer());
	}

	/** Someone joined: they get everyone's so far (theirs comes once their game says). */
	public static void onJoin(ServerPlayer player) {
		ServerPlayNetworking.send(player, payload(player.level().getServer()));
	}

	public static void onLeave(ServerPlayer player) {
		boolean had = BY_PLAYER.remove(player.getUUID()) != null;
		if (FLAGS.remove(player.getUUID()) != null || had) sendAll(player.level().getServer());
	}

	private static Net.Launchers payload(MinecraftServer server) {
		Map<String, String> out = new HashMap<>(), flags = new HashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String id = BY_PLAYER.get(p.getUUID()), cc = FLAGS.get(p.getUUID());
			if (id != null) out.put(p.getUUID().toString(), id);
			if (cc != null) flags.put(p.getUUID().toString(), cc);
		}
		return new Net.Launchers(out, flags);
	}

	private static void sendAll(MinecraftServer server) {
		Net.Launchers p = payload(server);
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(sp, p);
	}
}
