package gg.minebloxroyale.client.avatar;

import com.mojang.blaze3d.platform.NativeImage;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.CosmeticDef;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Shirt and pants designs (creator: detailed, nothing blocky): painted once per item as a smooth, anti-aliased picture
 * and wrapped round the torso or each leg (Cosmetics.submitClothes). The body part underneath already has the item's
 * main colour; the picture adds the design on a transparent background: collars, hems and stitching, pockets with
 * flaps, stripes, lapels and a tie, buckles, badges, camo, plaid, soft shading.
 * <p>
 * Layout, 48 pixels per stud, faces side by side as seen from outside: front | +X side | back | -X side. Torso:
 * 96 + 48 + 96 + 48 = 288 x 96. Leg: 4 x 48 = 192 x 96 (one picture per leg, so side designs go on the outer side).
 */
final class ClothingTextures {
	static final int P = 48;
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private ClothingTextures() {
	}

	static Identifier shirt(CosmeticDef c) {
		return CACHE.computeIfAbsent("shirt/" + c.id(), k -> upload(k, paintShirt(c)));
	}

	/** outerPlusX: this leg's outer side is its +X face. */
	static Identifier pants(CosmeticDef c, boolean outerPlusX) {
		return CACHE.computeIfAbsent("pants/" + c.id() + (outerPlusX ? "_px" : "_mx"), k -> upload(k, paintPants(c, outerPlusX)));
	}

	private static Identifier upload(String key, BufferedImage bi) {
		NativeImage img = new NativeImage(bi.getWidth(), bi.getHeight(), true);
		for (int y = 0; y < bi.getHeight(); y++)
			for (int x = 0; x < bi.getWidth(); x++) img.setPixel(x, y, bi.getRGB(x, y));
		Identifier id = MinebloxRoyale.id("clothes/" + key.replace('/', '_'));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "minebloxroyale " + key, img));
		return id;
	}

	// ---- shared painting helpers ----

	private static Graphics2D canvas(BufferedImage bi) {
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}

	private static Color col(int rgb, int alpha) {
		return new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
	}

	private static int shade(int rgb, float k) {
		int r = Math.min(255, (int) ((rgb >> 16 & 255) * k)), gg = Math.min(255, (int) ((rgb >> 8 & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
		return r << 16 | gg << 8 | b;
	}

	private static void fill(Graphics2D g, Shape s, int rgb, int alpha) {
		g.setColor(col(rgb, alpha));
		g.fill(s);
	}

	private static void line(Graphics2D g, Shape s, int rgb, int alpha, float width) {
		g.setColor(col(rgb, alpha));
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(s);
	}

	/** Dashed stitching along a shape. */
	private static void stitch(Graphics2D g, Shape s, int rgb, int alpha) {
		g.setColor(col(rgb, alpha));
		g.setStroke(new BasicStroke(1.1F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1F, new float[] { 2.5F, 2F }, 0));
		g.draw(s);
	}

	private static Shape round(double x, double y, double w, double h, double r) {
		return new RoundRectangle2D.Double(x, y, w, h, r, r);
	}

	private static Shape circle(double cx, double cy, double r) {
		return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
	}

	private static Shape star(double cx, double cy, double r, double inner) {
		GeneralPath p = new GeneralPath();
		for (int i = 0; i < 10; i++) {
			double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * inner;
			if (i == 0) p.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
			else p.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
		}
		p.closePath();
		return p;
	}

	private static Shape poly(double... xy) {
		GeneralPath p = new GeneralPath();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
		p.closePath();
		return p;
	}

	/** Soft shading over a face: a little darker at the bottom and towards its side edges (folds of fabric). */
	private static void shading(Graphics2D g, double x, double w, double h) {
		g.setPaint(new GradientPaint((float) x, 0, new Color(0, 0, 0, 0), (float) x, (float) h, new Color(0, 0, 0, 34)));
		g.fill(new Rectangle2D.Double(x, 0, w, h));
		g.setPaint(new GradientPaint((float) x, 0, new Color(0, 0, 0, 26), (float) (x + w * 0.18), 0, new Color(0, 0, 0, 0)));
		g.fill(new Rectangle2D.Double(x, 0, w * 0.18, h));
		g.setPaint(new GradientPaint((float) (x + w), 0, new Color(0, 0, 0, 26), (float) (x + w * 0.82), 0, new Color(0, 0, 0, 0)));
		g.fill(new Rectangle2D.Double(x + w * 0.82, 0, w * 0.18, h));
	}

	/** Random soft blobs (camo, rips), the same every time for an item. */
	private static void blobs(Graphics2D g, Random r, double x, double w, double h, int count, double size, int rgb, int alpha) {
		for (int i = 0; i < count; i++) {
			double bx = x + r.nextDouble() * w, by = r.nextDouble() * h, bw = size * (0.6 + r.nextDouble()), bh = size * (0.4 + r.nextDouble() * 0.6);
			Graphics2D gg = (Graphics2D) g.create();
			gg.rotate(r.nextDouble() * Math.PI, bx, by);
			fill(gg, new Ellipse2D.Double(bx - bw / 2, by - bh / 2, bw, bh), rgb, alpha);
			gg.dispose();
		}
	}

	// ---- shirts ----

	private static BufferedImage paintShirt(CosmeticDef c) {
		int W = 288, H = 96, F = 0, PX = 96, B = 144, MX = 240;
		BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas(bi);
		int base = c.color(), s = c.color2(), dark = shade(base, 0.68F), light = shade(base, 1.3F), gold = 0xD9B44A;
		double cx = F + 48, bcx = B + 48;
		Random rnd = new Random(c.id().hashCode());
		switch (c.shape()) {
			case "stripe" -> {
				for (double y : new double[] { 24, 46, 68 }) {
					fill(g, new Rectangle2D.Double(0, y, W, 12), s, 255);
					line(g, new Line2D.Double(0, y + 1.5, W, y + 1.5), 0xFFFFFF, 50, 1F);
				}
			}
			case "logo" -> {
				fill(g, circle(cx, 40, 17), s, 255);
				line(g, circle(cx, 40, 17), shade(s, 0.7F), 255, 2F);
				fill(g, star(cx, 41, 11, 0.45), 0xFFFFFF, 240);
				fill(g, circle(bcx, 34, 10), s, 230);
				fill(g, star(bcx, 35, 6.5, 0.45), 0xFFFFFF, 220);
			}
			case "suit" -> {
				fill(g, poly(cx - 15, 0, cx + 15, 0, cx, 50), 0xF4F4F4, 255); // shirt
				fill(g, poly(cx - 4, 4, cx + 4, 4, cx + 2.5, 10, cx - 2.5, 10), 0xB01828, 255); // knot
				fill(g, poly(cx - 2.5, 10, cx + 2.5, 10, cx + 5, 44, cx, 50, cx - 5, 44), 0xC0182A, 255); // tie
				line(g, new Line2D.Double(cx - 1, 14, cx + 2, 40), 0xFFFFFF, 50, 1.2F);
				fill(g, poly(cx - 15, 0, cx - 24, 0, cx - 22, 18, cx - 4, 56, cx, 50), shade(s, 0.85F), 255); // lapels
				fill(g, poly(cx + 15, 0, cx + 24, 0, cx + 22, 18, cx + 4, 56, cx, 50), shade(s, 0.85F), 255);
				line(g, poly(cx - 15, 0, cx - 24, 0, cx - 22, 18, cx - 4, 56, cx, 50), 0x000000, 70, 1F);
				line(g, poly(cx + 15, 0, cx + 24, 0, cx + 22, 18, cx + 4, 56, cx, 50), 0x000000, 70, 1F);
				for (double y : new double[] { 62, 76 }) fill(g, circle(cx, y, 2.6), 0x101014, 255);
				fill(g, round(cx + 18, 26, 14, 4, 2), 0xF4F4F4, 230); // pocket square
				line(g, new Line2D.Double(bcx, 60, bcx, 96), 0x000000, 60, 1.2F); // back vent
			}
			case "overalls" -> {
				Shape bib = round(cx - 24, 38, 48, 70, 10);
				fill(g, bib, s, 255);
				stitch(g, round(cx - 21, 41, 42, 70, 8), shade(s, 1.4F), 200);
				fill(g, new Rectangle2D.Double(0, 72, W, 24), s, 255);
				fill(g, round(cx - 9, 46, 18, 12, 4), shade(s, 0.8F), 255); // bib pocket
				for (double x : new double[] { cx - 19, cx + 13 }) fill(g, new Rectangle2D.Double(x, 0, 6, 42), s, 255);
				for (double x : new double[] { cx - 16, cx + 16 }) {
					fill(g, circle(x, 42, 3.4), gold, 255);
					line(g, circle(x, 42, 3.4), 0x6B4E12, 255, 1F);
				}
				// Straps crossing on the back.
				g.setStroke(new BasicStroke(6F));
				g.setColor(col(s, 255));
				g.draw(new Line2D.Double(bcx - 30, 0, bcx + 22, 74));
				g.draw(new Line2D.Double(bcx + 30, 0, bcx - 22, 74));
			}
			case "vest" -> {
				fill(g, poly(F, 0, cx - 10, 0, cx - 3, 44, cx - 6, 96, F, 96), s, 255);
				fill(g, poly(F + 96, 0, cx + 10, 0, cx + 3, 44, cx + 6, 96, F + 96, 96), s, 255);
				fill(g, new Rectangle2D.Double(PX, 0, 48, 96), s, 255);
				fill(g, new Rectangle2D.Double(B, 0, 96, 96), s, 255);
				fill(g, new Rectangle2D.Double(MX, 0, 48, 96), s, 255);
				for (double y : new double[] { 52, 64, 76, 88 }) fill(g, circle(cx - 8, y, 2.4), shade(s, 0.55F), 255);
				stitch(g, poly(cx - 10, 0, cx - 3, 44, cx - 6, 96), shade(s, 1.4F), 200);
				stitch(g, poly(cx + 10, 0, cx + 3, 44, cx + 6, 96), shade(s, 1.4F), 200);
			}
			case "belt" -> {
				fill(g, new Rectangle2D.Double(0, 66, W, 10), s, 255);
				line(g, new Line2D.Double(0, 67.5, W, 67.5), 0xFFFFFF, 40, 1F);
				fill(g, round(cx - 8, 63, 16, 16, 4), gold, 255);
				fill(g, round(cx - 4.5, 66.5, 9, 9, 2), s, 255);
				line(g, round(cx - 8, 63, 16, 16, 4), 0x6B4E12, 255, 1.2F);
			}
			case "badge" -> {
				fill(g, star(cx + 20, 34, 10, 0.5), gold, 255);
				line(g, star(cx + 20, 34, 10, 0.5), 0x6B4E12, 255, 1.2F);
				fill(g, circle(cx + 20, 34, 3), 0x6B4E12, 200);
				fill(g, round(cx - 32, 28, 22, 7, 3), 0xF4F4F4, 240);
				for (double x : new double[] { PX + 24, MX + 24 }) {
					fill(g, round(x - 10, 8, 20, 22, 8), s, 255);
					line(g, round(x - 10, 8, 20, 22, 8), gold, 255, 1.4F);
				}
				stitch(g, round(cx - 30, 40, 18, 16, 4), light, 180); // shirt pocket
			}
			case "cross" -> {
				fill(g, round(cx - 6, 24, 12, 36, 4), s, 255);
				fill(g, round(cx - 18, 36, 36, 12, 4), s, 255);
				fill(g, round(bcx - 9, 18, 18, 54, 5), s, 255);
				fill(g, round(bcx - 27, 36, 54, 18, 5), s, 255);
			}
			case "buttons" -> {
				fill(g, new Rectangle2D.Double(cx - 4, 0, 8, 96), dark, 120);
				for (double y = 10; y < 90; y += 13) {
					fill(g, circle(cx, y, 2.8), s, 255);
					fill(g, circle(cx - 0.8, y - 0.8, 1), 0xFFFFFF, 120);
				}
			}
			case "torn" -> {
				for (int i = 0; i < 6; i++) {
					double x = rnd.nextDouble() * W, y = 14 + rnd.nextDouble() * 70;
					GeneralPath p = new GeneralPath();
					p.moveTo(x, y);
					for (int k = 1; k <= 6; k++) p.lineTo(x + k * 3.5, y + (k % 2 == 0 ? -1 : 1) * (2 + rnd.nextDouble() * 3));
					for (int k = 6; k >= 0; k--) p.lineTo(x + k * 3.5, y + 5 + (k % 2 == 0 ? 1 : -1) * (1 + rnd.nextDouble() * 2));
					p.closePath();
					fill(g, p, shade(base, 0.35F), 230);
					line(g, p, light, 160, 1F);
				}
			}
			case "camo" -> {
				blobs(g, rnd, 0, W, H, 34, 22, s, 230);
				blobs(g, rnd, 0, W, H, 26, 16, dark, 210);
				blobs(g, rnd, 0, W, H, 18, 12, shade(s, 1.35F), 170);
			}
			case "dots" -> {
				for (int row = 0; row * 14 < H + 14; row++)
					for (double x = (row % 2) * 8; x < W + 8; x += 16) fill(g, circle(x, row * 14 + 6, 3.8), s, 255);
			}
			case "sash" -> {
				for (double ox : new double[] { cx, bcx }) {
					double dir = ox == cx ? 1 : -1;
					Shape band = poly(ox - dir * 48, 0, ox - dir * 30, 0, ox + dir * 48, 78, ox + dir * 48, 96);
					fill(g, band, s, 255);
					line(g, poly(ox - dir * 48, 0, ox + dir * 48, 96), gold, 255, 1.6F);
					line(g, poly(ox - dir * 30, 0, ox + dir * 48, 78), gold, 255, 1.6F);
				}
				fill(g, star(cx - 14, 30, 6, 0.45), gold, 255); // medal
			}
			case "pockets" -> {
				for (double x : new double[] { cx - 30, cx + 10 }) {
					fill(g, round(x, 30, 20, 20, 5), dark, 110);
					stitch(g, round(x + 1.5, 31.5, 17, 17, 4), light, 200);
					fill(g, poly(x - 1, 27, x + 21, 27, x + 21, 34, x + 10, 37, x - 1, 34), dark, 230);
					fill(g, circle(x + 10, 34, 1.8), s, 255);
				}
			}
			default -> { // plain: a chest pocket
				fill(g, round(cx + 10, 32, 18, 18, 5), dark, 90);
				stitch(g, round(cx + 11.5, 33.5, 15, 15, 4), light, 190);
			}
		}
		// Every shirt: a rounded collar, side seams, a stitched hem and soft shading.
		boolean openCollar = c.shape().equals("suit") || c.shape().equals("vest");
		if (!openCollar) {
			fill(g, new Ellipse2D.Double(cx - 18, -12, 36, 22), dark, 255);
			fill(g, new Ellipse2D.Double(cx - 14, -12, 28, 17), shade(base, 0.45F), 255);
			line(g, new Ellipse2D.Double(cx - 18, -12, 36, 22), light, 120, 1F);
		}
		fill(g, new Ellipse2D.Double(bcx - 16, -10, 32, 14), dark, 255);
		for (double x : new double[] { PX + 24, MX + 24 }) stitch(g, new Line2D.Double(x, 4, x, 88), dark, 170);
		fill(g, new Rectangle2D.Double(0, 89, W, 7), dark, 255);
		stitch(g, new Line2D.Double(0, 87, W, 87), dark, 200);
		for (double[] face : new double[][] { { F, 96 }, { PX, 48 }, { B, 96 }, { MX, 48 } }) shading(g, face[0], face[1], H);
		g.dispose();
		return bi;
	}

	// ---- pants (one leg) ----

	private static BufferedImage paintPants(CosmeticDef c, boolean outerPlusX) {
		int W = 192, H = 96, F = 0, PX = 48, B = 96, MX = 144;
		int OUT = outerPlusX ? PX : MX, IN = outerPlusX ? MX : PX;
		BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas(bi);
		int base = c.color(), s = c.color2(), dark = shade(base, 0.66F), light = shade(base, 1.35F), gold = 0xD9B44A;
		Random rnd = new Random(c.id().hashCode() + (outerPlusX ? 1 : 0));
		// The front face's edge on the outer side (front faces read +X on the left as seen from outside).
		double frontOuter = outerPlusX ? F + 2 : F + 46, frontInner = outerPlusX ? F + 46 : F + 2;
		switch (c.shape()) {
			case "jeans" -> {
				g.setPaint(new GradientPaint(F + 24, 30, new Color(255, 255, 255, 46), F + 24, 70, new Color(255, 255, 255, 0)));
				g.fill(new Rectangle2D.Double(F + 8, 14, 32, 60)); // worn, lighter thighs
				GeneralPath pocket = new GeneralPath();
				pocket.moveTo(frontOuter, 22);
				pocket.quadTo((frontOuter + frontInner) / 2, 20, frontOuter + (frontInner - frontOuter) * 0.5, 7);
				stitch(g, pocket, gold, 230);
				stitch(g, new Line2D.Double(OUT + 24, 8, OUT + 24, 88), gold, 230);
				stitch(g, new Line2D.Double(IN + 24, 8, IN + 24, 88), gold, 160);
				Shape back = poly(B + 12, 14, B + 36, 14, B + 35, 32, B + 24, 37, B + 13, 32);
				fill(g, back, dark, 70);
				stitch(g, back, gold, 230);
				for (int k = 0; k < 3; k++) line(g, new Line2D.Double(F + 14, 58 + k * 3, F + 34, 56 + k * 3), 0xFFFFFF, 40, 1F); // knee creases
			}
			case "stripe" -> {
				for (double x : new double[] { OUT + 15, OUT + 27 }) fill(g, new Rectangle2D.Double(x, 6, 6, 90), s, 255);
			}
			case "cargo" -> {
				Shape pocket = round(OUT + 8, 36, 32, 26, 6);
				fill(g, pocket, dark, 120);
				stitch(g, round(OUT + 10, 38, 28, 22, 5), light, 200);
				fill(g, poly(OUT + 6, 31, OUT + 42, 31, OUT + 42, 39, OUT + 24, 42, OUT + 6, 39), s, 255);
				fill(g, circle(OUT + 24, 39, 2), shade(s, 0.6F), 255);
			}
			case "plaid" -> {
				for (double y = 4; y < H; y += 22) fill(g, new Rectangle2D.Double(0, y, W, 8), s, 115);
				for (double x = 4; x < W; x += 22) fill(g, new Rectangle2D.Double(x, 0, 8, H), s, 115);
				for (double y = 15; y < H; y += 22) fill(g, new Rectangle2D.Double(0, y, W, 1.2), 0x000000, 70);
				for (double x = 15; x < W; x += 22) fill(g, new Rectangle2D.Double(x, 0, 1.2, H), 0x000000, 70);
			}
			case "belted" -> {
				fill(g, new Rectangle2D.Double(0, 6, W, 10), s, 255);
				line(g, new Line2D.Double(0, 7.5, W, 7.5), 0xFFFFFF, 40, 1F);
				double bx = frontInner + (outerPlusX ? -14 : 0);
				fill(g, round(bx, 4, 14, 14, 4), gold, 255);
				fill(g, round(bx + 3.5, 7.5, 7, 7, 2), s, 255);
			}
			case "knee" -> {
				Shape patch = round(F + 10, 50, 28, 22, 9);
				fill(g, patch, s, 255);
				stitch(g, round(F + 12, 52, 24, 18, 7), shade(s, 1.5F), 220);
			}
			case "pinstripe" -> {
				for (double x = 4; x < W; x += 8) fill(g, new Rectangle2D.Double(x, 6, 1.2, 90), s, 200);
			}
			case "bands" -> {
				for (double y : new double[] { 62, 76 }) {
					fill(g, new Rectangle2D.Double(0, y, W, 7), s, 255);
					line(g, new Line2D.Double(0, y + 1, W, y + 1), 0xFFFFFF, 50, 1F);
				}
			}
			case "camo" -> {
				blobs(g, rnd, 0, W, H, 22, 20, s, 230);
				blobs(g, rnd, 0, W, H, 16, 14, dark, 210);
				blobs(g, rnd, 0, W, H, 10, 10, shade(s, 1.35F), 170);
			}
			default -> {
			}
		}
		// Every pair: a waistband with belt loops, an inner seam, a stitched hem and soft shading.
		if (!c.shape().equals("plain")) {
			fill(g, new Rectangle2D.Double(0, 0, W, 6), dark, 255);
			for (double x : new double[] { F + 10, F + 38, B + 10, B + 38, OUT + 24 }) fill(g, round(x - 1.5, 0, 3, 9, 1.5), dark, 255);
		}
		stitch(g, new Line2D.Double(IN + 24, 10, IN + 24, 88), dark, 170);
		fill(g, new Rectangle2D.Double(0, 90, W, 6), dark, 230);
		stitch(g, new Line2D.Double(0, 88.5, W, 88.5), dark, 190);
		for (double face : new double[] { F, PX, B, MX }) shading(g, face, 48, H);
		g.dispose();
		return bi;
	}
}
