package gg.minebloxroyale.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.minebloxroyale.MinebloxRoyale;
import java.io.Reader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The HUD's smooth drawing (v2.0.0, creator: "Cleaner UI (taken from Fortnite and re-rendered smoother)"): panels and
 * bars with rounded, anti-aliased corners drawn in the screen's own pixels (not the GUI's three-times-bigger blocky
 * ones), the HUD's Blender icons and its lettering (a bold condensed face) from glyph atlases, all smoothly filtered at
 * any size (textures/gui/hud, made by fabric/tools/blender/hud_art.py). Positions and sizes are in GUI pixels, as the
 * rest of the HUD.
 */
public final class Smooth {
	public static final int LEFT = -1, CENTER = 0, RIGHT = 1;
	/** The face's metrics (hud_font.json), loaded once; null if missing (Minecraft's own font then). */
	private static Face face;
	private static boolean faceTried;

	private record Face(String chars, int cols, float cw, float ch, float base, float pad, int[] ems, float[] advance) {
	}

	private Smooth() {
	}

	private static float scale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	// ---- panels ----

	/** A rounded rectangle (radius r), one colour. */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
		panel(g, x, y, w, h, r, color, color);
	}

	/**
	 * A rounded rectangle shading from top to bottom (colours ARGB), its corners anti-aliased: drawn in screen pixels,
	 * each row of a corner as a solid run with one partly covered pixel at each end.
	 */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int top, int bottom) {
		float s = scale();
		int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
		float rad = Math.max(0, Math.min(r * s, Math.min(x1 - x0, y1 - y0) / 2F));
		int ri = (int) Math.ceil(rad);
		g.pose().pushMatrix();
		g.pose().scale(1F / s, 1F / s);
		// the middle: straight sides
		if (y1 - y0 > 2 * ri) g.fillGradient(x0, y0 + ri, x1, y1 - ri, mix(top, bottom, ri / (float) (y1 - y0)), mix(top, bottom, 1 - ri / (float) (y1 - y0)));
		for (int j = 0; j < ri && j < (y1 - y0 + 1) / 2; j++) {
			float dy = rad - (j + 0.5F);
			float in = rad - (float) Math.sqrt(Math.max(0, rad * rad - dy * dy));
			for (int row : new int[] { y0 + j, y1 - 1 - j }) {
				int c = mix(top, bottom, (row - y0 + 0.5F) / (y1 - y0));
				row(g, x0 + in, x1 - in, row, c);
			}
		}
		g.pose().popMatrix();
	}

	/** One row of pixels from xa to xb (fractional): the solid run, and the end pixels as much as they're covered. */
	private static void row(GuiGraphicsExtractor g, float xa, float xb, int y, int c) {
		int ia = (int) Math.ceil(xa), ib = (int) Math.floor(xb);
		if (ib > ia) g.fill(ia, y, ib, y + 1, c);
		float la = ia - xa, lb = xb - ib;
		if (la > 0.01F) g.fill(ia - 1, y, ia, y + 1, alpha(c, la));
		if (lb > 0.01F && ib >= ia) g.fill(ib, y, ib + 1, y + 1, alpha(c, lb));
	}

	/**
	 * A bar: its rounded track, then filled frac of the way in colour (a lighter top half, Fortnite-style), split into
	 * segments by thin gaps.
	 */
	public static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, float frac, int color, int segments) {
		panel(g, x, y, w, h, h / 2.5F, 0xB0000000);
		float f = Math.max(0, Math.min(1, frac));
		if (f > 0) {
			float fw = Math.max(h * 0.8F, w * f);
			panel(g, x, y, fw, h, h / 2.5F, lighter(color, 0.35F), color);
			panel(g, x + h * 0.2F, y + h * 0.12F, fw - h * 0.4F, h * 0.28F, h * 0.14F, 0x40FFFFFF); // the shine
		}
		for (int i = 1; i < segments; i++) {
			float sx = x + w * i / segments;
			if (sx < x + w * f) panel(g, sx - 0.35F, y, 0.7F, h, 0, 0x70000000);
		}
	}

	// ---- icons ----

	/** One of the HUD's Blender icons (textures/gui/hud/<name>.png, 128 px) at x, y, size across, tinted (white: as made). */
	public static void icon(GuiGraphicsExtractor g, String name, float x, float y, float size, int tint) {
		Identifier tex = MinebloxRoyale.id("textures/gui/hud/" + name + ".png");
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 128F, size / 128F);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, 128, 128, 128, 128, tint);
		g.pose().popMatrix();
	}

	// ---- lettering ----

	private static Face face() {
		if (!faceTried) {
			faceTried = true;
			try {
				var res = Minecraft.getInstance().getResourceManager().getResource(MinebloxRoyale.id("hud_font.json"));
				if (res.isPresent()) try (Reader r = res.get().openAsReader()) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					JsonArray cell = o.getAsJsonArray("cell"), ems = o.getAsJsonArray("ems"), adv = o.getAsJsonArray("advance");
					int[] e = new int[ems.size()];
					for (int i = 0; i < e.length; i++) e[i] = ems.get(i).getAsInt();
					float[] a = new float[adv.size()];
					for (int i = 0; i < a.length; i++) a[i] = adv.get(i).getAsFloat();
					face = new Face(o.get("chars").getAsString(), o.get("cols").getAsInt(), cell.get(0).getAsFloat(), cell.get(1).getAsFloat(),
						o.get("baseline").getAsFloat(), o.get("pad").getAsFloat(), e, a);
				}
			} catch (Exception ex) {
				MinebloxRoyale.LOG.warn("BR: HUD face missing, using Minecraft's font", ex);
			}
		}
		return face;
	}

	/** How wide a line of the HUD face is at this size (GUI px per em). */
	public static float width(String text, float size) {
		Face f = face();
		if (f == null) return Minecraft.getInstance().font.width(text) * size / 9F;
		float w = 0;
		for (int i = 0; i < text.length(); i++) {
			int k = f.chars().indexOf(text.charAt(i));
			w += (k < 0 ? 0.5F : f.advance()[k]) * size;
		}
		return w;
	}

	/**
	 * A line in the HUD face: size = GUI px per em (capitals stand about 0.73 of it), y = the top of its line (capitals
	 * start a little below), aligned LEFT / CENTER / RIGHT on x; shadow: a soft dark copy under it. Returns its width.
	 */
	public static float text(GuiGraphicsExtractor g, String text, float x, float y, float size, int color, int align, boolean shadow) {
		Face f = face();
		float w = width(text, size);
		float left = align == LEFT ? x : align == CENTER ? x - w / 2 : x - w;
		if (f == null) { // (no atlas: Minecraft's font, scaled)
			g.pose().pushMatrix();
			g.pose().translate(left, y);
			g.pose().scale(size / 9F, size / 9F);
			g.text(Minecraft.getInstance().font, text, 0, 0, color, shadow);
			g.pose().popMatrix();
			return w;
		}
		if (shadow) glyphs(g, f, text, left + size * 0.05F, y + size * 0.07F, size, (color >>> 24) * 2 / 3 << 24);
		glyphs(g, f, text, left, y, size, color);
		return w;
	}

	private static void glyphs(GuiGraphicsExtractor g, Face f, String text, float x, float y, float size, int color) {
		// the atlas nearest this size on screen (so it's neither blurred up nor thinned down much)
		float px = size * scale();
		int em = f.ems()[0];
		for (int e : f.ems()) if (Math.abs(e - px) < Math.abs(em - px)) em = e;
		Identifier tex = MinebloxRoyale.id("textures/gui/hud/hud_font_" + em + ".png");
		int rows = (f.chars().length() + f.cols() - 1) / f.cols();
		int cw = Math.round(f.cw() * em), ch = Math.round(f.ch() * em), texW = cw * f.cols(), texH = ch * rows;
		float baseline = y + size * 0.82F, pen = x;
		for (int i = 0; i < text.length(); i++) {
			int k = f.chars().indexOf(text.charAt(i));
			if (k < 0) {
				pen += 0.5F * size;
				continue;
			}
			if (text.charAt(i) != ' ') {
				g.pose().pushMatrix();
				// the glyph's cell: its pen point pad in from the left, its baseline `base` up from the bottom
				g.pose().translate(pen - f.pad() * size, baseline - (f.ch() - f.base()) * size);
				g.pose().scale(size / em, size / em);
				g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, (k % f.cols()) * cw, (k / f.cols()) * ch, cw, ch, texW, texH, color);
				g.pose().popMatrix();
			}
			pen += f.advance()[k] * size;
		}
	}

	// ---- colours ----

	static int alpha(int argb, float f) {
		return Math.round((argb >>> 24) * Math.max(0, Math.min(1, f))) << 24 | argb & 0xFFFFFF;
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = 0;
		for (int sh = 0; sh < 32; sh += 8) {
			int ca = a >>> sh & 255, cb = b >>> sh & 255;
			r |= Math.round(ca + (cb - ca) * t) << sh;
		}
		return r;
	}

	static int lighter(int argb, float f) {
		return mix(argb, argb | 0xFFFFFF, f) & 0xFFFFFF | argb & 0xFF000000;
	}
}
