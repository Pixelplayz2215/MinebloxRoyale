package gg.minebloxroyale;

import gg.minebloxroyale.item.ConsumableItem;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.sheets.AmmoDef;
import gg.minebloxroyale.sheets.ConsumableDef;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;

/** One item per weapons / consumables / ammo / materials sheet row, the reboot card, and the creative tab with its icon. */
public final class ModItems {
	public static final Map<String, WeaponItem> WEAPONS = new LinkedHashMap<>();
	public static final Map<String, ConsumableItem> CONSUMABLES = new LinkedHashMap<>();
	public static final Map<String, Item> AMMO = new LinkedHashMap<>();
	public static final Map<String, Item> MATERIALS = new LinkedHashMap<>();
	/** A teammate's reboot card (team modes): carried in the offhand, taken to a reboot van. */
	public static Item REBOOT_CARD;
	/** The Mineblox Royale icon (creator: a brand-new icon, also for the store pictures): the creative tab's picture. */
	public static Item ICON;
	/**
	 * Mineblox Royale's tab in the creative inventory (creator; the development account's /gamemode creative): every gun
	 * at every rarity, then ammo, heals and shields, materials and the reboot card.
	 */
	public static CreativeModeTab TAB;

	private ModItems() {
	}

	static void init() {
		for (WeaponDef w : Sheets.WEAPONS) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MinebloxRoyale.id("gun_" + w.id()));
			WEAPONS.put(w.id(), Registry.register(BuiltInRegistries.ITEM, key, new WeaponItem(w, new Item.Properties().setId(key).stacksTo(1))));
		}
		for (ConsumableDef c : Sheets.CONSUMABLES) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MinebloxRoyale.id(c.id()));
			// The vanilla CONSUMABLE component only supplies Minecraft's drinking/eating animation, sound and particles
			// while the item is used; what it does (and that 1 is used up) is ConsumableItem's own logic.
			var sound = BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace(Sheets.SOUNDS_BY_ID.get(c.useSound()).file()))
				.map(h -> (Holder<SoundEvent>) h)
				.orElse(c.anim().equals("eat") ? SoundEvents.GENERIC_EAT : SoundEvents.GENERIC_DRINK);
			var effects = Consumable.builder().consumeSeconds(c.useTicks() / 20F)
				.animation(c.anim().equals("eat") ? ItemUseAnimation.EAT : ItemUseAnimation.DRINK)
				.sound(sound).hasConsumeParticles(c.anim().equals("eat")).build();
			CONSUMABLES.put(c.id(), Registry.register(BuiltInRegistries.ITEM, key, new ConsumableItem(c,
				new Item.Properties().setId(key).stacksTo(c.maxStack()).component(DataComponents.CONSUMABLE, effects))));
		}
		for (AmmoDef a : Sheets.AMMO) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MinebloxRoyale.id("ammo_" + a.id()));
			AMMO.put(a.id(), Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(99))));
		}
		ResourceKey<Item> card = ResourceKey.create(Registries.ITEM, MinebloxRoyale.id("reboot_card"));
		REBOOT_CARD = Registry.register(BuiltInRegistries.ITEM, card, new Item(new Item.Properties().setId(card).stacksTo(4)));
		for (var m : Sheets.MATERIALS) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MinebloxRoyale.id("mat_" + m.id()));
			MATERIALS.put(m.id(), Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(99))));
		}
		ResourceKey<Item> icon = ResourceKey.create(Registries.ITEM, MinebloxRoyale.id("icon"));
		ICON = Registry.register(BuiltInRegistries.ITEM, icon, new Item(new Item.Properties().setId(icon).stacksTo(1)));
		TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MinebloxRoyale.id("main"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.minebloxroyale.main"))
			.icon(() -> new ItemStack(ICON))
			.displayItems((params, out) -> {
				for (WeaponDef w : Sheets.WEAPONS) for (RarityDef r : Sheets.RARITIES) out.accept(Guns.make(w.id(), r.id()));
				AMMO.values().forEach(out::accept);
				CONSUMABLES.values().forEach(out::accept);
				MATERIALS.values().forEach(out::accept);
				out.accept(REBOOT_CARD);
			})
			.build());
	}

	/** Ammo and materials: the things that live in the inventory part (not the hotbar). */
	public static boolean isAmmo(ItemStack stack) {
		return AMMO.containsValue(stack.getItem()) || MATERIALS.containsValue(stack.getItem());
	}

	public static ItemStack ammo(String ammoId, int count) {
		return new ItemStack(AMMO.get(ammoId), count);
	}
}
