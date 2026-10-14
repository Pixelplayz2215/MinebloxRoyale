package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.sheets.LoadoutDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

/**
 * Fortnite's inventory (loadout sheet): slot 1 = unbreakable pickaxe, slots 2-6 = five item slots, 7-9 locked. The
 * rest of the inventory (creator): one fixed slot per ammo type, then one per material (Wood, Brick, Metal), each
 * holding at most 99, never a second stack; every other slot is locked. Enforced every tick during a match.
 */
public final class Loadout {
	/** Most of one ammo type or material a slot holds. */
	private static final int CAP = 99;

	private Loadout() {
	}

	public static String role(int slot) {
		for (LoadoutDef d : Sheets.LOADOUT) if (d.slot() == slot) return d.role();
		return slotItem(slot) != null ? "ammo" : "locked";
	}

	/** The one item a main-inventory slot holds: ammo types first (ammo sheet order), then materials. Null = locked. */
	public static Item slotItem(int slot) {
		int i = slot - 9;
		if (i < 0) return null;
		List<Item> order = new ArrayList<>(ModItems.AMMO.values());
		order.addAll(ModItems.MATERIALS.values());
		return i < order.size() ? order.get(i) : null;
	}

	/** The fixed slot for this ammo / material, or -1. */
	static int slotFor(ItemStack s) {
		for (int i = 9; i < 36; i++) if (s.is(slotItem(i))) return i;
		return -1;
	}

	public static ItemStack pickaxe() {
		LoadoutDef d = Sheets.LOADOUT.stream().filter(x -> x.role().equals("pickaxe")).findFirst().orElseThrow();
		ItemStack s = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(d.item())));
		s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		// Harvesting tool: mines anything breakable quickly (what is breakable is decided by the harvest sheet).
		s.set(DataComponents.TOOL, new Tool(List.of(), 9F, 0, false));
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Pickaxe").withStyle(Style.EMPTY.withItalic(false)));
		// Hitting an opponent: match.pickaxeDamage in total (a player's own base attack is 1), quick swings.
		var attack = Attributes.ATTACK_DAMAGE;
		var speed = Attributes.ATTACK_SPEED;
		var add = AttributeModifier.Operation.ADD_VALUE;
		s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
			.add(attack, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
				MinebloxRoyale.setting("pickaxeDamage") - 1.0, add), EquipmentSlotGroup.MAINHAND)
			.add(speed, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.0, add),
				EquipmentSlotGroup.MAINHAND)
			.build());
		return s;
	}

	public static boolean isPickaxe(ItemStack s) {
		return s.has(DataComponents.UNBREAKABLE) && s.is(pickaxe().getItem());
	}

	/**
	 * Vanilla Minecraft items don't exist in Mineblox Royale: only our pickaxe (an iron pickaxe) and Mineblox Royale
	 * items.
	 */
	public static boolean isVanilla(ItemStack s) {
		return !s.isEmpty() && !isPickaxe(s) && BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft");
	}

	/** Deletes any vanilla item a player has (never dropped). */
	public static void stripVanilla(ServerPlayer p) {
		if (p.isCreative() || p.isSpectator()) return;
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (isVanilla(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
		if (isVanilla(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
	}

	public static void enforce(ServerPlayer p) {
		if (p.isCreative()) return; // the development account flying round in creative keeps its own inventory
		stripVanilla(p);
		Inventory inv = p.getInventory();
		int size = Math.min(36, inv.getContainerSize());
		for (int i = 0; i < size; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			if (Reboot.isCard(s)) { // reboot cards only ever live in the offhand (creator)
				inv.setItem(i, ItemStack.EMPTY);
				Reboot.toOffhand(p, s);
				continue;
			}
			String role = role(i);
			boolean ammo = ModItems.isAmmo(s), pick = isPickaxe(s);
			boolean ok = switch (role) {
				case "pickaxe" -> pick;
				case "item" -> !ammo && !pick;
				case "locked" -> false;
				default -> s.is(slotItem(i)) && s.getCount() <= CAP; // its own fixed slot, 99 at most
			};
			if (ok) continue;
			inv.setItem(i, ItemStack.EMPTY);
			place(p, s);
		}
		// The offhand holds reboot cards and nothing else (F no longer swaps hands).
		ItemStack off = p.getOffhandItem();
		if (!off.isEmpty() && !Reboot.isCard(off)) {
			p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			place(p, off);
		}
		int pickSlot = Sheets.LOADOUT.stream().filter(x -> x.role().equals("pickaxe")).findFirst().orElseThrow().slot();
		if (!isPickaxe(inv.getItem(pickSlot))) inv.setItem(pickSlot, pickaxe());
	}

	/**
	 * Fortnite rarity upgrade: walking over the same gun at a better rarity (Common < Uncommon < Rare < Epic <
	 * Legendary, rarities sheet "tier") swaps it into that slot and drops the old one on the ground. True if it did.
	 */
	public static boolean tryUpgrade(ServerPlayer p, ItemEntity ground) {
		ItemStack found = ground.getItem();
		if (!(found.getItem() instanceof WeaponItem) || ground.hasPickUpDelay() || p.isSpectator()) return false;
		Inventory inv = p.getInventory();
		int newTier = Guns.rarity(found).tier();
		int worstSlot = -1, worstTier = newTier;
		for (LoadoutDef d : Sheets.LOADOUT) {
			if (!d.role().equals("item")) continue;
			ItemStack there = inv.getItem(d.slot());
			if (!there.is(found.getItem())) continue;
			int tier = Guns.rarity(there).tier();
			if (tier < worstTier) {
				worstTier = tier;
				worstSlot = d.slot();
			}
		}
		if (worstSlot < 0) return false;
		ItemStack old = inv.getItem(worstSlot);
		inv.setItem(worstSlot, found.copy());
		var dropped = new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), old.copy());
		dropped.setPickUpDelay(60); // don't grab the old one straight back
		p.level().addFreshEntity(dropped);
		p.take(ground, found.getCount());
		ground.discard();
		p.sendSystemMessage(Component.literal("Upgraded to " + found.getHoverName().getString()), true);
		return true;
	}

	/**
	 * Guns and heals need a free item slot (or a stack of the same thing with room); ammo and materials need room in
	 * their slot (99).
	 */
	public static boolean canPickUp(ServerPlayer p, ItemStack s) {
		if (p.isCreative()) return true;
		if (ModItems.isAmmo(s)) {
			int slot = slotFor(s);
			return slot >= 0 && p.getInventory().getItem(slot).getCount() < CAP;
		}
		Inventory inv = p.getInventory();
		for (LoadoutDef d : Sheets.LOADOUT) {
			if (!d.role().equals("item")) continue;
			ItemStack there = inv.getItem(d.slot());
			if (there.isEmpty() || ItemStack.isSameItemSameComponents(there, s) && there.getCount() < there.getMaxStackSize()) return true;
		}
		return false;
	}

	/**
	 * Harvested materials: straight into their own slot, up to max (and never past the slot's 99). At the cap you just
	 * get no more, as in Fortnite (they used to spill into other slots and then be dropped on the ground).
	 */
	static void addMaterial(Player p, Item item, int amount, int max) {
		int slot = slotFor(new ItemStack(item));
		if (slot < 0) return;
		ItemStack there = p.getInventory().getItem(slot);
		if (!there.isEmpty() && !there.is(item)) return;
		int n = Math.min(Math.min(CAP, max), there.getCount() + amount);
		if (n > there.getCount()) p.getInventory().setItem(slot, new ItemStack(item, n));
	}

	/** Puts a stack where the rules allow it, or drops it (Fortnite drops what doesn't fit). */
	static void place(ServerPlayer p, ItemStack s) {
		Inventory inv = p.getInventory();
		if (isPickaxe(s)) return; // the pickaxe is re-given in its own slot
		if (Reboot.isCard(s)) {
			Reboot.toOffhand(p, s);
			return;
		}
		boolean ammo = ModItems.isAmmo(s);
		if (ammo) { // into its own slot, up to 99; the rest is dropped
			int slot = slotFor(s);
			if (slot >= 0) {
				ItemStack there = inv.getItem(slot);
				if (there.isEmpty()) {
					int move = Math.min(CAP, s.getCount());
					inv.setItem(slot, s.copyWithCount(move));
					s.shrink(move);
				} else if (there.is(s.getItem())) {
					int move = Math.max(0, Math.min(s.getCount(), CAP - there.getCount()));
					there.grow(move);
					s.shrink(move);
				}
			}
		}
		int size = Math.min(36, inv.getContainerSize());
		for (int i = 0; i < size && !s.isEmpty() && !ammo; i++) {
			String role = role(i);
			if (!role.equals("item")) continue;
			ItemStack there = inv.getItem(i);
			if (there.isEmpty()) {
				inv.setItem(i, s.copy());
				s.setCount(0);
			} else if (ItemStack.isSameItemSameComponents(there, s) && there.getCount() < there.getMaxStackSize()) {
				int move = Math.min(s.getCount(), there.getMaxStackSize() - there.getCount());
				there.grow(move);
				s.shrink(move);
			}
		}
		if (!s.isEmpty()) {
			Containers.dropItemStack(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), s.copy());
			p.sendSystemMessage(Component.literal("Inventory full - dropped " + s.getHoverName().getString()), true);
		}
	}
}
