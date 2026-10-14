package gg.minebloxroyale.hub;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.HubPieceDef;
import gg.minebloxroyale.sheets.ModeDef;
import gg.minebloxroyale.sheets.ObbyStageDef;
import gg.minebloxroyale.sheets.Sheets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** Builds the Roblox-style hub (a grey studded baseplate in the sky) and the obby from the hub and obby sheets. */
public final class HubBuilder {
	/** One obby platform: its top-surface blocks span [x0, x0+sizeX) x [z0, z0+sizeZ) at height y. */
	public record Platform(ObbyStageDef stage, int x0, int y, int z0) {
		public boolean contains(double px, double py, double pz) {
			return px >= x0 - 0.3 && px < x0 + stage.sizeX() + 0.3 && pz >= z0 - 0.3 && pz < z0 + stage.sizeZ() + 0.3 && py >= y + 0.9 && py < y + 2.5;
		}

		public Vec3 top() {
			return new Vec3(x0 + stage.sizeX() / 2.0, y + 1, z0 + stage.sizeZ() / 2.0);
		}
	}

	private static final List<Platform> PLATFORMS = new ArrayList<>();

	private HubBuilder() {
	}

	public static int hubY() {
		return Math.round(MinebloxRoyale.setting("hubY"));
	}

	/** Every hub text carries the build's version tag; text from an older build is deleted when its chunk loads. */
	static final String VERSION = "v8-" + Integer.toHexString((Sheets.HUB.toString() + Sheets.OBBY.toString() + MinebloxRoyale.version()).hashCode());
	public static final String VERSION_TAG = "br_hub_" + VERSION;

	/**
	 * Hub text (labels, boards) left from an older hub build: kill commands only reach loaded chunks, so check on load.
	 */
	public static boolean isStaleText(Entity e) {
		return e instanceof Display && e.entityTags().contains("br_hub_text") && !e.entityTags().contains(VERSION_TAG);
	}

	public static HubPieceDef piece(String id) {
		return Sheets.HUB_BY_ID.get(id);
	}

	public static Vec3 spawnPos() {
		HubPieceDef s = piece("spawn");
		return new Vec3(s.x() + 0.5, hubY() + s.y(), s.z() + 0.5);
	}

	public static List<Platform> platforms() {
		if (PLATFORMS.isEmpty()) {
			HubPieceDef entry = piece("obby_entry");
			int x = Math.round(entry.x() - entry.width() / 2F) - 1;
			int y = hubY();
			int z = Math.round(entry.z());
			for (ObbyStageDef s : Sheets.OBBY.stream().sorted((a, b) -> Integer.compare(a.index(), b.index())).toList()) {
				x += s.dx();
				y += s.dy();
				z += s.dz();
				PLATFORMS.add(new Platform(s, x - s.sizeX() + 1, y, z - s.sizeZ() / 2));
			}
		}
		return PLATFORMS;
	}

	/** The mode portal (Solo, Duos, Trios, Squads; modes sheet) this player stands on, or null. */
	public static ModeDef portalMode(ServerPlayer p) {
		if (p.isSpectator() || p.getY() < hubY() + 0.5 || p.getY() >= hubY() + 3) return null;
		for (ModeDef m : Sheets.MODES) {
			HubPieceDef portal = piece(m.portal());
			double half = portal.width() / 2.0;
			if (Math.abs(p.getX() - (portal.x() + 0.5)) < half && Math.abs(p.getZ() - (portal.z() + 0.5)) < half) return m;
		}
		return null;
	}

	public static void ensureBuilt(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GameRules rules = level.getGameRules();
		rules.set(GameRules.ADVANCE_TIME, false, server);
		rules.set(GameRules.ADVANCE_WEATHER, false, server);
		rules.set(GameRules.SPAWN_MOBS, false, server);
		rules.set(GameRules.SPAWN_PHANTOMS, false, server);
		// No mobs at all (creator): no monsters, patrols or traders; animals made by world generation are removed as
		// they load (MinebloxRoyale, ENTITY_LOAD). Fire never spreads.
		rules.set(GameRules.SPAWN_MONSTERS, false, server);
		rules.set(GameRules.SPAWN_PATROLS, false, server);
		rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
		rules.set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, server);
		rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server); // no hunger, no food, no regen: only heals heal
		rules.set(GameRules.SHOW_DEATH_MESSAGES, false, server);
		rules.set(GameRules.LOCATOR_BAR, false, server); // no player tracking above the hotbar (creator request)
		rules.set(GameRules.RESPAWN_RADIUS, 0, server); // joining players appear exactly on the spawn pad, never beside it
		rules.set(GameRules.LIMITED_CRAFTING, true, server); // crafting off: players know no recipes
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
		// The hub's chunks stay loaded all the time: a player joining (or sent back from a match) never stands over an
		// unloaded hub for a moment and falls through it.
		HubPieceDef plate = piece("baseplate");
		int phw = Math.round(plate.width() / 2F) + 2, phh = Math.round(plate.height() / 2F) + 2;
		for (int ccx = (Math.round(plate.x()) - phw) >> 4; ccx <= (Math.round(plate.x()) + phw) >> 4; ccx++)
			for (int ccz = (Math.round(plate.z()) - phh) >> 4; ccz <= (Math.round(plate.z()) + phh) >> 4; ccz++) level.setChunkForced(ccx, ccz, true);
		Vec3 spawn = spawnPos();
		server.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, BlockPos.containing(spawn), 180F, 0F));
		int y = hubY();
		// Rebuild when the hub/obby sheets changed since this world's hub was built (sheet changes, like test9's four
		// mode portals and commands board, change the hash part). The number goes up for code changes: "v2": hubs built
		// before the old-hub check was fixed are cleared once (old obby platforms were left floating). "v3": the floor
		// is 4 blocks thick. "v4": rebuilt once to bring back text that failed to appear (see label). "v5": the Sky
		// Tower has one more step, so its last jump is one block. "v6": the shooting range's rounds line. "v7": that
		// line bigger. "v8": the main board shows the build's version, so every new build's hub is rebuilt once.
		String version = VERSION;
		Path marker = server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_hub.txt");
		String built = "";
		try {
			built = Files.exists(marker) ? Files.readString(marker).trim() : "";
		} catch (IOException ignored) {
		}
		// Checked off the grid lines (x/z multiples of 8 are grey), so an old hub is really found and cleared.
		boolean hasHub = level.getBlockState(new BlockPos(4, y, 4)).is(block(piece("baseplate").block()))
			|| level.getBlockState(new BlockPos(0, y, 0)).is(block("minecraft:gray_concrete")) || level.getBlockState(new BlockPos(0, y, 0)).is(block("minecraft:white_concrete"));
		if (hasHub && built.equals(version)) return;
		long t0 = System.currentTimeMillis();
		if (hasHub) {
			MinebloxRoyale.LOG.info("BR: hub sheets changed ({} -> {}), clearing the old hub", built, version);
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int x = -160; x <= 60; x++)
				for (int z = -80; z <= 80; z++)
					for (int yy = y - 8; yy <= y + 64; yy++)
						if (!level.getBlockState(new BlockPos(x, yy, z)).isAir()) set(level, x, yy, z, air);
		}
		MinebloxRoyale.LOG.info("BR: building the hub at y={}", y);
		command(server, "kill @e[tag=br_hub_text]"); // hub text and the boards' item icons
		Leaderboard.markDirty();
		for (HubPieceDef p : Sheets.HUB) build(level, p, y);
		for (Platform pl : platforms()) {
			Block b = block(pl.stage().block());
			for (int dx = 0; dx < pl.stage().sizeX(); dx++)
				for (int dz = 0; dz < pl.stage().sizeZ(); dz++)
					set(level, pl.x0() + dx, pl.y(), pl.z0() + dz, b.defaultBlockState());
		}
		decorateObby(server, level);
		// Marked as built only once everything is in place (a crash half-way rebuilds it next time).
		try {
			Files.writeString(marker, version);
		} catch (IOException e) {
			MinebloxRoyale.LOG.warn("BR: couldn't write hub marker", e);
		}
		MinebloxRoyale.LOG.info("BR: hub built in {} ms ({} obby platforms)", System.currentTimeMillis() - t0, platforms().size());
	}

	private static void build(ServerLevel level, HubPieceDef p, int hubY) {
		BlockState block = block(p.block()).defaultBlockState();
		int cx = Math.round(p.x()), cz = Math.round(p.z()), y = hubY + Math.round(p.y());
		int halfW = Math.round(p.width() / 2F), halfH = Math.round(p.height() / 2F);
		switch (p.kind()) {
			case "baseplate" -> {
				BlockState line = block("minecraft:gray_concrete").defaultBlockState();
				// 4 blocks thick (creator): nobody joining can end up inside or under the floor.
				for (int x = cx - halfW; x < cx + halfW; x++)
					for (int z = cz - halfH; z < cz + halfH; z++) {
						for (int d = 1; d <= 3; d++) set(level, x, y - d, z, block);
						set(level, x, y, z, (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) ? line : block);
					}
			}
			case "spawn", "obby_start" -> {
				for (int x = cx - halfW; x <= cx + halfW; x++)
					for (int z = cz - halfH; z <= cz + halfH; z++)
						set(level, x, hubY, z, block);
			}
			case "portal" -> {
				// Exactly one floor square (7 x 7 between the grid lines), rimmed and glazed in its mode's colour, the
				// same as its floating name (creator).
				ModeDef mode = Sheets.MODES.stream().filter(m -> m.portal().equals(p.id())).findFirst().orElse(null);
				BlockState rim = mode == null ? Blocks.AMETHYST_BLOCK.defaultBlockState() : block(mode.edge()).defaultBlockState();
				int hw = (int) (p.width() / 2), hh = (int) (p.height() / 2);
				for (int x = cx - hw; x <= cx + hw; x++)
					for (int z = cz - hh; z <= cz + hh; z++) {
						boolean edge = x == cx - hw || x == cx + hw || z == cz - hh || z == cz + hh;
						set(level, x, hubY, z, edge ? rim : block);
						if (!edge) set(level, x, hubY - 1, z, Blocks.SEA_LANTERN.defaultBlockState());
					}
				String[] lines = p.label().split(Pattern.quote(" | "));
				String color = mode == null ? "light_purple" : String.format(Locale.ROOT, "#%06X", mode.color() & 0xFFFFFF);
				label(level.getServer(), cx + 0.5, hubY + 3.6, cz + 0.5, lines[0], color, 2.4F, "center", "south", true);
				if (lines.length > 1) label(level.getServer(), cx + 0.5, hubY + 2.9, cz + 0.5, lines[1], "white", 0.9F, "center", "south", true);
			}
			case "bus_display" -> {
				// A stone pad the parked Battle Bus (drawn by the client) stands on.
				int hw = Math.round(p.height() / 2F) + 1, hl = Math.round(p.width() / 2F) + 1;
				boolean alongX = p.facing().equals("east") || p.facing().equals("west");
				for (int a = -hl; a <= hl; a++)
					for (int b = -hw; b <= hw; b++)
						set(level, cx + (alongX ? a : b), hubY, cz + (alongX ? b : a), block);
			}
			case "text_board" -> {
				// A black board on two legs, with the label as big text on its front (' | ' = new line, first line =
				// title).
				boardFrame(level, p, hubY, block);
				String[] lines = p.label().split(Pattern.quote(" | "));
				double[] t = boardText(p);
				if (p.id().equals("rules_board")) titleWithVersion(level.getServer(), p, t, lines[0]);
				else label(level.getServer(), t[0], t[1], t[2], lines[0], "gold", (float) t[3] * 1.7F, "fixed", p.facing(), false);
				for (int i = 1; i < lines.length; i++)
					label(level.getServer(), t[0], boardLineY(p, i), t[2], lines[i], "white", (float) t[3], "fixed", p.facing(), false);
			}
			case "billboard" -> {
				// A black frame one block behind where the client draws the picture; legs down to the baseplate.
				int w = Math.round(p.width() / 2F) + 1, h = Math.round(p.height() / 2F) + 1;
				for (int a = -w; a <= w; a++)
					for (int b = -h; b <= h; b++) {
						int bx = p.facing().equals("south") || p.facing().equals("north") ? cx + a : cx + (p.facing().equals("west") ? 1 : -1);
						int bz = p.facing().equals("south") || p.facing().equals("north") ? cz + (p.facing().equals("south") ? -1 : 1) : cz + a;
						set(level, bx, y + b, bz, block);
					}
				for (int legY = hubY + 1; legY < y - h; legY++)
					for (int a : new int[] { -w + 2, w - 2 }) {
						int bx = p.facing().equals("south") || p.facing().equals("north") ? cx + a : cx + (p.facing().equals("west") ? 1 : -1);
						int bz = p.facing().equals("south") || p.facing().equals("north") ? cz + (p.facing().equals("south") ? -1 : 1) : cz + a;
						set(level, bx, legY, bz, Blocks.IRON_BLOCK.defaultBlockState());
					}
			}
			case "rarity_board", "heals_board" -> BoardRows.build(level, p, hubY, block);
			case "shooting_range" -> HubFun.buildRange(level, p, hubY);
			case "tower_obby" -> HubFun.buildTower(level, p, hubY);
			default -> {
			}
		}
	}

	/** A board's frame: black (the piece's block) with a purple border, on two iron legs, one block behind its text. */
	static void boardFrame(ServerLevel level, HubPieceDef p, int hubY, BlockState block) {
		int cx = Math.round(p.x()), cz = Math.round(p.z()), y = hubY + Math.round(p.y());
		int w = Math.round(p.width() / 2F) + 1, h = Math.round(p.height() / 2F) + 1;
		boolean ns = p.facing().equals("south") || p.facing().equals("north");
		int back = p.facing().equals("south") || p.facing().equals("east") ? -1 : 1;
		for (int a = -w; a <= w; a++)
			for (int b = -h; b <= h; b++)
				set(level, ns ? cx + a : cx + back, y + b, ns ? cz + back : cz + a, (Math.abs(a) == w || Math.abs(b) == h) ? block("minecraft:purple_concrete").defaultBlockState() : block);
		for (int legY = hubY + 1; legY < y - h; legY++)
			for (int a : new int[] { -w + 2, w - 2 }) set(level, ns ? cx + a : cx + back, legY, ns ? cz + back : cz + a, Blocks.IRON_BLOCK.defaultBlockState());
	}

	/** Where a text board's text goes: {x, title y, z, line scale}. */
	static double[] boardText(HubPieceDef p) {
		int cx = Math.round(p.x()), cz = Math.round(p.z()), y = hubY() + Math.round(p.y());
		int h = Math.round(p.height() / 2F) + 1;
		boolean ns = p.facing().equals("south") || p.facing().equals("north");
		int back = p.facing().equals("south") || p.facing().equals("east") ? -1 : 1;
		double front = back < 0 ? 0.1 : 0.9;
		float lineScale = p.width() >= 24 ? 3F : 2F;
		return new double[] { ns ? cx + 0.5 : cx + front, y + h - 1.6 - lineScale * 0.4, ns ? cz + front : cz + 0.5, lineScale };
	}

	/**
	 * The main board's title with the build's version beside it (creator: "next to the game name can we add the current
	 * version"): "MINEBLOX ROYALE" in gold and the version in aqua at the board's line size, the pair centred on the
	 * board, both standing on the title's line.
	 */
	private static void titleWithVersion(MinecraftServer server, HubPieceDef p, double[] t, String title) {
		String version = MinebloxRoyale.versionName();
		float titleScale = (float) t[3] * 1.7F, versionScale = (float) t[3];
		// A text display's font pixel is 0.025 blocks at scale 1.
		double tw = textWidth(title) * 0.025 * titleScale, vw = textWidth(version) * 0.025 * versionScale, gap = 0.8, shift = (gap + vw) / 2;
		double vx = tw / 2 + gap + vw / 2 - shift;
		int[] r = readersRight(p.facing());
		label(server, t[0] - r[0] * shift, t[1], t[2] - r[1] * shift, title, "gold", titleScale, "fixed", p.facing(), false);
		label(server, t[0] + r[0] * vx, t[1], t[2] + r[1] * vx, version, "aqua", versionScale, "fixed", p.facing(), false);
	}

	/** Bold text's width in font pixels (Minecraft's font: most characters 5 wide plus 1 apart, bold 1 more). */
	static int textWidth(String s) {
		int w = 0;
		for (char c : s.toCharArray())
			w += switch (c) {
				case ' ' -> 4;
				case 'I', 't' -> 5;
				case 'i', '.', ',', ':', ';', '!', '|', '\'' -> 3;
				case 'l' -> 4;
				default -> 7;
			};
		return w;
	}

	/** The way a reader of a board facing this way has on their right, as an x / z step. */
	private static int[] readersRight(String facing) {
		return switch (facing) {
			case "north" -> new int[] { -1, 0 };
			case "east" -> new int[] { 0, -1 };
			case "west" -> new int[] { 0, 1 };
			default -> new int[] { 1, 0 };
		};
	}

	/** Height of text line i (1 = first line under the title) on a text board. */
	static double boardLineY(HubPieceDef p, double i) {
		double[] t = boardText(p);
		return t[1] - t[3] * 0.7 - i * t[3] * 0.33 * 1.25;
	}

	static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
	}

	/** Yaw that makes a text display read from the side it faces. */
	static float yawFor(String facing) {
		return switch (facing) {
			case "west" -> 90F;
			case "north" -> 180F;
			case "east" -> -90F;
			default -> 0F;
		};
	}

	static String json(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/**
	 * Floating text (a vanilla text display, tagged so a rebuild replaces it). billboard: "center" turns to face the
	 * viewer, "fixed" stays on a board facing 'facing'.
	 */
	static void label(MinecraftServer server, double x, double y, double z, String text, String color, float scale, String billboard, String facing, boolean background) {
		label(server, "br_hub_text", x, y, z, text, color, scale, billboard, facing, background);
	}

	static void label(MinecraftServer server, String tag, double x, double y, double z, String text, String color, float scale, String billboard, String facing, boolean background) {
		// The summon command silently does nothing where the world isn't loaded (a hub rebuilt at server start, before
		// anyone is near the boards): load the spot first.
		server.overworld().getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
		command(server, String.format(Locale.ROOT,
			"summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\",\"" + VERSION_TAG + "\"],billboard:\"%s\",Rotation:[%.1ff,0f],line_width:%d,text:{text:\"%s\",color:\"%s\",bold:true},"
				+ "background:%d,shadow:1b,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
			x, y, z, tag, billboard, yawFor(facing), 400, json(text), color, background ? 0x60000000 : 0, scale, scale, scale));
	}

	/**
	 * Roblox-obby polish: a soft glow under every platform, light posts and "CHECKPOINT n/3" over each checkpoint, a
	 * golden "FINISH" over the end, and a sign at the start.
	 */
	private static void decorateObby(MinecraftServer server, ServerLevel level) {
		int checkpoints = (int) platforms().stream().filter(pl -> pl.stage().checkpoint() && !pl.stage().finish()).count(), n = 0;
		for (Platform pl : platforms()) {
			ObbyStageDef s = pl.stage();
			int cx = pl.x0() + s.sizeX() / 2, cz = pl.z0() + s.sizeZ() / 2;
			set(level, cx, pl.y() - 1, cz, Blocks.SEA_LANTERN.defaultBlockState());
			Vec3 top = pl.top();
			if (s.finish()) {
				for (int[] c : new int[][] { { 0, 0 }, { s.sizeX() - 1, 0 }, { 0, s.sizeZ() - 1 }, { s.sizeX() - 1, s.sizeZ() - 1 } })
					set(level, pl.x0() + c[0], pl.y() + 1, pl.z0() + c[1], Blocks.END_ROD.defaultBlockState());
				label(server, top.x, top.y + 2.2, top.z, "FINISH!", "gold", 2.5F, "center", "south", true);
				label(server, top.x, top.y + 1.4, top.z, "Unlocks an emote - back to the hub", "yellow", 0.9F, "center", "south", true);
			} else if (s.checkpoint()) {
				n++;
				set(level, pl.x0(), pl.y() + 1, pl.z0(), Blocks.END_ROD.defaultBlockState());
				set(level, pl.x0() + s.sizeX() - 1, pl.y() + 1, pl.z0() + s.sizeZ() - 1, Blocks.END_ROD.defaultBlockState());
				label(server, top.x, top.y + 1.8, top.z, "CHECKPOINT " + n + "/" + checkpoints, "green", 1.4F, "center", "south", true);
			}
		}
		HubPieceDef entry = piece("obby_entry");
		String[] lines = entry.label().split(Pattern.quote(" | "));
		label(server, entry.x() + 0.5, hubY() + 3.4, entry.z() + 0.5, lines[0], "aqua", 2F, "center", "south", true);
		if (lines.length > 1) label(server, entry.x() + 0.5, hubY() + 2.6, entry.z() + 0.5, lines[1], "white", 0.9F, "center", "south", true);
	}

	static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}

	static Block block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
	}
}
