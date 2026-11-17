package gg.minebloxroyale.client;

import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

	/**
	 * (2.1.0, for the HUD's slim line of stats, BRHud.gunStats) {damage, fire rate, reload} short: "33" (a shotgun's
	 * "10x10": per pellet, rounded, times its pellets), "5.0/s", "2.1s".
	 */
	static String[] chips(ItemStack stack) {
		WeaponDef w = ((WeaponItem) stack.getItem()).def;
		RarityDef r = Guns.rarity(stack);
		float dmg = w.damage() * r.damageMult();
		return new String[] { w.pellets() > 1 ? Math.round(dmg) + "x" + w.pellets() : num(dmg),
			String.format(Locale.ROOT, "%.1f", 20F / Math.max(1, w.fireIntervalTicks())) + "/s",
			String.format(Locale.ROOT, "%.1f", Math.round(w.reloadTicks() * r.reloadMult()) / 20F) + "s" };
	}

	private static Component stat(String name, String value) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	/** A whole number when it is one (30), else one decimal (31.5). */
	private static String num(float v) {
		return Math.abs(v - Math.round(v)) < 0.05F ? Math.round(v) + "" : String.format(Locale.ROOT, "%.1f", v);
	}
}
