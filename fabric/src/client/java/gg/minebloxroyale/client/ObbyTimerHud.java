package gg.minebloxroyale.client;

import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.PresetDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The obby timer on your screen (2.0.0, creator: "Add a timer to the Obby that starts as soon as you touch the first red
 * Obby Jump"; as the mockups in Mockups/Sky Towers and Obby v2.0.0): OBBY m:ss.t counting up while you run, your best
 * here and the obby-time presets' times; at the gold platform FINISHED and your time in gold with New best! or your
 * best, and any preset it unlocked, for 4 seconds. The server times the run (Obby); this counts up between its messages.
 * Since 2.1.0-test4 it is the bottom of the hub hint's panel (BRHud.hubHint; creator: "make the obby timer on the side
 * or bottom connected to the HUB UI"), not a panel of its own over the middle of the screen.
 */
public final class ObbyTimerHud {
	/** How tall its part of the hub panel is: a gold line, the clock, the small line. */
	public static final int HEIGHT = 26;
	private static final float BIG = 12, SMALL = 6.5F;
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

	/** Running, or finished under 4 seconds ago. */
	public static boolean showing() {
		return state == 1 || state == 2 && System.currentTimeMillis() - since <= 4000;
	}

	/** How wide its two lines are, for the hub panel to fit them. */
	public static float width() {
		String[] l = lines();
		return Math.max(Smooth.width(l[0], BIG), Smooth.width(l[1], SMALL));
	}

	/** Its part of the hub panel, from y down (HEIGHT), centred on cx, with a gold line w wide across the top. */
	public static void draw(GuiGraphicsExtractor g, float cx, float y, float w) {
		String[] l = lines();
		Smooth.panel(g, cx - w / 2, y + 1, w, 1.2F, 0.6F, 0xFFFFC828);
		Smooth.text(g, l[0], cx, y + 4.5F, BIG, state == 1 ? 0xFFFFFFFF : 0xFFFFD778, Smooth.CENTER, true);
		Smooth.text(g, l[1], cx, y + 18, SMALL, 0xFFC8CDD7, Smooth.CENTER, true);
	}

	/** The big line (OBBY and the clock, or FINISHED and the time) and the small one under it. */
	private static String[] lines() {
		if (state != 1) return new String[] { "FINISHED  " + clock(ticks * 50L), line };
		List<String> targets = new ArrayList<>();
		for (PresetDef p : Sheets.PRESETS) if (p.unlock().equals("obbytime")) targets.add(p.need() + " s");
		return new String[] { "OBBY  " + clock(ticks * 50L + System.currentTimeMillis() - since),
			(best > 0 ? "best " + clock(best * 50L) + "    " : "") + "targets: " + String.join("  ", targets) };
	}

	/** m:ss.t (milliseconds). */
	private static String clock(long ms) {
		long tenths = ms / 100;
		return tenths / 600 + ":" + String.format(Locale.ROOT, "%02d.%d", tenths / 10 % 60, tenths % 10);
	}
}
