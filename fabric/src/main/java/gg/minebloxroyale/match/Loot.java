package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.item.Guns;
import gg.minebloxroyale.item.WeaponItem;
import gg.minebloxroyale.sheets.LootDef;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.TerrainRuleDef;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Loot rolled from the loot sheet; weapons also roll a rarity (grey to gold). The loot chests (LootChests) roll it when
 * searched; the rare blue ones hold better loot: legendary and mythic weapons, often a Chug Jug. Every other vanilla
 * container left (tent barrels...) gets Fortnite loot instead of vanilla loot.
 */
public final class Loot {
	private Loot() {
	}

	/**
	 * Puts each item in a random empty slot (a random slot each, as before, sometimes landed two items in one slot and
	 * the first was lost: a chest showed fewer items than it rolled).
	 */
	private static void fill(Container c, List<ItemStack> items, RandomSource rnd) {
		int size = c.getContainerSize();
		for (ItemStack s : items) {
			int start = rnd.nextInt(size);
			for (int i = 0; i < size; i++) {
				int slot = (start + i) % size;
				if (!c.getItem(slot).isEmpty()) continue;
				c.setItem(slot, s);
				break;
			}
		}
	}

	static <T> T weighted(List<T> rows, ToIntFunction<T> weight, RandomSource rnd) {
		int total = rows.stream().mapToInt(weight).sum();
		int roll = rnd.nextInt(total);
		for (T t : rows) {
			roll -= weight.applyAsInt(t);
			if (roll < 0) return t;
		}
		return rows.getLast();
	}

	public static ItemStack roll(RandomSource rnd) {
		LootDef l = weighted(Sheets.LOOT, LootDef::weight, rnd);
		if (l.kind().equals("weapon")) {
			RarityDef r = weighted(Sheets.RARITIES, RarityDef::lootWeight, rnd);
			return Guns.make(l.weapon(), r.id());
		}
		if (l.kind().equals("ammo")) return ModItems.ammo(l.ammo(), Sheets.AMMO_BY_ID.get(l.ammo()).pickup() * l.count());
		return new ItemStack(ModItems.CONSUMABLES.get(l.consumable()), l.count());
	}

	/** Fortnite chest contents: chestRolls entries, plus a box of ammo for every weapon. */
	public static List<ItemStack> chestContents(RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		int rolls = Math.round(MinebloxRoyale.setting("chestRolls"));
		for (int i = 0; i < rolls; i++) {
			ItemStack s = roll(rnd);
			out.add(s);
			if (s.getItem() instanceof WeaponItem w)
				out.add(ModItems.ammo(w.def.ammo(), Sheets.AMMO_BY_ID.get(w.def.ammo()).pickup() * 2));
		}
		return out;
	}

	/**
	 * Rare (blue) chest loot (creator; the old ender chests): two legendary or mythic weapons with ammo, usually a Chug
	 * Jug, plus a shield item.
	 */
	public static List<ItemStack> enderContents(RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		List<LootDef> weapons = Sheets.LOOT.stream().filter(l -> l.kind().equals("weapon")).toList();
		for (int i = 0; i < 2 && !weapons.isEmpty(); i++) {
			LootDef l = weighted(weapons, LootDef::weight, rnd);
			ItemStack gun = Guns.make(l.weapon(), rnd.nextFloat() < 0.4F ? "mythic" : "legendary");
			out.add(gun);
			if (gun.getItem() instanceof WeaponItem w)
				out.add(ModItems.ammo(w.def.ammo(), Sheets.AMMO_BY_ID.get(w.def.ammo()).pickup() * 3));
		}
		if (rnd.nextFloat() < 0.75F) out.add(new ItemStack(ModItems.CONSUMABLES.get("chug_jug"), 1));
		out.add(new ItemStack(ModItems.CONSUMABLES.get(rnd.nextBoolean() ? "shield_potion" : "slurp_juice"), 1));
		return out;
	}

	/**
	 * Opening any container that still has a vanilla loot table (tent barrels...) fills it with Fortnite loot first.
	 * (Structure chests themselves become loot chests: LootChests.)
	 */
	public static void initContainers() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && !player.isSpectator()) swapVanilla(level, hit.getBlockPos());
			return InteractionResult.PASS;
		});
	}

	/** A container with a vanilla loot table: drop the table and fill it with Fortnite chest loot instead. */
	public static void swapVanilla(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof RandomizableContainer rc) || rc.getLootTable() == null) return;
		rc.setLootTable(null);
		if (!(rc instanceof Container c)) return;
		c.clearContent();
		fill(c, chestContents(level.getRandom()), level.getRandom());
	}

	/**
	 * Terrain sheet replace_loot_prefix: vanilla structure chests (villages, ruined portals...) roll Fortnite loot
	 * instead.
	 */
	public static void initStructureLoot() {
		List<String> prefixes = Sheets.TERRAIN.stream().filter(r -> r.kind().equals("replace_loot_prefix")).map(TerrainRuleDef::value).toList();
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			String path = table.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			if (prefixes.stream().noneMatch(path::startsWith)) return;
			drops.clear();
			drops.addAll(chestContents(context.getRandom()));
		});
	}

	/** Match over: a chest the match placed goes (searched or not). */
	static void clearChest(ServerLevel level, BlockPos at) {
		if (LootChests.is(level.getBlockState(at))) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
	}

	static void dropInventory(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) Containers.dropItemStack(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), s.copy());
		}
		inv.clearContent();
	}
}
