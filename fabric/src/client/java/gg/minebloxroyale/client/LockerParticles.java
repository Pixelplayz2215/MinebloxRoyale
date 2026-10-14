package gg.minebloxroyale.client;

import gg.minebloxroyale.sheets.CosmeticDef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The Locker preview's particle effect, drawn with the same Minecraft particle textures and colours you see around
 * players in the world (CosmeticParticles), spawned the same way: one every 'rate' ticks, 0.45-0.7 blocks out and
 * 0.4-1.9 blocks up, drifting up 0.015 blocks a tick (drips fall). Ones behind the avatar are drawn before it.
 */
final class LockerParticles {
	/**
	 * A particle type's textures (textures/particle/<name>.png), as its particles/<type>.json lists them. falls: drifts
	 * down (snow, petals, portal, drips) instead of floating up.
	 */
	record Look(String[] frames, boolean animated, boolean tinted, float size, boolean falls) {
		Look(String[] frames, boolean animated, boolean tinted, float size) {
			this(frames, animated, tinted, size, false);
		}
	}

	/**
	 * Effects drawn the Locker's way in the world too (CosmeticParticle), so both look the same (creator: the menu
	 * version looked right). The rest use Minecraft's own particle in the world.
	 */
	static final Set<String> SAME_AS_MENU = Set.of("electric_spark", "enchant", "totem_of_undying", "falling_spore_blossom",
		"white_ash", "warped_spore", "nautilus");

	/** The texture for a frame of this look (age / lifetime, and a per-particle pick for still looks). */
	static String frame(Look look, int age, int life, int pick) {
		int i = look.animated() ? Math.min(look.frames().length - 1, age * look.frames().length / Math.max(1, life)) : pick % look.frames().length;
		return look.frames()[i];
	}

	static Look look(String shape) {
		return switch (shape) {
			case "heart" -> new Look(new String[] { "heart" }, false, false, 0.2F);
			case "note" -> new Look(new String[] { "note" }, false, true, 0.2F);
			case "end_rod", "totem_of_undying" -> new Look(seq("glitter_", 7, 0), true, shape.equals("totem_of_undying"), 0.1F);
			case "small_flame" -> new Look(new String[] { "flame" }, false, false, 0.1F);
			case "soul_fire_flame" -> new Look(new String[] { "soul_fire_flame" }, false, false, 0.15F);
			case "snowflake" -> new Look(seq("generic_", 7, 0), true, true, 0.15F, true);
			case "portal" -> new Look(seq("generic_", 0, 7), false, true, 0.12F, true);
			case "white_ash", "crimson_spore", "warped_spore" -> new Look(new String[] { "generic_0" }, false, true, 0.08F);
			case "cherry_leaves" -> new Look(seq("cherry_", 0, 11), false, false, 0.2F, true);
			case "electric_spark", "glow", "wax_on" -> new Look(new String[] { "glow" }, false, true, 0.12F);
			case "enchant" -> new Look(letters(), false, true, 0.12F);
			case "happy_villager" -> new Look(new String[] { "glint" }, false, false, 0.12F); // the world's own colours
			case "bubble_pop" -> new Look(seq("bubble_pop_", 0, 4), true, false, 0.15F);
			case "crit" -> new Look(new String[] { "critical_hit" }, false, true, 0.15F);
			case "enchanted_hit" -> new Look(new String[] { "enchanted_hit" }, false, true, 0.15F);
			case "witch" -> new Look(seq("spell_", 7, 0), true, true, 0.15F);
			case "firefly" -> new Look(new String[] { "firefly" }, false, false, 0.1F);
			case "falling_spore_blossom" -> new Look(new String[] { "drip_fall" }, false, true, 0.1F);
			case "dripping_honey" -> new Look(new String[] { "drip_hang" }, false, true, 0.1F, true);
			case "nautilus" -> new Look(new String[] { "nautilus" }, false, false, 0.15F);
			case "sculk_soul" -> new Look(seq("sculk_soul_", 0, 10), true, false, 0.25F);
			default -> new Look(new String[] { "generic_0" }, false, true, 0.1F);
		};
	}

	private static String[] seq(String prefix, int from, int to) {
		int n = Math.abs(to - from) + 1, step = to >= from ? 1 : -1;
		String[] out = new String[n];
		for (int i = 0; i < n; i++) out[i] = prefix + (from + i * step);
		return out;
	}

	private static String[] letters() {
		String[] out = new String[26];
		for (int i = 0; i < 26; i++) out[i] = "sga_" + (char) ('a' + i);
		return out;
	}

	/** One particle: angle round you, distance, height (blocks), up speed, age, lifetime, texture pick. */
	private static final class P {
		double angle, dist, y, vy;
		int age, life, pick;
	}

	private final List<P> live = new ArrayList<>();
	private int ticks;

	void tick(CosmeticDef fx) {
		ticks++;
		if (fx != null && fx.rate() > 0 && ticks % fx.rate() == 0) {
			P p = new P();
			p.angle = Math.random() * Math.PI * 2;
			p.dist = 0.45 + Math.random() * 0.25;
			p.y = 0.4 + Math.random() * 1.5;
			Look l = look(fx.shape());
			p.vy = l.falls() ? -0.02 : 0.015;
			if (l.falls()) p.y = 0.9 + Math.random() * 1.2;
			p.life = 20 + (int) (Math.random() * 20);
			p.pick = (int) (Math.random() * 1000);
			live.add(p);
		}
		for (Iterator<P> it = live.iterator(); it.hasNext();) {
			P p = it.next();
			p.y += p.vy;
			if (++p.age > p.life || p.y < 0) it.remove();
		}
		if (fx == null) live.clear();
	}

	/** Draws the particles behind (front = false) or in front of (front = true) the avatar. */
	void draw(GuiGraphicsExtractor g, CosmeticDef fx, int cx, int feetY, float pxPerBlock, boolean front) {
		if (fx == null) return;
		Look look = look(fx.shape());
		int tint = look.tinted() ? 0xFF000000 | (fx.color() & 0xFFFFFF) : 0xFFFFFFFF;
		for (P p : live) {
			double z = Math.sin(p.angle); // toward the camera when positive
			if (z >= 0 != front) continue;
			int frame = look.animated() ? Math.min(look.frames().length - 1, p.age * look.frames().length / Math.max(1, p.life)) : p.pick % look.frames().length;
			Identifier tex = Identifier.withDefaultNamespace("textures/particle/" + look.frames()[frame] + ".png");
			float scale = (float) (1 + z * 0.15); // nearer ones a little bigger
			int s = Math.max(2, Math.round(look.size() * pxPerBlock * scale));
			int x = cx + (int) Math.round(Math.cos(p.angle) * p.dist * pxPerBlock) - s / 2;
			int y = feetY - (int) Math.round(p.y * pxPerBlock) - s / 2;
			g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, s, s, 1, 1, 1, 1, tint);
		}
	}
}
