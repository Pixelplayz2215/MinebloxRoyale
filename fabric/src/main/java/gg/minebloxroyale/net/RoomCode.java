package gg.minebloxroyale.net;

import gg.minebloxroyale.MinebloxRoyale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Each Mineblox Royale world's room code (creator): 8 digits, made once and kept in the world folder, so it stays the
 * same every time that world is hosted. Everyone in the room is told it when they join, and /rc shows it; friends type
 * /rc <code> to join (the host's game posts where to find it: RoomCodes on the client).
 */
public final class RoomCode {
	private static final String FILE = "minebloxroyale_room.txt";
	private static final SecureRandom RANDOM = new SecureRandom();

	private RoomCode() {
	}

	/** The code, and a random id for this world (two worlds that drew the same code can be told apart). */
	public record Code(String code, String owner) {
	}

	public static synchronized Code of(MinecraftServer server) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
		try {
			if (Files.exists(file)) {
				String[] s = Files.readString(file).trim().split("\\s+");
				if (s.length == 2 && s[0].matches("\\d{8}")) return new Code(s[0], s[1]);
			}
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't read the room code", e);
		}
		return renew(server);
	}

	/**
	 * A new code for this world (also when another world turns out to be using the same one); everyone in it is told.
	 */
	public static synchronized Code renew(MinecraftServer server) {
		byte[] id = new byte[8];
		RANDOM.nextBytes(id);
		Code c = new Code(Integer.toString(10_000_000 + RANDOM.nextInt(90_000_000)), HexFormat.of().formatHex(id));
		try {
			Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(FILE), c.code() + " " + c.owner() + "\n");
		} catch (IOException e) {
			MinebloxRoyale.couldntSave("the room code", server.getWorldPath(LevelResource.ROOT), e);
		}
		server.execute(() -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) sendTo(p, c);
		});
		return c;
	}

	public static void sendTo(ServerPlayer player, Code c) {
		ServerPlayNetworking.send(player, new Net.Room(c.code()));
	}
}
