package gg.minebloxroyale.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button in the HUD's smooth style (2.0.0-test6, creator: "make the locker UI/HUD cleaner, minimalistic to match the
 * rest of the HUD"): a dark rounded tile with the Inter lettering instead of Minecraft's grey stone button, its name
 * shrunk to fit (never cut off). A tab is lit when it's the open one; an item has a gold edge and a tick when it's what
 * you have on (or picked for your wheel, a gold dot in its corner), is dimmed with a lock when it's locked, and shows its price with the coin in
 * the Shop.
 */
public class SmoothButton extends Button {
	public enum Look {
		TAB, TILE, ARROW, MAIN
	}

	private static final int GOLD = 0xFFFFC83D, TILE = 0xFF2C3340, TILE_OVER = 0xFF3A4354, EDGE = 0xFF4A5366, WHITE = 0xFFF4F6FA, MUTED = 0xFF9AA3B2,
		DIM = 0xFF6B7385, INK = 0xFF3B2400;
	private final Look look;
	/** What you have on, picked, or the open tab. */
	public boolean on;
	/** Locked: dimmed, with a lock. */
	public boolean locked;
	/** In the Shop: its price (gold), else null. */
	public String price;
	/** Bought already (the Shop): a small note. */
	public boolean owned;

	public SmoothButton(int x, int y, int w, int h, String label, Look look, OnPress press) {
		super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
		this.look = look;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean over = isHovered() && (active || locked);
		String label = getMessage().getString();
		switch (look) {
			case MAIN -> {
				Smooth.panel(g, x, y, w, h, 5, over ? 0xFFFFD86B : GOLD);
				label(g, label, x, y, w, h, INK, 8);
			}
			case ARROW -> {
				Smooth.panel(g, x, y, w, h, 5, active ? (over ? TILE_OVER : TILE) : 0x80202630);
				label(g, label, x, y, w, h, active ? WHITE : DIM, 8);
			}
			case TAB -> {
				if (on) {
					Smooth.panel(g, x, y, w, h, 5, 0xFF3A3320);
					Smooth.panel(g, x, y + 3, 2.5F, h - 6, 1, GOLD);
				} else if (over) Smooth.panel(g, x, y, w, h, 5, 0xFF262C38);
				float size = 7.5F;
				while (size > 5 && Smooth.width(label, size) > w - 14) size -= 0.25F;
				Smooth.text(g, label, x + 9, y + (h - size) / 2 - 0.5F, size, on ? GOLD : over ? WHITE : 0xFFC3CAD6, Smooth.LEFT, false);
			}
			case TILE -> {
				if (on) Smooth.panel(g, x - 1, y - 1, w + 2, h + 2, 6, GOLD);
				Smooth.panel(g, x, y, w, h, 5, locked ? 0xFF1E232C : over ? TILE_OVER : TILE);
				if (!on && !locked) Smooth.panel(g, x, y + h - 1.2F, w, 1.2F, 0, over ? EDGE : 0x00000000);
				float right = w - 6;
				if (on) { // (a gold "chosen" dot in the corner)
					Smooth.panel(g, x + w - 11, y + (h - 8) / 2, 8, 8, 4, GOLD);
					Smooth.panel(g, x + w - 8.5F, y + (h - 3) / 2, 3, 3, 1.5F, INK);
					right = w - 16;
				} else if (locked) {
					lock(g, x + w - 11, y + (h - 8) / 2, 0xFF8A93A3);
					right = w - 16;
				} else if (price != null) {
					float pw = Smooth.width(price, 6.5F);
					Smooth.icon(g, "gold", x + w - pw - 13, y + (h - 7) / 2, 7, -1);
					Smooth.text(g, price, x + w - 4, y + (h - 6.5F) / 2 - 0.5F, 6.5F, GOLD, Smooth.RIGHT, false);
					right = w - pw - 17;
				} else if (owned) {
					Smooth.text(g, "owned", x + w - 4, y + (h - 5.5F) / 2 - 0.5F, 5.5F, MUTED, Smooth.RIGHT, false);
					right = w - Smooth.width("owned", 5.5F) - 8;
				}
				float size = 7.5F;
				while (size > 4.5F && Smooth.width(label, size) > right - 4) size -= 0.25F;
				Smooth.text(g, label, x + 5, y + (h - size) / 2 - 0.5F, size, locked ? DIM : on ? GOLD : WHITE, Smooth.LEFT, false);
			}
		}
	}

	/** A small padlock, 7 across, its top-left at x, y. */
	private static void lock(GuiGraphicsExtractor g, float x, float y, int color) {
		Smooth.panel(g, x + 1.5F, y, 4, 1.2F, 0.6F, color);
		Smooth.panel(g, x + 1.5F, y, 1.2F, 4, 0.6F, color);
		Smooth.panel(g, x + 4.3F, y, 1.2F, 4, 0.6F, color);
		Smooth.panel(g, x, y + 3.4F, 7, 4.8F, 1.2F, color);
	}

	private static void label(GuiGraphicsExtractor g, String label, float x, float y, float w, float h, int color, float size) {
		while (size > 4.5F && Smooth.width(label, size) > w - 6) size -= 0.25F;
		Smooth.text(g, label, x + w / 2, y + (h - size) / 2 - 0.5F, size, color, Smooth.CENTER, false);
	}
}
