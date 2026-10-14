package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Mineblox Royale's own buildings (creator request), built on each island as its areas load during the match: a shack in
 * one of eight woods (with a matching door), a watchtower, a walled supply depot, stone ruins, and for metal (creator:
 * no structures gave metal) a parking lot full of cars and a container yard with shipping containers. Each has a loot
 * chest inside: the gold one, or (match enderChestStructure, 20%) the rare blue one with better loot. Every block they
 * replace is remembered by IslandRestore, so the land is put back after the match.
 */
public final class CustomStructures {
	private CustomStructures() {
	}

	private static final String[] KINDS = { "shack", "watchtower", "supply depot", "parking lot (metal)", "container yard (metal)", "ruins" };
	private static final int LOT = 3;
	/** Buildings' middles are at least this far apart (the spots are random, and two could overlap). */
	private static final int GAP = 26;
	/**
	 * This island's buildings still to come: the guaranteed ones (creator: at least 2 of every kind, 3 parking lots),
	 * then random ones up to the match sheet's customStructures.
	 */
	private static final List<Integer> GUARANTEED = new ArrayList<>(), EXTRA = new ArrayList<>();
	/** This island's buildings so far: kind and floor middle. */
	private record Built(int kind, BlockPos at) {
	}

	private static final List<Built> CENTRES = new ArrayList<>();
	/** How many buildings this island gets in all. */
	private static int total;

	/** A new island: decide which buildings it gets (built later, as each spot's area loads). */
	static void plan(RandomSource rnd) {
		GUARANTEED.clear();
		EXTRA.clear();
		CENTRES.clear();
		int each = (int) MinebloxRoyale.setting("structuresEach"), lots = (int) MinebloxRoyale.setting("parkingLots");
		for (int k = 0; k < KINDS.length; k++)
			for (int i = 0; i < (k == LOT ? Math.max(each, lots) : each); i++) GUARANTEED.add(k);
		for (int i = GUARANTEED.size(); i < (int) MinebloxRoyale.setting("customStructures"); i++) EXTRA.add(rnd.nextInt(KINDS.length));
		total = GUARANTEED.size() + EXTRA.size();
		MinebloxRoyale.LOG.info("BR: this island's buildings ({} in all): {} guaranteed ({}), {} random ({})", total, GUARANTEED.size(), tally(GUARANTEED), EXTRA.size(), tally(EXTRA));
	}

	/** "3 parking lot (metal), 2 shack, ..." for the log. */
	private static String tally(List<Integer> kinds) {
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < KINDS.length; k++) {
			int n = 0;
			for (int kind : kinds) if (kind == k) n++;
			if (n > 0) sb.append(sb.isEmpty() ? "" : ", ").append(n).append(' ').append(KINDS[k]);
		}
		return sb.isEmpty() ? "none" : sb.toString();
	}

	/** The kinds' names for /br tp (DevCommands), in KINDS order. */
	public static final List<String> TP_NAMES = List.of("shack", "watchtower", "supply_depot", "parking_lot", "container_yard", "ruins");

	/** The floor middle of the nearest building of this kind built so far on this island (TP_NAMES index), or null. */
	public static BlockPos nearest(int kind, double x, double z) {
		BlockPos best = null;
		for (Built b : CENTRES)
			if (b.kind() == kind && (best == null || b.at().distToCenterSqr(x, b.at().getY(), z) < best.distToCenterSqr(x, best.getY(), z))) best = b.at();
		return best;
	}

	/** Whether this island still has buildings to come. */
	static boolean wantsMore() {
		return !GUARANTEED.isEmpty() || !EXTRA.isEmpty();
	}

	/**
	 * Builds this island's next building at the spot (x, z), if one fits there. Returns where its loot chest is (to add
	 * to the match's chests), or null when nothing was built: the ground is too steep or wet for any building still to
	 * come, or the spot is too close to another building. Such a spot is passed over and the building goes to a later
	 * spot, so the guaranteed ones are always built.
	 */
	static BlockPos build(ServerLevel level, int x, int z, RandomSource rnd) {
		for (Built c : CENTRES)
			if (Math.abs(c.at().getX() - x) < GAP && Math.abs(c.at().getZ() - z) < GAP) {
				MinebloxRoyale.LOG.info("BR: building spot {} {} passed over: too close to the {} at {} {}", x, z, KINDS[c.kind()], c.at().getX(), c.at().getZ());
				return null;
			}
		int center = ground(level, x, z);
		// A parking lot is always the big one (creator): it levels the ground under it, up to 4 blocks either way.
		Integer kind = take(flat(level, x, z, 10, 9, center, 4), flat(level, x, z, 4, 4, center, 2), rnd);
		if (kind == null) {
			MinebloxRoyale.LOG.info("BR: building spot {} {} passed over: too steep or wet for what's still due", x, z);
			return null;
		}
		// The floor goes level with the ground just outside the way in (creator: a shack's door was left a block above
		// the grass on a slope).
		int y = switch (kind) {
			case 0 -> ground(level, x, z - 4); // the shack's door, -Z
			case 2 -> ground(level, x, z - 5); // the depot's gate, -Z
			default -> center;
		};
		BlockPos o = new BlockPos(x, y, z);
		CENTRES.add(new Built(kind, o));
		building = null;
		BlockPos loot = switch (kind) {
			case 0 -> shack(level, o, rnd);
			case 1 -> watchtower(level, o);
			case 2 -> depot(level, o, rnd);
			case LOT -> parkingLot(level, o, rnd);
			case 4 -> containerYard(level, o, rnd);
			default -> ruins(level, o, rnd);
		};
		if (building != null) BUILT.add(building);
		// Where each one went, for finding them when testing (they're only built once their chunks have loaded), how many
		// so far, and what's still due.
		MinebloxRoyale.LOG.info("BR: built {} at {} {} {} ({} of {}; still due: {} guaranteed, {} random)", KINDS[kind], x, y, z, CENTRES.size(), total, tally(GUARANTEED),
			EXTRA.size());
		boolean rare = rnd.nextFloat() < MinebloxRoyale.setting("enderChestStructure");
		return LootChests.place(level, loot, rare, rnd) ? loot : null;
	}

	/**
	 * The building for a spot: a guaranteed one first (a parking lot whenever one is due and the ground takes it), then a
	 * random one; null when nothing still due fits.
	 */
	private static Integer take(boolean lotFits, boolean smallFits, RandomSource rnd) {
		for (List<Integer> due : List.of(GUARANTEED, EXTRA)) {
			if (lotFits && due.remove((Integer) LOT)) return LOT;
			List<Integer> fit = smallFits ? due.stream().filter(k -> k != LOT).toList() : List.of();
			if (!fit.isEmpty()) {
				Integer k = fit.get(rnd.nextInt(fit.size()));
				due.remove(k);
				return k;
			}
		}
		return null;
	}

	/**
	 * The ground height at (x, z): the first space above solid ground, looking through trees, plants and snow layers (a
	 * tree trunk once counted as the ground, lifting a building into the air).
	 */
	private static int ground(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		for (int i = 0; i < 40 && p.getY() > level.getMinY(); i++) {
			BlockState s = level.getBlockState(p);
			boolean through = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() && s.getFluidState().isEmpty();
			if (!through) break;
			p.move(Direction.DOWN);
		}
		return p.getY() + 1;
	}

	/**
	 * Room for a building: the corners and edge middles of the area (half sizes hx, hz) within `slope` blocks of the
	 * middle's height, and dry: no water anywhere on it (it used to be checked at the middle only, so clearing a
	 * building beside a pond let the water in round its chest).
	 */
	private static boolean flat(ServerLevel level, int x, int z, int hx, int hz, int center, int slope) {
		for (int dx = -hx; dx <= hx; dx += hx)
			for (int dz = -hz; dz <= hz; dz += hz)
				if (Math.abs(ground(level, x + dx, z + dz) - center) > slope) return false;
		for (int dx = -hx - 1; dx <= hx + 1; dx++)
			for (int dz = -hz - 1; dz <= hz + 1; dz++) {
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
				if (!level.getFluidState(new BlockPos(x + dx, top - 1, z + dz)).isEmpty()) return false;
			}
		return true;
	}

	/** The space each building on this island takes up, so harvesting treats it like a village house. */
	private static final List<BoundingBox> BUILT = new ArrayList<>();
	private static BoundingBox building;

	/** Is this block inside one of this match's buildings (Harvest: its walls, roof and glass can be broken)? */
	static boolean contains(BlockPos pos) {
		for (BoundingBox b : BUILT) if (b.isInside(pos)) return true;
		return false;
	}

	/** Match over: the island is put back, so its buildings are gone. */
	static void clearAll() {
		BUILT.clear();
		GUARANTEED.clear();
		EXTRA.clear();
		CENTRES.clear();
	}

	private static void put(ServerLevel level, BlockPos p, BlockState s) {
		IslandRestore.remember(level, p);
		level.setBlock(p, s, 2 | 16);
		building = building == null ? new BoundingBox(p) : BoundingBox.encapsulating(building, new BoundingBox(p));
	}

	/** Solid floor across the footprint, with the gaps underneath filled down to the ground (no floating corners). */
	private static void floor(ServerLevel level, BlockPos o, int half, BlockState top, BlockState fill) {
		floor(level, o, half, half, top, fill);
	}

	private static void floor(ServerLevel level, BlockPos o, int hx, int hz, BlockState top, BlockState fill) {
		for (int dx = -hx; dx <= hx; dx++)
			for (int dz = -hz; dz <= hz; dz++) {
				put(level, o.offset(dx, -1, dz), top);
				for (int d = 2; d <= 7; d++) {
					BlockPos below = o.offset(dx, -d, dz);
					if (!level.getBlockState(below).canBeReplaced()) break;
					put(level, below, fill);
				}
			}
	}

	private static void clear(ServerLevel level, BlockPos o, int half, int height) {
		clear(level, o, half, half, height);
	}

	/**
	 * Air in the building's space (clears grass, flowers and leaves), and over its footprint up to the open sky,
	 * treetops included (a parking lot built in a dark oak forest was hidden under the canopy, creator: "I can't find
	 * the parking lot").
	 */
	private static void clear(ServerLevel level, BlockPos o, int hx, int hz, int height) {
		for (int dx = -hx; dx <= hx; dx++)
			for (int dz = -hz; dz <= hz; dz++) {
				int sky = level.getHeight(Heightmap.Types.WORLD_SURFACE, o.getX() + dx, o.getZ() + dz) - o.getY();
				for (int dy = 0; dy < Math.min(40, Math.max(height, sky)); dy++) {
					BlockPos p = o.offset(dx, dy, dz);
					if (!level.getBlockState(p).isAir()) put(level, p, Blocks.AIR.defaultBlockState());
				}
			}
	}

	/** Shack woods (creator: shacks of different woods, with matching doors). */
	private static final String[] WOODS = { "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry" };

	/** A block by id (26.3 keeps coloured and copper blocks in collections, not as named fields). */
	private static BlockState block(String id) {
		var b = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id));
		if (b.isEmpty()) MinebloxRoyale.LOG.warn("BR: no block {} in this version, iron used instead", id);
		return b.orElse(Blocks.IRON_BLOCK).defaultBlockState();
	}

	/**
	 * A 7 x 7 shack in a random wood: log corners, plank walls, glass windows, a door of the same wood in the front
	 * (-Z) doorway, and a flat slab roof (dark oak, or spruce on a dark-oak shack).
	 */
	private static BlockPos shack(ServerLevel level, BlockPos o, RandomSource rnd) {
		String wood = WOODS[rnd.nextInt(WOODS.length)];
		BlockState planks = block("minecraft:" + wood + "_planks"), logs = block("minecraft:" + wood + "_log"), glass = Blocks.GLASS.defaultBlockState();
		BlockState roof = block(wood.equals("dark_oak") ? "minecraft:spruce_slab" : "minecraft:dark_oak_slab").setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		clear(level, o, 4, 6);
		floor(level, o, 3, planks, Blocks.COBBLESTONE.defaultBlockState());
		for (int dx = -3; dx <= 3; dx++)
			for (int dz = -3; dz <= 3; dz++) {
				boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3, corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
				if (!edge) continue;
				for (int dy = 0; dy < 3; dy++) {
					BlockState st = corner ? logs : planks;
					if (!corner && dy == 1 && (Math.abs(dx) == 1 || Math.abs(dz) == 1) && !(dz == -3 && Math.abs(dx) <= 1)) st = glass;
					if (dz == -3 && dx == 0 && dy < 2) continue; // the doorway (front, -Z)
					put(level, o.offset(dx, dy, dz), st);
				}
			}
		// A door of the same wood, both halves, facing out of the front.
		BlockState door = block("minecraft:" + wood + "_door").setValue(DoorBlock.FACING, Direction.SOUTH)
			.setValue(DoorBlock.OPEN, false);
		put(level, o.offset(0, 0, -3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(level, o.offset(0, 1, -3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		for (int dx = -4; dx <= 4; dx++)
			for (int dz = -4; dz <= 4; dz++) put(level, o.offset(dx, 3, dz), roof);
		return o.offset(2, 0, 2);
	}

	/** A watchtower: four log legs, a plank lookout 6 up with a fence rail, a ladder up the middle. */
	private static BlockPos watchtower(ServerLevel level, BlockPos o) {
		clear(level, o, 3, 9);
		for (int dx = -2; dx <= 2; dx += 4)
			for (int dz = -2; dz <= 2; dz += 4)
				for (int dy = -1; dy < 6; dy++) put(level, o.offset(dx, dy, dz), Blocks.SPRUCE_LOG.defaultBlockState());
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 0 && dz == 0) continue; // the ladder hole
				put(level, o.offset(dx, 6, dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) put(level, o.offset(dx, 7, dz), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		// A post for the ladder to hang on, from the ground to the lookout.
		for (int dy = 0; dy <= 6; dy++) {
			put(level, o.offset(0, dy, 1), Blocks.SPRUCE_PLANKS.defaultBlockState());
			put(level, o.offset(0, dy, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		put(level, o.offset(0, 6, 0), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
		return o.offset(1, 7, -1);
	}

	/** A supply depot: a stone-brick yard with a gate, barrels and crates stacked inside. */
	private static BlockPos depot(ServerLevel level, BlockPos o, RandomSource rnd) {
		clear(level, o, 4, 4);
		floor(level, o, 4, Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
		for (int dx = -4; dx <= 4; dx++)
			for (int dz = -4; dz <= 4; dz++) {
				if (Math.abs(dx) != 4 && Math.abs(dz) != 4) continue;
				if (dz == -4 && Math.abs(dx) <= 1) continue; // the gate
				put(level, o.offset(dx, 0, dz), Blocks.STONE_BRICKS.defaultBlockState());
				put(level, o.offset(dx, 1, dz), Blocks.STONE_BRICK_WALL.defaultBlockState());
			}
		for (int i = 0; i < 6; i++) {
			int dx = -3 + rnd.nextInt(7), dz = 1 + rnd.nextInt(3);
			if (dx == 2 && dz == 2) continue;
			put(level, o.offset(dx, 0, dz), (rnd.nextBoolean() ? Blocks.BARREL : Blocks.SPRUCE_PLANKS).defaultBlockState());
		}
		return o.offset(2, 0, 2);
	}

	/** Ruins: broken cobblestone walls (some blocks missing, some mossy) round a small room. */
	private static BlockPos ruins(ServerLevel level, BlockPos o, RandomSource rnd) {
		clear(level, o, 3, 4);
		floor(level, o, 3, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
		for (int dx = -3; dx <= 3; dx++)
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue;
				int h = 1 + rnd.nextInt(3);
				for (int dy = 0; dy < h; dy++) {
					if (rnd.nextFloat() < 0.15F) continue;
					put(level, o.offset(dx, dy, dz), (rnd.nextFloat() < 0.35F ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
				}
			}
		return o;
	}

	/**
	 * A parking lot (creator: bigger, more cars): grey asphalt with white lines, parking spaces 4 wide in two rows of five
	 * either side of an aisle, about two in three of them taken, and a light post at each end of the aisle. The loot
	 * chest stands in the aisle. The ground under it is levelled (filled below, cleared above).
	 */
	private static BlockPos parkingLot(ServerLevel level, BlockPos o, RandomSource rnd) {
		int hx = 10, hz = 9;
		clear(level, o, hx, hz, 5);
		floor(level, o, hx, hz, block("minecraft:gray_concrete"), Blocks.COBBLESTONE.defaultBlockState());
		BlockState line = block("minecraft:white_concrete");
		for (int row = -5; row <= 5; row += 10) {
			for (int lx = -hx; lx <= hx; lx += 4)
				for (int dz = -3; dz <= 3; dz++) put(level, o.offset(lx, -1, row + dz), line);
			for (int cx = -hx + 2; cx <= hx - 2; cx += 4)
				if (rnd.nextFloat() < 0.65F) car(level, o.offset(cx, 0, row), rnd, rnd.nextBoolean() ? Direction.NORTH : Direction.SOUTH);
		}
		for (int side = -1; side <= 1; side += 2) lightPost(level, o.offset(side * (hx - 1), 0, 0));
		return o;
	}

	/** Car body colours (creator: other colours too): Fortnite's bright paints, plus white, black and silver. */
	private static final int[] PAINT = { 0xFFD8352C, 0xFF2F6BD8, 0xFFF2C230, 0xFFF08A24, 0xFF3BB84A, 0xFF1EB8A6, 0xFFF2F4F8, 0xFF2A2D33, 0xFFB9C1CC,
		0xFF8A4FD8, 0xFFF06AA8, 0xFF7A1F2B };

	/**
	 * A parked car, nose to `front` (creator: smooth models like the reboot van and Battle Bus, other types and colours,
	 * and they still break for metal): a sedan, sports car, pickup, SUV, hatchback, police car or taxi. The server only
	 * puts down its invisible body (CarBlock, 3 wide and 5 long, a second layer where the cabin is); every player's game
	 * draws the car itself (CarModel), from the type, colours and facing kept in the core block.
	 */
	private static void car(ServerLevel level, BlockPos c, RandomSource rnd, Direction front) {
		CarBlockEntity.Kind kind = pickKind(rnd);
		int color = kind == CarBlockEntity.Kind.POLICE ? 0xFF22252B : kind == CarBlockEntity.Kind.TAXI ? 0xFFF2C230 : PAINT[rnd.nextInt(PAINT.length)];
		int trim = kind == CarBlockEntity.Kind.POLICE ? 0xFFF2F4F8 : color == 0xFFF2F4F8 || color == 0xFFF2C230 ? 0xFF22252B : 0xFFF2F4F8;
		Direction right = front.getClockWise();
		BlockState part = ModBlocks.CAR_PART.defaultBlockState(), low = part.setValue(CarBlock.LOW, true);
		for (int lz = -2; lz <= 2; lz++)
			for (int lx = -1; lx <= 1; lx++) {
				BlockPos p = c.relative(front, -lz).relative(right, lx);
				put(level, p, lx == 0 && lz == 0 ? ModBlocks.CAR_CORE.defaultBlockState() : part);
				// The cabin (and a pickup's bed) above: full where the roof is high, half over a low one.
				int top = cabin(kind, lz);
				if (top > 0) put(level, p.above(), top == 2 ? part : low);
			}
		if (level.getBlockEntity(c) instanceof CarBlockEntity data) {
			data.setup(kind, color, trim, front);
			level.sendBlockUpdated(c, level.getBlockState(c), level.getBlockState(c), 3);
		}
	}

	private static CarBlockEntity.Kind pickKind(RandomSource rnd) {
		float r = rnd.nextFloat();
		return r < 0.25F ? CarBlockEntity.Kind.SEDAN : r < 0.4F ? CarBlockEntity.Kind.SPORTS : r < 0.55F ? CarBlockEntity.Kind.PICKUP : r < 0.7F ? CarBlockEntity.Kind.SUV
			: r < 0.85F ? CarBlockEntity.Kind.HATCHBACK : r < 0.92F ? CarBlockEntity.Kind.POLICE : CarBlockEntity.Kind.TAXI;
	}

	/** How high the body is over row lz (-2 = the nose): 0 nothing above the first block, 1 half a block more, 2 a whole one. */
	private static int cabin(CarBlockEntity.Kind kind, int lz) {
		return switch (kind) {
			case SPORTS -> lz >= -1 && lz <= 0 ? 1 : 0;
			case HATCHBACK -> lz >= -1 ? 1 : 0;
			case SUV -> lz >= -1 ? 2 : 0;
			case PICKUP -> lz >= -1 && lz <= 0 ? 2 : lz > 0 ? 1 : 0;
			default -> lz >= -1 && lz <= 1 ? 1 : 0;
		};
	}

	/** A parking-lot light: an iron pole with a lamp on top. */
	private static void lightPost(ServerLevel level, BlockPos at) {
		for (int dy = 0; dy < 4; dy++) put(level, at.above(dy), Blocks.IRON_BARS.defaultBlockState());
		put(level, at.above(4), Blocks.SEA_LANTERN.defaultBlockState());
	}

	/** Shipping container sides: cut copper in its four shades. */
	private static final String[] CONTAINER = { "minecraft:waxed_cut_copper", "minecraft:waxed_exposed_cut_copper", "minecraft:waxed_weathered_cut_copper",
		"minecraft:waxed_oxidized_cut_copper" };

	/** A container yard: two shipping containers (sometimes one stacked on the other), the loot inside the first. */
	private static BlockPos containerYard(ServerLevel level, BlockPos o, RandomSource rnd) {
		clear(level, o, 4, 7);
		floor(level, o, 4, Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
		container(level, o, rnd);
		container(level, rnd.nextFloat() < 0.4F ? o.above(3) : o.offset(0, 0, 3), rnd);
		return o.offset(-2, 0, 0);
	}

	/**
	 * A shipping container 7 long (along x), 3 wide and 3 tall: walls, roof and floor of one shade, open at the +X end
	 * with iron bars at the other.
	 */
	private static void container(ServerLevel level, BlockPos c, RandomSource rnd) {
		BlockState wall = block(CONTAINER[rnd.nextInt(CONTAINER.length)]);
		for (int dx = -3; dx <= 3; dx++)
			for (int dz = -1; dz <= 1; dz++)
				for (int dy = 0; dy <= 2; dy++) {
					boolean inside = dz == 0 && dy <= 1 && dx > -3;
					if (inside) put(level, c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					else if (dx == -3 && dz == 0 && dy == 1) put(level, c.offset(dx, dy, dz), Blocks.IRON_BARS.defaultBlockState());
					else put(level, c.offset(dx, dy, dz), wall);
				}
	}
}
