package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.bot.BotEntity;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A car someone drives (v2.0.0, creator: "Drivable cars ... steal cars from the lots"). A parked car (CarBlock) becomes
 * one when someone gets in (F), keeping its type, colours, facing and damage; it stays a driven car
 * after (still in the way, still breakable). The creator's rules:
 * <ul>
 * <li>W A S D drive, Shift gets out, Space boosts: one press, one boost, 3 seconds at three times sprint speed (about 17
 * blocks a second; driving is twice sprint, about 11), costing 30 seconds of fuel; 4 boosts per car, ever.</li>
 * <li>Up to 4 inside: the first one in drives; only their teammates can join them.</li>
 * <li>About 5 minutes of fuel, no way to refuel: out of fuel, it stays where it stopped.</li>
 * <li>No shooting from a car (Guns), no grenades either.</li>
 * <li>Boosting into a building smashes through it (builds, the island's houses and structures); the ground (hills,
 * cliffs) stops it, and it won't drive into water. A moving car doesn't hurt anyone it runs into.</li>
 * <li>600 durability: pickaxe hits, shots, rockets (300) and grenades (100) all count, from anyone not inside; wrecked,
 * everyone inside is thrown out unhurt.</li>
 * </ul>
 * The driver's own game moves the car (like a boat) from their keys; the server keeps the fuel, the boosts and the
 * damage, and smashes what a boosting car runs into. A bot drives it on the server (BotEntity, botInput).
 */
public class CarEntity extends Entity {
	public static EntityType<CarEntity> TYPE;
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TRIM = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> BOOSTS = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> BOOSTING = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
	/** Blocks a tick: driving (about 11 a second), boosting (about 17); how fast it gets going and slows. */
	private static final float SPEED = 0.55F, BOOST_SPEED = 0.85F, ACCEL = 0.035F, BRAKE = 0.05F, COAST = 0.02F;
	/** A boost kicking in (2.0.0-test3: the mod's own, make_audio.py car/boost). */
	private static final net.minecraft.sounds.SoundEvent BOOST_SOUND = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(MinebloxRoyale.id("car.boost"));
	/** Degrees a tick it turns at speed. */
	private static final float TURN = 4.5F;
	public static final int FUEL_TICKS = 5 * 60 * 20, BOOST_TICKS = 60, BOOST_FUEL = 30 * 20, MAX_BOOSTS = 4, SEATS = 4;
	/** The driver's keys in this game (client side): set by the client (its LocalPlayer's input). */
	public static Supplier<Input> localInput = () -> Input.EMPTY;
	private float speed, turning;
	private boolean jumpWas;
	/** A bot driving: its keys (server side). */
	private Input botInput = Input.EMPTY;

	public CarEntity(EntityType<? extends CarEntity> type, Level level) {
		super(type, level);
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MinebloxRoyale.id("car"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<CarEntity>of(CarEntity::new, MobCategory.MISC).sized(2.3F, 1.4F).clientTrackingRange(12).updateInterval(1).build(key));
	}

	/**
	 * Someone gets into a parked car: the car's blocks go and a driven car takes their place, the same car (type,
	 * colours, facing, damage). Null if there was no car there.
	 */
	public static @Nullable CarEntity fromParked(ServerLevel level, BlockPos anyPart) {
		Set<BlockPos> parts = CarBlock.parts(level, anyPart);
		CarBlockEntity data = null;
		for (BlockPos p : parts)
			if (level.getBlockEntity(p) instanceof CarBlockEntity be) data = be;
		if (data == null) return null;
		CarEntity car = new CarEntity(TYPE, level);
		car.entityData.set(KIND, data.kind().ordinal());
		car.entityData.set(COLOR, data.color());
		car.entityData.set(TRIM, data.trim());
		car.entityData.set(DAMAGE, data.damage());
		BlockPos core = data.getBlockPos();
		car.snapTo(core.getX() + 0.5, core.getY(), core.getZ() + 0.5, data.facing().toYRot(), 0F);
		for (BlockPos p : parts) {
			IslandRestore.remember(level, p);
			level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		level.addFreshEntity(car);
		return car;
	}

	/**
	 * F next to a car (Interact): into the nearest driven car within reach, else the parked car next to you (made a
	 * driven car). True if you got in.
	 */
	public static boolean tryEnter(ServerPlayer p) {
		MatchManager mm = MatchManager.get();
		if (p.isPassenger() || !mm.isAlive(p) || Knocked.is(p)) return false;
		ServerLevel level = p.level();
		CarEntity best = null;
		double bd = 3.2 * 3.2;
		for (CarEntity c : level.getEntitiesOfClass(CarEntity.class, p.getBoundingBox().inflate(4))) {
			double d = c.getBoundingBox().distanceToSqr(p.getEyePosition());
			if (d < bd) {
				bd = d;
				best = c;
			}
		}
		if (best == null) {
			BlockPos near = null;
			BlockPos at = p.blockPosition();
			double nd = 3.0 * 3.0;
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, 2, 3)))
				if (level.getBlockState(q).getBlock() instanceof CarBlock && q.distToCenterSqr(p.position()) < nd) {
					nd = q.distToCenterSqr(p.position());
					near = q.immutable();
				}
			if (near != null) best = fromParked(level, near);
		}
		return best != null && p.startRiding(best);
	}

	// ---- what it is ----

	public CarBlockEntity.Kind kind() {
		CarBlockEntity.Kind[] all = CarBlockEntity.Kind.values();
		return all[Math.floorMod(entityData.get(KIND), all.length)];
	}

	public int color() {
		return entityData.get(COLOR);
	}

	public int trim() {
		return entityData.get(TRIM);
	}

	/** Durability left (out of carDurability, 600). */
	public float durabilityLeft() {
		return Math.max(0, CarBlockEntity.durability() - entityData.get(DAMAGE));
	}

	/** How damaged it looks: 1 new ... 4 nearly wrecked (as a parked car). */
	public int stage() {
		float left = durabilityLeft() / CarBlockEntity.durability();
		return left > 0.75F ? 1 : left > 0.5F ? 2 : left > 0.25F ? 3 : 4;
	}

	public int fuelTicks() {
		return entityData.get(FUEL);
	}

	public int boostsLeft() {
		return entityData.get(BOOSTS);
	}

	public boolean boosting() {
		return entityData.get(BOOSTING) > 0;
	}

	// ---- driving ----

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof LivingEntity driver ? driver : null;
	}

	/** A bot behind the wheel sets its keys here each tick. */
	public void setBotInput(Input in) {
		botInput = in;
	}

	@Override
	public void tick() {
		super.tick();
		if (isLocalInstanceAuthoritative()) {
			LivingEntity driver = getControllingPassenger();
			Input in = driver == null || fuelTicks() <= 0 ? Input.EMPTY : level().isClientSide() ? localInput.get() : botInput;
			drive(in);
		} else {
			setDeltaMovement(Vec3.ZERO);
			speed = 0;
		}
		if (level() instanceof ServerLevel level) serverTick(level);
		else effects();
	}

	/** One tick of driving from the keys (whichever game moves the car). */
	private void drive(Input in) {
		float target = in.forward() ? (boosting() ? BOOST_SPEED : SPEED) : in.backward() ? -SPEED * 0.4F : 0;
		float rate = target == 0 ? COAST : Math.signum(target) != Math.signum(speed) && speed != 0 ? BRAKE : ACCEL * (boosting() ? 2 : 1);
		speed += Mth.clamp(target - speed, -rate, rate);
		float steer = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
		turning = steer * TURN * Mth.clamp(Math.abs(speed) / 0.3F, 0, 1) * Math.signum(speed);
		setYRot(getYRot() - turning);
		double yaw = Math.toRadians(getYRot());
		double dx = -Math.sin(yaw) * speed, dz = Math.cos(yaw) * speed;
		// not into water: a car stops at the edge
		BlockPos ahead = BlockPos.containing(getX() + Math.signum(dx) * 1.6 + dx, getY() + 0.1, getZ() + Math.signum(dz) * 1.6 + dz);
		if (!level().getFluidState(ahead).isEmpty() || !level().getFluidState(ahead.below()).isEmpty() && level().getBlockState(ahead.below()).isAir()) {
			dx = dz = 0;
			speed = 0;
		}
		double vy = onGround() ? -0.04 : Math.max(-2.0, getDeltaMovement().y - 0.08);
		Vec3 want = new Vec3(dx, vy, dz);
		Vec3 before = position();
		move(MoverType.SELF, want);
		Vec3 got = position().subtract(before);
		// into a wall or a cliff it can't climb: it stops dead (the server breaks what a boost runs into)
		if (horizontalCollision && Math.hypot(got.x, got.z) < Math.hypot(dx, dz) * 0.5) speed *= 0.2F;
		setDeltaMovement(got.x, onGround() ? 0 : vy, got.z);
	}

	@Override
	public float maxUpStep() {
		return 1.0F; // (over kerbs, steps and gentle slopes; a block and more stops it)
	}

	/** The server's part: a new boost on a Space press, fuel used, what a boost runs into smashed. */
	private void serverTick(ServerLevel level) {
		LivingEntity driver = getControllingPassenger();
		Input in = driver instanceof ServerPlayer sp ? sp.getLastClientInput() : driver != null ? botInput : Input.EMPTY;
		int fuel = fuelTicks(), boost = entityData.get(BOOSTING);
		if (in.jump() && !jumpWas && boost == 0 && boostsLeft() > 0 && fuel > 0) {
			entityData.set(BOOSTS, boostsLeft() - 1);
			entityData.set(BOOSTING, BOOST_TICKS);
			fuel = Math.max(0, fuel - BOOST_FUEL); // (with less than 30 s left, a boost uses up what's left)
			level.playSound(null, getX(), getY(), getZ(), BOOST_SOUND, SoundSource.NEUTRAL, 1.2F, 1.0F); // (2.0.0-test3: its own sound)
		}
		jumpWas = in.jump();
		if (boost > 0) {
			entityData.set(BOOSTING, boost - 1);
			smashAhead(level);
		}
		if (driver != null && (in.forward() || in.backward()) && fuel > 0) fuel--;
		if (fuel != fuelTicks()) entityData.set(FUEL, fuel);
	}

	/** Boosting: builds and the island's buildings just ahead of the car break (never the ground). */
	private void smashAhead(ServerLevel level) {
		double yaw = Math.toRadians(getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = fz, rz = -fx;
		for (double along = 1.4; along <= 3.6; along += 0.5)
			for (double side = -1.1; side <= 1.1; side += 0.55)
				for (int up = 0; up <= 2; up++) {
					BlockPos p = BlockPos.containing(getX() + fx * along + rx * side, getY() + 0.3 + up, getZ() + fz * along + rz * side);
					if (Builds.isBuilt(p)) Builds.breakPiece(level, p);
					else if (Explosions.breakable(level.getBlockState(p))) {
						IslandRestore.remember(level, p);
						level.destroyBlock(p, false, getControllingPassenger());
					}
				}
	}

	/** Each player's game: smoke from a nearly wrecked engine, flame out of the back while boosting. */
	private void effects() {
		var r = level().getRandom();
		double yaw = Math.toRadians(getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		if (stage() >= 4 && r.nextFloat() < 0.4F)
			level().addParticle(ParticleTypes.SMOKE, getX() + fx * 1.6, getY() + 1.2, getZ() + fz * 1.6, 0, 0.05, 0);
		if (boosting())
			for (int i = 0; i < 2; i++)
				level().addParticle(i == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, getX() - fx * 2.2, getY() + 0.45, getZ() - fz * 2.2, -fx * 0.2, 0.02, -fz * 0.2);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	// ---- getting in and out ----

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		if (getPassengers().size() >= SEATS) return false;
		// (once someone's in, only their teammates can join them)
		Entity first = getFirstPassenger();
		return first == null || Teams.sameTeam(first, passenger);
	}

	/** Getting in is F (tryEnter), never a right click: right mouse aims a gun, which shouldn't pull you into a car. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		return InteractionResult.PASS;
	}

	/** The seats: the driver front left, then front right, back left, back right (hips on the seat). */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int i = Math.max(0, getPassengers().indexOf(passenger));
		double side = i % 2 == 0 ? 0.42 : -0.42, along = i < 2 ? 0.15 : -0.85;
		double yaw = Math.toRadians(getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		return new Vec3(fx * along + fz * side, 0.42, fz * along - fx * side);
	}

	/** Passengers turn with the car (and can look round, as in a boat). */
	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger.isLocalInstanceAuthoritative()) {
			passenger.setYRot(passenger.getYRot() - turning);
			passenger.setYHeadRot(passenger.getYHeadRot() - turning);
		}
		if (passenger instanceof LivingEntity le) le.setYBodyRot(getYRot());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		// out of their own side, else anywhere round the car that's free
		int i = Math.max(0, getPassengers().indexOf(passenger));
		for (float side : i % 2 == 0 ? new float[] { 90, -90, 180, 0 } : new float[] { -90, 90, 180, 0 }) {
			Vec3 dir = getCollisionHorizontalEscapeVector(getBbWidth() * 1.2, passenger.getBbWidth(), getYRot() + side);
			Vec3 at = new Vec3(getX() + dir.x, getY(), getZ() + dir.z);
			if (level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(at))) return at;
		}
		return new Vec3(getX(), getBoundingBox().maxY, getZ());
	}

	// ---- in the way, and breakable ----

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	/** A hit with the pickaxe (or a punch): 100 damage and 10 metal for whoever's outside it, as a parked car. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!(source.getEntity() instanceof LivingEntity by) || hasPassenger(by)) return false;
		if (by instanceof ServerPlayer p) Harvest.giveCarMetal(p);
		else if (by instanceof BotEntity) Harvest.giveCarMetalBot(by);
		damage(level, CarBlockEntity.durability() / 6F, by);
		level.playSound(null, getX(), getY() + 0.5, getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35F, 1.6F);
		return true;
	}

	/** Shots, rockets and grenades (and pickaxe hits): damage that adds up; at its durability the car is wrecked. */
	public void damage(ServerLevel level, float amount, @Nullable Entity by) {
		if (by != null && hasPassenger(by)) return; // (whoever's inside can't hurt their own car)
		float total = entityData.get(DAMAGE) + amount;
		entityData.set(DAMAGE, total);
		if (total < CarBlockEntity.durability()) {
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.8, getZ(), 3, 0.6, 0.3, 0.6, 0.2);
			return;
		}
		// wrecked: everyone thrown out unhurt, a puff of smoke
		ejectPassengers();
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 3, 1.0, 0.5, 1.0, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8, getZ(), 20, 1.0, 0.5, 1.0, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.3F);
		discard();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(COLOR, 0xFFD8352C);
		builder.define(TRIM, 0xFFF2F4F8);
		builder.define(DAMAGE, 0F);
		builder.define(FUEL, FUEL_TICKS);
		builder.define(BOOSTS, MAX_BOOSTS);
		builder.define(BOOSTING, 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	/** Cars are the match's: a new island next time. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
