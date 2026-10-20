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
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

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
		// the map square on screen (GUI coordinates, as enableScissor makes them), for what's drawn in one piece
		ScreenRectangle box = new ScreenRectangle(x0, y0, size, size).transformMaxBounds(g.pose());
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
			// The storm's purple, its black edge and the next circle (creator request), smooth, as one piece (2.0.0).
			Smooth.Batch b = new Smooth.Batch(RenderPipelines.GUI, TextureSetup.noTexture()).clip(box);
			stormInto(b, S, (st[0] - px) * k + half, (st[1] - pz) * k + half, st[2] * k, 1.6 * gs / 3.0 * 2,
				(hud.nextX() - px) * k + half, (hud.nextZ() - pz) * k + half, hud.nextR() * k, 1.2 * gs / 3.0 * 2);
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
						if (qx >= 1 && qy >= 1 && qx < S - 1 && qy < S - 1) b.quad(qx - 1, qy - 1, qx + 1, qy + 1, 0xFF000000, 0xFF000000);
					}
				}
			}
			b.submit(g);
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

	private static final int STORM_PURPLE = 0x9A6A1FB8;

	/**
	 * The storm on a map (the minimap and the big map, 2.0.0; creator: "as the storm gets closer in, my frames drop, so
	 * do my friends"): the purple outside the circle, its black edge and the next circle, each a ring of thin pieces
	 * round the circle with soft edges, all into one batch. Before, the rings were drawn a pixel at a time: once the
	 * circles fit on the map, as the storm closes in, that was thousands of separate pieces every frame. In screen
	 * pixels: S the map's size, (sx, sz, sr) the storm, (nx, nz, nr) the next circle, edge and nextEdge their rings'
	 * widths.
	 */
	static void stormInto(Smooth.Batch b, int S, double sx, double sz, double sr, double edge, double nx, double nz, double nr, double nextEdge) {
		// the purple, from the storm's edge out past the map's farthest corner
		double far = Math.hypot(Math.max(Math.abs(sx), Math.abs(sx - S)), Math.max(Math.abs(sz), Math.abs(sz - S))) + 4;
		if (far > sr) band(b, S, sx, sz, Math.max(0, sr), far, STORM_PURPLE, STORM_PURPLE);
		ringBand(b, S, sx, sz, sr, 0x000000, edge);
		ringBand(b, S, nx, nz, nr, 0x000000, nextEdge);
	}

	/** A ring of width w: solid in the middle, fading out over its last pixel each side (as the old pixel rings). */
	private static void ringBand(Smooth.Batch b, int S, double cx, double cy, double r, int rgb, double w) {
		if (r <= 1) return;
		double hw = w / 2, core = Math.max(0, hw - 0.5);
		int solid = 0xFF000000 | rgb, clear = rgb & 0xFFFFFF;
		band(b, S, cx, cy, Math.max(0, r - hw - 0.5), r - core, clear, solid);
		if (core > 0) band(b, S, cx, cy, r - core, r + core, solid, solid);
		band(b, S, cx, cy, r + core, r + hw + 0.5, solid, clear);
	}

	/**
	 * The band between radii r0 and r1 round (cx, cy), shading from c0 to c1: enough pieces that the inner edge looks
	 * round (under a tenth of a pixel off), only those over the map square (S, a little margin).
	 */
	private static void band(Smooth.Batch b, int S, double cx, double cy, double r0, double r1, int c0, int c1) {
		int n = (int) Math.max(48, Math.min(1024, Math.ceil(2 * Math.PI * Math.max(r0, r1 * 0.25) / 3)));
		double step = 2 * Math.PI / n, c = 1, s = 0;
		for (int i = 0; i < n; i++) {
			double cn = Math.cos((i + 1) * step), sn = Math.sin((i + 1) * step);
			float ax = (float) (cx + c * r0), ay = (float) (cy + s * r0), bx = (float) (cx + c * r1), by = (float) (cy + s * r1);
			float dx = (float) (cx + cn * r1), dy = (float) (cy + sn * r1), ex = (float) (cx + cn * r0), ey = (float) (cy + sn * r0);
			c = cn;
			s = sn;
			if (Math.max(Math.max(ax, bx), Math.max(dx, ex)) < -2 || Math.min(Math.min(ax, bx), Math.min(dx, ex)) > S + 2
				|| Math.max(Math.max(ay, by), Math.max(dy, ey)) < -2 || Math.min(Math.min(ay, by), Math.min(dy, ey)) > S + 2) continue;
			b.quad(ax, ay, c0, bx, by, c1, dx, dy, c1, ex, ey, c0);
		}
	}

	/**
	 * A ring in screen pixels (waypoints): soft-edged, kept inside the map square (S), as one piece (2.0.0; it used to be
	 * drawn a pixel at a time, each pixel a separate piece for Minecraft to place among all the others).
	 */
	static void ring(GuiGraphicsExtractor g, int S, double cx, double cy, double r, int rgb, double width) {
		if (r <= 1) return;
		Smooth.Batch b = new Smooth.Batch(RenderPipelines.GUI, TextureSetup.noTexture()).clip(new ScreenRectangle(0, 0, S, S).transformMaxBounds(g.pose()));
		ringBand(b, S, cx, cy, r, rgb, width);
		b.submit(g);
	}

	/** Arrow pixels (dx, dy, alpha, outline) for each of 128 headings and each GUI scale, worked out once. */
	private static final Map<Integer, int[]> ARROWS = new HashMap<>();

	/** Anti-aliased arrowhead (4x4 samples per screen pixel); a slightly bigger copy underneath is the dark outline. */
	static void arrow(GuiGraphicsExtractor g, double cx, double cy, double fx, double fz, int gs) {
		arrow(g, cx, cy, fx, fz, gs, 0xFFFFD21F);
	}

	static void arrow(GuiGraphicsExtractor g, double cx, double cy, double fx, double fz, int gs, int color) {
		arrow(g, cx, cy, fx, fz, gs, color, null);
	}

	/** (clip: kept inside that box, GUI coordinates; its pixels go as one piece, 2.0.0) */
	static void arrow(GuiGraphicsExtractor g, double cx, double cy, double fx, double fz, int gs, int color, @Nullable ScreenRectangle clip) {
		int bucket = Math.floorMod((int) Math.round(Math.atan2(fz, fx) / (2 * Math.PI) * 128), 128);
		int[] px = ARROWS.computeIfAbsent(gs * 1000 + bucket, key -> arrowPixels(bucket, gs));
		int x0 = (int) Math.floor(cx), y0 = (int) Math.floor(cy), rgb = color & 0xFFFFFF;
		Smooth.Batch b = new Smooth.Batch(RenderPipelines.GUI, TextureSetup.noTexture()).clip(clip);
		for (int i = 0; i < px.length; i += 4) {
			int c = (px[i + 2] << 24) | (px[i + 3] == 1 ? 0x101010 : rgb);
			b.quad(x0 + px[i], y0 + px[i + 1], x0 + px[i] + 1, y0 + px[i + 1] + 1, c, c);
		}
		b.submit(g);
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
