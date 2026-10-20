package gg.minebloxroyale.bot;

import gg.minebloxroyale.match.Builds;
import gg.minebloxroyale.sheets.BuildPieceDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * How a bot builds (creator: "advanced building techniques, they don't just auto build when shot: ramp, wall, floor and
 * crank 90s as long as they have the right materials", and "bots strategically build"). Single pieces on the same grid
 * as players' (Builds), and plans of several pieces:
 * <ul>
 * <li>a ramp push: one ramp at a time towards someone above, walking up each (taking the high ground);</li>
 * <li>90s: a ramp and a wall over it, turning 90 degrees each storey, climbing on the spot (out-building someone);</li>
 * <li>a box: four walls round itself while it heals, opened again on the side it leaves by;</li>
 * <li>a ramp up the ground in its way, or a floor over a drop, when it's stuck getting somewhere.</li>
 * </ul>
 * Every piece costs its own materials (BotBag.buildMaterial), as a player's do. A plan stops when it's done, runs out of
 * materials, can't place a piece or the bot can't get onto it; the bot then carries on as before.
 */
final class BotBuilder {
	private enum Plan { NONE, RAMP_PUSH, NINETIES }

	/** Pieces at most in one plan, and how long the bot gets to walk onto each ramp. */
	private static final int MAX_STEPS = 8, CLIMB_TICKS = 50;

	private final BotEntity bot;
	private Plan plan = Plan.NONE;
	private Direction dir = Direction.NORTH;
	private int steps, climbTicks, cooldown;
	private double goalY;
	/** Where the bot walks to next: just above the top of the ramp it last built. */
	private Vec3 climbTo;
	/** The box round the bot (its walls, to open one again) and what it's made of. */
	private final List<Builds.Placement> box = new ArrayList<>();
	private String boxMaterial;

	BotBuilder(BotEntity bot) {
		this.bot = bot;
	}

	boolean busy() {
		return plan != Plan.NONE;
	}

	void stop() {
		plan = Plan.NONE;
		climbTo = null;
	}

	// ---- single pieces ----

	private static BuildPieceDef piece(String id) {
		return Sheets.BUILDS_BY_ID.get(id);
	}

	private int tileX() {
		return Math.floorDiv(Mth.floor(bot.getX()), Builds.tile());
	}

	private int tileZ() {
		return Math.floorDiv(Mth.floor(bot.getZ()), Builds.tile());
	}

	/** The storey its feet are in (ramps and floors start from here: never above its feet). */
	private int feetStorey() {
		return Math.floorDiv(Mth.floor(bot.getY() + 0.01), Builds.storey());
	}

	/** The storey its eyes are in (a wall here covers its head). */
	private int eyeStorey() {
		return Math.floorDiv(Mth.floor(bot.getEyeY()), Builds.storey());
	}

	/** Places one piece, paid for from the bag; false if it can't afford it or nothing could be placed there. */
	private boolean place(ServerLevel level, String pieceId, Builds.Placement p, boolean strong) {
		BuildPieceDef piece = piece(pieceId);
		String mat = piece == null ? null : bot.bag().buildMaterial(piece.cost(), strong);
		if (mat == null || !Builds.build(level, bot.position(), piece, Sheets.MATERIALS_BY_ID.get(mat), p)) return false;
		bot.bag().spend(mat, piece.cost());
		bot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		return true;
	}

	/** Enough materials, all kinds together, for this many pieces (wall, floor and ramp cost the same)? */
	private boolean canAfford(int pieces) {
		return bot.bag().totalMaterials() >= piece("wall").cost() * pieces;
	}

	/** A wall on the edge of its tile facing `towards`, high enough to cover its head (cover from a shooter). */
	boolean wall(ServerLevel level, Direction towards) {
		if (cooldown > 0) return false;
		boolean ok = place(level, "wall", new Builds.Placement(tileX(), eyeStorey(), tileZ(), towards), false);
		if (ok) cooldown = 6;
		return ok;
	}

	/** A ramp in the tile ahead, rising the way it faces, from its feet up. Returns the spot on top to walk to. */
	private Vec3 ramp(ServerLevel level, Direction d, int storey) {
		Builds.Placement p = new Builds.Placement(tileX() + d.getStepX(), storey, tileZ() + d.getStepZ(), d);
		if (!place(level, "ramp", p, false)) return null;
		// The top step: the highest block of the ramp's shape (where its blocks go doesn't depend on the material), in the
		// middle of its row.
		BlockPos top = null;
		for (BlockPos b : Builds.shape(piece("ramp"), Sheets.MATERIALS.get(0), p).keySet()) if (top == null || b.getY() > top.getY()) top = b;
		int t = Builds.tile();
		double cx = p.tx() * t + t / 2.0, cz = p.tz() * t + t / 2.0;
		return new Vec3(d.getAxis() == Direction.Axis.X ? top.getX() + 0.5 : cx, top.getY() + 1, d.getAxis() == Direction.Axis.Z ? top.getZ() + 0.5 : cz);
	}

	// ---- plans ----

	/** Someone above it, further off: ramps towards them until it's level with them (the high ground). */
	boolean startRampPush(Vec3 towards, double reachY) {
		if (busy() || cooldown > 0 || !canAfford(2)) return false;
		plan = Plan.RAMP_PUSH;
		dir = Direction.fromYRot(yawTo(towards));
		goalY = reachY;
		steps = 0;
		climbTo = null;
		return true;
	}

	/** Someone above it, close by (or building): 90s on the spot until it's above them. */
	boolean startNineties(Vec3 towards, double reachY) {
		if (busy() || cooldown > 0 || !canAfford(4)) return false;
		plan = Plan.NINETIES;
		dir = Direction.fromYRot(yawTo(towards));
		goalY = reachY;
		steps = 0;
		climbTo = null;
		return true;
	}

	private float yawTo(Vec3 at) {
		return (float) (Mth.atan2(at.z - bot.getZ(), at.x - bot.getX()) * (180F / Math.PI)) - 90F;
	}

	/**
	 * Every tick while a plan runs: once it stands on top of the last ramp (or at the start), the next pieces; it walks
	 * up each ramp. True while the plan has the bot's legs (it still shoots).
	 */
	boolean tick(ServerLevel level) {
		if (cooldown > 0) cooldown--;
		if (plan == Plan.NONE) return false;
		if (bot.getY() >= goalY || steps >= MAX_STEPS) {
			stop();
			return false;
		}
		if (climbTo != null) {
			boolean there = Math.abs(bot.getX() - climbTo.x) < 1.6 && Math.abs(bot.getZ() - climbTo.z) < 1.6 && bot.getY() >= climbTo.y - 0.6;
			if (!there) {
				if (++climbTicks > CLIMB_TICKS) {
					stop(); // couldn't get onto it
					return false;
				}
				if (bot.getNavigation().isDone() || climbTicks % 10 == 0) bot.getNavigation().moveTo(climbTo.x, climbTo.y, climbTo.z, 1.1);
				return true;
			}
		}
		if (cooldown > 0) return true;
		int storey = feetStorey();
		if (plan == Plan.NINETIES && steps > 0) dir = dir.getClockWise(); // the turn
		Vec3 top = ramp(level, dir, storey);
		if (top == null) {
			stop();
			return false;
		}
		// 90s: a wall over the far end of the ramp, one storey up, to turn against (and for cover).
		if (plan == Plan.NINETIES) place(level, "wall", new Builds.Placement(tileX() + dir.getStepX(), storey + 1, tileZ() + dir.getStepZ(), dir), false);
		steps++;
		climbTo = top;
		climbTicks = 0;
		cooldown = 8;
		bot.getNavigation().moveTo(top.x, top.y, top.z, 1.1);
		return true;
	}

	// ---- the box while healing ----

	/** Walls on all four sides, toughest material it can afford for all four (creator: box up while healing). */
	boolean box(ServerLevel level) {
		if (busy()) return false;
		box.clear(); // (an older box it never opened, knocked inside it, stays as it is)
		String mat = bot.bag().buildMaterial(piece("wall").cost() * 4, true);
		if (mat == null) return false;
		int tx = tileX(), tz = tileZ(), ty = eyeStorey();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Builds.Placement p = new Builds.Placement(tx, ty, tz, d);
			if (Builds.build(level, bot.position(), piece("wall"), Sheets.MATERIALS_BY_ID.get(mat), p)) {
				bot.bag().spend(mat, piece("wall").cost());
				box.add(p);
			}
		}
		boxMaterial = mat;
		if (!box.isEmpty()) bot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		return !box.isEmpty();
	}

	/** Done healing: break the box's wall on the side it's heading to (the others stay, like a player's). */
	void openBox(ServerLevel level, Vec3 heading) {
		if (box.isEmpty()) return;
		Direction d = Direction.fromYRot(yawTo(heading));
		for (Builds.Placement p : box)
			if (p.dir() == d) {
				// (its middle block: the corners are shared with the walls beside it on the grid, 1.2.0)
				List<BlockPos> blocks = new ArrayList<>(Builds.shape(piece("wall"), Sheets.MATERIALS_BY_ID.get(boxMaterial), p).keySet());
				Builds.breakPiece(level, blocks.get(blocks.size() / 2));
			}
		box.clear();
	}

	// ---- getting unstuck ----

	/**
	 * Stuck on the way to `goal`: ground a storey or more higher in the way gets a ramp up it; a drop of 3+ blocks or
	 * water a floor over it. True if it built something to walk on.
	 */
	boolean unblock(ServerLevel level, Vec3 goal) {
		if (busy() || cooldown > 0) return false;
		Direction d = Direction.fromYRot(yawTo(goal));
		int t = Builds.tile(), feet = Mth.floor(bot.getY() + 0.01);
		int ax = (tileX() + d.getStepX()) * t + t / 2, az = (tileZ() + d.getStepZ()) * t + t / 2;
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az);
		boolean water = !level.getFluidState(new BlockPos(ax, ground - 1, az)).isEmpty();
		if (ground - feet >= 2) {
			Vec3 top = ramp(level, d, feetStorey());
			if (top == null) return false;
			cooldown = 8;
			bot.getNavigation().moveTo(top.x, top.y, top.z, 1.1);
			return true;
		}
		if ((feet - ground >= 3 || water) && place(level, "floor", new Builds.Placement(tileX() + d.getStepX(), feetStorey(), tileZ() + d.getStepZ(), d), false)) {
			cooldown = 8;
			bot.getNavigation().moveTo(ax + 0.5, feet, az + 0.5, 1.0);
			return true;
		}
		return false;
	}
}
