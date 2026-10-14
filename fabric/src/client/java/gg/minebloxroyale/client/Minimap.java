package gg.minebloxroyale.client;

import com.mojang.blaze3d.platform.NativeImage;
import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Fortnite-style minimap (hud sheet "minimap"): framed live terrain (north up) sampled from loaded chunks, the storm
 * filled purple with a black edge, the next safe circle as a black ring, a dashed path to safety and the player's
 * arrow, all smooth (screen pixels). Direction is shown by the compass bar at the top of the screen (BRHud).
 */
public final class Minimap {
	/** The sampled terrain picture: bigger than the view, so it can scroll smoothly under you between samples. */
	static final int RES = 96;
	static final int VIEW = 64; // pixels shown across the minimap
	static final int BLOCKS_PER_PIXEL = 5; // 320 blocks across: the whole island fits
	private static final Identifier TEX_ID = MinebloxRoyale.id("minimap");
	private static DynamicTexture texture;
	/** World position of the shown picture's top-left corner, and of the one being sampled. */
	private static int originX, originZ, nextX, nextZ;
	private static int sampleRow = -1;
	private static long lastDone;
	private static final int[] pending = new int[RES * RES], heights = new int[RES * RES];

	private Minimap() {
	}

	/**
	 * A few rows of terrain per frame (never one big pause): a new picture is started when you've moved 30 blocks
	 * from the centre of the current one, or every 2 seconds, and swapped in once complete.
	 */
	static void sample(Minecraft mc) {
		Level level = mc.level;
		if (level == null || mc.player == null) return;
		if (texture == null) {
			texture = new DynamicTexture(() -> "minebloxroyale minimap", RES, RES, true);
			mc.getTextureManager().register(TEX_ID, texture);
			sampleRow = -1;
		}
		if (sampleRow < 0) {
			double cx = originX + RES * BLOCKS_PER_PIXEL / 2.0, cz = originZ + RES * BLOCKS_PER_PIXEL / 2.0;
			boolean far = Math.abs(mc.player.getX() - cx) > 30 || Math.abs(mc.player.getZ() - cz) > 30;
			if (!far && System.currentTimeMillis() - lastDone < 2000) return;
			nextX = mc.player.getBlockX() - RES * BLOCKS_PER_PIXEL / 2;
			nextZ = mc.player.getBlockZ() - RES * BLOCKS_PER_PIXEL / 2;
			sampleRow = 0;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int n = 0; n < 12 && sampleRow < RES; n++, sampleRow++) {
			int pz = sampleRow;
			for (int px = 0; px < RES; px++) {
				int x = nextX + px * BLOCKS_PER_PIXEL, z = nextZ + pz * BLOCKS_PER_PIXEL;
				int argb = 0xFF2A3A55; // unexplored: Fortnite-ish deep blue
				int y = Integer.MIN_VALUE;
				if (level.hasChunk(x >> 4, z >> 4)) {
					y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					MapColor col = state.getMapColor(level, pos);
					argb = 0xFF000000 | (col == MapColor.NONE ? 0x2A3A55 : col.col);
				}
				heights[pz * RES + px] = y;
				pending[pz * RES + px] = argb;
			}
		}
		if (sampleRow < RES) return;
		// Complete: hill shading from the height difference to the north-west neighbour (like a real map), then it's
		// swapped in.
		NativeImage img = texture.getPixels();
		for (int pz = 0; pz < RES; pz++)
			for (int px = 0; px < RES; px++) {
				int c = pending[pz * RES + px];
				if (px > 0 && pz > 0) {
					int h = heights[pz * RES + px], hn = heights[(pz - 1) * RES + px - 1];
					if (h != Integer.MIN_VALUE && hn != Integer.MIN_VALUE) {
						int d = Math.max(-3, Math.min(3, h - hn)) * 14;
						c = 0xFF000000 | clamp(((c >> 16) & 255) + d) << 16 | clamp(((c >> 8) & 255) + d) << 8 | clamp((c & 255) + d);
					}
				}
				img.setPixel(px, pz, c);
			}
		texture.upload();
		originX = nextX;
		originZ = nextZ;
		sampleRow = -1;
		lastDone = System.currentTimeMillis();
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	static void draw(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.player == null) return;
		sample(mc);
		HudElementDef def = Sheets.HUD_BY_ID.get("minimap");
		int size = def.size();
		int x0 = g.guiWidth() + def.x() - size, y0 = def.y();
		// Frame: dark border with a light inner line.
		g.fill(x0 - 3, y0 - 3, x0 + size + 3, y0 + size + 3, 0xE0101420);
		g.fill(x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, 0xFFB8C4D6);
		// The view is centred on exactly where you are (not where the last sample was taken), so it scrolls smoothly.
		double px = mc.player.getX(), pz = mc.player.getZ();
		float u = (float) ((px - originX) / BLOCKS_PER_PIXEL - VIEW / 2.0), v = (float) ((pz - originZ) / BLOCKS_PER_PIXEL - VIEW / 2.0);
		g.enableScissor(x0, y0, x0 + size, y0 + size);
		g.blit(RenderPipelines.GUI_TEXTURED, TEX_ID, x0, y0, u, v, size, size, VIEW, VIEW, RES, RES);
		g.disableScissor();
		float scale = size / (float) (VIEW * BLOCKS_PER_PIXEL);
		var hud = ClientState.hud;
		boolean storm = hud.phase().equals("LIVE") || hud.phase().equals("ENDED");
		// Everything on top of the terrain is drawn in real screen pixels, anti-aliased, so it is smooth instead of
		// blocky.
		int gs = Math.round(mc.getWindow().getGuiScale());
		int S = size * gs;
		float k = scale * gs; // screen pixels per block
		double half = S / 2.0;
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale(1F / gs, 1F / gs);
		if (storm) {
			double[] st = ClientState.storm();
			double sx = (st[0] - px) * k + half, sz = (st[1] - pz) * k + half, sr = st[2] * k;
			stormFillFast(g, S, sx, sz, sr, Math.max(1, gs / 2));
			// Black storm edge and next circle (creator request), smooth.
			ring(g, S, sx, sz, sr, 0x000000, 1.6 * gs / 3.0 * 2);
			ring(g, S, (hud.nextX() - px) * k + half, (hud.nextZ() - pz) * k + half, hud.nextR() * k, 0x000000, 1.2 * gs / 3.0 * 2);
			// Dashed black path to the safe zone while you're outside the next circle.
			double pdx = px - hud.nextX(), pdz = pz - hud.nextZ();
			double pd = Math.sqrt(pdx * pdx + pdz * pdz);
			if (pd > hud.nextR() && hud.nextR() > 0.5) {
				double ex = hud.nextX() + pdx / pd * hud.nextR(), ez = hud.nextZ() + pdz / pd * hud.nextR();
				double bx = (ex - px) * k + half, bz = (ez - pz) * k + half;
				double len = Math.hypot(bx - half, bz - half), dash = 3.0 * gs, gap = 2.0 * gs;
				for (double t = 0; t < len; t += dash + gap) {
					double t1 = Math.min(len, t + dash);
					for (double q = t; q < t1; q += 1.5) {
						int qx = (int) Math.round(half + (bx - half) * q / len), qy = (int) Math.round(half + (bz - half) * q / len);
						if (qx >= 1 && qy >= 1 && qx < S - 1 && qy < S - 1) g.fill(qx - 1, qy - 1, qx + 1, qy + 1, 0xFF000000);
					}
				}
			}
		}
		// Team modes: reboot vans, your team's cards, teammates' waypoints and teammates.
		teamMarks(g, S, k, px - half / k, pz - half / k, gs);
		// Player arrow: smooth arrowhead with a dark outline, pointing where you face (yellow; your teammate colour in
		// a team mode, as your teammates see you).
		double yaw = Math.toRadians(mc.player.getYRot());
		arrow(g, half, half, -Math.sin(yaw), Math.cos(yaw), gs, ClientState.myColor());
		g.pose().popMatrix();
	}

	/**
	 * Team-mode marks on a map drawn in screen pixels: S = its size, k = pixels per block, (ox, oz) = the world point
	 * at pixel 0,0. Reboot vans (a little blue van, greyer while recharging; only while a teammate's card is out to be
	 * rebooted, creator: the green box blended into the grass), your team's reboot cards on the ground, teammates'
	 * waypoints (rings in their colours) and teammates (dots in their colours; knocked ones blink red).
	 */
	static void teamMarks(GuiGraphicsExtractor g, int S, double k, double ox, double oz, int gs) {
		if (!ClientState.teamMatch()) return;
		var sq = ClientState.squad;
		double u = Math.max(1, gs / 3.0 * 2); // about 2 screen pixels per GUI pixel at GUI scale 3
		// A teammate's card is out (on the ground or carried): only then do the vans matter.
		boolean cardOut = !sq.cards().isEmpty() || sq.mates().stream().anyMatch(m -> m.status() == 2);
		if (cardOut) for (var v : sq.vans()) {
			int x = (int) Math.round((v.x() - ox) * k), y = (int) Math.round((v.z() - oz) * k);
			int hw = (int) Math.round(4.5 * u), hh = (int) Math.round(2.6 * u), o = Math.max(1, (int) Math.round(u * 0.6)), wr = Math.max(1, (int) Math.round(u * 1.1));
			if (x < -hw || y < -hh || x > S + hw || y > S + hh) continue;
			int body = v.readyIn() == 0 ? 0xFF2F7BFF : 0xFF5C6F8F, glass = v.readyIn() == 0 ? 0xFFBFE3FF : 0xFFA9B4C4;
			// Side view: dark outline, blue body, a row of windows, the windscreen at the front, two wheels.
			g.fill(x - hw - o, y - hh - o, x + hw + o, y + hh - wr + o, 0xFF101010);
			g.fill(x - hw, y - hh, x + hw, y + hh - wr, body);
			g.fill(x - hw + o, y - hh + o, x + hw - 2 * wr - o, y - hh + o + wr, glass);
			g.fill(x + hw - 2 * wr, y - hh + o, x + hw - o, y, glass);
			for (int wx : new int[] { x - hw + 2 * wr, x + hw - 2 * wr }) {
				g.fill(wx - wr - o, y + hh - 2 * wr, wx + wr + o, y + hh + o, 0xFF101010);
				g.fill(wx - wr + o, y + hh - 2 * wr + o, wx + wr - o, y + hh - o, 0xFF8A8F98);
			}
		}
		for (var c : sq.cards()) {
			int x = (int) Math.round((c.x() - ox) * k), y = (int) Math.round((c.z() - oz) * k);
			int hw = (int) Math.round(2 * u), hh = (int) Math.round(3 * u), o = Math.max(1, (int) Math.round(u * 0.6));
			if (x < 0 || y < 0 || x > S || y > S) continue;
			g.fill(x - hw - o, y - hh - o, x + hw + o, y + hh + o, Waypoint.CARD); // the cards' cyan-blue
			g.fill(x - hw, y - hh, x + hw, y + hh, 0xFFF4F8FF);
		}
		for (var m : sq.mates())
			if (m.waypoint()) ring(g, S, (m.wx() - ox) * k, (m.wz() - oz) * k, 4.0 * u, ClientState.teamColor(m.slot()) & 0xFFFFFF, 2.4 * u);
		boolean blink = System.currentTimeMillis() / 300 % 2 == 0;
		for (var m : sq.mates()) {
			if (m.status() >= 2) continue;
			double x = (m.x() - ox) * k, y = (m.z() - oz) * k;
			if (x < 0 || y < 0 || x > S || y > S) continue;
			boolean down = m.status() == 1;
			disc(g, x, y, 3.2 * u, down && blink ? 0xFFE03A3A : 0xFF101010);
			disc(g, x, y, 2.2 * u, ClientState.teamColor(m.slot()));
		}
	}

	/** A filled circle in screen pixels. */
	static void disc(GuiGraphicsExtractor g, double cx, double cy, double r, int color) {
		for (int y = (int) Math.floor(cy - r); y <= (int) Math.ceil(cy + r); y++) {
			double dy = y + 0.5 - cy;
			if (Math.abs(dy) > r) continue;
			double hw = Math.sqrt(r * r - dy * dy);
			g.fill((int) Math.round(cx - hw), y, (int) Math.round(cx + hw), y + 1, color);
		}
	}

	/**
	 * Fast storm area for the big map: purple outside the circle in bands a few pixels tall (the ring drawn on top
	 * hides the steps), and whole rectangles above and below the circle, so a few hundred pieces instead of thousands.
	 */
	static void stormFillFast(GuiGraphicsExtractor g, int S, double sx, double sz, double sr, int band) {
		int top = (int) Math.max(0, Math.min(S, Math.floor(sz - sr))), bottom = (int) Math.max(0, Math.min(S, Math.ceil(sz + sr)));
		if (top > 0) g.fill(0, 0, S, top, 0x9A6A1FB8);
		if (bottom < S) g.fill(0, bottom, S, S, 0x9A6A1FB8);
		for (int y = top; y < bottom; y += band) {
			int y2 = Math.min(bottom, y + band);
			double dy = (y + y2) / 2.0 - sz;
			double hw = Math.abs(dy) >= sr ? 0 : Math.sqrt(sr * sr - dy * dy);
			int a = (int) Math.max(0, Math.min(S, Math.round(sx - hw))), b = (int) Math.max(0, Math.min(S, Math.round(sx + hw)));
			if (a > 0) g.fill(0, y, a, y2, 0x9A6A1FB8);
			if (b < S) g.fill(b, y, S, y2, 0x9A6A1FB8);
		}
	}

	/** Fast ring for the big map: short rotated bars round the circle (a few hundred pieces, whatever the size). */
	static void ringFast(GuiGraphicsExtractor g, int S, double cx, double cy, double r, int rgb, double width) {
		if (r <= 1) return;
		int n = (int) Math.max(24, Math.min(720, 2 * Math.PI * r / 5));
		double step = 2 * Math.PI / n, len = 2 * Math.PI * r / n + 1.5;
		float w = (float) (width / 2);
		for (int i = 0; i < n; i++) {
			double a = (i + 0.5) * step, x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
			if (x < -len || y < -len || x > S + len || y > S + len) continue; // off the map square
			g.pose().pushMatrix();
			g.pose().translate((float) x, (float) y);
			g.pose().rotate((float) (a + Math.PI / 2));
			int hl = (int) Math.round(len / 2), hw2 = Math.max(1, Math.round(w));
			g.fill(-hl, -hw2, hl, hw2, 0xFF000000 | rgb);
			g.pose().popMatrix();
		}
	}

	/**
	 * Anti-aliased ring in screen pixels: every pixel's coverage of the band |distance - r| < width/2. Only the pixels
	 * near the circle are visited (two short spans per row), so even a big ring on the full map is cheap.
	 */
	static void ring(GuiGraphicsExtractor g, int S, double cx, double cy, double r, int rgb, double width) {
		if (r <= 1) return;
		double hw = width / 2, ro = r + hw + 1, ri = Math.max(0, r - hw - 1);
		int y0 = (int) Math.max(0, Math.floor(cy - ro)), y1 = (int) Math.min(S, Math.ceil(cy + ro));
		for (int y = y0; y < y1; y++) {
			double dy = y + 0.5 - cy;
			if (Math.abs(dy) > ro) continue;
			double xo = Math.sqrt(ro * ro - dy * dy), xi = Math.abs(dy) < ri ? Math.sqrt(ri * ri - dy * dy) : 0;
			span(g, S, (int) Math.floor(cx - xo), (int) Math.ceil(cx - xi), y, cx, cy, r, hw, rgb);
			span(g, S, (int) Math.floor(cx + xi), (int) Math.ceil(cx + xo), y, cx, cy, r, hw, rgb);
		}
	}

	private static void span(GuiGraphicsExtractor g, int S, int xa, int xb, int y, double cx, double cy, double r, double hw, int rgb) {
		for (int x = Math.max(0, xa); x < Math.min(S, xb + 1); x++) {
			double d = Math.abs(Math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r);
			double cover = Math.max(0, Math.min(1, hw + 0.5 - d));
			if (cover > 0.02) g.fill(x, y, x + 1, y + 1, ((int) (cover * 255) << 24) | rgb);
		}
	}

	/** Arrow pixels (dx, dy, alpha, outline) for each of 128 headings and each GUI scale, worked out once. */
	private static final Map<Integer, int[]> ARROWS = new HashMap<>();

	/** Anti-aliased arrowhead (4x4 samples per screen pixel); a slightly bigger copy underneath is the dark outline. */
	static void arrow(GuiGraphicsExtractor g, double cx, double cy, double fx, double fz, int gs) {
		arrow(g, cx, cy, fx, fz, gs, 0xFFFFD21F);
	}

	static void arrow(GuiGraphicsExtractor g, double cx, double cy, double fx, double fz, int gs, int color) {
		int bucket = Math.floorMod((int) Math.round(Math.atan2(fz, fx) / (2 * Math.PI) * 128), 128);
		int[] px = ARROWS.computeIfAbsent(gs * 1000 + bucket, key -> arrowPixels(bucket, gs));
		int x0 = (int) Math.floor(cx), y0 = (int) Math.floor(cy), rgb = color & 0xFFFFFF;
		for (int i = 0; i < px.length; i += 4) g.fill(x0 + px[i], y0 + px[i + 1], x0 + px[i] + 1, y0 + px[i + 1] + 1, (px[i + 2] << 24) | (px[i + 3] == 1 ? 0x101010 : rgb));
	}

	private static int[] arrowPixels(int bucket, int gs) {
		double a = bucket / 128.0 * 2 * Math.PI, fx = Math.cos(a), fz = Math.sin(a);
		double unit = gs / 3.0 * 2.2; // shape units -> screen pixels (about 15 px long at GUI scale 3)
		int reach = (int) Math.ceil(7 * unit) + 2;
		List<Integer> out = new ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			double grow = pass == 0 ? 1.5 / unit + 0.35 : 0;
			for (int dy = -reach; dy <= reach; dy++)
				for (int dx = -reach; dx <= reach; dx++) {
					int hits = 0;
					for (int sy = 0; sy < 4; sy++)
						for (int sx = 0; sx < 4; sx++) {
							double px = (dx + (sx + 0.5) / 4) / unit, py = (dy + (sy + 0.5) / 4) / unit;
							if (inArrow(px * fx + py * fz, -px * fz + py * fx, grow)) hits++;
						}
					if (hits == 0) continue;
					out.add(dx);
					out.add(dy);
					out.add(hits * 255 / 16);
					out.add(pass == 0 ? 1 : 0);
				}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Arrow space (u = forward, v = sideways): tip u=4, back corners u=-3 v=+-3, notch u=-1. */
	private static boolean inArrow(double u, double v, double grow) {
		double tip = 4 + grow * 1.6, back = -3 - grow, half = 3 + grow, notch = -1 - grow;
		if (u > tip || u < back) return false;
		if (Math.abs(v) > half * (tip - u) / (tip - back)) return false;
		return u >= notch + (back - notch) * Math.abs(v) / half;
	}
}
