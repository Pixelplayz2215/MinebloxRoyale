package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.sheets.HarvestRuleDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fortnite harvesting (harvest sheet), with the pickaxe only: trees, logs, planks, bricks, copper and iron anywhere,
 * any stone one block at a time, and the walls and roofs of buildings (village houses and Mineblox Royale's own). A
 * tree falls whole with everything growing on it, a wall falls as one flat section, a roof as one connected section;
 * materials go straight into their slot. Everything else (ground, snow, sand, leaves...) can't be mined at all.
 */
public final class Harvest {
	/** quickRule's answers, per block state. */
	private static final Map<BlockState, Optional<HarvestRuleDef>> QUICK = new HashMap<>();

	private Harvest() {
	}

	/**
	 * The harvest-sheet row for a block, or null when it can't be broken. inStructure: null = unknown (client side).
	 */
	static HarvestRuleDef rule(BlockState state, Boolean inStructure) {
		// Any part of a parked car: a hit on the car, for metal (creator: cars break for metal). The sheet's "@car" row
		// isn't a block name, so the car is recognised here, on both sides (each player's game decides whether a hit
		// can start at all).
		if (state.getBlock() instanceof CarBlock) return Sheets.HARVEST_BY_ID.get("h_car");
		HarvestRuleDef r = row(state, inStructure);
		return r == null || r.collapse().equals("none") || r.collapse().equals("attached") ? null : r;
	}

	/** Vines, cocoa, bee nests...: they go with the tree they grow on. */
	static boolean attached(BlockState state) {
		HarvestRuleDef r = row(state, false);
		return r != null && r.collapse().equals("attached");
	}

	/** The first matching harvest-sheet row, whatever its collapse. */
	private static HarvestRuleDef row(BlockState state, Boolean inStructure) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		for (HarvestRuleDef r : Sheets.HARVEST) {
			if (r.where().equals("structure") && Boolean.FALSE.equals(inStructure)) continue;
			// "single" rows (stone out in the open) don't apply in a building, where stone is part of a wall.
			if (r.collapse().equals("single") && Boolean.TRUE.equals(inStructure)) continue;
			String m = r.match();
			boolean hit;
			if (m.equals("@any")) hit = !state.isAir();
			else if (m.startsWith("#")) hit = state.is(TagKey.create(Registries.BLOCK, Identifier.parse(m.substring(1))));
			else if (m.startsWith("*")) hit = id.getPath().endsWith(m.substring(1));
			else if (m.startsWith("~")) hit = id.getPath().contains(m.substring(1));
			else hit = id.toString().equals(m);
			if (hit) return r;
		}
		return null;
	}

	/**
	 * Server: is this block part of a building: a generated structure (village house, ruined portal...) or one of
	 * Mineblox Royale's own (shack, watchtower, supply depot, ruins), which Minecraft doesn't know are structures.
	 */
	static boolean inStructure(ServerLevel level, BlockPos pos) {
		return CustomStructures.contains(pos) || level.structureManager().getStructureWithPieceAt(pos, s -> true).isValid();
	}

	/**
	 * Bots looking round for something to harvest: the row for a block wherever it is (out in the open; no lookup of
	 * the building it might be in, which is slow), remembered per block state. A car is metal; a reboot van never breaks.
	 */
	public static HarvestRuleDef quickRule(BlockState s) {
		if (s.is(Blocks.BARRIER)) return null;
		return QUICK.computeIfAbsent(s, k -> Optional.ofNullable(rule(k, false))).orElse(null);
	}

	public static HarvestRuleDef rule(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		// Reboot vans (invisible barrier blocks) never break (creator), even inside a village.
		if (s.is(Blocks.BARRIER)) return null;
		HarvestRuleDef r = rule(s, false);
		if (r != null && !r.collapse().equals("single")) return r;
		// In a building: its own rows (a stone wall falls as a wall, not one block at a time).
		HarvestRuleDef in = inStructure(level, pos) ? rule(s, true) : null;
		return in != null ? in : r;
	}

	public static void init() {
		// Only the pickaxe breaks things; unbreakable blocks never even start mining.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (player.isCreative() || player.isSpectator()) return InteractionResult.PASS;
			if (!Loadout.isPickaxe(player.getMainHandItem()) || Knocked.is(player)) return InteractionResult.FAIL;
			if (level instanceof ServerLevel sl) return !Builds.isBuilt(pos) && rule(sl, pos) == null ? InteractionResult.FAIL : InteractionResult.PASS;
			return rule(level.getBlockState(pos), null) == null ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player.isCreative()) return true;
			if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || !Loadout.isPickaxe(player.getMainHandItem()) || Knocked.is(player)) return false;
			if (Builds.isBuilt(pos)) {
				Builds.breakPiece(sl, pos); // a built piece breaks whole, no materials back
				return false;
			}
			HarvestRuleDef r = rule(sl, pos);
			if (r != null) collapse(sl, sp, pos, state, r);
			return false;
		});
	}

	static void collapse(ServerLevel level, ServerPlayer player, BlockPos start, BlockState state, HarvestRuleDef r) {
		Map<String, Float> mats = fell(level, start, r);
		for (var e : mats.entrySet()) give(player, e.getKey(), Math.round(e.getValue()));
		MinebloxRoyale.LOG.debug("BR: {} broke a {} {}", player.getName().getString(), r.collapse(), mats);
	}

	/** Bots harvest too: breaks what the pickaxe would at pos and returns the materials it gives (or null). */
	public static Map<String, Float> harvestAt(ServerLevel level, BlockPos pos) {
		HarvestRuleDef r = rule(level, pos);
		return r == null ? null : fell(level, pos, r);
	}

	/** Brings down the tree / wall / roof section (or hits the car) and returns the materials it is worth. */
	static Map<String, Float> fell(ServerLevel level, BlockPos start, HarvestRuleDef r) {
		if (r.collapse().equals("car")) return CarBlock.hit(level, start, r);
		Set<BlockPos> fall = switch (r.collapse()) {
			case "tree" -> tree(level, start, r);
			case "roof" -> roof(level, start, r);
			// Just the block hit (stone out in the open: a whole cliff doesn't come down; in a building it's a wall
			// row).
			case "single" -> Set.of(start);
			default -> wall(level, start, r);
		};
		Map<String, Float> mats = new HashMap<>();
		int n = 0;
		for (BlockPos p : fall) {
			BlockState s = level.getBlockState(p);
			HarvestRuleDef br = rule(level, p);
			if (br == null && !attached(s)) continue;
			if (br != null) mats.merge(br.material(), br.perBlock(), Float::sum);
			IslandRestore.remember(level, p);
			if (n++ % 3 == 0) level.levelEvent(2001, p, Block.getId(s));
			level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		return mats;
	}

	/**
	 * One tree: the trunk (logs joined to logs, branches included; a neighbour touching it log to log is split off by
	 * ownTrunk) and the natural leaves that belong to it. Leaves belong to this tree only when they are closer to its
	 * trunk than to any other tree's logs; leaves exactly in between two trees are left alone, so breaking one tree
	 * never takes its neighbours. Leaves never break on their own (harvest sheet: attached), only with their tree.
	 * Logs with no natural leaves are house logs: a wall.
	 */
	static Set<BlockPos> tree(ServerLevel level, BlockPos start, HarvestRuleDef r) {
		// 1. The trunk: logs touching logs only (never through leaves, which is how neighbouring trees got pulled in).
		// start is always a log: leaves can't be harvested, so they never start a fall.
		Set<BlockPos> joined = new LinkedHashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty() && joined.size() < r.maxBlocks()) {
			BlockPos p = q.poll();
			if (joined.contains(p) || !isLog(level, p)) continue;
			joined.add(p);
			for (BlockPos np : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) if (!joined.contains(np)) q.add(np.immutable());
		}
		Set<BlockPos> logs = ownTrunk(level, joined, start);
		// 2. Natural leaves near the trunk.
		// (The leaves row's maxBlocks is how many leaves, and separately how many vines and other extras, one tree can
		// drop: big jungle and dark oak canopies run to hundreds, and any left over would float.)
		int leafCap = Sheets.HARVEST_BY_ID.get("h_leaves").maxBlocks();
		Set<BlockPos> leaves = new LinkedHashSet<>();
		q.clear(); // (what the trunk search hadn't got to yet)
		q.addAll(logs);
		Set<BlockPos> seen = new HashSet<>(logs);
		while (!q.isEmpty() && leaves.size() < leafCap) {
			BlockPos p = q.poll();
			for (Direction d : Direction.values()) {
				BlockPos np = p.relative(d);
				if (!seen.add(np) || !isNaturalLeaf(level, np) || nearestLogDistance(np, logs) > 6) continue;
				leaves.add(np);
				q.add(np);
			}
		}
		if (leaves.isEmpty()) return wall(level, start, r); // a house log
		// 3. Other trees' logs around this one decide who owns the leaves in between.
		List<BlockPos> others = new ArrayList<>();
		int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
		for (BlockPos p : leaves) {
			x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
			x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
		}
		for (BlockPos p : BlockPos.betweenClosed(x0 - 6, y0 - 6, z0 - 6, x1 + 6, y1 + 6, z1 + 6))
			if (!logs.contains(p) && isLog(level, p)) others.add(p.immutable());
		Set<BlockPos> out = new LinkedHashSet<>(logs);
		for (BlockPos leaf : leaves) {
			int mine = nearestLogDistance(leaf, logs), theirs = Integer.MAX_VALUE;
			for (BlockPos o : others) theirs = Math.min(theirs, o.distManhattan(leaf));
			if (mine < theirs) out.add(leaf); // a tie: can't tell whose it is, so it stays
		}
		// 4. Everything growing on the tree falls with it, so nothing is left floating (creator): vines (and the vines
		// hanging below them), cocoa pods, bee nests, hanging moss, propagules, resin, a creaking heart in the trunk.
		// First every such thing joined to the tree (never through leaves: whose leaves are was settled above)...
		List<BlockPos> extras = new ArrayList<>();
		ArrayDeque<BlockPos> extra = new ArrayDeque<>(out);
		Set<BlockPos> seenExtra = new HashSet<>(out);
		while (!extra.isEmpty() && extras.size() < leafCap) {
			BlockPos p = extra.poll();
			for (Direction d : Direction.values()) {
				BlockPos np = p.relative(d);
				if (!seenExtra.add(np)) continue;
				BlockState ns = level.getBlockState(np);
				if (ns.getBlock() instanceof LeavesBlock || !attached(ns)) continue;
				extras.add(np);
				extra.add(np);
			}
		}
		// ...then, top down and again until nothing changes, each one goes unless something that stays still holds it
		// (a vine also on a tree that isn't falling, cocoa on another trunk, a propagule planted in the ground).
		extras.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (boolean changed = true; changed;) {
			changed = false;
			for (BlockPos p : extras)
				if (!out.contains(p) && !stillHeld(level, p, out)) {
					out.add(p);
					changed = true;
				}
		}
		return out;
	}

	/**
	 * Trees growing side by side (dark forests, jungles) can touch log to log. Every tree stands on its own trunk base
	 * (logs resting on the ground; the four logs of a 2x2 trunk are one base), so the joined logs are shared out by
	 * which base they are nearest (counting through the logs), and only the tree that was hit falls. A log exactly as
	 * near to two trees belongs to neither and stays.
	 */
	private static Set<BlockPos> ownTrunk(ServerLevel level, Set<BlockPos> joined, BlockPos seed) {
		List<BlockPos> roots = new ArrayList<>();
		for (BlockPos p : joined) {
			BlockState below = level.getBlockState(p.below());
			if (!joined.contains(p.below()) && !below.isAir() && !below.canBeReplaced() && !(below.getBlock() instanceof LeavesBlock)) roots.add(p);
		}
		// Roots side by side (one block up or down for slopes) are one trunk.
		Map<BlockPos, Integer> base = new HashMap<>();
		int bases = 0;
		for (BlockPos root : roots) {
			if (base.containsKey(root)) continue;
			int id = bases++;
			base.put(root, id);
			ArrayDeque<BlockPos> q = new ArrayDeque<>(List.of(root));
			while (!q.isEmpty()) {
				BlockPos p = q.poll();
				for (BlockPos o : roots)
					if (!base.containsKey(o) && Math.abs(o.getY() - p.getY()) <= 1 && Math.abs(o.getX() - p.getX()) + Math.abs(o.getZ() - p.getZ()) == 1) {
						base.put(o, id);
						q.add(o);
					}
			}
		}
		if (bases <= 1) return joined; // one tree
		// Spread out from every base at once, through the logs; a log reached as soon by two trees is nobody's (-1).
		Map<BlockPos, Integer> dist = new HashMap<>(), owner = new HashMap<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		for (BlockPos root : roots) {
			dist.put(root, 0);
			owner.put(root, base.get(root));
			q.add(root);
		}
		while (!q.isEmpty()) {
			BlockPos p = q.poll();
			int d = dist.get(p) + 1, o = owner.get(p);
			for (BlockPos np : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				if (!joined.contains(np)) continue;
				Integer nd = dist.get(np);
				if (nd == null) {
					BlockPos at = np.immutable();
					dist.put(at, d);
					owner.put(at, o);
					q.add(at);
				} else if (nd == d && owner.get(np) != o) owner.put(np.immutable(), -1);
			}
		}
		int mine = owner.getOrDefault(seed, -1);
		if (mine < 0) { // the hit log is exactly between two trees: it goes with the trunk nearest to it
			BlockPos nearest = roots.get(0);
			for (BlockPos root : roots) if (root.distSqr(seed) < nearest.distSqr(seed)) nearest = root;
			mine = base.get(nearest);
		}
		Set<BlockPos> out = new LinkedHashSet<>();
		for (BlockPos p : joined) if (owner.getOrDefault(p, -1) == mine) out.add(p);
		out.add(seed);
		return out;
	}

	/**
	 * Is something growing on a tree still held up once the falling blocks are gone? (Minecraft's own rules for each.)
	 */
	private static boolean stillHeld(ServerLevel level, BlockPos p, Set<BlockPos> falling) {
		BlockState s = level.getBlockState(p);
		if (s.getBlock() instanceof VineBlock || s.getBlock() instanceof MultifaceBlock) {
			// A vine or resin face needs the block it's stuck to; a vine can also hang from the same face of the vine
			// above.
			for (Direction d : Direction.values()) {
				if (!MultifaceBlock.hasFace(s, d)) continue;
				BlockPos n = p.relative(d);
				if (!falling.contains(n) && MultifaceBlock.canAttachTo(level, d, n, level.getBlockState(n))) return true;
				BlockState above = level.getBlockState(p.above());
				if (d.getAxis() != Direction.Axis.Y && !falling.contains(p.above()) && above.is(s.getBlock()) && MultifaceBlock.hasFace(above, d)) return true;
			}
			return false;
		}
		if (s.getBlock() instanceof CocoaBlock) return !falling.contains(p.relative(s.getValue(CocoaBlock.FACING)));
		// Anything else hangs from the block above (or the same thing above it), or rests against a solid block.
		BlockState above = level.getBlockState(p.above());
		if (!falling.contains(p.above()) && (above.is(s.getBlock()) || solid(above))) return true;
		for (Direction d : Direction.values()) {
			if (d == Direction.UP) continue;
			BlockPos n = p.relative(d);
			if (!falling.contains(n) && solid(level.getBlockState(n)) && !attached(level.getBlockState(n))) return true;
		}
		return false;
	}

	private static boolean solid(BlockState s) {
		return !s.isAir() && !s.canBeReplaced();
	}

	private static boolean isLog(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		HarvestRuleDef pr = rule(s, false);
		return pr != null && pr.collapse().equals("tree") && !(s.getBlock() instanceof LeavesBlock);
	}

	private static boolean isNaturalLeaf(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.getBlock() instanceof LeavesBlock && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT));
	}

	private static int nearestLogDistance(BlockPos p, Set<BlockPos> logs) {
		int best = Integer.MAX_VALUE;
		for (BlockPos l : logs) best = Math.min(best, l.distManhattan(p));
		return best;
	}

	/** One flat wall: connected wall-type blocks in the broken block's vertical plane, near the hit. */
	static Set<BlockPos> wall(ServerLevel level, BlockPos start, HarvestRuleDef r) {
		boolean alongX = isWall(level, start.east()) || isWall(level, start.west());
		boolean alongZ = isWall(level, start.north()) || isWall(level, start.south());
		Direction.Axis spread = alongX && !alongZ ? Direction.Axis.X : !alongX && alongZ ? Direction.Axis.Z : (wallRun(level, start, Direction.Axis.X) >= wallRun(level, start, Direction.Axis.Z) ? Direction.Axis.X : Direction.Axis.Z);
		Set<BlockPos> out = new LinkedHashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty() && out.size() < r.maxBlocks()) {
			BlockPos p = q.poll();
			if (out.contains(p) || !isWall(level, p)) continue;
			if (Math.abs(p.getX() - start.getX()) > 10 || Math.abs(p.getZ() - start.getZ()) > 10 || Math.abs(p.getY() - start.getY()) > 8) continue;
			out.add(p);
			q.add(p.above());
			q.add(p.below());
			if (spread == Direction.Axis.X) {
				q.add(p.east());
				q.add(p.west());
			} else {
				q.add(p.north());
				q.add(p.south());
			}
		}
		return out;
	}

	private static int wallRun(ServerLevel level, BlockPos p, Direction.Axis axis) {
		int n = 0;
		for (int i = 1; i <= 4; i++) {
			if (isWall(level, p.relative(axis, i))) n++;
			if (isWall(level, p.relative(axis, -i))) n++;
		}
		return n;
	}

	private static boolean isWall(ServerLevel level, BlockPos p) {
		HarvestRuleDef r = rule(level, p);
		return r != null && (r.collapse().equals("wall") || (r.collapse().equals("tree") && !(level.getBlockState(p).getBlock() instanceof LeavesBlock)));
	}

	/** A roof section: connected roof-type blocks (stairs/slabs), diagonals included. */
	static Set<BlockPos> roof(ServerLevel level, BlockPos start, HarvestRuleDef r) {
		Set<BlockPos> out = new LinkedHashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty() && out.size() < r.maxBlocks()) {
			BlockPos p = q.poll();
			if (out.contains(p)) continue;
			HarvestRuleDef pr = rule(level, p);
			if (pr == null || !pr.collapse().equals("roof")) continue;
			if (p.distManhattan(start) > 24) continue;
			out.add(p);
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++)
					for (int dz = -1; dz <= 1; dz++)
						if (dx != 0 || dy != 0 || dz != 0) q.add(p.offset(dx, dy, dz));
		}
		return out;
	}

	/**
	 * Materials go straight into their own inventory slot (Fortnite), up to maxCarry (99); past that you get no more.
	 */
	static void give(Player p, String material, int amount) {
		if (amount > 0) Loadout.addMaterial(p, ModItems.MATERIALS.get(material), amount, Sheets.MATERIALS_BY_ID.get(material).maxCarry());
	}
}
