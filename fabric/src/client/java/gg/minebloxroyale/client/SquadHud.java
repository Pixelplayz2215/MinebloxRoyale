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
		g.fill(x - 3, y - 3, x + w + 3, y + mates.size() * 22 + 1, 0x90101420);
		float maxHp = MinebloxRoyale.setting("maxHealth"), maxSh = MinebloxRoyale.setting("maxShield");
		for (Net.Mate m : mates) {
			int color = ClientState.teamColor(m.slot());
			boolean out = m.status() == 3;
			g.fill(x, y + 1, x + 6, y + 7, out ? 0xFF555555 : color);
			String name = m.name();
			int nameW = mc.font.width(name);
			// Just the name in their colour, then the shield and health bars (creator: no distance), and their eliminations.
			g.text(mc.font, name, x + 9, y, out ? 0xFF8A8A8A : color, true);
			// Their eliminations at the row's right end (creator).
			String k = String.valueOf(m.kills());
			int kw = Math.round(mc.font.width(k) * 0.75F);
			BRHud.icon(g, x + w - kw - 9, y - 1, 0xFFFF5A5A, BRHud.ELIM);
			small(g, mc, k, x + w - kw, y + 1, 0xFFFFD45A);
			int by = y + 10;
			switch (m.status()) {
				case 0 -> {
					bar(g, x, by, w, m.shield() / maxSh, 0xFF3AA0FF);
					bar(g, x, by + 5, w, m.health() / maxHp, 0xFF55D655);
				}
				case 1 -> {
					// Knocked: the red bar runs down as they bleed out.
					bar(g, x, by + 2, w, m.health() / maxHp, 0xFFE03A3A);
					small(g, mc, "KNOCKED " + clock(m.seconds()), x + 12 + nameW, y + 1, 0xFFFF6060);
				}
				case 2 -> small(g, mc, "REBOOT CARD " + clock(m.seconds()), x + 9, by + 1, Waypoint.CARD); // the cards' cyan-blue
				default -> small(g, mc, "OUT", x + 9, by + 1, 0xFF8A8A8A);
			}
			y += 22;
		}
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, float frac, int color) {
		g.fill(x, y, x + w, y + 3, 0xFF000000);
		g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, frac))), y + 3, color);
	}

	private static void small(GuiGraphicsExtractor g, Minecraft mc, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75F, 0.75F);
		g.text(mc.font, text, 0, 0, color, true);
		g.pose().popMatrix();
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
				g.centeredText(mc.font, carry, cx, y + 14, 0xFF5CE1FF);
			}
		}
		if (prompt == null) return false;
		prompt(g, mc, cx, y, prompt);
		return true;
	}

	/** A one-line prompt on a dark strip, centred at cx. */
	static void prompt(GuiGraphicsExtractor g, Minecraft mc, int cx, int y, String text) {
		int w = mc.font.width(text) + 12;
		g.fill(cx - w / 2, y - 3, cx + w / 2, y + 11, 0xB0101420);
		g.centeredText(mc.font, text, cx, y, 0xFFFFFFFF);
	}

	private static void banner(GuiGraphicsExtractor g, Minecraft mc, int cx, int y, String big, int bigColor, String line, int lineColor) {
		int w = Math.max(mc.font.width(line), mc.font.width(big) * 2) + 16;
		g.fill(cx - w / 2, y - 4, cx + w / 2, y + 24, 0xC0200808);
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(1.6F, 1.6F);
		g.centeredText(mc.font, big, 0, 0, bigColor);
		g.pose().popMatrix();
		g.centeredText(mc.font, line, cx, y + 14, lineColor);
	}

	static void progress(GuiGraphicsExtractor g, Minecraft mc, int cx, int y, int w, String label, float frac, int color) {
		w = Math.max(w, mc.font.width(label) + 12);
		g.fill(cx - w / 2 - 2, y - 3, cx + w / 2 + 2, y + 18, 0xC0101420);
		g.centeredText(mc.font, label, cx, y, 0xFFFFFFFF);
		g.fill(cx - w / 2, y + 11, cx + w / 2, y + 15, 0xFF2A2A2A);
		g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * Math.max(0, Math.min(1, frac))), y + 15, color);
	}

	// ---- in the hub ----

	/**
	 * "Solo 1 Duos 0 Trios 2 Squads 0": how many stand on each mode portal, in each mode's colour. Width in pixels.
	 */
	static int padsWidth(Minecraft mc) {
		int w = 0;
		String[] n = ClientState.hud.pads().split(",");
		for (int i = 0; i < Sheets.MODES.size(); i++) w += mc.font.width(Sheets.MODES.get(i).name() + " " + (i < n.length ? n[i] : "0")) + (i > 0 ? 10 : 0);
		return w;
	}

	static void pads(GuiGraphicsExtractor g, Minecraft mc, int cx, int y) {
		String[] n = ClientState.hud.pads().split(",");
		int x = cx - padsWidth(mc) / 2;
		for (int i = 0; i < Sheets.MODES.size(); i++) {
			ModeDef m = Sheets.MODES.get(i);
			String count = i < n.length ? n[i] : "0";
			String text = m.name() + " " + count;
			boolean someone = !count.equals("0");
			g.text(mc.font, text, x, y, someone ? 0xFF000000 | m.color() : 0xFF7A8496, true);
			x += mc.font.width(text) + 10;
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
		int w = mc.font.width(label);
		String me = mc.player.getName().getString();
		for (int i = 0; i < names.size(); i++) w += mc.font.width(names.get(i).equals(me) ? "You" : names.get(i)) + (i > 0 ? mc.font.width(", ") : 0);
		int x = cx - w / 2;
		g.fill(x - 6, y - 3, x + w + 6, y + 11, 0xB0101420);
		g.text(mc.font, label, x, y, 0xFFB8C4D6, true);
		x += mc.font.width(label);
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				g.text(mc.font, ", ", x, y, 0xFFB8C4D6, true);
				x += mc.font.width(", ");
			}
			String n = names.get(i).equals(me) ? "You" : names.get(i);
			g.text(mc.font, n, x, y, names.get(i).equals(me) ? 0xFFFF55FF : ClientState.teamColor(i), true);
			x += mc.font.width(n);
		}
	}
}
