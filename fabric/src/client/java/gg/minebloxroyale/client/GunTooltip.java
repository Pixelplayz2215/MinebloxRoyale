package gg.minebloxroyale.client;

import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * (2.1.0, Ultraslayerz via the creator: "Gun stats (reload time, fire rate, damage)") A gun's numbers when you hover it
 * in your inventory or a chest, for its rarity: damage a shot (a shotgun's per pellet, times its pellets) and a
 * headshot's, shots a second, the reload in seconds and the magazine. The same numbers the guns use (weapons and
 * rarities sheets), so they're always right.
 */
public final class GunTooltip {
	private GunTooltip() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!(stack.getItem() instanceof WeaponItem item)) return;
			WeaponDef w = item.def;
			RarityDef r = Guns.rarity(stack);
			float dmg = w.damage() * r.damageMult();
			String shot = w.pellets() > 1 ? num(dmg) + " x " + w.pellets() : num(dmg);
			lines.add(stat("Damage", shot + "  (headshot " + num(dmg * w.headshotMult()) + (w.pellets() > 1 ? " x " + w.pellets() : "") + ")"));
			lines.add(stat("Fire rate", String.format(Locale.ROOT, "%.1f", 20F / Math.max(1, w.fireIntervalTicks())) + " shots a second"));
			lines.add(stat("Reload", String.format(Locale.ROOT, "%.1f", Math.round(w.reloadTicks() * r.reloadMult()) / 20F) + " s"));
			lines.add(stat("Magazine", w.magazine() + ""));
		});
	}

	private static Component stat(String name, String value) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	/** A whole number when it is one (30), else one decimal (31.5). */
	private static String num(float v) {
		return Math.abs(v - Math.round(v)) < 0.05F ? Math.round(v) + "" : String.format(Locale.ROOT, "%.1f", v);
	}
}
