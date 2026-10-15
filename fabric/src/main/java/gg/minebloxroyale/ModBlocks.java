package gg.minebloxroyale;

import gg.minebloxroyale.match.CarBlock;
import gg.minebloxroyale.match.CarBlockEntity;
import gg.minebloxroyale.match.LootChestBlock;
import gg.minebloxroyale.match.LootChestBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Mineblox Royale's own blocks: the Fortnite-style loot chests and the parked cars. */
public final class ModBlocks {
	/** The gold-trimmed loot chest, and the rare blue one (better loot). Unbreakable; they glow a little while closed. */
	public static Block LOOT_CHEST, RARE_CHEST;
	/** Where each player's game draws a loot chest (its Blender models, the lid swinging open). */
	public static BlockEntityType<LootChestBlockEntity> LOOT_CHEST_ENTITY;
	/** A parked car's invisible body blocks, and the one that keeps its data (CarBlock). */
	public static Block CAR_PART, CAR_CORE;
	public static BlockEntityType<CarBlockEntity> CAR_ENTITY;

	private ModBlocks() {
	}

	static void init() {
		LOOT_CHEST = chest("loot_chest", false, MapColor.GOLD);
		RARE_CHEST = chest("rare_chest", true, MapColor.COLOR_BLUE);
		LOOT_CHEST_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MinebloxRoyale.id("loot_chest"),
			new BlockEntityType<>(LootChestBlockEntity::new, Set.of(LOOT_CHEST, RARE_CHEST)));
		CAR_PART = car("car_part", false);
		CAR_CORE = car("car_core", true);
		CAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MinebloxRoyale.id("car"), new BlockEntityType<>(CarBlockEntity::new, Set.of(CAR_CORE)));
	}

	private static Block chest(String id, boolean rare, MapColor color) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MinebloxRoyale.id(id));
		return Blocks.register(key, p -> new LootChestBlock(p, rare), BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable()
			.sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE).lightLevel(s -> s.getValue(LootChestBlock.OPEN) ? 3 : 7));
	}

	/** Breakable with the pickaxe (each hit is a Harvest hit on the car), never by explosions. */
	private static Block car(String id, boolean core) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MinebloxRoyale.id(id));
		return Blocks.register(key, p -> new CarBlock(p, core), BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.2F, 3600000.0F).noLootTable()
			.sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
	}
}
