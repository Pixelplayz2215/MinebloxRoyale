package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Each player's Locker outfit (cosmetics, presets, emotes and gliders sheets): one item per slot, the 8 emotes on their
 * wheel and their glider, saved with the world and shown to everyone. Each player's own game also remembers it and
 * sends it on join, so the outfit follows the player to any world. Cosmetic only: nothing here touches health,
 * shield, damage or movement.
 */
public final class Locker {
	/** The most emotes on the emote wheel (B). */
	public static final int EMOTE_SLOTS = 8;
	private static final Map<UUID, Map<String, String>> OUTFITS = new HashMap<>();
	private static MinecraftServer loadedFor;

	private Locker() {
	}

	/** "hat:hat_tophat,shirt:shirt_red" */
	static String encode(Map<String, String> outfit) {
		return outfit.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
	}

	/**
	 * A preset's pieces in slot order: {slot, cosmetic id or "-"} for hat, hair, face, back, shirt, pants and shoes.
	 */
	public static String[][] pieces(PresetDef preset) {
		return new String[][] { { "hat", preset.hat() }, { "hair", preset.hair() }, { "face", preset.face() }, { "back", preset.back() }, { "shirt", preset.shirt() },
			{ "pants", preset.pants() }, { "shoes", preset.shoes() } };
	}

	/**
	 * A player picked (or took off, id "none") an item in the Locker. Slots: the cosmetics sheet's (hat, hair, face,
	 * back, shirt, pants, shoes, particle), "glider" (gliders sheet, must be unlocked), "emotes" (up to 8 unlocked
	 * emote ids joined by '+') and "preset" (presets sheet: puts on every piece of that outfit).
	 */
	public static void equip(ServerPlayer p, String slot, String id) {
		MinecraftServer server = p.level().getServer();
		load(server);
		Map<String, String> outfit = OUTFITS.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
		if (!apply(p, outfit, slot, id)) return;
		save(server);
		broadcast(server, p, outfit);
	}

	/** On join, each player's own game sends the outfit it remembers (so it follows them to any world). */
	public static void restore(ServerPlayer p, String encoded) {
		MinecraftServer server = p.level().getServer();
		load(server);
		Map<String, String> outfit = new LinkedHashMap<>();
		for (String part : encoded.split(",")) {
			String[] si = part.split(":", 2);
			if (si.length == 2) apply(p, outfit, si[0], si[1]);
		}
		OUTFITS.put(p.getUUID(), outfit);
		save(server);
		broadcast(server, p, outfit);
	}

	private static boolean apply(ServerPlayer p, Map<String, String> outfit, String slot, String id) {
		if (id.equals("none") || id.equals("-")) {
			outfit.remove(slot);
			return true;
		}
		switch (slot) {
			case "preset" -> {
				PresetDef preset = Sheets.PRESETS_BY_ID.get(id);
				if (preset == null) return false;
				String[][] pieces = pieces(preset);
				// A preset made of locked pieces (the Dominus sets) needs them unlocked.
				for (String[] piece : pieces) {
					CosmeticDef c = Sheets.COSMETICS_BY_ID.get(piece[1]);
					if (c != null && !Obby.hasCosmetic(p, c)) return false;
				}
				for (String[] piece : pieces) {
					if (piece[1] == null || piece[1].equals("-")) outfit.remove(piece[0]);
					else outfit.put(piece[0], piece[1]);
				}
				return true;
			}
			case "glider" -> {
				GliderDef g = Sheets.GLIDERS_BY_ID.get(id);
				if (g == null || !Obby.hasGlider(p, g)) return false;
				outfit.put(slot, id);
				return true;
			}
			case "emotes" -> {
				LinkedHashSet<String> ids = new LinkedHashSet<>();
				for (String e : id.split("[+]")) {
					EmoteDef def = Sheets.EMOTES_BY_ID.get(e);
					if (def != null && Obby.hasEmote(p, def) && ids.size() < EMOTE_SLOTS) ids.add(e);
				}
				outfit.put(slot, String.join("+", ids));
				return true;
			}
			default -> {
				CosmeticDef c = Sheets.COSMETICS_BY_ID.get(id);
				if (c == null || !c.slot().equals(slot) || !Obby.hasCosmetic(p, c)) return false;
				outfit.put(slot, id);
				return true;
			}
		}
	}

	private static void broadcast(MinecraftServer server, ServerPlayer p, Map<String, String> outfit) {
		for (ServerPlayer o : server.getPlayerList().getPlayers()) Net.lockerTo(o, p.getUUID(), encode(outfit));
	}

	/** Someone joined: they get everyone's outfit, everyone gets theirs. */
	public static void onJoin(ServerPlayer joined) {
		MinecraftServer server = joined.level().getServer();
		load(server);
		for (ServerPlayer o : server.getPlayerList().getPlayers()) {
			Net.lockerTo(joined, o.getUUID(), encode(OUTFITS.getOrDefault(o.getUUID(), Map.of())));
			if (o != joined) Net.lockerTo(o, joined.getUUID(), encode(OUTFITS.getOrDefault(joined.getUUID(), Map.of())));
		}
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_lockers.txt");
	}

	private static void load(MinecraftServer server) {
		if (loadedFor == server) return;
		loadedFor = server;
		OUTFITS.clear();
		try {
			for (String line : Files.readAllLines(file(server))) {
				String[] kv = line.split("=", 2);
				if (kv.length != 2) continue;
				Map<String, String> outfit = new LinkedHashMap<>();
				for (String part : kv[1].trim().split(",")) {
					String[] si = part.split(":", 2);
					if (si.length == 2 && (Sheets.COSMETICS_BY_ID.containsKey(si[1]) || si[0].equals("glider") || si[0].equals("emotes"))) outfit.put(si[0], si[1]);
				}
				OUTFITS.put(UUID.fromString(kv[0].trim()), outfit);
			}
		} catch (IOException | IllegalArgumentException ignored) {
		}
	}

	private static void save(MinecraftServer server) {
		StringBuilder b = new StringBuilder();
		OUTFITS.forEach((k, v) -> b.append(k).append('=').append(encode(v)).append('\n'));
		try {
			Files.writeString(file(server), b.toString());
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't save lockers", e);
		}
	}
}
