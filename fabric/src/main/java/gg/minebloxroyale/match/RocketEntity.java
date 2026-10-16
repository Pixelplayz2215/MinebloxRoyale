package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * A rocket (v2.0.0, creator: "Fires a slow, visible rocket that explodes where it hits"): flies straight from the rocket
 * launcher at rocketSpeed, trailing flame and smoke, and goes off (Explosions) on the first block or fighter in its way,
 * or after 5 seconds in the air. Its blast hits as hard as its launcher's rarity. Both sides fly it the same way every
 * tick, so it moves smoothly; only the server's goes off. Not saved with the world.
 */
public class RocketEntity extends Entity {
	public static EntityType<RocketEntity> TYPE;
	private static final int LIFE = 100;
	private UUID owner;
	private float mult = 1F;
	private int age;

	public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MinebloxRoyale.id("rocket"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(0.3F, 0.3F).clientTrackingRange(10).updateInterval(1).build(key));
	}

	/**
	 * Fired from a launcher: towards aim (null: where they look), leaving from the launcher's tube over the right shoulder
	 * (2.0.0-test5: it came out of the face).
	 */
	public static void fire(ServerLevel level, LivingEntity shooter, WeaponDef w, RarityDef r, Vec3 aim) {
		Vec3 eye = shooter.getEyePosition();
		Vec3 dir = aim != null && aim.distanceToSqr(eye) > 1e-4 ? aim.subtract(eye).normalize() : shooter.getLookAngle();
		RocketEntity rk = new RocketEntity(TYPE, level);
		rk.owner = shooter.getUUID();
		rk.mult = r.damageMult();
		double yaw = Math.toRadians(shooter.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 start = eye.add(dir.scale(1.0)).add(right.scale(0.3)).add(0, -0.22, 0);
		rk.snapTo(start.x, start.y, start.z, 0F, 0F);
		rk.setDeltaMovement(dir.scale(MinebloxRoyale.setting("rocketSpeed")));
		rk.face();
		level.addFreshEntity(rk);
	}

	private void face() {
		Vec3 v = getDeltaMovement();
		setYRot((float) (Mth.atan2(-v.x, v.z) * (180F / Math.PI)));
		setXRot((float) -(Mth.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * (180F / Math.PI)));
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 from = position(), v = getDeltaMovement(), to = from.add(v);
		if (level() instanceof ServerLevel level) {
			Entity shooter = owner == null ? null : level.getEntity(owner);
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(this, from, end, new AABB(from, end).inflate(0.6),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && (e != shooter || age > 6) && !Teams.sameTeam(shooter, e), from.distanceToSqr(end));
			if (hit != null || block.getType() != HitResult.Type.MISS || ++age > LIFE) {
				Vec3 at = hit != null ? hit.getLocation() : block.getType() != HitResult.Type.MISS ? block.getLocation().subtract(v.normalize().scale(0.3)) : position();
				Explosions.blast(level, at, shooter, MinebloxRoyale.setting("rocketRadius"), MinebloxRoyale.setting("rocketDamage") * mult,
					MinebloxRoyale.setting("rocketBuildDamage"), MinebloxRoyale.setting("rocketCarDamage"));
				discard();
				return;
			}
		} else {
			// the motor's flame and a trail of smoke, behind it
			Vec3 back = v.normalize().scale(-0.45);
			level().addParticle(ParticleTypes.FLAME, getX() + back.x, getY() + back.y, getZ() + back.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
			level().addParticle(ParticleTypes.SMOKE, getX() + back.x * 1.4, getY() + back.y * 1.4, getZ() + back.z * 1.4, 0, 0.01, 0);
			if (age++ % 2 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + back.x * 2, getY() + back.y * 2, getZ() + back.z * 2, 0, 0.02, 0);
		}
		setPos(to.x, to.y, to.z);
		face();
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
