package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Battle Bus: flies a straight route across the island at the world's height limit with every player inside.
 * Players leave it with Space (or at the end of the route) and skydive. Not saved with the world; the match owns it.
 */
public class BattleBusEntity extends Entity {
	public static EntityType<BattleBusEntity> TYPE;
	private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(BattleBusEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(BattleBusEntity.class, EntityDataSerializers.FLOAT);
	private double dirX = 1, dirZ = 0, travelled, routeLength;
	/**
	 * (2.0.0, creator: "make the battle bus smoother while flying in the air? It still stutters jumping back and forth")
	 * In a player's game: the newest position the world sent (a tick or so old by the time it arrives). Minecraft used to
	 * put the bus straight there with every update, while this game also moved it itself every tick, so it jumped ahead
	 * and was pulled back many times a second (worst while the world is busy making the island, when its bus moves
	 * unevenly). Now the bus glides on at its own speed and these only nudge it: see tick.
	 */
	private @Nullable Vec3 sent;

	public BattleBusEntity(EntityType<? extends BattleBusEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MinebloxRoyale.id("battle_bus"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<BattleBusEntity>of(BattleBusEntity::new, MobCategory.MISC).sized(8F, 3F).clientTrackingRange(32).updateInterval(1).build(key));
	}

	public void route(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		routeLength = Math.sqrt(d.x * d.x + d.z * d.z);
		dirX = d.x / routeLength;
		dirZ = d.z / routeLength;
		entityData.set(DIR_X, (float) dirX);
		entityData.set(DIR_Z, (float) dirZ);
		snapTo(from.x, from.y, from.z, (float) Math.toDegrees(Math.atan2(-dirX, dirZ)), 0F);
	}

	/** Back in step with the match's clock (it stopped ticking while far from everyone). */
	public void catchUp(Vec3 at, double travelled) {
		this.travelled = travelled;
		setPos(at.x, getY(), at.z);
	}

	public boolean arrived() {
		return travelled >= routeLength;
	}

	/** 0..1 along the route. */
	public float progress() {
		return routeLength <= 0 ? 1 : (float) Math.min(1, travelled / routeLength);
	}

	@Override
	public void tick() {
		// Only the match's own bus flies. A player saved riding one (the game closed during the ride) brings a copy back
		// with them when the world opens again: it goes, and they're let off.
		if (!level().isClientSide() && MatchManager.get().bus() != this) {
			ejectPassengers();
			discard();
			return;
		}
		super.tick();
		// Both sides move the bus the same way every tick, so riding it is smooth (no waiting for position packets).
		float speed = MinebloxRoyale.setting("busSpeed");
		double step = speed, side = 0;
		if (level().isClientSide()) {
			dirX = entityData.get(DIR_X);
			dirZ = entityData.get(DIR_Z);
			// (2.0.0) Keep in step with the world gently: a little faster when behind where it says, a little slower
			// when ahead, never backwards; across the route, eased back onto the line. Far off (a new ride, a catch-up):
			// straight there.
			Vec3 at = sent;
			if (at != null) {
				double ex = at.x - getX(), ez = at.z - getZ();
				double along = ex * dirX + ez * dirZ, across = -ex * dirZ + ez * dirX;
				if (Math.abs(along) > 40 || Math.abs(across) > 4 || Math.abs(at.y - getY()) > 4) {
					setPos(at);
					xo = xOld = at.x;
					yo = yOld = at.y;
					zo = zOld = at.z;
					sent = null;
					return;
				}
				step = speed + Mth.clamp(along * 0.05, -speed, speed);
				side = across * 0.2;
			}
		}
		xo = getX();
		zo = getZ();
		setPos(getX() + dirX * step - dirZ * side, getY(), getZ() + dirZ * step + dirX * side);
		setYRot((float) Math.toDegrees(Math.atan2(-dirX, dirZ)));
		travelled += speed;
	}

	/**
	 * (2.0.0) The world's position updates are noted, not jumped to (tick follows them gently). The first, and any far
	 * from where the bus is, still move it at once.
	 */
	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return new InterpolationHandler() {
			@Override
			public PositionAndRotation target() {
				Vec3 at = sent;
				return PositionAndRotation.of(at != null ? at : position(), getYRot(), getXRot());
			}

			@Override
			public boolean interpolateTo(PositionPath path, float yRot, float xRot, boolean rotate) {
				if (path == null) return false;
				Vec3 to = path.endPosition();
				if (to.distanceToSqr(position()) > 48 * 48) return false; // (far: Minecraft puts it there at once)
				sent = to;
				return true;
			}

			@Override
			public void interpolate() {
			}

			@Override
			public void applyPredictedMovement(Vec3 movement) {
			}

			@Override
			public boolean hasActiveInterpolation() {
				return false;
			}

			@Override
			public void cancel() {
				sent = null;
			}
		};
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < 16;
	}

	/** Seats spread along the cabin. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		// 16 seats (rooms of 16 players): two rows of 8 along the cabin.
		int i = Math.max(0, getPassengers().indexOf(passenger));
		double along = -3.5 + (i % 8) * 1.0, side = (i / 8) % 2 == 0 ? -0.6 : 0.6;
		double yaw = Math.toRadians(getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw); // forward
		return new Vec3(fx * along - fz * side, 0.4, fz * along + fx * side);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DIR_X, 1F);
		builder.define(DIR_Z, 0F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
