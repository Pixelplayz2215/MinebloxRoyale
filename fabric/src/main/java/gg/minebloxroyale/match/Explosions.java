package gg.minebloxroyale.match;

import gg.minebloxroyale.bot.BotEntity;
import gg.minebloxroyale.net.Net;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rockets and grenades going off (v2.0.0, creator: "explodes where it hits: area damage to players, bots and builds
 * (structures it hits break)"): players and bots in the blast are hurt, the most at its middle and less towards its
 * edge (never the one who fired it, never their teammates); built pieces in it take damage, the island's buildings,
 * trees and furniture in the middle of it break (the ground itself never does), and cars take their own damage once
 * each (creator: a rocket 300, so 2 wreck a car; a grenade 100). In the hub (the shooting range) a blast is only for
 * show: nothing is hurt or broken there.
 */
public final class Explosions {
	private Explosions() {
	}

	public static void blast(ServerLevel level, Vec3 at, Entity owner, float radius, float damage, float buildDamage, float carDamage) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 24, radius * 0.4, radius * 0.3, radius * 0.4, 0.04);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, radius * 0.3, radius * 0.2, radius * 0.3, 0.06);
		Net.sfxAll(level.getServer(), "explosion", at);
		if (!real(owner)) return;
		// people and bots: falling off from the middle; a wall between doesn't save you (as Fortnite's splash)
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> e.isAlive() && !e.isSpectator())) {
			if (e == owner) continue;
			double d = e.getBoundingBox().distanceToSqr(at);
			d = Math.sqrt(d);
			if (d > radius) continue;
			float amount = damage * Mth.clamp(1.25F - (float) (d / radius), 0F, 1F);
			if (owner instanceof LivingEntity le) Combat.gunDamage(level, le, e, amount, false);
			else e.hurtServer(level, level.damageSources().explosion(null, null), amount);
		}
		// driven cars (v2.0.0): their own damage, once each
		for (CarEntity car : level.getEntitiesOfClass(CarEntity.class, new AABB(at, at).inflate(radius)))
			if (Math.sqrt(car.getBoundingBox().distanceToSqr(at)) <= radius) car.damage(level, carDamage, owner);
		// builds, parked cars, the island's buildings
		Set<Object> pieces = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<BlockPos> carsDone = new HashSet<>();
		int r = Mth.ceil(radius);
		BlockPos c = BlockPos.containing(at);
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			double d = Vec3.atCenterOf(p).distanceTo(at);
			if (d > radius) continue;
			if (Builds.isBuilt(p)) {
				Object piece = Builds.pieceAt(p);
				if (pieces.add(piece)) Builds.damage(level, p.immutable(), buildDamage);
				continue;
			}
			BlockState st = level.getBlockState(p);
			if (st.getBlock() instanceof CarBlock) {
				if (carsDone.contains(p)) continue;
				carsDone.addAll(CarBlock.parts(level, p.immutable()));
				CarBlock.shot(level, p.immutable(), carDamage);
				continue;
			}
			if (d <= radius * 0.75 && breakable(st)) {
				IslandRestore.remember(level, p.immutable()); // (the island is put back after the match)
				level.destroyBlock(p.immutable(), false, owner);
			}
		}
	}

	/** In a live match (not the hub's shooting range): the blast does damage. */
	private static boolean real(Entity owner) {
		MatchManager mm = MatchManager.get();
		if (mm.state() != MatchManager.State.LIVE) return false;
		if (owner instanceof ServerPlayer p) return mm.isAlive(p);
		return owner instanceof BotEntity || owner == null;
	}

	/**
	 * What a blast knocks out: the island's buildings, trees and things in them; never the ground (dirt, sand, stone,
	 * ores, snow, clay, gravel), water, chests, nor anything that can't be broken.
	 */
	static boolean breakable(BlockState st) {
		if (st.isAir() || !st.getFluidState().isEmpty() || st.getBlock().defaultDestroyTime() < 0) return false;
		if (st.getBlock() instanceof LootChestBlock || st.getBlock() instanceof CarBlock) return false;
		if (BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().endsWith("_ore")) return false; // (ores are ground too)
		// (SUBSTRATE_OVERWORLD: in Minecraft 26.3 the dirt tag is only dirt, coarse and rooted dirt; grass blocks, podzol,
		// mycelium, mud and moss are in this one, so without it a boosting car or a blast broke the grass, creator:
		// "it still breaks ground")
		return !(st.is(BlockTags.SUBSTRATE_OVERWORLD) || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.TERRACOTTA)
			|| st.is(BlockTags.SNOW) || st.is(BlockTags.ICE) || st.is(Blocks.GRAVEL) || st.is(Blocks.CLAY) || st.is(Blocks.BEDROCK) || st.is(Blocks.SANDSTONE)
			|| st.is(Blocks.RED_SANDSTONE) || st.is(Blocks.MUD) || st.is(Blocks.PACKED_MUD) || st.is(Blocks.MOSS_BLOCK));
	}
}
