package gg.minebloxroyale.client;

import gg.minebloxroyale.net.Net;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fortnite damage numbers: only the player who hit sees them (the server sends each one to the attacker alone). White
 * for health, blue while the target still had shield, yellow and bigger for a headshot. They pop up over the target,
 * drift upwards and fade within a second.
 */
public final class DamageNumbers {
	private record Pop(double x, double y, double z, String text, int color, boolean big, long at) {
	}

	private static final List<Pop> POPS = new ArrayList<>();
	private static final long LIFE_MS = 900;

	private DamageNumbers() {
	}

	public static void add(Net.DamageNumber d) {
		Minecraft mc = Minecraft.getInstance();
		int color = switch (d.kind()) {
			case 2 -> 0xFFFFD42A; // headshot
			case 1 -> 0xFF6EC1FF; // shield
			default -> 0xFFFFFFFF; // health
		};
		// Small sideways jitter so quick hits don't stack on one spot.
		double j = (mc.level != null ? mc.level.getRandom().nextDouble() - 0.5 : 0) * 0.6;
		POPS.add(new Pop(d.x() + j, d.y(), d.z() + j, String.valueOf(Math.round(d.amount())), color, d.kind() == 2, System.currentTimeMillis()));
		if (POPS.size() > 40) POPS.remove(0);
	}

	static void draw(GuiGraphicsExtractor g, Minecraft mc) {
		long now = System.currentTimeMillis();
		for (Iterator<Pop> it = POPS.iterator(); it.hasNext();) {
			Pop p = it.next();
			float t = (now - p.at) / (float) LIFE_MS;
			if (t >= 1) {
				it.remove();
				continue;
			}
			double[] s = Waypoint.project(mc, g, p.x, p.y + t * 0.8, p.z);
			if (s[2] < 0) continue;
			int alpha = (int) (255 * (t < 0.7F ? 1 : (1 - t) / 0.3F));
			float scale = (p.big ? 1.6F : 1.2F) * (t < 0.12F ? 0.7F + t / 0.12F * 0.3F : 1F);
			g.pose().pushMatrix();
			g.pose().translate((float) s[0], (float) s[1]);
			g.pose().scale(scale, scale);
			g.centeredText(mc.font, p.text, 0, -4, (Math.max(8, alpha) << 24) | (p.color & 0xFFFFFF));
			g.pose().popMatrix();
		}
	}

	public static void clear() {
		POPS.clear();
	}
}
