package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.client.avatar.Cosmetics;
import gg.minebloxroyale.sheets.CosmeticDef;
import gg.minebloxroyale.sheets.GliderDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Development only: a picture of every Locker item, for checking how they look. Creating
 * minebloxroyale/audit_locker.txt in the game folder (optionally listing the tabs to do, e.g. "hat face") opens the
 * Locker and, for each item, wears it on the preview (on this screen only, nothing is sent) and saves screenshots from
 * the front, left, right and back (about a second on each) into minebloxroyale/audit/. Your own outfit comes back at
 * the end. "backview" in the file takes only the back view of back accessories (creator: one picture each is enough).
 * Item ids in the file (e.g. "preset_rockstar back_guitar") limit their tabs to just those items.
 */
public final class LockerAudit {
	/** Front, left, right, back. */
	private static final float[] YAWS = { 0, 90, 270, 180 };
	private static final String[] VIEW = { "1front", "2left", "3right", "4back" };

	private record Shot(String tab, String name, Map<String, String> outfit, float yaw, String view) {
	}

	private static final Deque<Shot> QUEUE = new ArrayDeque<>();
	private static Map<String, String> saved;
	private static Path dir;
	private static int wait, n, checkTicks, frameAtSet;
	private static boolean backView, hidHud;
	/** Item ids named in the file: a tab with any of them takes only those. */
	private static Set<String> onlyIds = Set.of();

	private static boolean keep(String tab, String id) {
		boolean tabNamed = onlyIds.stream().anyMatch(x -> tab.equals("preset") ? Sheets.PRESETS_BY_ID.containsKey(x)
			: tab.equals("glider") ? Sheets.GLIDERS_BY_ID.containsKey(x) : Sheets.COSMETICS_BY_ID.containsKey(x) && Sheets.COSMETICS_BY_ID.get(x).slot().equals(tab));
		return !tabNamed || onlyIds.contains(id);
	}

	/** True while a run is taking pictures (the Locker leaves out its hint text). */
	static boolean active() {
		return !QUEUE.isEmpty();
	}

	private LockerAudit() {
	}

	public static void tick(Minecraft mc) {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment() || mc.player == null) return;
		if (QUEUE.isEmpty()) {
			if (++checkTicks % 20 == 0) start(mc);
			return;
		}
		if (!(mc.gui.screen() instanceof LockerScreen)) mc.gui.setScreen(new LockerScreen());
		Shot s = QUEUE.peek();
		if (wait == 0) {
			Cosmetics.OUTFITS.put(mc.player.getUUID(), s.outfit());
			ClientState.EMOTES.remove(mc.player.getId());
			if (!s.tab().equals(LockerScreen.tab)) {
				LockerScreen.tab = s.tab();
				if (mc.gui.screen() instanceof LockerScreen ls) ls.refresh();
			}
			LockerScreen.previewYaw = s.yaw();
			frameAtSet = LockerScreen.frames;
		}
		// Hold each angle about a second (20 ticks) and at least 5 newly drawn frames before the screenshot.
		if (++wait < 20 || LockerScreen.frames - frameAtSet < 5) return;
		wait = 0;
		QUEUE.poll();
		String file = String.format(Locale.ROOT, "%04d_%s_%s_%s.png", n++, s.tab(), s.name(), s.view());
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> {
			try (img) {
				img.writeToFile(dir.resolve(file));
			} catch (Exception e) {
				MinebloxRoyale.LOG.warn("BR audit: couldn't save {}", file, e);
			}
		});
		if (QUEUE.isEmpty()) finish(mc);
	}

	private static void start(Minecraft mc) {
		Path trigger = mc.gameDirectory.toPath().resolve("minebloxroyale/audit_locker.txt");
		if (!Files.isRegularFile(trigger)) return;
		String only = "";
		try {
			only = Files.readString(trigger).trim();
			Files.delete(trigger);
			dir = mc.gameDirectory.toPath().resolve("minebloxroyale/audit");
			Files.createDirectories(dir);
			try (var old = Files.list(dir)) {
				for (Path p : old.toList()) Files.delete(p);
			}
		} catch (Exception e) {
			MinebloxRoyale.LOG.warn("BR audit: couldn't start", e);
			return;
		}
		saved = Cosmetics.OUTFITS.get(mc.player.getUUID());
		backView = only.contains("backview");
		onlyIds = Set.copyOf(List.of(only.split("\\s+")));
		// Clean pictures: the HUD (chat, scoreboard, hints) is hidden while the run lasts, like F1.
		hidHud = !mc.gui.hud.isHidden();
		if (hidHud) mc.gui.hud.toggle();
		n = 0;
		wait = 0;
		if (only.contains("combo")) { // every hat with every hair (nothing else worn)
			for (CosmeticDef hat : Sheets.COSMETICS) {
				// (Listing hat_... or hair_... ids in the file limits the run to those.)
				if (!hat.slot().equals("hat") || only.contains("hat_") && !only.contains(hat.id())) continue;
				for (CosmeticDef hr : Sheets.COSMETICS) {
					if (!hr.slot().equals("hair") || only.contains("hair_") && !only.contains(hr.id())) continue;
					Map<String, String> o = new LinkedHashMap<>();
					o.put("hat", hat.id());
					o.put("hair", hr.id());
					add("hat", hat.id() + "__" + hr.id(), o);
				}
			}
			MinebloxRoyale.LOG.info("BR audit: {} Locker screenshots queued (hat + hair combos)", QUEUE.size());
			return;
		}
		String hair = Sheets.COSMETICS.stream().filter(c -> c.slot().equals("hair")).map(CosmeticDef::id).findFirst().orElse(null);
		for (String tab : new String[] { "preset", "hat", "hair", "face", "back", "shirt", "pants", "shoes", "glider", "particle" }) {
			if (!only.isEmpty() && !only.contains(tab)) continue;
			if (tab.equals("preset")) {
				for (PresetDef p : Sheets.PRESETS) {
					if (!keep(tab, p.id())) continue;
					Map<String, String> o = new LinkedHashMap<>();
					put(o, "hat", p.hat());
					put(o, "hair", p.hair());
					put(o, "face", p.face());
					put(o, "back", p.back());
					put(o, "shirt", p.shirt());
					put(o, "pants", p.pants());
					put(o, "shoes", p.shoes());
					add(tab, p.id(), o);
				}
			} else if (tab.equals("particle")) {
				for (CosmeticDef c : Sheets.COSMETICS) {
					if (!c.slot().equals("particle")) continue;
					Map<String, String> o = new LinkedHashMap<>();
					o.put("particle", c.id());
					QUEUE.add(new Shot(tab, c.id(), o, 0, "1front")); // (held 3 s so particles have time to appear)
					QUEUE.add(new Shot(tab, c.id(), o, 0, "1front"));
					QUEUE.add(new Shot(tab, c.id(), o, 0, "1front"));
				}
			} else if (tab.equals("glider")) {
				for (GliderDef g : Sheets.GLIDERS) {
					if (!keep(tab, g.id())) continue;
					Map<String, String> o = new LinkedHashMap<>();
					o.put("glider", g.id());
					add(tab, g.id(), o);
				}
			} else {
				for (CosmeticDef c : Sheets.COSMETICS) {
					if (!c.slot().equals(tab) || !keep(tab, c.id())) continue;
					Map<String, String> o = new LinkedHashMap<>();
					// Hats and faces are checked over hair (hair must tuck under them, never poke through).
					if ((tab.equals("hat") || tab.equals("face")) && hair != null) o.put("hair", hair);
					o.put(tab, c.id());
					add(tab, c.id(), o);
				}
			}
		}
		MinebloxRoyale.LOG.info("BR audit: {} Locker screenshots queued", QUEUE.size());
	}

	private static void put(Map<String, String> o, String slot, String id) {
		if (id != null && !id.isEmpty() && !id.equals("-")) o.put(slot, id);
	}

	private static void add(String tab, String id, Map<String, String> o) {
		if (backView && tab.equals("back")) {
			QUEUE.add(new Shot(tab, id, o, YAWS[3], VIEW[3]));
			return;
		}
		for (int i = 0; i < YAWS.length; i++) QUEUE.add(new Shot(tab, id, o, YAWS[i], VIEW[i]));
	}

	private static void finish(Minecraft mc) {
		if (hidHud && mc.gui.hud.isHidden()) mc.gui.hud.toggle();
		hidHud = false;
		if (saved != null) Cosmetics.OUTFITS.put(mc.player.getUUID(), saved);
		else Cosmetics.OUTFITS.remove(mc.player.getUUID());
		LockerScreen.previewYaw = 0;
		try {
			var w = mc.getWindow();
			Files.writeString(dir.resolve("done.txt"), "guiScale=" + w.getGuiScale() + " width=" + w.getWidth() + " height=" + w.getHeight() + " shots=" + n);
		} catch (Exception ignored) {
		}
		MinebloxRoyale.LOG.info("BR audit: done ({} screenshots)", n);
	}
}
