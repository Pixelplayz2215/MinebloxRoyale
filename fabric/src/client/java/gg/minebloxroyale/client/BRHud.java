package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.LoadoutDef;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;

/** The Fortnite-style HUD from the hud sheet. */
public final class BRHud {
	private static int lastPhase = -1;
	private static boolean lastShrinking;
	private static long bannerAt;

	private BRHud() {
	}

	public static void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (Aim.scoped()) scope(g);
		if (ClientState.inMatch()) {
			if (ClientState.outsideStorm(mc.player.getX(), mc.player.getZ()) && !mc.player.isSpectator()) stormTint(g);
			Minimap.draw(g, mc);
			statsRow(g, mc);
			compass(g, mc);
			Waypoint.screenMarker(g, mc);
			SquadHud.panel(g, mc);
			if (!SquadHud.interact(g, mc)) ChestHud.draw(g, mc); // revive / reboot first, else a chest's "Hold F to search"
			DamageNumbers.draw(g, mc);
			BuildMode.hud(g, mc);
			scaled(g, "storm_banner", g.guiWidth() / 2F, 0, () -> stormBanner(g, mc));
			scaled(g, "ammo", g.guiWidth(), g.guiHeight(), () -> ammo(g, mc));
			reloadingText(g, mc);
			scaled(g, "materials", g.guiWidth(), g.guiHeight(), () -> materials(g, mc));
			scaled(g, "bus_hint", g.guiWidth() / 2F, g.guiHeight(), () -> busHint(g, mc));
		} else {
			scaled(g, "hub_hint", g.guiWidth() / 2F, 0, () -> hubHint(g, mc));
		}
		if (!mc.player.isSpectator()) scaled(g, "health_shield", 0, g.guiHeight(), () -> healthShield(g, mc));
	}

	/** Draws a panel at its hud-sheet scale, shrunk towards the given anchor point. */
	private static void scaled(GuiGraphicsExtractor g, String id, float ax, float ay, Runnable draw) {
		float s = Sheets.HUD_BY_ID.get(id).scale();
		g.pose().pushMatrix();
		g.pose().translate(ax, ay);
		g.pose().scale(s, s);
		g.pose().translate(-ax, -ay);
		draw.run();
		g.pose().popMatrix();
	}

	/**
	 * Minecraft's hotbar at the hud sheet's "hotbar" scale (bottom centre), with the locked Fortnite slots greyed out.
	 * Hidden while looking through a sniper scope (creator), like the gun in your hand.
	 */
	public static void hotbar(GuiGraphicsExtractor g, Runnable vanilla) {
		if (Aim.scoped()) return;
		scaled(g, "hotbar", g.guiWidth() / 2F, g.guiHeight(), () -> {
			vanilla.run();
			if (ClientState.inMatch()) lockedSlots(g);
		});
	}

	/**
	 * Sniper scope (creator's pick of four mockups, "A: big lens, tinted sides"; the old one blacked out too much): a
	 * lens as tall as the screen with a dark rim, the sides outside it dimmed rather than black, a fine crosshair with a
	 * gap in the middle, range marks below it and a red centre dot. Drawn in screen pixels, so the edge stays smooth. It
	 * fades in as the zoom eases in (creator: "a slow zoom in"), and shows a hit marker when a shot lands.
	 */
	private static void scope(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		float gs = (float) mc.getWindow().getGuiScale();
		float fade = Aim.scopeFade(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		int w = Math.round(g.guiWidth() * gs), h = Math.round(g.guiHeight() * gs), cx = w / 2, cy = h / 2, r = h / 2;
		float k = h / 1440F; // sizes below are for a 1440-pixel-tall screen
		int rim = Math.max(2, Math.round(10 * k)), line = Math.max(1, Math.round(3 * k)), gap = Math.round(40 * k), step = 2;
		int dim = faded(0x9E000000, fade), rimColor = faded(0xFF141A22, fade), black = faded(0xFF000000, fade);
		g.pose().pushMatrix();
		g.pose().scale(1F / gs, 1F / gs);
		// (as one piece, 2.0.0: it was some 2,900 separate ones a frame on a 1440-pixel screen)
		Smooth.Batch b = new Smooth.Batch(RenderPipelines.GUI, TextureSetup.noTexture());
		for (int y = 0; y < h; y += step) {
			double dy = y + step / 2.0 - cy;
			if (Math.abs(dy) >= r) {
				b.quad(0, y, w, y + step, dim, dim);
				continue;
			}
			int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
			int in = Math.abs(dy) < r - rim ? (int) Math.round(Math.sqrt((double) (r - rim) * (r - rim) - dy * dy)) : 0;
			b.quad(0, y, cx - dx, y + step, dim, dim);
			b.quad(cx + dx, y, w, y + step, dim, dim);
			b.quad(cx - dx, y, cx - in, y + step, rimColor, rimColor);
			b.quad(cx + in, y, cx + dx, y + step, rimColor, rimColor);
		}
		int half = line / 2;
		b.quad(cx - r + rim, cy - half, cx - gap, cy - half + line, black, black);
		b.quad(cx + gap, cy - half, cx + r - rim, cy - half + line, black, black);
		b.quad(cx - half, cy - r + rim, cx - half + line, cy - gap, black, black);
		b.quad(cx - half, cy + gap, cx - half + line, cy + r - rim, black, black);
		int tick = Math.round(14 * k);
		for (int i = 1; i <= 4; i++) {
			int y = cy + gap + Math.round(i * 46 * k);
			b.quad(cx - tick, y - half, cx + tick, y - half + line, black, black);
		}
		int dot = Math.max(2, Math.round(6 * k));
		for (int y = -dot; y <= dot; y++) {
			int dx = (int) Math.round(Math.sqrt((double) dot * dot - y * y));
			b.quad(cx - dx, cy + y, cx + dx + 1, cy + y + 1, faded(0xFFFF2828, fade), faded(0xFFFF2828, fade));
		}
		hitMarker(b, cx, cy, k);
		b.submit(g);
		g.pose().popMatrix();
	}

	/**
	 * A hit marker in the scope when a shot of yours lands (creator): four short white strokes on the diagonals round the
	 * centre, fading out; a headshot's are gold, longer and thicker.
	 */
	private static void hitMarker(Smooth.Batch b, int cx, int cy, float k) {
		float m = Aim.hitMarker();
		if (m == 0) return;
		boolean head = m < 0;
		int color = faded(head ? 0xFFFFC21F : 0xFFFFFFFF, Math.abs(m));
		int inner = Math.round(14 * k), outer = inner + Math.round((head ? 30 : 20) * k), thick = Math.max(2, Math.round((head ? 5 : 3) * k));
		for (int i = inner; i <= outer; i++)
			for (int[] d : new int[][] { { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } }) {
				int x = cx + d[0] * i, y = cy + d[1] * i;
				b.quad(x - thick / 2, y - thick / 2, x - thick / 2 + thick, y - thick / 2 + thick, color, color);
			}
	}

	/** A colour with its alpha scaled by f (0 to 1). */
	private static int faded(int argb, float f) {
		return Math.round((argb >>> 24) * f) << 24 | argb & 0xFFFFFF;
	}

	/** Fortnite-style crosshair (hud sheet "crosshair"): the gap follows the gun's spread, tighter while aiming. */
	public static void crosshair(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator() || Aim.scoped() || mc.gui.screen() != null) return;
		// Drawn in real screen pixels (not GUI pixels) so it stays small and crisp at any GUI scale, like Fortnite's.
		float px = 1F / mc.getWindow().getGuiScale();
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() / 2F, g.guiHeight() / 2F);
		g.pose().scale(px, px);
		var w = Aim.heldGun();
		int col = 0xF0FFFFFF, shadow = 0x80000000;
		g.fill(-2, -2, 2, 2, shadow);
		g.fill(-1, -1, 1, 1, col);
		if (w != null) {
			float spread = w.spreadDeg() * (Aim.aiming() ? w.adsSpreadMult() : 1F) / Aim.zoom(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			int gap = Math.min(22, 4 + Math.round(spread * 2.5F)), len = Sheets.HUD_BY_ID.get("crosshair").size();
			int[][] arms = { { -gap - len, -1, -gap, 1 }, { gap, -1, gap + len, 1 }, { -1, -gap - len, 1, -gap }, { -1, gap, 1, gap + len } };
			for (int[] a : arms) {
				g.fill(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, shadow);
				g.fill(a[0], a[1], a[2], a[3], col);
			}
		}
		g.pose().popMatrix();
	}

	/**
	 * Fortnite compass bar (hud sheet "compass"): headings slide past as you turn; you face what is under the marker.
	 */
	private static void compass(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("compass");
		int w = d.size(), cx = g.guiWidth() / 2 + d.x(), y = d.y();
		float span = 80F; // degrees visible either side of the centre
		float heading = ((mc.player.getYRot() + 180F) % 360F + 360F) % 360F; // 0 = north, 90 = east
		g.fill(cx - w / 2, y, cx + w / 2, y + 14, 0x80101420);
		String[] names = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
		for (int a = 0; a < 360; a += 15) {
			float delta = ((a - heading) % 360F + 540F) % 360F - 180F;
			if (Math.abs(delta) > span) continue;
			int x = Math.round(cx + delta / span * (w / 2F - 6));
			int alpha = (int) (255 * (1F - (float) Math.pow(Math.abs(delta) / span, 3)));
			if (alpha < 8) continue;
			if (a % 45 == 0) {
				String n = names[a / 45];
				int col = (a % 90 == 0 ? 0xFFFFFF : 0xC8CDD8) | (Math.max(alpha, 40) << 24);
				g.centeredText(mc.font, n, x, y + 3, col);
			} else {
				g.fill(x, y + 4, x + 1, y + 10, (alpha / 2 << 24) | 0xFFFFFF);
			}
		}
		// Teammates' waypoints in their colours (team modes), then yours in red on top: a mark at its bearing (held at
		// the bar's end when it's outside the bar).
		for (double[] t : Waypoint.teammates()) compassMark(g, cx, y, w, span, heading, Waypoint.bearingTo(mc, t[0], t[1]), (int) t[2]);
		for (double[] c : Waypoint.cards()) compassMark(g, cx, y, w, span, heading, Waypoint.bearingTo(mc, c[0], c[1]), (int) c[2]);
		if (Waypoint.isSet()) compassMark(g, cx, y, w, span, heading, Waypoint.bearing(mc), 0xFFFF3030);
		// Centre marker: a small yellow notch under the bar.
		g.fill(cx, y + 14, cx + 1, y + 17, 0xFFFFD21F);
		g.fill(cx - 1, y + 14, cx + 2, y + 15, 0xFFFFD21F);
		String deg = Math.round(heading) % 360 + "";
		g.pose().pushMatrix();
		g.pose().translate(cx, y + 18);
		g.pose().scale(0.75F, 0.75F);
		g.centeredText(mc.font, deg, 0, 0, 0xFFFFD21F);
		g.pose().popMatrix();
	}

	private static void compassMark(GuiGraphicsExtractor g, int cx, int y, int w, float span, float heading, float bearing, int color) {
		float wd = ((bearing - heading) % 360F + 540F) % 360F - 180F;
		int wx = Math.round(cx + Math.max(-span, Math.min(span, wd)) / span * (w / 2F - 6));
		g.fill(wx - 2, y + 1, wx + 3, y + 13, 0xC0101010);
		g.fill(wx - 1, y + 2, wx + 2, y + 12, color);
	}

	private static String clock(int seconds) {
		return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
	}

	private static void stormTint(GuiGraphicsExtractor g) {
		int w = g.guiWidth(), h = g.guiHeight();
		g.fill(0, 0, w, h, 0x506A1FB8);
		// Vignette: darker purple bands at the edges.
		for (int i = 0; i < 6; i++) {
			int a = 0x30 - i * 6, d = i * 6;
			int c = (a << 24) | 0x3A0A6A;
			g.fill(d, d, w - d, d + 6, c);
			g.fill(d, h - d - 6, w - d, h - d, c);
			g.fill(d, d + 6, d + 6, h - d - 6, c);
			g.fill(w - d - 6, d + 6, w - d, h - d - 6, c);
		}
	}

	/** One row under the map: storm timer, players left, eliminations, each with a small pixel icon. */
	private static void statsRow(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("stats_row");
		var hud = ClientState.hud;
		// Laid out at a natural 90 px width, then scaled so the row is exactly as wide as the minimap above it. In a team
		// match a fourth cell, the team's eliminations, makes it a cell longer to the left (creator).
		final int natural = 90;
		boolean team = ClientState.teamMatch();
		int cells = team ? 4 : 3, cell = natural / 3, width = cell * cells;
		float s = (d.size() + 6) / (float) (natural + 6); // the minimap frame is 3 px wider on each side
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() + d.x() - d.size() - 3 - (width - natural) * s, d.y());
		g.pose().scale(s, s);
		int x = 3, y = 3, right = x + width;
		g.fill(x - 3, y - 3, right + 3, y + 13, 0xE0101420); // same colour as the minimap frame it hangs from
		// Storm clock.
		icon(g, x + 1, y, 0xFFB45CFF, new String[] { ".###.", "#...#", "#.#.#", "#..##", ".###." });
		g.text(mc.font, hud.secondsLeft() > 0 ? clock(hud.secondsLeft()) : "--", x + 8, y + 1, hud.shrinking() ? 0xFFE6A8FF : 0xFFFFFFFF, true);
		// Players left.
		icon(g, x + cell + 1, y, 0xFFFFFFFF, new String[] { "..#..", ".###.", "..#..", ".###.", ".#.#." });
		g.text(mc.font, "" + hud.alive(), x + cell + 8, y + 1, 0xFFFFFFFF, true);
		// Eliminations: yours, then (team match) your whole team's, you included.
		icon(g, x + 2 * cell + 1, y, 0xFFFF5A5A, ELIM);
		g.text(mc.font, "" + hud.kills(), x + 2 * cell + 8, y + 1, 0xFFFFD45A, true);
		if (team) {
			int total = hud.kills();
			for (var m : ClientState.squad.mates()) total += m.kills();
			icon(g, x + 3 * cell + 1, y, 0xFF8FD3FF, TEAM);
			g.text(mc.font, "" + total, x + 3 * cell + 8, y + 1, 0xFF8FD3FF, true);
		}
		g.pose().popMatrix();
	}

	/** Little pixel icons: an elimination, and two people (a team). */
	static final String[] ELIM = { "..#..", "#.#.#", "#####", "#.#.#", "..#.." }, TEAM = { "#...#", "#...#", ".....", "##.##", "##.##" };

	static void icon(GuiGraphicsExtractor g, int x, int y, int color, String[] rows) {
		for (int r = 0; r < rows.length; r++)
			for (int c = 0; c < rows[r].length(); c++)
				if (rows[r].charAt(c) == '#') g.fill(x + c + 1, y + r + 2, x + c + 2, y + r + 3, color);
	}

	private static void stormBanner(GuiGraphicsExtractor g, Minecraft mc) {
		var hud = ClientState.hud;
		if (hud.stormPhase() != lastPhase || hud.shrinking() != lastShrinking) {
			lastPhase = hud.stormPhase();
			lastShrinking = hud.shrinking();
			bannerAt = System.currentTimeMillis();
		}
		if (System.currentTimeMillis() - bannerAt > 5000 || hud.stormPhase() <= 0) return;
		HudElementDef d = Sheets.HUD_BY_ID.get("storm_banner");
		String text = hud.shrinking() ? "STORM EYE SHRINKING" : "STORM EYE SHRINKS IN " + clock(hud.secondsLeft());
		int w = Math.max(d.size(), mc.font.width(text) + 20);
		int x = g.guiWidth() / 2 + d.x() - w / 2, y = d.y();
		g.fill(x, y, x + w, y + 16, 0xC0301060);
		g.fill(x, y + 15, x + w, y + 16, 0xFFD58CFF);
		g.centeredText(mc.font, text, g.guiWidth() / 2 + d.x(), y + 4, 0xFFFFFFFF);
	}

	private static void healthShield(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("health_shield");
		int x = d.x(), y = g.guiHeight() + d.y(), w = d.size();
		float hp = mc.player.getHealth(), maxHp = mc.player.getMaxHealth();
		float sh = ClientState.hud.shield();
		g.fill(x - 4, y - 4, x + w + 30, y + 24, 0x90101420);
		// Shield: blue diamond icon + bar.
		icon(g, x - 2, y - 2, 0xFF6EC1FF, new String[] { "..#..", ".###.", "#####", ".###.", "..#.." });
		bar(g, mc, x + 6, y, w - 6, sh / MinebloxRoyale.setting("maxShield"), 0xFF3AA0FF, 0xFF1F5E99, Math.round(sh) + "");
		// Health: green cross + bar.
		icon(g, x - 2, y + 10, 0xFF6CF06C, new String[] { ".###.", "#####", "#####", "#####", ".###." });
		icon(g, x - 2, y + 10, 0xFFFFFFFF, new String[] { ".....", "..#..", ".###.", "..#..", "....." });
		bar(g, mc, x + 6, y + 12, w - 6, hp / Math.max(1, maxHp), 0xFF55D655, 0xFF2D7A2D, Math.round(hp) + "");
	}

	private static void bar(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, float frac, int color, int dark, String label) {
		g.fill(x - 1, y - 1, x + w + 1, y + 9, 0xFF000000);
		g.fill(x, y, x + w, y + 8, 0xFF2A2A2A);
		int fw = Math.round(w * Math.max(0, Math.min(1, frac)));
		g.fill(x, y, x + fw, y + 8, color);
		g.fill(x, y + 5, x + fw, y + 8, dark);
		for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + 8, 0x60000000);
		g.text(mc.font, label, x + w + 4, y, 0xFFFFFFFF, true);
	}

	private static void ammo(GuiGraphicsExtractor g, Minecraft mc) {
		ItemStack stack = mc.player.getMainHandItem();
		if (!(stack.getItem() instanceof WeaponItem w)) return;
		HudElementDef d = Sheets.HUD_BY_ID.get("ammo");
		RarityDef r = Guns.rarity(stack);
		long now = mc.level.getGameTime();
		boolean reloading = Guns.reloading(stack, now);
		int reserve = Guns.reserve(mc.player, w.def);
		String name = r.name().toUpperCase(Locale.ROOT) + " " + w.def.name().toUpperCase(Locale.ROOT);
		String count = Guns.ammo(stack) + "";
		String rest = "" + reserve;
		int x = g.guiWidth() + d.x(), y = g.guiHeight() + d.y();
		int width = Math.max(d.size(), mc.font.width(name) + 10);
		g.fill(x - width, y - 4, x + 2, y + 26, 0xA0101420);
		g.fill(x - width, y - 4, x + 2, y + 8, 0xFF000000 | r.color());
		g.text(mc.font, name, x - mc.font.width(name) - 2, y - 2, 0xFF101010, false);
		g.pose().pushMatrix();
		g.pose().translate(x - width + 4, y + 10);
		g.pose().scale(1.6F, 1.6F);
		g.text(mc.font, count, 0, 0, Guns.ammo(stack) == 0 ? 0xFFFF6060 : 0xFFFFFFFF, true);
		g.pose().popMatrix();
		g.text(mc.font, "| " + rest, x - width + 6 + (int) (mc.font.width(count) * 1.6F), y + 14, reserve == 0 ? 0xFFFF6060 : 0xFFB0B0B0, true);
		if (reloading) {
			long until = Guns.tag(stack).getLongOr("reloadUntil", 0L);
			float total = w.def.reloadTicks() * r.reloadMult();
			float frac = 1F - (until - now) / total;
			g.fill(x - width, y + 24, x - width + Math.round((width + 2) * Math.max(0, Math.min(1, frac))), y + 26, 0xFFFFD45A);
		}
	}

	/** "RELOADING" under the crosshair (unscaled, screen centre). */
	private static void reloadingText(GuiGraphicsExtractor g, Minecraft mc) {
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.getItem() instanceof WeaponItem && Guns.reloading(stack, mc.level.getGameTime()))
			g.centeredText(mc.font, "RELOADING", g.guiWidth() / 2, g.guiHeight() / 2 + 12, 0xFFFFD45A);
	}

	/** Wood / Brick / Metal counts (hud sheet "materials"). */
	private static void materials(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("materials");
		int x = g.guiWidth() + d.x() - d.size(), y = g.guiHeight() + d.y();
		g.fill(x - 3, y - 3, x + d.size() + 3, y + 11, 0xA0101420);
		int cell = d.size() / 3, i = 0;
		for (var m : Sheets.MATERIALS) {
			var item = ModItems.MATERIALS.get(m.id());
			int n = 0;
			for (int s = 0; s < mc.player.getInventory().getContainerSize(); s++) if (mc.player.getInventory().getItem(s).is(item)) n += mc.player.getInventory().getItem(s).getCount();
			int cx = x + i * cell;
			g.fill(cx, y + 1, cx + 6, y + 7, 0xFF000000 | m.color());
			g.text(mc.font, "" + n, cx + 9, y, 0xFFFFFFFF, true);
			i++;
		}
	}

	private static void busHint(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("bus_hint");
		String text = switch (DropControl.phase()) {
			case BUS -> "PRESS SPACE TO JUMP";
			case SKYDIVE -> "SKYDIVING  " + Math.round(DropControl.heightAboveGround()) + "m  -  SPACE: OPEN GLIDER";
			case GLIDE -> "GLIDER  " + Math.round(DropControl.heightAboveGround()) + "m  -  SPACE: CLOSE GLIDER";
			default -> null;
		};
		if (text == null) return;
		int w = mc.font.width(text) + 16;
		int x = g.guiWidth() / 2 + d.x() - w / 2, y = g.guiHeight() + d.y();
		g.fill(x, y, x + w, y + 16, 0xC0101420);
		g.fill(x, y + 15, x + w, y + 16, 0xFF6EC1FF);
		g.centeredText(mc.font, text, g.guiWidth() / 2 + d.x(), y + 4, 0xFFFFFFFF);
	}

	/** Fortnite has a pickaxe + 5 item slots: grey out the locked hotbar slots. */
	private static void lockedSlots(GuiGraphicsExtractor g) {
		int left = g.guiWidth() / 2 - 91, top = g.guiHeight() - 22;
		for (LoadoutDef d : Sheets.LOADOUT) {
			if (!d.role().equals("locked")) continue;
			int x = left + 1 + d.slot() * 20, y = top + 1;
			g.fill(x, y, x + 20, y + 20, 0xC0101010);
			for (int i = 4; i < 16; i++) {
				g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFAA3333);
				g.fill(x + 19 - i, y + i, x + 20 - i, y + i + 1, 0xFFAA3333);
			}
		}
	}

	private static void hubHint(GuiGraphicsExtractor g, Minecraft mc) {
		var hud = ClientState.hud;
		HudElementDef d = Sheets.HUD_BY_ID.get("hub_hint");
		String line1 = switch (hud.phase()) {
			case "COUNTDOWN" -> hud.label().replace(" - Bus boarding", "") + ": Battle Bus boarding in " + hud.secondsLeft() + "s - stay on the portal!";
			case "LIVE", "ENDED" -> "Match in progress - " + hud.alive() + " left";
			default -> "Everyone on the same portal starts the match (" + hud.onPortal() + "/" + hud.hubCount() + ")";
		};
		// How many stand on each mode portal (Solo, Duos, Trios, Squads), in the hub only.
		boolean pads = hud.phase().equals("HUB") || hud.phase().equals("COUNTDOWN");
		// The island countdown (creator: in place of "Blue pad: Obby"), while players wait in the hub.
		boolean island = hud.label().startsWith("Island") || hud.label().startsWith("Finding");
		String line2 = island ? hud.label() + "  |  B: Emotes  |  K: Locker" : "B: Emotes  |  K: Locker";
		// The room's code, always on in the hub (creator): "Room Code:" in grey, the code in orange.
		String code = RoomCodes.current(), label = "Room Code: ";
		int codeW = code == null ? 0 : mc.font.width(label) + mc.font.width(code);
		int w = Math.max(Math.max(Math.max(mc.font.width(line1), mc.font.width(line2)), codeW), pads ? SquadHud.padsWidth(mc) : 0);
		int cx = g.guiWidth() / 2 + d.x();
		int y = d.y(), rows = 2 + (pads ? 1 : 0) + (code == null ? 0 : 1);
		g.fill(cx - w / 2 - 6, y - 4, cx + w / 2 + 6, y + rows * 11, 0xB0101420);
		g.centeredText(mc.font, line1, cx, y, 0xFFFFFFFF);
		y += 11;
		if (pads) {
			SquadHud.pads(g, mc, cx, y);
			y += 11;
		}
		g.centeredText(mc.font, line2, cx, y, 0xFFB8C4D6);
		y += 11;
		if (code != null) {
			int x = cx - codeW / 2;
			g.text(mc.font, label, x, y, 0xFFA0A0A0, true);
			g.text(mc.font, code, x + mc.font.width(label), y, 0xFFFF9A1F, true);
			y += 11;
		}
		// Your party under the banner (creator: parties of up to 4).
		SquadHud.party(g, mc, y + 6);
	}
}
