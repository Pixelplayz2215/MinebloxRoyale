package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown grenade (v2.0.0, creator: "Thrown with left click in an arc, bounce, and explode after a short fuse: area
 * damage and they break builds"): it flies in an arc, bounces off the ground and walls (losing speed each time, with a
 * knock), rolls to a stop, and goes off (Explosions) grenadeFuseTicks after the throw. Both sides move it the same way;
 * only the server's goes off. Not saved with the world.
 */
public class GrenadeEntity extends Entity {
	public static EntityType<GrenadeEntity> TYPE;
	private UUID owner;
	private int age;

	public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
		super(type, level);
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MinebloxRoyale.id("grenade"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build(key));
	}

	private static final java.util.Map<UUID, Long> LAST = new java.util.HashMap<>();

	/**
	 * A player throws the grenade in their hand (the throw action): not knocked, not in a car, not too soon after the
	 * last; one is used up (creative keeps them).
	 */
	public static void tryThrow(net.minecraft.server.level.ServerPlayer p, Vec3 aim) {
		var stack = p.getMainHandItem();
		if (!stack.is(gg.minebloxroyale.ModItems.GRENADE) || p.isSpectator() || Knocked.is(p) || p.getVehicle() != null) return;
		ServerLevel level = p.level();
		long now = level.getGameTime();
		if (now - LAST.getOrDefault(p.getUUID(), -1000L) < MinebloxRoyale.setting("grenadeCooldownTicks")) return;
		LAST.put(p.getUUID(), now);
		throwFrom(level, p, aim);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		if (!p.isCreative()) stack.shrink(1);
	}

	/** Thrown by `who` towards `at` (where they aim): in an arc, a little upwards. */
	public static void throwFrom(ServerLevel level, LivingEntity who, Vec3 at) {
		Vec3 eye = who.getEyePosition();
		Vec3 dir = at != null && at.distanceToSqr(eye) > 1e-4 ? at.subtract(eye).normalize() : who.getLookAngle();
		GrenadeEntity g = new GrenadeEntity(TYPE, level);
		g.owner = who.getUUID();
		Vec3 start = eye.add(dir.scale(0.6)).add(0, -0.2, 0);
		g.snapTo(start.x, start.y, start.z, who.getYRot(), 0F);
		g.setDeltaMovement(dir.scale(MinebloxRoyale.setting("grenadeThrow")).add(0, 0.2, 0).add(who.getDeltaMovement().multiply(1, 0, 1)));
		level.addFreshEntity(g);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 want = getDeltaMovement().add(0, -0.05, 0);
		Vec3 before = position();
		move(MoverType.SELF, want);
		Vec3 got = position().subtract(before);
		// bounces: what the move stopped short is turned back, softer
		double vx = want.x, vy = want.y, vz = want.z;
		boolean knock = false;
		if (Math.abs(got.x - want.x) > 1e-4) {
			vx = -want.x * 0.45;
			knock |= Math.abs(want.x) > 0.08;
		}
		if (Math.abs(got.z - want.z) > 1e-4) {
			vz = -want.z * 0.45;
			knock |= Math.abs(want.z) > 0.08;
		}
		if (Math.abs(got.y - want.y) > 1e-4) {
			knock |= Math.abs(want.y) > 0.12;
			vy = want.y < 0 && Math.abs(want.y) > 0.12 ? -want.y * 0.4 : 0;
			vx *= 0.75;
			vz *= 0.75;
		}
		setDeltaMovement(vx * 0.99, vy * 0.99, vz * 0.99);
		if (level() instanceof ServerLevel level) {
			if (knock) Net.sfxAll(level.getServer(), "grenade_bounce", position());
			if (++age >= MinebloxRoyale.setting("grenadeFuseTicks")) {
				Entity thrower = owner == null ? null : level.getEntity(owner);
				Explosions.blast(level, position().add(0, 0.2, 0), thrower, MinebloxRoyale.setting("grenadeRadius"), MinebloxRoyale.setting("grenadeDamage"),
					MinebloxRoyale.setting("grenadeBuildDamage"), MinebloxRoyale.setting("grenadeCarDamage"));
				discard();
			}
		} else age++;
	}

	/** Spinning while it flies (the renderer's turn), resting when it lies still. */
	public float spin(float partialTicks) {
		Vec3 v = getDeltaMovement();
		return (age + partialTicks) * (float) Math.min(40, v.length() * 120);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
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
