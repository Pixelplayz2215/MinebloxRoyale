package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.Reboot;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.ModeDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Team modes on the HUD: the teammate panel (hud sheet "squad": names in their colours with shield and health bars,
 * KNOCKED, reboot card time, OUT), the interact prompts and progress ("interact": hold F to revive or reboot), the
 * knocked banner, and in the hub your party ("party") and how many stand on each mode portal.
 */
public final class SquadHud {
	private SquadHud() {
	}

	static String key() {
		return MinebloxRoyaleClient.interactKey().getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
	}

	private static String clock(float seconds) {
		int s = Math.max(0, Math.round(seconds));
		return s >= 60 ? s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60) : s + "s";
	}

	// ---- in a match ----

	/** Teammates down the top-left corner, in slot order. */
	static void panel(GuiGraphicsExtractor g, Minecraft mc) {
		if (!ClientState.teamMatch() || ClientState.squad.mates().isEmpty()) return;
		HudElementDef d = Sheets.HUD_BY_ID.get("squad");
		int x = d.x(), y = d.y(), w = d.size();
		List<Net.Mate> mates = ClientState.squad.mates();
		Smooth.panel(g, x - 4, y - 4, w + 8, mates.size() * 22 + 4, 5, 0x8010141E, 0xA0080B12);
		float maxHp = MinebloxRoyale.setting("maxHealth"), maxSh = MinebloxRoyale.setting("maxShield");
		for (Net.Mate m : mates) {
			int color = ClientState.teamColor(m.slot());
			boolean out = m.status() == 3;
			Smooth.panel(g, x, y + 1, 6, 6, 2, out ? 0xFF555555 : color);
			String name = m.name();
			// Just the name in their colour, then the shield and health bars (creator: no distance), and their eliminations.
			float nameW = Smooth.text(g, name, x + 9, y - 0.5F, 8.5F, out ? 0xFF8A8A8A : color, Smooth.LEFT, true);
			// Their eliminations at the row's right end (creator).
			String k = String.valueOf(m.kills());
			float kw = Smooth.width(k, 7);
			Smooth.icon(g, "kills", x + w - kw - 9, y - 0.5F, 8, -1);
			small(g, mc, k, x + w - kw, y + 0.5F, 0xFFFFD45A);
			int by = y + 10;
			switch (m.status()) {
				case 0 -> {
					bar(g, x, by, w, m.shield() / maxSh, 0xFF3AA0FF);
					bar(g, x, by + 5, w, m.health() / maxHp, 0xFF55D655);
				}
				case 1 -> {
					// Knocked: the red bar runs down as they bleed out.
					bar(g, x, by + 2, w, m.health() / maxHp, 0xFFE03A3A);
					small(g, mc, "KNOCKED " + clock(m.seconds()), x + 12 + nameW, y + 0.5F, 0xFFFF6060);
				}
				case 2 -> small(g, mc, "REBOOT CARD " + clock(m.seconds()), x + 9, by + 0.5F, Waypoint.CARD); // the cards' cyan-blue
				default -> small(g, mc, "OUT", x + 9, by + 0.5F, 0xFF8A8A8A);
			}
			y += 22;
		}
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, float frac, int color) {
		Smooth.bar(g, x, y, w, 3.4F, frac, color, 1);
	}

	private static void small(GuiGraphicsExtractor g, Minecraft mc, String text, float x, float y, int color) {
		Smooth.text(g, text, x, y, 7, color, Smooth.LEFT, true);
	}

	/**
	 * Under the crosshair: what F does here (revive, reboot), its progress while held, the van recharging; or, knocked,
	 * the bleed-out time and who is reviving you. True if it showed anything there (else a chest's prompt can: ChestHud).
	 */
	static boolean interact(GuiGraphicsExtractor g, Minecraft mc) {
		if (!ClientState.teamMatch() || mc.player == null || mc.player.isSpectator()) return false;
		Net.Squad sq = ClientState.squad;
		HudElementDef d = Sheets.HUD_BY_ID.get("interact");
		int cx = g.guiWidth() / 2 + d.x(), y = g.guiHeight() / 2 + d.y();
		if (Knocked.CLIENT.contains(mc.player.getId())) {
			// A red edge round the screen while you're down.
			int w = g.guiWidth(), h = g.guiHeight();
			for (int i = 0; i < 5; i++) {
				int c = (0x40 - i * 8) << 24 | 0xB01010, e = i * 5;
				g.fill(e, e, w - e, e + 5, c);
				g.fill(e, h - e - 5, w - e, h - e, c);
				g.fill(e, e + 5, e + 5, h - e - 5, c);
				g.fill(w - e - 5, e + 5, w - e, h - e - 5, c);
			}
			banner(g, mc, cx, y, "KNOCKED", 0xFFFF4040, "Bleeding out in " + clock(sq.bleedLeft()) + " - crawl to a teammate", 0xFFFFFFFF);
			if (!sq.revivedBy().isEmpty()) progress(g, mc, cx, y + 26, d.size(), sq.revivedBy() + " is reviving you", sq.revivedProgress(), 0xFF55D655);
			return true;
		}
		if (!sq.action().isEmpty()) {
			progress(g, mc, cx, y, d.size(), sq.action(), sq.actionProgress(), 0xFFFFD45A);
			return true;
		}
		String prompt = null;
		double reach = MinebloxRoyale.setting("reviveRange") + 0.5;
		for (Net.Mate m : sq.mates())
			if (m.status() == 1 && Math.hypot(m.x() - mc.player.getX(), m.z() - mc.player.getZ()) <= reach && Math.abs(m.y() - mc.player.getY()) < 2.5)
				prompt = "Hold " + key() + " to revive " + m.name();
		List<String> cards = new ArrayList<>();
		for (var o : Reboot.owners(mc.player.getOffhandItem())) cards.add(o.name());
		if (prompt == null && !cards.isEmpty()) {
			Net.VanMark van = null;
			double r = MinebloxRoyale.setting("rebootRange");
			for (Net.VanMark v : sq.vans()) if (Math.hypot(v.x() - mc.player.getX(), v.z() - mc.player.getZ()) <= r) van = v;
			if (van == null) prompt = null;
			else if (van.readyIn() > 0) prompt = "Reboot van recharging - " + clock(van.readyIn());
			else prompt = "Hold " + key() + " to reboot " + String.join(", ", cards);
			if (van == null) {
				String carry = "Carrying " + String.join(", ", cards) + "'s reboot card - take it to a reboot van (on the map)";
				Smooth.text(g, carry, cx, y + 14, 8.5F, 0xFF5CE1FF, Smooth.CENTER, true);
			}
		}
		if (prompt == null) return false;
		prompt(g, mc, cx, y, prompt);
		return true;
	}

	/** A one-line prompt on a dark strip, centred at cx. */
	static void prompt(GuiGraphicsExtractor g, Minecraft mc, int cx, int y, String text) {
		float w = Smooth.width(text, 9) + 16;
		Smooth.panel(g, cx - w / 2, y - 4, w, 15, 5, 0xB0182030, 0xC00C1018);
		Smooth.text(g, text, cx, y - 0.5F, 9, 0xFFFFFFFF, Smooth.CENTER, true);
	}

	private static void banner(GuiGraphicsExtractor g, Minecraft mc, int cx, int y, String big, int bigColor, String line, int lineColor) {
		float w = Math.max(Smooth.width(line, 9), Smooth.width(big, 15)) + 20;
		Smooth.panel(g, cx - w / 2, y - 5, w, 31, 6, 0xC0381010, 0xD0180606);
		Smooth.text(g, big, cx, y - 2, 15, bigColor, Smooth.CENTER, true);
		Smooth.text(g, line, cx, y + 14, 9, lineColor, Smooth.CENTER, true);
	}

	static void progress(GuiGraphicsExtractor g, Minecraft mc, int cx, int y, int w, String label, float frac, int color) {
		float bw = Math.max(w, Smooth.width(label, 9) + 14);
		Smooth.panel(g, cx - bw / 2 - 3, y - 4, bw + 6, 23, 5, 0xC0182030, 0xD00C1018);
		Smooth.text(g, label, cx, y - 1, 9, 0xFFFFFFFF, Smooth.CENTER, true);
		Smooth.bar(g, cx - bw / 2, y + 11, bw, 4, frac, color, 1);
	}

	// ---- in the hub ----

	/**
	 * "Solo 1 Duos 0 Trios 2 Squads 0": how many stand on each mode portal, in each mode's colour; the build modes on
	 * one row, the No Build ones (v2.0.0) on the next ("NO BUILD: Solo 0 ..."). Width in pixels of the wider row.
	 */
	static int padsWidth(Minecraft mc) {
		return Math.max(rowWidth(mc, false), rowWidth(mc, true));
	}

	/** How many rows of portal counts: one, or two with the No Build portals. */
	static int padRows() {
		return Sheets.MODES.stream().anyMatch(ModeDef::noBuild) ? 2 : 1;
	}

	private static final String NO_BUILD = "NO BUILD: ";

	private static String shortName(ModeDef m) {
		return m.noBuild() ? m.name().replace("No Build ", "") : m.name();
	}

	private static int rowWidth(Minecraft mc, boolean noBuild) {
		String[] n = ClientState.hud.pads().split(",");
		float w = noBuild ? Smooth.width(NO_BUILD, PAD_TEXT) : 0;
		int k = 0;
		for (int i = 0; i < Sheets.MODES.size(); i++) {
			ModeDef m = Sheets.MODES.get(i);
			if (m.noBuild() != noBuild) continue;
			w += Smooth.width(shortName(m) + " " + (i < n.length ? n[i] : "0"), PAD_TEXT) + (k++ > 0 ? 10 : 0);
		}
		return (int) Math.ceil(w);
	}

	/** The portal counts' lettering size. */
	private static final float PAD_TEXT = 9;

	static void pads(GuiGraphicsExtractor g, Minecraft mc, int cx, int y) {
		String[] n = ClientState.hud.pads().split(",");
		for (boolean noBuild : new boolean[] { false, true }) {
			if (noBuild && padRows() < 2) break;
			float x = cx - rowWidth(mc, noBuild) / 2F;
			if (noBuild) x += Smooth.text(g, NO_BUILD, x, y - 0.5F, PAD_TEXT, 0xFFB8C4D6, Smooth.LEFT, true);
			for (int i = 0; i < Sheets.MODES.size(); i++) {
				ModeDef m = Sheets.MODES.get(i);
				if (m.noBuild() != noBuild) continue;
				String count = i < n.length ? n[i] : "0";
				String text = shortName(m) + " " + count;
				boolean someone = !count.equals("0");
				x += Smooth.text(g, text, x, y - 0.5F, PAD_TEXT, someone ? 0xFF000000 | m.color() : 0xFF7A8496, Smooth.LEFT, true) + 10;
			}
			y += 11;
		}
	}

	/**
	 * Under the hub banner: your party, each name in its teammate colour. Nothing when you're not in one (creator: the
	 * commands board already explains /party).
	 */
	static void party(GuiGraphicsExtractor g, Minecraft mc, int top) {
		HudElementDef d = Sheets.HUD_BY_ID.get("party");
		int cx = g.guiWidth() / 2 + d.x(), y = Math.max(top, d.y());
		List<String> names = ClientState.partyNames;
		if (names.isEmpty()) return;
		String label = "PARTY " + names.size() + "/" + Math.round(MinebloxRoyale.setting("partyMax")) + ": ";
		float size = 9, w = Smooth.width(label, size);
		String me = mc.player.getName().getString();
		for (int i = 0; i < names.size(); i++) w += Smooth.width(names.get(i).equals(me) ? "You" : names.get(i), size) + (i > 0 ? Smooth.width(", ", size) : 0);
		float x = cx - w / 2;
		Smooth.panel(g, x - 7, y - 4, w + 14, 15, 5, 0xA0182030, 0xB00C1018);
		x += Smooth.text(g, label, x, y - 0.5F, size, 0xFFB8C4D6, Smooth.LEFT, true);
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) x += Smooth.text(g, ", ", x, y - 0.5F, size, 0xFFB8C4D6, Smooth.LEFT, true);
			String n = names.get(i).equals(me) ? "You" : names.get(i);
			x += Smooth.text(g, n, x, y - 0.5F, size, names.get(i).equals(me) ? 0xFFFF55FF : ClientState.teamColor(i), Smooth.LEFT, true);
		}
	}
}
