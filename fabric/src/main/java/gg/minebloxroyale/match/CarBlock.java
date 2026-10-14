package gg.minebloxroyale.match;

import gg.minebloxroyale.sheets.HarvestRuleDef;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A parked car's body (creator: smooth models like the reboot van and Battle Bus, but cars break for metal). The car is
 * a block of these invisible blocks (full, or half height over a low bonnet or bed) that you bump into and stand on; one
 * of them, the core, keeps the car's type, colours and facing (CarBlockEntity) and every player's game draws the smooth
 * car there (CarRenderer: its Blender model). A car has a durability (match sheet carDurability, 600): every pickaxe hit on any
 * part and every gun shot that hits it adds damage, which stays (creator: it adds up between hits), the car looks more
 * and more damaged as it goes, and when the damage reaches the durability the whole car is wrecked. Pickaxe hits also
 * give metal (harvest sheet h_car: perBlock = metal a hit, maxBlocks = hits a new car takes from the pickaxe alone).
 */
public class CarBlock extends Block implements EntityBlock {
	/** Half height (the top of a low bonnet, boot or pickup bed). */
	public static final BooleanProperty LOW = BooleanProperty.create("low");
	private static final VoxelShape HALF = Block.box(0, 0, 0, 16, 8, 16);
	/** The block that keeps the car's data (one a car). */
	public final boolean core;

	public CarBlock(Properties properties, boolean core) {
		super(properties);
		this.core = core;
		registerDefaultState(stateDefinition.any().setValue(LOW, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LOW);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(LOW) ? HALF : super.getShape(state, level, pos, context);
	}

	/** Invisible: the car is drawn as one smooth model by each player's game. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return core ? new CarBlockEntity(pos, state) : null;
	}

	/** In each player's game: a nearly wrecked car smokes from its engine. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (!level.isClientSide() || !core) return null;
		return (l, p, s, be) -> {
			if (be instanceof CarBlockEntity car) car.smoke(l);
		};
	}

	/** The car this block belongs to: every car block joined to it (cars in a lot stand a block apart). */
	static Set<BlockPos> parts(Level level, BlockPos start) {
		Set<BlockPos> out = new LinkedHashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		q.add(start.immutable());
		while (!q.isEmpty() && out.size() < 80) {
			BlockPos p = q.poll();
			if (out.contains(p) || !(level.getBlockState(p).getBlock() instanceof CarBlock)) continue;
			out.add(p);
			for (Direction d : Direction.values()) q.add(p.relative(d));
		}
		return out;
	}

	/**
	 * A pickaxe hit on a car: carDurability / maxBlocks of h_car damage (100), metal for the hitter (perBlock of h_car)
	 * and a clank. Returns the materials this hit gives.
	 */
	static Map<String, Float> hit(ServerLevel level, BlockPos at, HarvestRuleDef r) {
		damage(level, at, CarBlockEntity.durability() / r.maxBlocks(), true);
		return Map.of(r.material(), r.perBlock());
	}

	/** A gun shot that hit a car: its damage counts against the car's durability (no metal). */
	public static void shot(ServerLevel level, BlockPos at, float amount) {
		damage(level, at, amount, false);
	}

	/** Adds damage to the car `at` belongs to; once the damage reaches its durability, the whole car comes down. */
	private static void damage(ServerLevel level, BlockPos at, float amount, boolean pickaxe) {
		Set<BlockPos> car = parts(level, at);
		CarBlockEntity data = null;
		for (BlockPos p : car)
			if (level.getBlockEntity(p) instanceof CarBlockEntity be) data = be;
		Vec3 c = Vec3.atCenterOf(at);
		if (data != null && data.addDamage(amount) < CarBlockEntity.durability()) {
			float pitch = 1.5F + level.getRandom().nextFloat() * 0.3F;
			level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, pickaxe ? 0.35F : 0.15F, pickaxe ? pitch : pitch + 0.3F);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, pickaxe ? 8 : 3, 0.3, 0.3, 0.3, 0.2);
			return;
		}
		for (BlockPos p : car) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
		Vec3 mid = data == null ? c : Vec3.atBottomCenterOf(data.getBlockPos());
		level.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y + 0.8, mid.z, 3, 1.0, 0.5, 1.0, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y + 0.8, mid.z, 20, 1.0, 0.5, 1.0, 0.02);
		level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.3F);
	}
}
