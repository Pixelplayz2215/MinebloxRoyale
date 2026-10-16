package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Each player's shooting-range record in this world, for unlocks (creator: some emotes and gliders unlock with a range
 * score, some with bullseyes): their best round score and their lifetime bullseyes, kept in minebloxroyale_range.txt
 * ("uuid=best,bullseyes"). Their game also files them with their other worlds' stats (MyUnlocks), like kills and wins.
 */
public final class RangeStats {
	private static final Map<UUID, int[]> STATS = new HashMap<>();
	private static MinecraftServer loadedFor;

	private RangeStats() {
	}

	/** {best round score, bullseyes} in this world. */
	public static int[] get(MinecraftServer server, UUID id) {
		load(server);
		int[] s = STATS.get(id);
		return s == null ? new int[2] : s.clone();
	}

	/** A range round ended with this score. */
	public static void roundScore(ServerPlayer p, int score) {
		if (score > get(p.level().getServer(), p.getUUID())[0]) update(p, s -> s[0] = score);
	}

	/** One more bullseye. */
	public static void bullseye(ServerPlayer p) {
		update(p, s -> s[1]++);
	}

	/** Changes the record, saves it, and tells the player about anything it just unlocked. */
	private static void update(ServerPlayer p, Consumer<int[]> change) {
		MinecraftServer server = p.level().getServer();
		load(server);
		UnlockRule.Stats before = Obby.career(p);
		change.accept(STATS.computeIfAbsent(p.getUUID(), k -> new int[2]));
		save(server);
		Obby.announceUnlocks(p, before);
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_range.txt");
	}

	private static void load(MinecraftServer server) {
		if (loadedFor == server) return;
		loadedFor = server;
		STATS.clear();
		try {
			for (String line : Files.readAllLines(file(server))) {
				String[] kv = line.split("=", 2);
				if (kv.length != 2) continue;
				String[] n = kv[1].split(",");
				try {
					STATS.put(UUID.fromString(kv[0].trim()), new int[] { Integer.parseInt(n[0].trim()), Integer.parseInt(n[1].trim()) });
				} catch (RuntimeException ignored) {
				}
			}
		} catch (IOException ignored) { // (no record yet)
		}
	}

	private static void save(MinecraftServer server) {
		StringBuilder b = new StringBuilder();
		STATS.forEach((k, v) -> b.append(k).append('=').append(v[0]).append(',').append(v[1]).append('\n'));
		try {
			Files.writeString(file(server), b.toString());
		} catch (IOException e) {
			MinebloxRoyale.couldntSave("the shooting range record", file(server), e);
		}
	}
}
