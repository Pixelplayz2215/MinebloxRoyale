package gg.minebloxroyale.client;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.match.CarEntity;
import gg.minebloxroyale.sheets.HudElementDef;
import gg.minebloxroyale.sheets.LoadoutDef;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
			scaled(g, "compass", g.guiWidth() / 2F, 0, () -> compass(g, mc));
			Waypoint.screenMarker(g, mc);
			scaled(g, "squad", 0, 0, () -> SquadHud.panel(g, mc));
			// revive / reboot first, else a chest's "Hold F to search"
			scaled(g, "interact", g.guiWidth() / 2F, g.guiHeight() / 2F, () -> {
				if (!SquadHud.interact(g, mc)) ChestHud.draw(g, mc);
			});
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

	/**
	 * Draws a panel at its hud-sheet scale, shrunk towards the given anchor point; on a screen narrower than the HUD is
	 * laid out for (hud sheet "hud_fit"), smaller by as much, so the bottom row never overlaps (2.0.0-test2, when the
	 * whole HUD got 15% bigger).
	 */
	private static void scaled(GuiGraphicsExtractor g, String id, float ax, float ay, Runnable draw) {
		float s = Sheets.HUD_BY_ID.get(id).scale() * Math.min(1F, g.guiWidth() / (float) Sheets.HUD_BY_ID.get("hud_fit").size());
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
		// v2.0.0: in a match, Fortnite's hotbar (the pickaxe and five item slots as smooth tiles); in the hub, Minecraft's.
		if (ClientState.inMatch()) scaled(g, "hotbar", g.guiWidth() / 2F, g.guiHeight(), () -> matchHotbar(g, Minecraft.getInstance()));
		else scaled(g, "hotbar", g.guiWidth() / 2F, g.guiHeight(), vanilla);
	}

	/**
	 * The match's hotbar, Fortnite-style (v2.0.0, creator: "Cleaner UI"): the pickaxe and the five item slots as rounded
	 * tiles, a gun's in its rarity's colour with its magazine count, a stack's count, each slot's number; the one in hand
	 * raised and outlined in white. (Minecraft's three slots after them are locked in a match: not shown.)
	 */
	private static void matchHotbar(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.player == null) return;
		var inv = mc.player.getInventory();
		int sel = inv.getSelectedSlot(), n = 0;
		for (LoadoutDef d : Sheets.LOADOUT) if (!d.role().equals("locked")) n++;
		float tile = 20, gap = 3, total = n * tile + (n - 1) * gap;
		float x0 = g.guiWidth() / 2F - total / 2, y0 = g.guiHeight() - tile - 4;
		for (int i = 0; i < n; i++) {
			ItemStack st = inv.getItem(i);
			boolean on = i == sel;
			float x = x0 + i * (tile + gap), y = y0 - (on ? 3 : 0);
			int top = 0xB0313848, bottom = 0xC0151922;
			if (st.getItem() instanceof WeaponItem) {
				int c = 0xFF000000 | Guns.rarity(st).color();
				top = 0xE8000000 | Smooth.lighter(c, 0.15F) & 0xFFFFFF;
				bottom = 0xE8000000 | Smooth.mix(c, 0xFF000000, 0.45F) & 0xFFFFFF;
			}
			if (on) Smooth.panel(g, x - 1.3F, y - 1.3F, tile + 2.6F, tile + 2.6F, 4.6F, 0xFFFFFFFF);
			Smooth.panel(g, x, y, tile, tile, 3.6F, top, bottom);
			if (!st.isEmpty()) {
				g.item(st, Math.round(x + 2), Math.round(y + 2));
				if (st.getItem() instanceof WeaponItem) Smooth.text(g, Guns.ammo(st) + "", x + tile - 1.4F, y + tile - 7.6F, 6.5F, 0xFFFFFFFF, Smooth.RIGHT, true);
				else if (st.getCount() > 1) Smooth.text(g, st.getCount() + "", x + tile - 1.4F, y + tile - 7.6F, 6.5F, 0xFFFFFFFF, Smooth.RIGHT, true);
			}
			Smooth.text(g, (i + 1) + "", x + 1.6F, y + 0.8F, 5F, on ? 0xFFFFFFFF : 0xA0FFFFFF, Smooth.LEFT, false);
		}
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
		for (int y = 0; y < h; y += step) {
			double dy = y + step / 2.0 - cy;
			if (Math.abs(dy) >= r) {
				g.fill(0, y, w, y + step, dim);
				continue;
			}
			int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
			int in = Math.abs(dy) < r - rim ? (int) Math.round(Math.sqrt((double) (r - rim) * (r - rim) - dy * dy)) : 0;
			g.fill(0, y, cx - dx, y + step, dim);
			g.fill(cx + dx, y, w, y + step, dim);
			g.fill(cx - dx, y, cx - in, y + step, rimColor);
			g.fill(cx + in, y, cx + dx, y + step, rimColor);
		}
		int half = line / 2;
		g.fill(cx - r + rim, cy - half, cx - gap, cy - half + line, black);
		g.fill(cx + gap, cy - half, cx + r - rim, cy - half + line, black);
		g.fill(cx - half, cy - r + rim, cx - half + line, cy - gap, black);
		g.fill(cx - half, cy + gap, cx - half + line, cy + r - rim, black);
		int tick = Math.round(14 * k);
		for (int i = 1; i <= 4; i++) {
			int y = cy + gap + Math.round(i * 46 * k);
			g.fill(cx - tick, y - half, cx + tick, y - half + line, black);
		}
		int dot = Math.max(2, Math.round(6 * k));
		for (int y = -dot; y <= dot; y++) {
			int dx = (int) Math.round(Math.sqrt((double) dot * dot - y * y));
			g.fill(cx - dx, cy + y, cx + dx + 1, cy + y + 1, faded(0xFFFF2828, fade));
		}
		hitMarker(g, cx, cy, k);
		g.pose().popMatrix();
	}

	/**
	 * A hit marker in the scope when a shot of yours lands (creator): four short white strokes on the diagonals round the
	 * centre, fading out; a headshot's are gold, longer and thicker.
	 */
	private static void hitMarker(GuiGraphicsExtractor g, int cx, int cy, float k) {
		float m = Aim.hitMarker();
		if (m == 0) return;
		boolean head = m < 0;
		int color = faded(head ? 0xFFFFC21F : 0xFFFFFFFF, Math.abs(m));
		int inner = Math.round(14 * k), outer = inner + Math.round((head ? 30 : 20) * k), thick = Math.max(2, Math.round((head ? 5 : 3) * k));
		for (int i = inner; i <= outer; i++)
			for (int[] d : new int[][] { { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } }) {
				int x = cx + d[0] * i, y = cy + d[1] * i;
				g.fill(x - thick / 2, y - thick / 2, x - thick / 2 + thick, y - thick / 2 + thick, color);
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
		Smooth.panel(g, cx - w / 2F, y, w, 14, 5, 0x70101420, 0x90101420);
		String[] names = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
		for (int a = 0; a < 360; a += 15) {
			float delta = ((a - heading) % 360F + 540F) % 360F - 180F;
			if (Math.abs(delta) > span) continue;
			float x = cx + delta / span * (w / 2F - 8);
			int alpha = (int) (255 * (1F - (float) Math.pow(Math.abs(delta) / span, 3)));
			if (alpha < 8) continue;
			if (a % 45 == 0) {
				int col = (a % 90 == 0 ? 0xFFFFFF : 0xC8CDD8) | (Math.max(alpha, 40) << 24);
				Smooth.text(g, names[a / 45], x, y + 2.6F, 8.5F, col, Smooth.CENTER, false);
			} else {
				Smooth.panel(g, x - 0.4F, y + 4.5F, 0.8F, 5, 0.4F, (alpha / 2 << 24) | 0xFFFFFF);
			}
		}
		// Teammates' waypoints in their colours (team modes), then yours in red on top: a mark at its bearing (held at
		// the bar's end when it's outside the bar).
		for (double[] t : Waypoint.teammates()) compassMark(g, cx, y, w, span, heading, Waypoint.bearingTo(mc, t[0], t[1]), (int) t[2]);
		for (double[] c : Waypoint.cards()) compassMark(g, cx, y, w, span, heading, Waypoint.bearingTo(mc, c[0], c[1]), (int) c[2]);
		if (Waypoint.isSet()) compassMark(g, cx, y, w, span, heading, Waypoint.bearing(mc), 0xFFFF3030);
		// Centre marker: a small yellow notch under the bar, the heading under it.
		Smooth.panel(g, cx - 1.5F, y + 14.3F, 3, 2.4F, 1, 0xFFFFD21F);
		Smooth.text(g, Math.round(heading) % 360 + "", cx, y + 17.5F, 6.5F, 0xFFFFD21F, Smooth.CENTER, true);
	}

	private static void compassMark(GuiGraphicsExtractor g, int cx, int y, int w, float span, float heading, float bearing, int color) {
		float wd = ((bearing - heading) % 360F + 540F) % 360F - 180F;
		float wx = cx + Math.max(-span, Math.min(span, wd)) / span * (w / 2F - 8);
		Smooth.panel(g, wx - 2.4F, y + 1.2F, 4.8F, 11.6F, 2.2F, 0xC0101010);
		Smooth.panel(g, wx - 1.4F, y + 2.2F, 2.8F, 9.6F, 1.4F, color);
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
		float x = 3, y = 3;
		Smooth.panel(g, x - 3, y - 3, width + 6, 16, 3, 0xE0101420, 0xE0080B12); // the minimap frame's colour, hanging from it
		// The storm's clock, players left, your eliminations, then (team match) your whole team's, you included: each a
		// Blender icon and a number.
		Smooth.icon(g, "storm", x, y - 0.5F, 10, -1);
		Smooth.text(g, hud.secondsLeft() > 0 ? clock(hud.secondsLeft()) : "--", x + 11, y + 0.6F, 8.5F, hud.shrinking() ? 0xFFE6A8FF : 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.icon(g, "players", x + cell, y - 0.5F, 10, -1);
		Smooth.text(g, "" + hud.alive(), x + cell + 11, y + 0.6F, 8.5F, 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.icon(g, "kills", x + 2 * cell, y - 0.5F, 10, -1);
		Smooth.text(g, "" + hud.kills(), x + 2 * cell + 11, y + 0.6F, 8.5F, 0xFFFFD45A, Smooth.LEFT, true);
		if (team) {
			int total = hud.kills();
			for (var m : ClientState.squad.mates()) total += m.kills();
			Smooth.icon(g, "team", x + 3 * cell, y - 0.5F, 10, -1);
			Smooth.text(g, "" + total, x + 3 * cell + 11, y + 0.6F, 8.5F, 0xFF8FD3FF, Smooth.LEFT, true);
		}
		g.pose().popMatrix();
	}

	private static void stormBanner(GuiGraphicsExtractor g, Minecraft mc) {
		var hud = ClientState.hud;
		if (hud.stormPhase() != lastPhase || hud.shrinking() != lastShrinking) {
			lastPhase = hud.stormPhase();
			lastShrinking = hud.shrinking();
			bannerAt = System.currentTimeMillis();
		}
		long t = System.currentTimeMillis() - bannerAt;
		if (t > 5000 || hud.stormPhase() <= 0) return;
		HudElementDef d = Sheets.HUD_BY_ID.get("storm_banner");
		String text = hud.shrinking() ? "STORM EYE SHRINKING" : "STORM EYE SHRINKS IN " + clock(hud.secondsLeft());
		float a = Math.min(1, Math.min(t / 250F, (5000 - t) / 400F)); // (fading in and out)
		float w = Math.max(d.size(), Smooth.width(text, 10) + 34);
		float cx = g.guiWidth() / 2F + d.x(), x = cx - w / 2, y = d.y();
		Smooth.panel(g, x, y, w, 18, 6, Smooth.alpha(0xE0512094, a), Smooth.alpha(0xE0261046, a));
		Smooth.panel(g, x + 6, y + 16, w - 12, 1.2F, 0.6F, Smooth.alpha(0xFFD58CFF, a));
		Smooth.icon(g, "storm", x + 4, y + 2.5F, 13, Smooth.alpha(0xFFFFFFFF, a));
		Smooth.text(g, text, cx + 7, y + 4, 10, Smooth.alpha(0xFFFFFFFF, a), Smooth.CENTER, true);
	}

	private static void healthShield(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("health_shield");
		float x = d.x(), y = g.guiHeight() + d.y(), w = d.size();
		float hp = mc.player.getHealth(), maxHp = mc.player.getMaxHealth();
		float sh = ClientState.hud.shield();
		// Fortnite-style: the shield above the health, each its Blender icon, its number and a segmented bar.
		Smooth.panel(g, x - 5, y - 7, w + 10, 29, 6, 0x8010141E, 0xA0080B12);
		Smooth.icon(g, "shield", x - 3, y - 6, 12, -1);
		Smooth.text(g, Math.round(sh) + "", x + 10, y - 4.6F, 9, 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.bar(g, x + 27, y - 3, w - 29, 6.5F, sh / MinebloxRoyale.setting("maxShield"), 0xFF2E8CFF, 4);
		Smooth.icon(g, "health", x - 3, y + 7, 12, -1);
		Smooth.text(g, Math.round(hp) + "", x + 10, y + 8.4F, 9, 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.bar(g, x + 27, y + 10, w - 29, 6.5F, hp / Math.max(1, maxHp), 0xFF3DD64C, 4);
	}


	private static void ammo(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.player.getVehicle() instanceof CarEntity car) {
			car(g, mc, car);
			return;
		}
		ItemStack stack = mc.player.getMainHandItem();
		if (!(stack.getItem() instanceof WeaponItem w)) return;
		HudElementDef d = Sheets.HUD_BY_ID.get("ammo");
		RarityDef r = Guns.rarity(stack);
		long now = mc.level.getGameTime();
		boolean reloading = Guns.reloading(stack, now);
		int reserve = Guns.reserve(mc.player, w.def), loaded = Guns.ammo(stack);
		String name = r.name().toUpperCase(Locale.ROOT) + " " + w.def.name().toUpperCase(Locale.ROOT);
		float x = g.guiWidth() + d.x(), y = g.guiHeight() + d.y();
		float width = Math.max(d.size(), Smooth.width(name, 7.5F) + 12);
		int rc = 0xFF000000 | r.color();
		// The plate: the gun's name on its rarity's colour, then its ammo type's icon, the magazine big and what's left.
		Smooth.panel(g, x - width, y - 6, width + 2, 30, 5, 0x9010141E, 0xB0080B12);
		Smooth.panel(g, x - width, y - 6, width + 2, 11.5F, 5, Smooth.lighter(rc, 0.2F), Smooth.mix(rc, 0xFF000000, 0.25F));
		Smooth.text(g, name, x - 4, y - 4.6F, 7.5F, 0xFFFFFFFF, Smooth.RIGHT, true);
		Smooth.icon(g, "ammo_" + w.def.ammo(), x - width + 1, y + 7, 15, -1);
		float cw = Smooth.text(g, loaded + "", x - width + 17, y + 6, 15, loaded == 0 ? 0xFFFF6060 : 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.text(g, "/ " + reserve, x - width + 19 + cw, y + 11.5F, 8.5F, reserve == 0 ? 0xFFFF6060 : 0xFFB8C0CC, Smooth.LEFT, true);
		if (reloading) {
			long until = Guns.tag(stack).getLongOr("reloadUntil", 0L);
			float total = w.def.reloadTicks() * r.reloadMult();
			Smooth.bar(g, x - width + 3, y + 20.3F, width - 4, 2.4F, 1F - (until - now) / total, 0xFFFFD45A, 1);
		}
	}

	/**
	 * In a car (v2.0.0, creator: "Fuel where the ammo shows"): in the ammo plate's place, the car's fuel left (minutes
	 * and seconds, a bar), its boosts left (four pips) and its durability (a bar and a number, out of 600); everyone
	 * inside sees the same.
	 */
	private static void car(GuiGraphicsExtractor g, Minecraft mc, CarEntity car) {
		HudElementDef d = Sheets.HUD_BY_ID.get("ammo");
		float x = g.guiWidth() + d.x(), y = g.guiHeight() + d.y(), width = d.size() + 10;
		Smooth.panel(g, x - width, y - 6, width + 2, 30, 5, 0x9010141E, 0xB0080B12);
		Smooth.panel(g, x - width, y - 6, width + 2, 11.5F, 5, 0xFF4A5A70, 0xFF2A3446);
		boolean driver = car.getControllingPassenger() == mc.player;
		Smooth.text(g, driver ? "DRIVING" : "RIDING ALONG", x - width + 4, y - 4.6F, 7.5F, 0xFFFFFFFF, Smooth.LEFT, true);
		// boosts: four pips at the plate's top right, lit while left
		for (int i = 0; i < CarEntity.MAX_BOOSTS; i++)
			Smooth.panel(g, x - 6 - i * 6, y - 3.4F, 4.4F, 4.4F, 2.2F, i < car.boostsLeft() ? (car.boosting() ? 0xFFFFE04A : 0xFF6EC1FF) : 0x60FFFFFF);
		int fuel = car.fuelTicks() / 20;
		Smooth.text(g, "FUEL " + fuel / 60 + ":" + String.format(Locale.ROOT, "%02d", fuel % 60), x - width + 4, y + 7, 8, fuel == 0 ? 0xFFFF6060 : 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.bar(g, x - width + 46, y + 8.5F, width - 50, 4.5F, car.fuelTicks() / (float) CarEntity.FUEL_TICKS, 0xFFFFB13A, 1);
		int left = Math.round(car.durabilityLeft());
		Smooth.text(g, "CAR " + left, x - width + 4, y + 15.5F, 8, 0xFFFFFFFF, Smooth.LEFT, true);
		Smooth.bar(g, x - width + 46, y + 17, width - 50, 4.5F, left / gg.minebloxroyale.MinebloxRoyale.setting("carDurability"), 0xFF3DD64C, 4);
	}

	/** "RELOADING" under the crosshair (unscaled, screen centre). */
	private static void reloadingText(GuiGraphicsExtractor g, Minecraft mc) {
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.getItem() instanceof WeaponItem && Guns.reloading(stack, mc.level.getGameTime()))
			Smooth.text(g, "RELOADING", g.guiWidth() / 2F, g.guiHeight() / 2F + 11, 8, 0xFFFFD45A, Smooth.CENTER, true);
	}

	/** Wood / Brick / Metal counts (hud sheet "materials"). */
	private static void materials(GuiGraphicsExtractor g, Minecraft mc) {
		HudElementDef d = Sheets.HUD_BY_ID.get("materials");
		float x = g.guiWidth() + d.x() - d.size(), y = g.guiHeight() + d.y();
		Smooth.panel(g, x - 4, y - 4, d.size() + 6, 16, 5, 0x9010141E, 0xB0080B12);
		float cell = d.size() / 3F;
		int i = 0;
		for (var m : Sheets.MATERIALS) {
			var item = ModItems.MATERIALS.get(m.id());
			int n = 0;
			for (int s = 0; s < mc.player.getInventory().getContainerSize(); s++) if (mc.player.getInventory().getItem(s).is(item)) n += mc.player.getInventory().getItem(s).getCount();
			float cx = x + i * cell;
			Smooth.icon(g, m.id(), cx - 1, y - 2.5F, 12, -1); // (wood, brick, metal: the Blender icons)
			Smooth.text(g, "" + n, cx + 12, y - 0.6F, 8.5F, n == 0 ? 0xFF9AA2AD : 0xFFFFFFFF, Smooth.LEFT, true);
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
		float w = Smooth.width(text, 9) + 22, cx = g.guiWidth() / 2F + d.x(), y = g.guiHeight() + d.y();
		Smooth.panel(g, cx - w / 2, y, w, 17, 6, 0xC0182030, 0xD00C1018);
		Smooth.panel(g, cx - w / 2 + 6, y + 15.2F, w - 12, 1.2F, 0.6F, 0xFF6EC1FF);
		Smooth.text(g, text, cx, y + 4, 9, 0xFFFFFFFF, Smooth.CENTER, true);
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
		float size = 9;
		float codeW = code == null ? 0 : Smooth.width(label, size) + Smooth.width(code, size);
		float w = Math.max(Math.max(Math.max(Smooth.width(line1, size), Smooth.width(line2, size)), codeW), pads ? SquadHud.padsWidth(mc) : 0);
		int cx = g.guiWidth() / 2 + d.x();
		int y = d.y(), rows = 2 + (pads ? SquadHud.padRows() : 0) + (code == null ? 0 : 1);
		Smooth.panel(g, cx - w / 2 - 8, y - 5, w + 16, rows * 11 + 6, 6, 0xA0182030, 0xC00C1018);
		Smooth.text(g, line1, cx, y - 0.5F, size, 0xFFFFFFFF, Smooth.CENTER, true);
		y += 11;
		if (pads) {
			SquadHud.pads(g, mc, cx, y);
			y += 11 * SquadHud.padRows();
		}
		Smooth.text(g, line2, cx, y - 0.5F, size, 0xFFB8C4D6, Smooth.CENTER, true);
		y += 11;
		if (code != null) {
			float x = cx - codeW / 2;
			x += Smooth.text(g, label, x, y - 0.5F, size, 0xFFA0A0A0, Smooth.LEFT, true);
			Smooth.text(g, code, x, y - 0.5F, size, 0xFFFF9A1F, Smooth.LEFT, true);
			y += 11;
		}
		// Your party under the banner (creator: parties of up to 4).
		SquadHud.party(g, mc, y + 6);
	}
}
