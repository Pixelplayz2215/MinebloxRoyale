package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A parked car's data, kept in its core block (CarBlock) and sent to every player's game, which draws the car: its type
 * (CarBlockEntity.Kind), body and trim colours, the way its nose points, and the damage it has taken.
 */
public class CarBlockEntity extends BlockEntity {
	/**
	 * The car types (creator: other types of cars too), in Fortnite's style, with where the engine smokes when the car is
	 * nearly wrecked (over the buckled bonnet: height and how far forward of the middle, in blocks).
	 */
	public enum Kind {
		SEDAN(1.24F, 1.67F), SPORTS(1.05F, 1.59F), PICKUP(1.33F, 1.82F), SUV(1.35F, 1.84F), HATCHBACK(1.17F, 1.49F), POLICE(1.24F, 1.67F), TAXI(1.24F, 1.67F);

		final float engineY, engineZ;

		Kind(float engineY, float engineZ) {
			this.engineY = engineY;
			this.engineZ = engineZ;
		}
	}

	private int kind, color = 0xFFD8352C, trim = 0xFFF2F4F8, facing = Direction.NORTH.get2DDataValue();
	/** Damage taken so far (creator: it adds up and stays between hits until the car is wrecked). */
	private float damage;

	public CarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CAR_ENTITY, pos, state);
	}

	public void setup(Kind kind, int color, int trim, Direction facing) {
		this.kind = kind.ordinal();
		this.color = color;
		this.trim = trim;
		this.facing = facing.get2DDataValue();
		setChanged();
	}

	public Kind kind() {
		Kind[] all = Kind.values();
		return all[Math.floorMod(kind, all.length)];
	}

	public int color() {
		return color;
	}

	public int trim() {
		return trim;
	}

	/** The way the car's nose points. */
	public Direction facing() {
		return Direction.from2DDataValue(facing);
	}

	/** Damage a car takes before it's wrecked (match sheet carDurability, 600). */
	public static float durability() {
		return MinebloxRoyale.setting("carDurability");
	}

	/**
	 * How damaged the car looks (creator: the model changes as it's hit): 1 new, 2 scuffed (450 or less left), 3 damaged
	 * (300 or less), 4 nearly wrecked (150 or less).
	 */
	public int stage() {
		float left = 1F - damage / durability();
		return left > 0.75F ? 1 : left > 0.5F ? 2 : left > 0.25F ? 3 : 4;
	}

	/** More damage; returns the total. Every player's game is told, so the car shows it. */
	float addDamage(float amount) {
		damage += amount;
		setChanged();
		if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		return damage;
	}

	/** In each player's game: a nearly wrecked car smokes from its engine. */
	void smoke(Level level) {
		RandomSource rnd = level.getRandom();
		if (stage() < 4 || rnd.nextFloat() > 0.4F) return;
		Kind k = kind();
		Direction nose = facing();
		double x = getBlockPos().getX() + 0.5 + nose.getStepX() * k.engineZ + (rnd.nextDouble() - 0.5) * 0.4;
		double z = getBlockPos().getZ() + 0.5 + nose.getStepZ() * k.engineZ + (rnd.nextDouble() - 0.5) * 0.4;
		double y = getBlockPos().getY() + k.engineY;
		level.addParticle(rnd.nextFloat() < 0.3F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0, 0.04 + rnd.nextDouble() * 0.03, 0);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		kind = input.getIntOr("kind", 0);
		color = input.getIntOr("color", color);
		trim = input.getIntOr("trim", trim);
		facing = input.getIntOr("facing", facing);
		// (Cars saved before durability counted pickaxe hits: 6 of them wrecked a car.)
		damage = input.getFloatOr("damage", input.getIntOr("hits", 0) * durability() / 6F);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("kind", kind);
		output.putInt("color", color);
		output.putInt("trim", trim);
		output.putInt("facing", facing);
		output.putFloat("damage", damage);
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
