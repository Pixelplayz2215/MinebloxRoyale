package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModBlocks;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Fortnite-style chests (creator): placed on bare ground, searched by holding F (players, Interact; bots, BotEntity),
 * then the lid opens and the loot pops out onto the ground. Every other chest in the world becomes one too.
 */
public final class LootChests {
	/** Vanilla structure chests seen as their chunks load, turned into loot chests a few a tick. */
	private static final Queue<BlockPos> VANILLA = new ConcurrentLinkedQueue<>();

	private LootChests() {
	}

	public static boolean is(BlockState s) {
		return s.getBlock() instanceof LootChestBlock;
	}

	/** A loot chest that hasn't been searched yet. */
	public static boolean closed(Level level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return is(s) && !s.getValue(LootChestBlock.OPEN);
	}

	/** Seconds a search takes (match sheet chestSearchSeconds), in ticks. */
	public static int searchTicks() {
		return Math.max(1, Math.round(MinebloxRoyale.setting("chestSearchSeconds") * 20));
	}

	/** Puts a closed chest (rare: the blue one) at a free, dry spot, facing a random way. False if it can't go there. */
	static boolean place(ServerLevel level, BlockPos at, boolean rare, RandomSource rnd) {
		if (at.getY() <= level.getMinY() + 1 || !level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty()) return false;
		BlockState chest = (rare ? ModBlocks.RARE_CHEST : ModBlocks.LOOT_CHEST).defaultBlockState()
			.setValue(LootChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(rnd));
		level.setBlock(at, chest, 3);
		return true;
	}

	/**
	 * Where a loose chest can stand near (x, z): on bare natural ground (grass, dirt, sand, stone, snow...), never on a
	 * roof, a tree, in a building, or in or on water (creator). A few spots close by are tried; null if none will do.
	 */
	static BlockPos groundSpot(ServerLevel level, int x, int z, RandomSource rnd) {
		for (int i = 0; i < 8; i++) {
			int px = x + (i == 0 ? 0 : rnd.nextInt(13) - 6), pz = z + (i == 0 ? 0 : rnd.nextInt(13) - 6);
			if (!level.hasChunk(px >> 4, pz >> 4)) continue;
			BlockPos at = new BlockPos(px, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz), pz), below = at.below();
			if (!naturalGround(level.getBlockState(below)) || !level.getFluidState(at).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
			if (!level.getBlockState(at).canBeReplaced() || CustomStructures.contains(at) || CustomStructures.contains(below) || Harvest.inStructure(level, below))
				continue;
			return at;
		}
		return null;
	}

	private static boolean naturalGround(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.SNOW)
			|| s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND);
	}

	/**
	 * Searched: the lid opens and its loot (rolled now: a rare chest's is better) pops out of the front onto the ground,
	 * spread a little, the way Fortnite's chests spill. `by` is who searched it (for nothing yet but the log).
	 */
	public static void open(ServerLevel level, BlockPos pos, LivingEntity by) {
		BlockState state = level.getBlockState(pos);
		if (!is(state) || state.getValue(LootChestBlock.OPEN)) return;
		boolean rare = ((LootChestBlock) state.getBlock()).rare;
		level.setBlock(pos, state.setValue(LootChestBlock.OPEN, true), 3);
		RandomSource rnd = level.getRandom();
		List<ItemStack> loot = rare ? Loot.enderContents(rnd) : Loot.chestContents(rnd);
		Direction front = state.getValue(LootChestBlock.FACING);
		double side = -(loot.size() - 1) / 2.0;
		for (ItemStack s : loot) {
			// Out over the front edge, fanned from side to side.
			double fx = front.getStepX(), fz = front.getStepZ(), sx = -fz, sz = fx;
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5 + fx * 0.45, pos.getY() + 0.85, pos.getZ() + 0.5 + fz * 0.45, s);
			double spread = side++ * 0.07 + (rnd.nextDouble() - 0.5) * 0.04, out = 0.14 + rnd.nextDouble() * 0.08;
			item.setDeltaMovement(new Vec3(fx * out + sx * spread, 0.26 + rnd.nextDouble() * 0.06, fz * out + sz * spread));
			item.setPickUpDelay(8);
			level.addFreshEntity(item);
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8F, rare ? 1.25F : 1.05F);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, rare ? 1.6F : 1.3F);
		level.sendParticles(new DustParticleOptions(rare ? 0x5AB4FF : 0xFFD54A, 1.0F), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 24, 0.3, 0.2, 0.3, 0.02);
		MinebloxRoyale.LOG.debug("BR: {} searched a {} chest at {}", by.getName().getString(), rare ? "rare" : "loot", pos);
	}

	/**
	 * The closed chest this fighter is searching with F: the nearest one in front of them within reach of their eyes
	 * (or null).
	 */
	public static BlockPos target(LivingEntity e) {
		Vec3 eye = e.getEyePosition(), look = e.getLookAngle();
		double reach = MinebloxRoyale.setting("chestReach");
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		int r = (int) Math.ceil(reach);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(eye).offset(-r, -r, -r), BlockPos.containing(eye).offset(r, r, r))) {
			if (!closed(e.level(), p)) continue;
			Vec3 to = Vec3.atCenterOf(p).subtract(eye);
			double d = to.length();
			if (d > reach + 0.5) continue;
			double facing = to.normalize().dot(look);
			if (facing < 0.35 && d > 1.4) continue; // behind you (unless right at your feet)
			double score = d - facing * 2;
			if (score < bestScore) {
				bestScore = score;
				best = p.immutable();
			}
		}
		return best;
	}

	// ---- village and other structure chests ----

	/**
	 * Every chest in the world (creator: "ALL chests"): plain, trapped and copper chests, and ender chests (as the rare
	 * blue one), wherever they are (villages, outposts, ruins...), are noted as their chunks load and become loot
	 * chests. (The hub has none.)
	 */
	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level != level.getServer().overworld()) return;
			for (BlockEntity be : chunk.getBlockEntities().values())
				if (vanillaChest(be.getBlockState())) VANILLA.add(be.getBlockPos());
		});
	}

	private static boolean vanillaChest(BlockState s) {
		return s.getBlock() instanceof ChestBlock || s.getBlock() instanceof EnderChestBlock;
	}

	/**
	 * Every tick: a few noted chests become closed loot chests facing the same way (their own contents are cleared first,
	 * so nothing spills; they roll Fortnite loot when searched). Ones under water are just removed: chests only stand on
	 * dry ground (creator).
	 */
	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int n = 0; n < 8 && !VANILLA.isEmpty(); n++) {
			BlockPos pos = VANILLA.poll();
			if (level.isLoaded(pos)) convert(level, pos);
		}
	}

	private static void convert(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!vanillaChest(s)) return;
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity c) {
			c.setLootTable(null);
			c.clearContent();
		}
		boolean wet = s.hasProperty(ChestBlock.WATERLOGGED) && s.getValue(ChestBlock.WATERLOGGED) || !level.getFluidState(pos.above()).isEmpty();
		if (wet) {
			level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
			return;
		}
		boolean rare = s.getBlock() instanceof EnderChestBlock;
		level.setBlock(pos, (rare ? ModBlocks.RARE_CHEST : ModBlocks.LOOT_CHEST).defaultBlockState()
			.setValue(LootChestBlock.FACING, s.getValue(HorizontalDirectionalBlock.FACING)), 2);
		// The other half of a double chest becomes a chest of its own (half a vanilla double chest would look broken).
		if (s.hasProperty(ChestBlock.TYPE) && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) convert(level, pos.relative(ChestBlock.getConnectedDirection(s)));
	}
}
