package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModItems;
import gg.minebloxroyale.sheets.BuildPieceDef;
import gg.minebloxroyale.sheets.MaterialDef;
import gg.minebloxroyale.sheets.Sheets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fortnite building (builds sheet): walls, floors and ramps on a world grid. The shapes are shared by the client's
 * preview and the server's placement. Pieces cost materials, only fill air, take gun damage, break whole to the pickaxe,
 * and are all removed when the match ends.
 * <p>
 * The grid (1.2.0, a tester on 1.0.1 by way of the creator: "In Fortnite the building is grid snapped so no matter where
 * you try to place builds the builds will never be uneven or go into each other"): lines every tile (4 blocks) across
 * and every storey (4 blocks) up, and every piece sits ON them, as Fortnite's do. A wall stands on the line between two
 * tiles (the same wall whichever side you build it from), a floor lies on a storey's line, and a ramp runs from one
 * line to the next, so one ramp's top is the next one's bottom. Where pieces meet on a line they share those blocks
 * instead of one taking them from the other; a shared block stays while any piece still has it. Before, each piece
 * filled the inside of its own tile: walls built from either side of an edge stood a block apart, and a floor and the
 * wall under it fought over the same row.
 */
public final class Builds {
	/** Where a piece goes: its grid tile and the way the builder faces. */
	public record Placement(int tx, int ty, int tz, Direction dir) {
	}

	private static final class Piece {
		/** Every block it has, shared with another piece or not, and what each should be. */
		final List<BlockPos> blocks = new ArrayList<>();
		final Map<BlockPos, BlockState> states = new HashMap<>();
		float health;
	}

	/** Each built block and the pieces that have it: several where pieces meet on a grid line, the first placed first. */
	private static final Map<BlockPos, List<Piece>> BY_BLOCK = new HashMap<>();

	private Builds() {
	}

	public static int tile() {
		return Math.round(MinebloxRoyale.setting("buildTile"));
	}

	public static int storey() {
		return Math.round(MinebloxRoyale.setting("buildHeight"));
	}

	/** Where a builder standing at (x, y, z) facing yaw / pitch would put this piece (no ramp under them). */
	public static Placement target(BuildPieceDef piece, double x, double y, double z, float yaw, float pitch) {
		return target(null, piece, x, y, z, yaw, pitch);
	}

	/**
	 * Where a builder standing at (x, y, z) facing yaw / pitch would put this piece. On a ramp rising the way they face,
	 * they count as being at its top, as in Fortnite's ramp rush (1.2.0, a tester on 1.0.1: "sometimes you need to be
	 * close to the edge of a build to build the ramp straight": the next ramp used to start at the bottom of the one you
	 * were on until you'd reached its very top). So the next ramp, or a floor ahead, carries on up from it, from anywhere
	 * on the ramp (or jumping on it), and a wall goes in front of that next piece instead of across your way up.
	 */
	public static Placement target(@Nullable BlockGetter level, BuildPieceDef piece, double x, double y, double z, float yaw, float pitch) {
		int t = tile(), h = storey();
		Direction dir = Direction.fromYRot(yaw);
		int tx = Math.floorDiv((int) Math.floor(x), t), tz = Math.floorDiv((int) Math.floor(z), t), ty = Math.floorDiv((int) Math.floor(y + 0.01), h);
		Placement next = level == null ? null : rampAhead(level, x, y, z, dir);
		if (next != null && (piece.kind().equals("ramp") || piece.kind().equals("wall") || pitch < 55)) return next;
		boolean ahead = switch (piece.kind()) {
			case "wall" -> false;
			case "floor" -> pitch < 55;
			default -> true;
		};
		if (ahead) {
			tx += dir.getStepX();
			tz += dir.getStepZ();
		}
		return new Placement(tx, ty, tz, dir);
	}

	/**
	 * Standing on a built ramp that rises the way you face: the next one up (the tile past it, a storey higher, its
	 * bottom this one's top); null if there's none under you. The stairs under your feet are looked for a little lower too
	 * in case you're jumping; of the ramps on the grid that have that block, the highest one there is counts (on the line
	 * where two meet, the upper one if it's built).
	 */
	private static @Nullable Placement rampAhead(BlockGetter level, double x, double y, double z, Direction dir) {
		BlockPos stair = null;
		for (double below : new double[] { 0.01, 0.7, 1.3 }) {
			BlockPos pos = BlockPos.containing(x, y - below, z);
			BlockState s = level.getBlockState(pos);
			if (isStep(s, dir)) {
				stair = pos;
				break;
			}
			if (!s.isAir()) return null; // (standing on something else)
		}
		if (stair == null) return null;
		int t = tile(), h = storey();
		boolean alongX = dir.getAxis() == Direction.Axis.X;
		int along = Math.floorDiv(alongX ? stair.getX() : stair.getZ(), t), across = alongX ? stair.getZ() : stair.getX();
		int sy = Math.floorDiv(stair.getY(), h);
		int[] acrossTiles = Math.floorMod(across, t) == 0 ? new int[] { Math.floorDiv(across, t) - 1, Math.floorDiv(across, t) } : new int[] { Math.floorDiv(across, t) };
		Placement best = null;
		for (int c : acrossTiles)
			for (int da = -1; da <= 1; da++)
				for (int ds = -1; ds <= 1; ds++) {
					Placement r = alongX ? new Placement(along + da, sy + ds, c, dir) : new Placement(c, sy + ds, along + da, dir);
					if ((best == null || r.ty() > best.ty()) && rampPositions(r).containsKey(stair) && isStep(level.getBlockState(rampPos(r, t / 2, t / 2)), dir)) best = r;
				}
		return best == null ? null : new Placement(best.tx() + dir.getStepX(), best.ty() + 1, best.tz() + dir.getStepZ(), dir);
	}

	/** A built ramp's step: stairs rising the way you face, the right way up. */
	private static boolean isStep(BlockState s, Direction dir) {
		return s.getBlock() instanceof StairBlock && s.getValue(StairBlock.FACING) == dir && s.getValue(StairBlock.HALF) == Half.BOTTOM;
	}

	/** A ramp's block k steps along it (0 at its low end, on a grid line) and a across it. */
	private static BlockPos rampPos(Placement p, int k, int a) {
		int t = tile(), h = storey();
		int ox = p.tx() * t, oz = p.tz() * t, y = p.ty() * h - 1 + Math.round(k * h / (float) t);
		return switch (p.dir()) {
			case NORTH -> new BlockPos(ox + a, y, oz + t - k);
			case SOUTH -> new BlockPos(ox + a, y, oz + k);
			case WEST -> new BlockPos(ox + t - k, y, oz + a);
			default -> new BlockPos(ox + k, y, oz + a);
		};
	}

	/** Every block of a ramp (and how far along it each is), from line to line. */
	private static Map<BlockPos, Integer> rampPositions(Placement p) {
		int t = tile();
		Map<BlockPos, Integer> out = new LinkedHashMap<>();
		for (int k = 0; k <= t; k++)
			for (int a = 0; a <= t; a++) out.put(rampPos(p, k, a), k);
		return out;
	}

	/**
	 * The blocks of a piece and what each one is (made of the material's block / stairs), on the grid's lines: a tile's
	 * lines are x = tx * tile and (tx + 1) * tile (the same for z), a storey's is y = ty * storey - 1 (you stand on it at
	 * ty * storey). A wall: the line on the side you face, from the storey's line to the next one up. A floor: the
	 * storey's line, edge to edge. A ramp: from the near line at the storey's line to the far line one storey up.
	 */
	public static Map<BlockPos, BlockState> shape(BuildPieceDef piece, MaterialDef mat, Placement p) {
		int t = tile(), h = storey();
		int ox = p.tx() * t, base = p.ty() * h - 1, oz = p.tz() * t;
		BlockState block = block(mat.block()), stairs = block(mat.stairs());
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		Direction d = p.dir();
		switch (piece.kind()) {
			case "wall" -> {
				for (int a = 0; a <= t; a++)
					for (int y = 0; y <= h; y++) {
						BlockPos pos = switch (d) {
							case NORTH -> new BlockPos(ox + a, base + y, oz);
							case SOUTH -> new BlockPos(ox + a, base + y, oz + t);
							case WEST -> new BlockPos(ox, base + y, oz + a);
							default -> new BlockPos(ox + t, base + y, oz + a);
						};
						out.put(pos, block);
					}
			}
			case "floor" -> {
				for (int a = 0; a <= t; a++)
					for (int b = 0; b <= t; b++) out.put(new BlockPos(ox + a, base, oz + b), block);
			}
			default -> { // ramp: one step up per block, rising the way the builder faces
				BlockState step = stairs.hasProperty(StairBlock.FACING) ? stairs.setValue(StairBlock.FACING, d) : stairs;
				for (BlockPos pos : rampPositions(p).keySet()) out.put(pos, step);
			}
		}
		return out;
	}

	private static BlockState block(String id) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
		return (b == null ? Blocks.OAK_PLANKS : b).defaultBlockState();
	}

	public static int materialCount(Player p, String material) {
		var item = ModItems.MATERIALS.get(material);
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
		return n;
	}

	private static void spend(Player p, String material, int amount) {
		var item = ModItems.MATERIALS.get(material);
		for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.is(item)) continue;
			int take = Math.min(amount, s.getCount());
			s.shrink(take);
			amount -= take;
		}
	}

	/** Server: a builder asks for a piece. Checks reach and cost, fills only air / replaceable blocks. */
	public static void place(ServerLevel level, Player builder, String pieceId, String materialId, Placement p) {
		BuildPieceDef piece = Sheets.BUILDS_BY_ID.get(pieceId);
		MaterialDef mat = Sheets.MATERIALS_BY_ID.get(materialId);
		if (piece == null || mat == null || builder.isSpectator()) return;
		if (!builder.isCreative() && materialCount(builder, materialId) < piece.cost()) return;
		if (!build(level, builder.position(), piece, mat, p)) return;
		if (!builder.isCreative()) spend(builder, materialId, piece.cost());
	}

	/**
	 * Places a piece for a builder at 'from' (players pay in place(); bots keep their own count). True if anything was
	 * built.
	 */
	public static boolean build(ServerLevel level, Vec3 from, BuildPieceDef piece, MaterialDef mat, Placement p) {
		Map<BlockPos, BlockState> shape = shape(piece, mat, p);
		double reach = MinebloxRoyale.setting("buildReach");
		BlockPos first = shape.keySet().iterator().next();
		if (Vec3.atCenterOf(first).distanceToSqr(from) > (reach + tile()) * (reach + tile())) return false;
		Piece built = new Piece();
		built.health = mat.pieceHealth();
		List<BlockPos> shared = new ArrayList<>();
		for (var e : shape.entrySet()) {
			BlockPos pos = e.getKey();
			if (BY_BLOCK.containsKey(pos)) { // another piece's block on the same line: shared, as it is
				shared.add(pos);
				continue;
			}
			BlockState there = level.getBlockState(pos);
			if (!there.isAir() && !there.canBeReplaced()) continue;
			if (!there.isAir()) IslandRestore.remember(level, pos); // grass etc. comes back after the match
			level.setBlock(pos, e.getValue(), 3);
			built.blocks.add(pos);
			built.states.put(pos, e.getValue());
			BY_BLOCK.computeIfAbsent(pos, k -> new ArrayList<>()).add(built);
		}
		if (built.blocks.isEmpty()) return false; // (nothing new: the same piece is there already, or solid ground)
		for (BlockPos pos : shared) {
			built.blocks.add(pos);
			built.states.put(pos, shape.get(pos));
			BY_BLOCK.get(pos).add(built);
		}
		level.playSound(null, first, mat.id().equals("wood") ? SoundEvents.WOOD_PLACE : SoundEvents.STONE_PLACE,
			SoundSource.BLOCKS, 1F, 1F);
		return true;
	}

	public static boolean isBuilt(BlockPos pos) {
		return BY_BLOCK.containsKey(pos);
	}

	/** The built piece a block belongs to (null: none; where pieces meet, the first placed): one blast hurts each once. */
	public static Object pieceAt(BlockPos pos) {
		List<Piece> owners = BY_BLOCK.get(pos);
		return owners == null || owners.isEmpty() ? null : owners.get(0);
	}

	/** Pickaxe on a built block: the whole piece goes (no materials back, as in Fortnite). */
	public static void breakPiece(ServerLevel level, BlockPos pos) {
		if (!(pieceAt(pos) instanceof Piece piece)) return;
		level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
		remove(level, piece);
	}

	/** Gun damage on a built block: the piece breaks when its health runs out. */
	public static void damage(ServerLevel level, BlockPos pos, float amount) {
		if (!(pieceAt(pos) instanceof Piece piece)) return;
		piece.health -= amount;
		if (piece.health <= 0) breakPiece(level, pos);
	}

	/**
	 * A piece goes: its blocks go too, except those another piece still has (they become what that piece made them: a
	 * wall's block where a ramp's step was, say).
	 */
	private static void remove(ServerLevel level, Piece piece) {
		for (BlockPos b : piece.blocks) {
			List<Piece> owners = BY_BLOCK.get(b);
			if (owners == null) continue;
			boolean shown = !owners.isEmpty() && owners.get(0) == piece;
			if (!owners.remove(piece)) continue;
			BlockState now = level.getBlockState(b);
			if (owners.isEmpty()) {
				BY_BLOCK.remove(b);
				if (now == piece.states.get(b)) level.setBlock(b, Blocks.AIR.defaultBlockState(), 2 | 16);
			} else if (shown && now == piece.states.get(b)) {
				BlockState next = owners.get(0).states.get(b);
				if (next != null && next != now) level.setBlock(b, next, 2 | 16);
			}
		}
	}

	/** Match over: every built piece disappears (the island is reused for the next match). */
	public static void clearAll(ServerLevel level) {
		HashSet<Piece> all = new HashSet<>();
		for (List<Piece> owners : BY_BLOCK.values()) all.addAll(owners);
		for (Piece p : all) remove(level, p);
		BY_BLOCK.clear();
	}
}
