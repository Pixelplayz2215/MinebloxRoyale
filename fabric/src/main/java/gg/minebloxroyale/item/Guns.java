package gg.minebloxroyale.item;

import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.hub.HubFun;
import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.match.CarBlock;
import gg.minebloxroyale.match.Combat;
import gg.minebloxroyale.match.Knocked;
import gg.minebloxroyale.match.MatchManager;
import gg.minebloxroyale.match.Teams;
import gg.minebloxroyale.net.Net;
import gg.minebloxroyale.sheets.RarityDef;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.SoundDef;
import gg.minebloxroyale.sheets.WeaponDef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Gun state on the item (rarity, ammo, reload) and hitscan shooting shared by players and bots. */
public final class Guns {
	private Guns() {
	}

	public static ItemStack make(String weaponId, String rarityId) {
		WeaponDef w = Sheets.WEAPONS_BY_ID.get(weaponId);
		RarityDef r = Sheets.RARITIES_BY_ID.get(rarityId);
		ItemStack stack = new ItemStack(ModItems.WEAPONS.get(weaponId));
		CompoundTag tag = new CompoundTag();
		tag.putString("rarity", r.id());
		tag.putInt("ammo", w.magazine());
		tag.putLong("reloadUntil", 0L);
		tag.putLong("nextShot", 0L);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(r.name() + " " + w.name())
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(r.color())).withItalic(false)));
		// The model's accent parts (and the icon's stripe) are drawn in the rarity colour (items/gun_*.json tints).
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(r.color())));
		return stack;
	}

	public static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	static void save(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static RarityDef rarity(ItemStack stack) {
		return Sheets.RARITIES_BY_ID.getOrDefault(tag(stack).getStringOr("rarity", "common"), Sheets.RARITIES.get(0));
	}

	public static int ammo(ItemStack stack) {
		return tag(stack).getIntOr("ammo", 0);
	}

	public static boolean reloading(ItemStack stack, long now) {
		return tag(stack).getLongOr("reloadUntil", 0L) > now;
	}

	/** Ammo of this type in a player's inventory (bots have endless ammo). */
	public static int reserve(LivingEntity who, WeaponDef w) {
		if (!(who instanceof Player p)) return Integer.MAX_VALUE;
		var item = ModItems.AMMO.get(w.ammo());
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	private static int takeReserve(Player p, WeaponDef w, int want) {
		var item = ModItems.AMMO.get(w.ammo());
		int got = 0;
		for (int i = p.getInventory().getContainerSize() - 1; i >= 0 && got < want; i--) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.is(item)) continue;
			int take = Math.min(want - got, s.getCount());
			s.shrink(take);
			got += take;
		}
		return got;
	}

	public static void startReload(LivingEntity who, ItemStack stack, WeaponDef w) {
		long now = who.level().getGameTime();
		CompoundTag tag = tag(stack);
		if (tag.getLongOr("reloadUntil", 0L) > now || tag.getIntOr("ammo", 0) >= w.magazine()) return;
		if (reserve(who, w) <= 0) {
			if (who instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("No " + Sheets.AMMO_BY_ID.get(w.ammo()).name() + "!").withStyle(ChatFormatting.RED), true);
			return;
		}
		int ticks = Math.round(w.reloadTicks() * rarity(stack).reloadMult());
		tag.putLong("reloadUntil", now + ticks);
		save(stack, tag);
		playSheetSound(who, w.reloadSound());
	}

	/**
	 * Called every tick for guns in an inventory: finishes reloads, taking ammo from the inventory (Fortnite rules).
	 */
	public static void tickReload(ItemStack stack, WeaponDef w, long now, LivingEntity owner) {
		CompoundTag tag = tag(stack);
		long until = tag.getLongOr("reloadUntil", 0L);
		if (until != 0L && until <= now) {
			int have = tag.getIntOr("ammo", 0);
			int need = w.magazine() - have;
			int got = owner instanceof Player p ? takeReserve(p, w, need) : need;
			tag.putLong("reloadUntil", 0L);
			tag.putInt("ammo", have + got);
			save(stack, tag);
		}
	}

	/** Fires once if the gun is ready. spreadMult > 1 makes bots miss more. Returns true if it fired. */
	public static boolean tryFire(LivingEntity shooter, ItemStack stack, WeaponDef w, float spreadMult) {
		return tryFire(shooter, stack, w, spreadMult, null);
	}

	/** aim: where the player's crosshair points (null = straight along their look). */
	public static boolean tryFire(LivingEntity shooter, ItemStack stack, WeaponDef w, float spreadMult, Vec3 aim) {
		if (!(shooter.level() instanceof ServerLevel level) || Knocked.is(shooter)) return false;
		long now = level.getGameTime();
		CompoundTag tag = tag(stack);
		if (tag.getLongOr("reloadUntil", 0L) > now || tag.getLongOr("nextShot", 0L) > now) return false;
		int ammo = tag.getIntOr("ammo", 0);
		if (ammo <= 0) {
			if (reserve(shooter, w) <= 0) {
				tag.putLong("nextShot", now + 10);
				save(stack, tag);
				playSheetSound(shooter, "dry_fire");
			}
			startReload(shooter, stack, w);
			return false;
		}
		tag.putInt("ammo", ammo - 1);
		tag.putLong("nextShot", now + w.fireIntervalTicks());
		save(stack, tag);
		RarityDef r = rarity(stack);
		shoot(level, shooter, w, r, spreadMult, aim);
		if (ammo - 1 <= 0) startReload(shooter, stack, w);
		return true;
	}

	static void shoot(ServerLevel level, LivingEntity shooter, WeaponDef w, RarityDef r, float spreadMult, Vec3 aim) {
		RandomSource rnd = shooter.getRandom();
		Vec3 eye = shooter.getEyePosition();
		Map<LivingEntity, Float> damage = new HashMap<>();
		Set<LivingEntity> headshots = new HashSet<>();
		for (int p = 0; p < w.pellets(); p++) {
			// Aimed at the crosshair, every gun hits exactly there; only the shotgun keeps its pellet spread (creator).
			float spread = aim != null && w.pellets() <= 1 ? 0F : w.spreadDeg() * spreadMult;
			// Towards the crosshair's point when the player sent one, else along the look; spread around that line.
			float baseYaw = shooter.getYRot(), basePitch = shooter.getXRot();
			if (aim != null) {
				Vec3 d = aim.subtract(eye);
				baseYaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
				basePitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
			}
			float yaw = baseYaw + (rnd.nextFloat() - 0.5F) * 2F * spread;
			float pitch = basePitch + (rnd.nextFloat() - 0.5F) * 2F * spread;
			Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
			if (aim == null && shooter instanceof Player pl && !(w.scope() && pl.isUsingItem())) dir = shoulderAim(level, pl, eye, dir, w.rangeBlocks());
			Vec3 end = eye.add(dir.scale(w.rangeBlocks()));
			BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
			if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
			// Outside a match (the hub's shooting range) bullets pass through other players: no damage, no hit markers.
			boolean hubShot = shooter instanceof ServerPlayer hs && !MatchManager.get().isAlive(hs);
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != shooter && !(hubShot && e instanceof Player) && !Teams.sameTeam(shooter, e), eye.distanceToSqr(end));
			Vec3 impact = end;
			if (hit != null && hit.getEntity() instanceof LivingEntity target) {
				impact = hit.getLocation();
				boolean head = impact.y > target.getEyeY() - 0.3;
				if (head) headshots.add(target);
				damage.merge(target, w.damage() * r.damageMult() * (head ? w.headshotMult() : 1F), Float::sum);
			} else if (block.getType() == HitResult.Type.BLOCK && shooter instanceof ServerPlayer sp && level.getBlockState(block.getBlockPos()).is(Blocks.TARGET)) {
				HubFun.onBulletHit(level, sp, block.getBlockPos(), block.getLocation()); // the hub's shooting range
			} else if (block.getType() == HitResult.Type.BLOCK && level.getBlockState(block.getBlockPos()).getBlock() instanceof CarBlock) {
				// Shots wear a car down too (creator: its damage adds up until it's wrecked).
				CarBlock.shot(level, block.getBlockPos(), w.damage() * r.damageMult());
			} else if (block.getType() == HitResult.Type.BLOCK && Builds.isBuilt(block.getBlockPos())) {
				// Fortnite: bullets chew through built walls, floors and ramps.
				Builds.damage(level, block.getBlockPos(), w.damage() * r.damageMult());
			}
			// Tracer: a few particles along the shot.
			double len = eye.distanceTo(impact);
			for (double d = 1.5; d < len; d += 3.0) {
				Vec3 pt = eye.add(dir.scale(d));
				level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 2, 0.05, 0.05, 0.05, 0.01);
		}
		playSheetSound(shooter, w.fireSound());
		for (var e : damage.entrySet()) {
			Combat.gunDamage(level, shooter, e.getKey(), e.getValue(), headshots.contains(e.getKey()));
			// The shooter hears the hit; a headshot has its own sound (creator).
			if (shooter instanceof ServerPlayer sp) Net.sfxTo(sp, headshots.contains(e.getKey()) ? "headshot" : "hit_marker", sp.position());
		}
	}

	/** Over-the-shoulder camera (1.1 right, 0.2 up): aim at what the crosshair sees, then shoot from the eye. */
	static Vec3 shoulderAim(ServerLevel level, Player p, Vec3 eye, Vec3 dir, float range) {
		double yaw = Math.toRadians(p.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 cam = eye.add(right.scale(1.1)).add(0, 0.2, 0);
		Vec3 far = cam.add(dir.scale(range));
		BlockHitResult hit = level.clip(new ClipContext(cam, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 target = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
		Vec3 d = target.subtract(eye);
		return d.lengthSqr() < 1.0E-4 ? dir : d.normalize();
	}

	public static void playSheetSound(Entity at, String soundId) {
		SoundDef s = Sheets.SOUNDS_BY_ID.get(soundId);
		if (s == null) return;
		if (s.game().equals("minecraft")) {
			SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(s.file()));
			if (ev != null) at.level().playSound(null, at.getX(), at.getY(), at.getZ(), ev, SoundSource.PLAYERS, s.volume(), s.pitch() * Mth.nextFloat(at.getRandom(), 0.95F, 1.05F));
		} else if (at.level() instanceof ServerLevel level) {
			Net.sfxAll(level.getServer(), soundId, at.position());
		}
	}
}
