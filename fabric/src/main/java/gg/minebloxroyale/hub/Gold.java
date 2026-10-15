package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Gold (v2.0.0, creator: "Gold, and a Locker shop: players earn gold and spend it on cooler items"; the OG emotes and
 * the Griddy "to the locker shop and give it a price for gold"). Earned in matches (match sheet gold*: eliminations, a
 * top-10 / top-5 finish, playing, a Victory Royale, a boss), spent in the Locker on items whose unlock is "shop" (their
 * need is the price). Kept in minebloxroyale_gold.txt ("uuid=earned,spent,item+item"): what was earned and what was
 * spent (both only ever grow) and what was bought. Each player's own game files them with their other worlds' stats
 * (MyUnlocks), like kills and wins, so gold and what it bought follow the player into every world.
 */
public final class Gold {
	private static final Map<UUID, int[]> GOLD = new HashMap<>();
	private static final Map<UUID, Set<String>> OWNED = new HashMap<>();
	private static MinecraftServer loadedFor;

	private Gold() {
	}

	/** {earned, spent} in this world. */
	static int[] get(MinecraftServer server, UUID id) {
		load(server);
		int[] g = GOLD.get(id);
		return g == null ? new int[2] : g.clone();
	}

	/** What the player bought in this world. */
	static Set<String> owned(MinecraftServer server, UUID id) {
		load(server);
		return Set.copyOf(OWNED.getOrDefault(id, Set.of()));
	}

	/** Gold from a match sheet setting (gold per elimination...), for something the player did. */
	public static void award(ServerPlayer p, String setting, String why) {
		award(p, Math.round(MinebloxRoyale.setting(setting)), why);
	}

	public static void award(ServerPlayer p, int amount, String why) {
		if (amount <= 0) return;
		MinecraftServer server = p.level().getServer();
		load(server);
		GOLD.computeIfAbsent(p.getUUID(), k -> new int[2])[0] += amount;
		save(server);
		p.sendSystemMessage(Component.literal("+" + amount + " gold (" + why + ")").withStyle(ChatFormatting.GOLD), true);
		Obby.sendUnlocks(p);
	}

	/**
	 * A shop item: anything in the emotes, cosmetics, gliders or presets sheets whose unlock is "shop" (need = its price;
	 * a preset, 2.0.0-test3, is a whole outfit).
	 */
	public record Item(String id, String name, int price, String kind) {
	}

	public static Item item(String id) {
		EmoteDef e = Sheets.EMOTES_BY_ID.get(id);
		if (e != null && e.unlock().equals("shop")) return new Item(id, e.name(), e.need(), "emote");
		CosmeticDef c = Sheets.COSMETICS_BY_ID.get(id);
		if (c != null && c.unlock().equals("shop")) return new Item(id, c.name(), c.need(), c.slot());
		GliderDef g = Sheets.GLIDERS_BY_ID.get(id);
		if (g != null && g.unlock().equals("shop")) return new Item(id, g.name(), g.need(), "glider");
		PresetDef p = Sheets.PRESETS_BY_ID.get(id);
		if (p != null && p.unlock().equals("shop")) return new Item(id, p.name(), p.need(), "preset");
		return null;
	}

	/**
	 * The Locker's shop: buys item id, if the player has the gold and doesn't have it yet. A preset makes every piece of
	 * it the player's too (creator: "When a preset is purchased, the individual item gets added to it's respective
	 * category page").
	 */
	public static void buy(ServerPlayer p, String id) {
		Item it = item(id);
		if (it == null) return;
		MinecraftServer server = p.level().getServer();
		UnlockRule.Stats st = Obby.career(p);
		if (Obby.tester(p) || st.owned().contains(id)) {
			p.sendSystemMessage(Component.literal(it.name() + " is already yours").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		if (st.gold() < it.price()) {
			p.sendSystemMessage(Component.literal("Not enough gold: " + it.name() + " costs " + it.price() + ", you have " + st.gold()).withStyle(ChatFormatting.RED), true);
			return;
		}
		load(server);
		GOLD.computeIfAbsent(p.getUUID(), k -> new int[2])[1] += it.price();
		Set<String> owned = OWNED.computeIfAbsent(p.getUUID(), k -> new LinkedHashSet<>());
		owned.add(id);
		if (it.kind().equals("preset"))
			for (String[] piece : Locker.pieces(Sheets.PRESETS_BY_ID.get(id))) if (piece[1] != null && !piece[1].equals("-")) owned.add(piece[1]);
		save(server);
		Obby.sendUnlocks(p);
		p.sendSystemMessage(Component.literal("Bought " + it.name() + " for " + it.price() + " gold - " + (st.gold() - it.price()) + " left").withStyle(ChatFormatting.GOLD));
		MinebloxRoyale.LOG.info("BR: {} bought {} for {} gold", p.getName().getString(), id, it.price());
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_gold.txt");
	}

	private static void load(MinecraftServer server) {
		if (loadedFor == server) return;
		loadedFor = server;
		GOLD.clear();
		OWNED.clear();
		try {
			for (String line : Files.readAllLines(file(server))) {
				String[] kv = line.split("=", 2);
				if (kv.length != 2) continue;
				String[] n = kv[1].split(",");
				try {
					UUID id = UUID.fromString(kv[0].trim());
					GOLD.put(id, new int[] { Integer.parseInt(n[0].trim()), Integer.parseInt(n[1].trim()) });
					if (n.length > 2 && !n[2].isBlank() && !n[2].trim().equals("-")) OWNED.put(id, new LinkedHashSet<>(Arrays.asList(n[2].trim().split("[+]"))));
				} catch (RuntimeException ignored) {
				}
			}
		} catch (IOException ignored) { // (no gold yet)
		}
	}

	private static void save(MinecraftServer server) {
		StringBuilder b = new StringBuilder();
		GOLD.forEach((k, v) -> {
			Set<String> own = OWNED.getOrDefault(k, Set.of());
			b.append(k).append('=').append(v[0]).append(',').append(v[1]).append(',').append(own.isEmpty() ? "-" : String.join("+", own)).append('\n');
		});
		try {
			Files.writeString(file(server), b.toString());
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't save gold", e);
		}
	}
}
