package gg.minebloxroyale.match;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Fortnite-style loot chest (creator): the gold-trimmed wooden chest, or the rare one (the blue special chest, in
 * place of the old ender chests). Its own models, closed and open (fabric/tools/models/make_chest_models.py). Nobody
 * opens it with right-click: holding F next to it searches it (LootChests), the lid flips open and the loot pops out
 * onto the ground. A closed chest glitters and chimes now and then, as Fortnite's do.
 */
public class LootChestBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 13.0);
	/** The rare (blue) chest: better loot (Loot.enderContents). */
	public final boolean rare;

	public LootChestBlock(Properties properties, boolean rare) {
		super(properties);
		this.rare = rare;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Right-click: just a reminder of how chests open (F, like Fortnite's interact key). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!state.getValue(OPEN) && player instanceof ServerPlayer sp)
			sp.sendSystemMessage(Component.literal("Hold F to search").withStyle(ChatFormatting.YELLOW), true);
		return InteractionResult.SUCCESS;
	}

	/** Closed: golden (or blue) glints over the lid, and now and then a soft chime. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(OPEN)) return;
		DustParticleOptions glint = new DustParticleOptions(rare ? 0x5AB4FF : 0xFFD54A, 0.7F);
		for (int i = 0; i < 2; i++)
			level.addParticle(glint, pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.7 + random.nextDouble() * 0.4,
				pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, 0.02, 0);
		if (random.nextInt(12) == 0)
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4F,
				rare ? 1.5F : 1.15F + random.nextFloat() * 0.1F, false);
	}
}
