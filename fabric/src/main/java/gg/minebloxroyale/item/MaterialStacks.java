package gg.minebloxroyale.item;

import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.sheets.MaterialDef;
import gg.minebloxroyale.sheets.Sheets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Up to 500 of each material in its one slot, past Minecraft's 99 a stack (2.0.0, creator: "yeah, 500. DO what's
 * easiest to ensure players have 500 of each Material. (Wood, Stone/Brick, and Metal)"). A material stack holds its
 * count times "each" (custom data, 1 when there's none): up to 99 is a plain stack, more is one item that stands for
 * them all, so Minecraft's own count never passes 99 and saving and joining work as before. Minecraft only merges stacks
 * with the same "each" and splitting keeps it, so dropping, splitting and merging on the ground keep the total right.
 */
public final class MaterialStacks {
	private static final String EACH = "each";

	private MaterialStacks() {
	}

	public static boolean is(ItemStack s) {
		return ModItems.MATERIALS.containsValue(s.getItem());
	}

	/** How many materials a stack holds (any other item: its count). */
	public static int amount(ItemStack s) {
		if (s.isEmpty() || !is(s)) return s.getCount();
		CustomData data = s.get(DataComponents.CUSTOM_DATA);
		return s.getCount() * (data == null ? 1 : Math.max(1, data.copyTag().getIntOr(EACH, 1)));
	}

	/** A stack holding n of a material (empty for 0). */
	public static ItemStack of(Item item, int n) {
		ItemStack s = new ItemStack(item);
		set(s, n);
		return s;
	}

	/** Makes a material stack hold n (0 empties it). */
	public static void set(ItemStack s, int n) {
		if (n <= 0) {
			s.setCount(0);
		} else if (n <= s.getMaxStackSize()) {
			s.remove(DataComponents.CUSTOM_DATA);
			s.setCount(n);
		} else {
			CompoundTag tag = new CompoundTag();
			tag.putInt(EACH, n);
			s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			s.setCount(1);
		}
	}

	/** Whether a stack is as set() makes it (moving stacks round in the inventory screen can put two big ones together). */
	public static boolean tidy(ItemStack s) {
		return amount(s) <= s.getMaxStackSize() ? !s.has(DataComponents.CUSTOM_DATA) : s.getCount() == 1;
	}

	/** The most a player or bot carries of this material (materials sheet maxCarry: 500). */
	public static int max(Item item) {
		for (MaterialDef m : Sheets.MATERIALS) if (ModItems.MATERIALS.get(m.id()) == item) return m.maxCarry();
		return item.getDefaultMaxStackSize();
	}
}
