package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The island is reused match after match, so every block a match changes (trees and walls harvested by players or
 * bots, grass a build replaced) is remembered here the first time it changes, and put back when the match ends.
 * The list is also saved to the world folder every few seconds, so a crash mid-match is repaired on the next start.
 */
public final class IslandRestore {
	private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
	private static boolean dirty;

	private IslandRestore() {
	}

	/** Call before changing a block on the island. Only the first (original) state of each spot is kept. */
	public static void remember(ServerLevel level, BlockPos pos) {
		if (ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos)) == null) dirty = true;
	}

	/** Puts every changed block back as it was before the match. */
	public static void restoreAll(ServerLevel level) {
		if (ORIGINAL.isEmpty()) return;
		int n = ORIGINAL.size();
		// Logs before leaves and attachments (vines, cocoa) so nothing is placed without its support; no neighbour
		// updates, so leaves don't decay and vines don't drop while the tree is put back. No "on place" either: a block
		// put back isn't newly placed (a carved pumpkin next to a chunk that isn't loaded yet, at server start, crashed
		// Minecraft's iron golem check).
		List<Map.Entry<BlockPos, BlockState>> order = new ArrayList<>(ORIGINAL.entrySet());
		order.sort((a, b) -> Integer.compare(rank(a.getValue()), rank(b.getValue())));
		int failed = 0;
		for (var e : order) {
			try {
				if (level.getBlockState(e.getKey()) != e.getValue())
					level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE);
			} catch (RuntimeException ex) {
				if (failed++ == 0) MinebloxRoyale.LOG.warn("BR: couldn't put back {} at {}", e.getValue(), e.getKey(), ex);
			}
		}
		if (failed > 0) MinebloxRoyale.LOG.warn("BR: {} island blocks couldn't be put back", failed);
		ORIGINAL.clear();
		dirty = true;
		save(level.getServer());
		MinebloxRoyale.LOG.info("BR: island restored ({} blocks put back)", n);
	}

	private static int rank(BlockState s) {
		if (s.is(BlockTags.LOGS)) return 0;
		if (s.is(BlockTags.LEAVES)) return 1;
		return s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 0 : 2;
	}

	/** Saves the list when it changed (called from the match tick every few seconds). */
	public static void save(MinecraftServer server) {
		if (!dirty) return;
		dirty = false;
		try {
			Path f = file(server);
			if (ORIGINAL.isEmpty()) {
				Files.deleteIfExists(f);
				return;
			}
			StringBuilder sb = new StringBuilder();
			for (var e : ORIGINAL.entrySet()) {
				BlockPos p = e.getKey();
				sb.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append(' ').append(BlockStateParser.serialize(e.getValue())).append('\n');
			}
			Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			MinebloxRoyale.LOG.warn("BR: couldn't save the island restore list: {}", e.toString());
		}
	}

	/** Server start: if the last session stopped mid-match (crash), put the island back now. */
	public static void loadAndRestore(MinecraftServer server) {
		Path f = file(server);
		if (!Files.exists(f)) return;
		try {
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String[] parts = line.split(" ", 4);
				if (parts.length < 4) continue;
				BlockPos p = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				ORIGINAL.putIfAbsent(p, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, parts[3], false).blockState());
			}
		} catch (Exception e) {
			MinebloxRoyale.LOG.warn("BR: couldn't read the island restore list: {}", e.toString());
		}
		restoreAll(server.overworld());
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_restore.txt");
	}
}
