package gg.minebloxroyale.match;

import gg.minebloxroyale.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A loot chest's spot for each player's game to draw it (ChestRenderer: the Blender models, the lid swinging open when
 * it's searched). It keeps nothing: open or closed, which way it faces and which chest it is are all in the block.
 */
public class LootChestBlockEntity extends BlockEntity {
	public LootChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.LOOT_CHEST_ENTITY, pos, state);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
