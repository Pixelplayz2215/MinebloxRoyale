package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.net.Net;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The whole island as one picture for the full map (Tab). Built on the server from the pregenerated chunks (clients
 * only know the chunks around them), saved next to the world and sent to every client once it is complete (the next
 * island's once the match it was made during is over).
 */
public final class IslandMap {
	public static final int RES = 256;
	/** The map being drawn: the island being generated. */
	private static int[] rgb, heights;
	private static double originX, originZ, span;
	private static Path file;
	/**
	 * The finished map everyone sees (Tab). The next island is generated during the match, so its map waits in pending
	 * until everyone is back in the hub. Server thread only.
	 */
	private static Net.@Nullable IslandMapImage shown, pending;
	private static boolean held;

	private IslandMap() {
	}

	/** A world opens: nothing to show until its own island's map is ready (never the last world's). */
	static void reset() {
		shown = null;
		pending = null;
		held = false;
	}

	/** A match starts: a map finished from now on is the next island's, kept back until the match is over. */
	static void hold() {
		held = true;
	}

	/** Back in the hub: the next island's map is shown if it's done (otherwise as soon as it is). */
	static void release(MinecraftServer server) {
		held = false;
		if (pending != null) publish(server, pending);
		pending = null;
	}

	private static void publish(MinecraftServer server, Net.IslandMapImage map) {
		if (held) {
			pending = map;
			return;
		}
		shown = map;
		server.getPlayerList().getPlayers().forEach(IslandMap::sendTo);
	}

	/** Sets the map up for this island. True when a finished map was already saved for it. */
	static boolean prepare(MinecraftServer server, double cx, double cz, float radius) {
		span = 2 * (radius + 64);
		originX = cx - span / 2;
		originZ = cz - span / 2;
		rgb = new int[RES * RES];
		heights = new int[RES * RES];
		Arrays.fill(rgb, 0x2A3A55);
		Arrays.fill(heights, Integer.MIN_VALUE);
		// "_v2": maps saved before the chunk-edge fix had dark lines, so they are drawn again once.
		file = server.getWorldPath(LevelResource.ROOT).resolve("minebloxroyale_map_" + (int) cx + "_" + (int) cz + "_v2.png");
		if (!Files.exists(file)) return false;
		try {
			BufferedImage img = ImageIO.read(file.toFile());
			if (img == null || img.getWidth() != RES) return false;
			img.getRGB(0, 0, RES, RES, rgb, 0, RES);
			for (int i = 0; i < rgb.length; i++) rgb[i] &= 0xFFFFFF;
			publish(server, image());
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** Called for every generated island chunk: fills the pixels inside it (top block's map colour, height). */
	static void sample(ChunkAccess chunk) {
		if (rgb == null) return;
		double bpp = span / RES;
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		// Every pixel whose world spot falls inside this chunk's 16 x 16 blocks (no gaps between chunks).
		int px0 = (int) Math.ceil((x0 - originX) / bpp), px1 = (int) Math.ceil((x0 + 16 - originX) / bpp) - 1;
		int pz0 = (int) Math.ceil((z0 - originZ) / bpp), pz1 = (int) Math.ceil((z0 + 16 - originZ) / bpp) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int pz = Math.max(0, pz0); pz <= Math.min(RES - 1, pz1); pz++)
			for (int px = Math.max(0, px0); px <= Math.min(RES - 1, px1); px++) {
				int wx = (int) Math.floor(originX + px * bpp), wz = (int) Math.floor(originZ + pz * bpp);
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
				// The heightmap can point at the air just above the ground: step down to the first block with a colour.
				MapColor c = MapColor.NONE;
				for (int down = 0; down < 4 && c == MapColor.NONE; down++) {
					pos.set(wx, y - down, wz);
					c = chunk.getBlockState(pos).getMapColor(chunk, pos);
				}
				rgb[pz * RES + px] = c == MapColor.NONE ? 0x2A3A55 : c.col;
				heights[pz * RES + px] = y;
			}
	}

	/** All chunks sampled (island worker thread): hill shading, save, show it (or keep it for after the match). */
	static void finish(MinecraftServer server) {
		if (rgb == null) return;
		for (int pz = RES - 1; pz >= 1; pz--)
			for (int px = RES - 1; px >= 1; px--) {
				int h = heights[pz * RES + px], hn = heights[(pz - 1) * RES + px - 1];
				if (h == Integer.MIN_VALUE || hn == Integer.MIN_VALUE) continue;
				int d = Math.max(-3, Math.min(3, h - hn)) * 12, c = rgb[pz * RES + px];
				rgb[pz * RES + px] = clamp((c >> 16 & 255) + d) << 16 | clamp((c >> 8 & 255) + d) << 8 | clamp((c & 255) + d);
			}
		try {
			BufferedImage img = new BufferedImage(RES, RES, BufferedImage.TYPE_INT_RGB);
			img.setRGB(0, 0, RES, RES, rgb, 0, RES);
			ImageIO.write(img, "png", file.toFile());
		} catch (IOException e) {
			MinebloxRoyale.couldntSave("the island map", file, e);
		}
		Net.IslandMapImage map = image();
		MinebloxRoyale.LOG.info("BR: island map ready");
		server.execute(() -> publish(server, map));
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	public static void sendTo(ServerPlayer p) {
		if (shown != null) Net.mapTo(p, shown);
	}

	private static Net.IslandMapImage image() {
		byte[] bytes = new byte[RES * RES * 3];
		for (int i = 0; i < rgb.length; i++) {
			bytes[i * 3] = (byte) (rgb[i] >> 16);
			bytes[i * 3 + 1] = (byte) (rgb[i] >> 8);
			bytes[i * 3 + 2] = (byte) rgb[i];
		}
		return new Net.IslandMapImage(RES, (float) originX, (float) originZ, (float) span, bytes);
	}
}
