package gg.minebloxroyale.client;

import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The obby timer on your screen (2.0.0, creator: "Add a timer to the Obby that starts as soon as you touch the first red
 * Obby Jump"; as the mockups in Mockups/Sky Towers and Obby v2.0.0): under the hub hint while you run (hud sheet
 * "obby_timer"), OBBY m:ss.t counting up, your best here and the obby-time presets' times; at the gold platform
 * FINISHED and your time in gold with New best! or your best, and any preset it unlocked, for 4 seconds. The server
 * times the run (Obby); this counts up between its messages.
 */
public final class ObbyTimerHud {
	private static int state, ticks, best;
	private static String line = "";
	private static long since;

	private ObbyTimerHud() {
	}

	public static void receive(Net.ObbyTimer p) {
		state = p.state();
		ticks = p.ticks();
		best = p.best();
		line = p.line();
		since = System.currentTimeMillis();
	}

	public static void draw(GuiGraphicsExtractor g) {
		long now = System.currentTimeMillis();
		if (state == 0 || state == 2 && now - since > 4000) return;
		HudElementDef d = Sheets.HUD_BY_ID.get("obby_timer");
		String big, small;
		int color;
		if (state == 1) {
			big = "OBBY  " + clock(ticks * 50L + now - since);
			List<String> targets = new ArrayList<>();
			for (PresetDef p : Sheets.PRESETS) if (p.unlock().equals("obbytime")) targets.add(p.need() + " s");
			small = (best > 0 ? "best " + clock(best * 50L) + "    " : "") + "targets: " + String.join("  ", targets);
			color = 0xFFFFFFFF;
		} else {
			big = "FINISHED  " + clock(ticks * 50L);
			small = line;
			color = 0xFFFFD778;
		}
		float cx = g.guiWidth() / 2F + d.x(), y = d.y();
		float w = Math.max(Smooth.width(big, 13), Smooth.width(small, 6.5F)) + 20;
		Smooth.panel(g, cx - w / 2, y, w, 30, 6, 0xD2101420, 0xD20C1018);
		Smooth.panel(g, cx - w / 2 + 5, y + 27.6F, w - 10, 1.2F, 0.6F, 0xFFFFC828);
		Smooth.text(g, big, cx, y + 3, 13, color, Smooth.CENTER, true);
		Smooth.text(g, small, cx, y + 18.5F, 6.5F, 0xFFC8CDD7, Smooth.CENTER, true);
	}

	/** m:ss.t (milliseconds). */
	private static String clock(long ms) {
		long tenths = ms / 100;
		return tenths / 600 + ":" + String.format(Locale.ROOT, "%02d.%d", tenths / 10 % 60, tenths % 10);
	}
}
