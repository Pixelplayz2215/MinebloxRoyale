package gg.minebloxroyale.bot;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.match.Harvest;
import gg.minebloxroyale.match.IslandRestore;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Keeping a bot on the move (creator: "bots can swim like any normal player including the fast swim", "if spawned in a
 * tree, they have an unstuck protocol (only for stuck bots)", "make sure all bots work as intended and don't get lost
 * mid game"):
 * <ul>
 * <li>swimming: at the surface like a player, and sprint-swimming towards where it's going (Minecraft's fast swim:
 * low in the water, lying flat, about twice as fast), coming up for air when it needs to;</li>
 * <li>stuck in a tree (leaves or logs round it, not getting anywhere): it fells the trunk next to it (wood, as a player
 * would) or clears the leaves boxing it in, and if that doesn't free it, it drops to the ground under the tree;</li>
 * <li>stuck anywhere else on its way: a jump, a step to the side, a ramp or a floor (BotBuilder.unblock), another
 * chest or harvest spot if that's where it was going, a new path; after about 20 s with no player close by it's set
 * down on open ground a few blocks on, so no bot is left stuck.</li>
 * </ul>
 * A bot standing still on purpose (searching a chest, healing, fighting from where it is) is never touched.
 */
final class BotMovement {
	/** Fast swim: speed (blocks a tick; a player's is about 0.2) and how far it has to go to bother. */
	private static final double FAST_SWIM = 0.2, FAST_SWIM_FROM = 4;

	private final BotEntity bot;
	private final BotBuilder builder;
	private boolean fastSwimming;
	private Vec3 checkedFrom;
	private int stuck;

	BotMovement(BotEntity bot, BotBuilder builder) {
		this.bot = bot;
		this.builder = builder;
	}

	// ---- swimming ----

	/** Every tick. */
	void swimTick(ServerLevel level) {
		Path path = bot.getNavigation().getPath();
		BlockPos goal = bot.getNavigation().getTargetPos();
		boolean fast = bot.isInWater() && !bot.dropping() && path != null && !path.isDone() && goal != null
			&& goal.distToCenterSqr(bot.position()) > FAST_SWIM_FROM * FAST_SWIM_FROM && bot.getAirSupply() > bot.getMaxAirSupply() / 3;
		if (fast) {
			Vec3 next = Vec3.atBottomCenterOf(path.getNextNodePos());
			double dx = next.x - bot.getX(), dz = next.z - bot.getZ(), d = Math.max(0.01, Math.hypot(dx, dz));
			// Just under the surface (it breathes as it comes up between strokes, like a player).
			double depth = surface(level) - 0.9 - bot.getY();
			Vec3 want = new Vec3(dx / d * FAST_SWIM, Mth.clamp(depth * 0.1, -0.06, 0.06), dz / d * FAST_SWIM);
			bot.setDeltaMovement(bot.getDeltaMovement().lerp(want, 0.3));
			float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
			bot.setYRot(yaw);
			bot.setYHeadRot(yaw);
		}
		if (fast != fastSwimming) {
			fastSwimming = fast;
			bot.setSprinting(fast);
			bot.setSwimming(fast);
			if (!bot.isKnocked()) bot.setPose(fast ? Pose.SWIMMING : Pose.STANDING); // lying flat, like a swimming player
		}
	}

	/** The water's surface above the bot (the first block up that isn't water). */
	private double surface(ServerLevel level) {
		BlockPos.MutableBlockPos p = bot.blockPosition().mutable();
		for (int i = 0; i < 16 && !level.getFluidState(p).isEmpty(); i++) p.move(Direction.UP);
		return p.getY();
	}

	// ---- getting unstuck ----

	/**
	 * Every 2 s: is it getting anywhere? goal: where it's heading, or null when it's standing still on purpose (then
	 * it's never counted as stuck).
	 */
	void watch(ServerLevel level, Vec3 goal) {
		Vec3 here = bot.position();
		boolean moved = checkedFrom == null || checkedFrom.distanceToSqr(here) > 1.0;
		checkedFrom = here;
		if (goal == null || moved || builder.busy() || horizontalDistSqr(goal, here) < 3 * 3) {
			stuck = 0;
			return;
		}
		stuck++;
		if (inTree(level)) {
			outOfTree(level);
			return;
		}
		switch (stuck) {
			case 1 -> bot.getJumpControl().jump();
			case 2 -> {
				// A step to one side, then on again.
				double dx = goal.x - here.x, dz = goal.z - here.z, d = Math.max(0.01, Math.hypot(dx, dz)), side = bot.getRandom().nextBoolean() ? 4 : -4;
				bot.getNavigation().moveTo(here.x - dz / d * side, here.y, here.z + dx / d * side, 1.1);
			}
			case 3, 5, 7 -> {
				if (!builder.unblock(level, goal)) bot.getJumpControl().jump();
			}
			case 4 -> {
				// A chest, loot or harvest spot it can't get to: it picks another (the old one is left for a minute).
				bot.giveUpTarget();
				bot.getNavigation().stop();
			}
			case 6, 8 -> {
				bot.getNavigation().stop(); // a fresh path
				bot.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.1);
			}
			default -> {
				// About 20 s stuck: set down on open ground a few blocks on (never in water), if no player is close
				// enough to see it.
				if (stuck < 10 || level.getNearestPlayer(bot, 24) != null) return;
				double dx = goal.x - here.x, dz = goal.z - here.z, d = Math.max(0.01, Math.hypot(dx, dz));
				int sx = Mth.floor(here.x + dx / d * 6), sz = Mth.floor(here.z + dz / d * 6);
				int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
				if (level.getFluidState(new BlockPos(sx, sy - 1, sz)).isEmpty()) {
					bot.teleportTo(sx + 0.5, sy, sz + 0.5);
					MinebloxRoyale.LOG.info("BR: bot {} was stuck for {} s: set down a few blocks on", bot.getName().getString(), stuck * 2);
				}
				stuck = 0;
			}
		}
	}

	private static double horizontalDistSqr(Vec3 a, Vec3 b) {
		return (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z);
	}

	/** Leaves under it or round it (on a canopy, or caught in one), or a trunk it's wedged against with leaves about. */
	private boolean inTree(ServerLevel level) {
		BlockPos feet = bot.blockPosition();
		if (isLeaves(level.getBlockState(feet.below()))) return true;
		int leaves = 0;
		for (Direction d : Direction.Plane.HORIZONTAL)
			for (int dy = 0; dy <= 1; dy++) if (isLeaves(level.getBlockState(feet.relative(d).above(dy)))) leaves++;
		return leaves >= 2 || isLeaves(level.getBlockState(feet)) || isLeaves(level.getBlockState(feet.above()));
	}

	private static boolean isLeaves(BlockState s) {
		return s.is(BlockTags.LEAVES);
	}

	/**
	 * Stuck in a tree: first fell the trunk beside it (the tree comes down with its leaves; the wood goes in its bag), or
	 * clear the leaves round and under it; still stuck after that, drop to the ground under the tree.
	 */
	private void outOfTree(ServerLevel level) {
		BlockPos feet = bot.blockPosition();
		if (stuck <= 2) {
			for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1)))
				if (level.getBlockState(p).is(BlockTags.LOGS)) {
					Map<String, Float> mats = Harvest.harvestAt(level, p.immutable());
					if (mats != null) {
						mats.forEach((m, n) -> bot.bag().addMaterial(m, Math.round(n)));
						return;
					}
				}
			for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
				BlockState s = level.getBlockState(p);
				if (!isLeaves(s)) continue;
				IslandRestore.remember(level, p); // (the island is put back after the match)
				level.levelEvent(2001, p, Block.getId(s));
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			return;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
		if (ground < feet.getY() && level.getBlockState(new BlockPos(feet.getX(), ground, feet.getZ())).isAir()) {
			bot.teleportTo(feet.getX() + 0.5, ground, feet.getZ() + 0.5);
			MinebloxRoyale.LOG.info("BR: bot {} stuck in a tree: dropped to the ground under it", bot.getName().getString());
		}
		stuck = 0;
	}
}
