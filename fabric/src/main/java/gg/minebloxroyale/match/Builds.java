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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fortnite building (builds sheet): walls, floors and ramps that snap to a world grid. The shapes are shared by the
 * client's preview and the server's placement. Pieces cost materials, only fill air, take gun damage, break whole to
 * the pickaxe, and are all removed when the match ends.
 */
public final class Builds {
	/** Where a piece goes: its grid tile and the way the builder faces. */
	public record Placement(int tx, int ty, int tz, Direction dir) {
	}

	private static final class Piece {
		final List<BlockPos> blocks = new ArrayList<>();
		final Map<BlockPos, BlockState> states = new HashMap<>();
		float health;
	}

	private static final Map<BlockPos, Piece> BY_BLOCK = new HashMap<>();

	private Builds() {
	}

	public static int tile() {
		return Math.round(MinebloxRoyale.setting("buildTile"));
	}

	public static int storey() {
		return Math.round(MinebloxRoyale.setting("buildHeight"));
	}

	/** Where a builder standing at (x, y, z) facing yaw / pitch would put this piece. */
	public static Placement target(BuildPieceDef piece, double x, double y, double z, float yaw, float pitch) {
		int t = tile(), h = storey();
		Direction dir = Direction.fromYRot(yaw);
		int tx = Math.floorDiv((int) Math.floor(x), t), tz = Math.floorDiv((int) Math.floor(z), t), ty = Math.floorDiv((int) Math.floor(y + 0.01), h);
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

	/** The blocks of a piece and what each one is (made of the material's block / stairs). */
	public static Map<BlockPos, BlockState> shape(BuildPieceDef piece, MaterialDef mat, Placement p) {
		int t = tile(), h = storey();
		int ox = p.tx() * t, oy = p.ty() * h, oz = p.tz() * t;
		BlockState block = block(mat.block()), stairs = block(mat.stairs());
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		Direction d = p.dir();
		switch (piece.kind()) {
			case "wall" -> {
				for (int a = 0; a < t; a++)
					for (int y = 0; y < h; y++) {
						BlockPos pos = switch (d) {
							case NORTH -> new BlockPos(ox + a, oy + y, oz);
							case SOUTH -> new BlockPos(ox + a, oy + y, oz + t - 1);
							case WEST -> new BlockPos(ox, oy + y, oz + a);
							default -> new BlockPos(ox + t - 1, oy + y, oz + a);
						};
						out.put(pos, block);
					}
			}
			case "floor" -> {
				for (int a = 0; a < t; a++)
					for (int b = 0; b < t; b++) out.put(new BlockPos(ox + a, oy - 1, oz + b), block);
			}
			default -> { // ramp: one step up per block, rising the way the builder faces
				BlockState step = stairs.hasProperty(StairBlock.FACING) ? stairs.setValue(StairBlock.FACING, d) : stairs;
				for (int k = 0; k < t; k++)
					for (int a = 0; a < t; a++) {
						int y = oy + Math.min(h - 1, k * h / t);
						BlockPos pos = switch (d) {
							case NORTH -> new BlockPos(ox + a, y, oz + t - 1 - k);
							case SOUTH -> new BlockPos(ox + a, y, oz + k);
							case WEST -> new BlockPos(ox + t - 1 - k, y, oz + a);
							default -> new BlockPos(ox + k, y, oz + a);
						};
						out.put(pos, step);
					}
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
		for (var e : shape.entrySet()) {
			BlockState there = level.getBlockState(e.getKey());
			if (!there.isAir() && !there.canBeReplaced()) continue;
			if (!there.isAir()) IslandRestore.remember(level, e.getKey()); // grass etc. comes back after the match
			level.setBlock(e.getKey(), e.getValue(), 3);
			built.blocks.add(e.getKey());
			built.states.put(e.getKey(), e.getValue());
			BY_BLOCK.put(e.getKey(), built);
		}
		if (built.blocks.isEmpty()) return false;
		level.playSound(null, first, mat.id().equals("wood") ? SoundEvents.WOOD_PLACE : SoundEvents.STONE_PLACE,
			SoundSource.BLOCKS, 1F, 1F);
		return true;
	}

	public static boolean isBuilt(BlockPos pos) {
		return BY_BLOCK.containsKey(pos);
	}

	/** Pickaxe on a built block: the whole piece goes (no materials back, as in Fortnite). */
	public static void breakPiece(ServerLevel level, BlockPos pos) {
		Piece piece = BY_BLOCK.get(pos);
		if (piece == null) return;
		level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
		remove(level, piece);
	}

	/** Gun damage on a built block: the piece breaks when its health runs out. */
	public static void damage(ServerLevel level, BlockPos pos, float amount) {
		Piece piece = BY_BLOCK.get(pos);
		if (piece == null) return;
		piece.health -= amount;
		if (piece.health <= 0) breakPiece(level, pos);
	}

	private static void remove(ServerLevel level, Piece piece) {
		for (BlockPos b : piece.blocks) {
			if (BY_BLOCK.get(b) != piece) continue;
			BY_BLOCK.remove(b);
			if (level.getBlockState(b) == piece.states.get(b)) level.setBlock(b, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
	}

	/** Match over: every built piece disappears (the island is reused for the next match). */
	public static void clearAll(ServerLevel level) {
		for (Piece p : new ArrayList<>(new HashSet<>(BY_BLOCK.values()))) remove(level, p);
		BY_BLOCK.clear();
	}
}
