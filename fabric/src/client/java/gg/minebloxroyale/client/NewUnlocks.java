package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.hub.Locker;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.EmoteDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * NEW on what you've just unlocked (2.0.0, creator: "When something is unlocked, add a "NEW" badge or a red dot next to
 * the name in locker. Once Player hover's over the new item, that badge is cleared ... (Doesn't apply to gold bought
 * items as they already have the shop tab that shows it unlocked.)"). An item unlocked by playing (eliminations, wins,
 * the obby, the range...) that you haven't hovered in the Locker yet is new: its tile gets a red NEW badge, its tab a red
 * dot, and the hub's hint says so. Gold items (unlock "shop") never are. What's been seen is kept per Minecraft account
 * beside the unlocks (PlayerFiles, seen.txt: "<account> <item>"). The first time an account's unlocks arrive, everything
 * it already has counts as seen ("<account> *"), so updating doesn't light up every old unlock at once.
 */
public final class NewUnlocks {
	private static @Nullable Set<String> seen;
	private static @Nullable String loadedFor;
	private static boolean baselined;

	private NewUnlocks() {
	}

	private static Path file() {
		return PlayerFiles.dir().resolve("seen.txt");
	}

	private static Set<String> seen() {
		String account = PlayerFiles.account();
		if (seen != null && account.equals(loadedFor)) return seen;
		Set<String> s = new HashSet<>();
		baselined = false;
		try {
			if (Files.exists(file()))
				for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
					String[] p = line.trim().split(" ", 2);
					if (p.length != 2 || !p[0].equals(account)) continue;
					if (p[1].equals("*")) baselined = true;
					else s.add(p[1]);
				}
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't read seen.txt ({})", e.getMessage());
		}
		loadedFor = account;
		return seen = s;
	}

	private static void save(List<String> items) {
		if (items.isEmpty()) return;
		StringBuilder out = new StringBuilder();
		for (String i : items) out.append(PlayerFiles.account()).append(' ').append(i).append(System.lineSeparator());
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			MinebloxRoyale.LOG.info("BR: couldn't save seen.txt ({})", e.getMessage());
		}
	}

	/**
	 * This world told us our unlocks (MinebloxRoyaleClient, Net.Unlocks): the first time for this account, all it has
	 * now counts as seen.
	 */
	public static void onStats() {
		Set<String> s = seen();
		if (baselined) return;
		List<String> add = new ArrayList<>(unlockedItems());
		add.removeAll(s);
		s.addAll(add);
		add.add("*");
		baselined = true;
		save(add);
	}

	/** Hovered in the Locker: not new any more. */
	public static void markSeen(String item) {
		if (seen().add(item)) {
			save(List.of(item));
			anyAt = 0;
		}
	}

	/** An unlocked item's tile ("emote:dab", "glider:...", "cos:...", "preset:..."): not hovered yet. */
	public static boolean isNew(String item) {
		Set<String> s = seen();
		return baselined && !s.contains(item);
	}

	/** Anything new in this Locker tab ("emote", "glider", "preset", or a cosmetic slot). */
	public static boolean anyIn(String tab) {
		Set<String> s = seen();
		if (!baselined) return false;
		for (String i : itemsOf(tab)) if (!s.contains(i)) return true;
		return false;
	}

	private static long anyAt;
	private static boolean anyWas;

	/** Anything new anywhere (the hub's hint, every frame: worked out once a second). */
	public static boolean any() {
		long now = System.currentTimeMillis();
		if (now - anyAt < 1000) return anyWas;
		anyAt = now;
		Set<String> s = seen();
		anyWas = false;
		if (baselined) for (String i : unlockedItems()) if (!s.contains(i)) anyWas = true;
		return anyWas;
	}

	/** Every item unlocked by playing (not gold) that this account has now. */
	private static List<String> unlockedItems() {
		List<String> out = new ArrayList<>(itemsOf("emote"));
		out.addAll(itemsOf("glider"));
		out.addAll(itemsOf("preset"));
		for (CosmeticDef c : Sheets.COSMETICS)
			if (!c.unlock().equals("shop") && ClientState.unlocked(c)) out.add("cos:" + c.id());
		return out;
	}

	/** One tab's unlocked, not-for-gold items (cosmetic slots by their slot). */
	private static List<String> itemsOf(String tab) {
		List<String> out = new ArrayList<>();
		switch (tab) {
			case "emote" -> {
				for (EmoteDef e : Sheets.EMOTES)
					if (!e.unlock().equals("shop") && ClientState.unlocked(e)) out.add("emote:" + e.id());
			}
			case "glider" -> {
				for (GliderDef g : Sheets.GLIDERS)
					if (!g.unlock().equals("shop") && ClientState.unlocked(g)) out.add("glider:" + g.id());
			}
			case "preset" -> {
				for (PresetDef p : Sheets.PRESETS) {
					if (p.unlock().equals("shop")) continue;
					boolean open = true;
					for (String[] piece : Locker.pieces(p)) {
						CosmeticDef c = Sheets.COSMETICS_BY_ID.get(piece[1]);
						if (c != null && !ClientState.unlocked(c)) open = false;
					}
					if (open) out.add("preset:" + p.id());
				}
			}
			case "shop" -> {
			}
			default -> {
				for (CosmeticDef c : Sheets.COSMETICS)
					if (c.slot().equals(tab) && !c.unlock().equals("shop") && ClientState.unlocked(c)) out.add("cos:" + c.id());
			}
		}
		return out;
	}
}
