package gg.minebloxroyale.match;

import gg.minebloxroyale.MinebloxRoyale;
import gg.minebloxroyale.sheets.Sheets;
import gg.minebloxroyale.sheets.TerrainRuleDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Picks where a match island goes (terrain sheet): biomes are sampled from the world generator (no chunks generated),
 * and the first candidate with no excluded biome (ocean, beach, swamp, snow, mountains...), no ground above max_height
 * and no slope steeper than max_slope inside the first circle wins. Only land never generated before is used.
 */
public final class Island {
	private Island() {
	}

	public static double[] findCenter(ServerLevel level, double baseX, double baseZ, float radius) {
		return findCenter(level, baseX, baseZ, 1, 0, radius);
	}

	/** Searches from (baseX, baseZ) heading along (dirX, dirZ) (a unit vector). */
	public static double[] findCenter(ServerLevel level, double baseX, double baseZ, double dirX, double dirZ, float radius) {
		List<TagKey<Biome>> tags = new ArrayList<>();
		List<ResourceKey<Biome>> biomes = new ArrayList<>();
		int spacing = 40, maxHeight = Integer.MAX_VALUE, maxSlope = Integer.MAX_VALUE;
		for (TerrainRuleDef r : Sheets.TERRAIN) {
			switch (r.kind()) {
				case "exclude_biome_tag" -> tags.add(TagKey.create(Registries.BIOME, Identifier.parse(r.value())));
				case "exclude_biome" -> biomes.add(ResourceKey.create(Registries.BIOME, Identifier.parse(r.value())));
				case "sample_spacing" -> spacing = Integer.parseInt(r.value());
				case "max_height" -> maxHeight = Integer.parseInt(r.value());
				case "max_slope" -> maxSlope = Integer.parseInt(r.value());
				default -> {
				}
			}
		}
		int tries = Math.round(MinebloxRoyale.setting("landSearchTries"));
		Random rnd = new Random((long) (baseX * 31 + baseZ));
		int[] why = new int[3], bestWhy = new int[3]; // excluded biome, too high, too steep
		double bestX = baseX, bestZ = baseZ;
		int bestBad = Integer.MAX_VALUE;
		for (int t = 0; t < tries; t++) {
			// Spread out from the base, mostly in the chosen direction, further each try, so every match gets fresh
			// land and a bad stretch of terrain (mountains, sea) doesn't use up all the tries.
			double ang = Math.atan2(dirZ, dirX) + (rnd.nextDouble() - 0.5) * Math.PI * (t < 20 ? 0.6 : 2);
			double dist = radius * (1 + t * 0.6) * (0.6 + rnd.nextDouble() * 0.8);
			double cx = baseX + Math.cos(ang) * dist, cz = baseZ + Math.sin(ang) * dist;
			// Only land that was never generated: world-generation changes (e.g. no caves) apply to new chunks only.
			if (generated(level, cx, cz, radius + 64)) continue;
			int bad = 0, n = (int) (2 * radius / spacing) + 1;
			Arrays.fill(why, 0);
			int[][] h = new int[n][n];
			for (int ix = 0; ix < n && bad < bestBad; ix++)
				for (int iz = 0; iz < n; iz++) {
					double dx = -radius + ix * spacing, dz = -radius + iz * spacing;
					h[ix][iz] = Integer.MIN_VALUE;
					if (dx * dx + dz * dz > (double) radius * radius) continue;
					int x = (int) (cx + dx), z = (int) (cz + dz);
					Holder<Biome> b = level.getUncachedNoiseBiome(x >> 2, 64 >> 2, z >> 2);
					boolean excluded = biomes.stream().anyMatch(b::is) || tags.stream().anyMatch(b::is);
					if (excluded) {
						bad++;
						why[0]++;
					}
					// No giant mountains or cliffs: the ground height from the world generator (nothing is generated).
					if (maxHeight != Integer.MAX_VALUE || maxSlope != Integer.MAX_VALUE) {
						int y = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
						h[ix][iz] = y;
						if (y > maxHeight) {
							bad++;
							why[1]++;
						}
						if (ix > 0 && h[ix - 1][iz] != Integer.MIN_VALUE && Math.abs(y - h[ix - 1][iz]) > maxSlope || iz > 0 && h[ix][iz - 1] != Integer.MIN_VALUE && Math.abs(y - h[ix][iz - 1]) > maxSlope) {
							bad++;
							why[2]++;
						}
					}
				}
			if (bad < bestBad) {
				bestBad = bad;
				System.arraycopy(why, 0, bestWhy, 0, 3);
				bestX = cx;
				bestZ = cz;
			}
			if (bad == 0) break;
		}
		MinebloxRoyale.LOG.info("BR: island centre {} {} ({} bad samples: {} excluded biome, {} too high, {} too steep)", (int) bestX, (int) bestZ, bestBad,
			bestWhy[0], bestWhy[1], bestWhy[2]);
		return new double[] { bestX, bestZ };
	}

	/** True if any region file (512 x 512 blocks of saved chunks) overlaps the square around the centre. */
	private static boolean generated(ServerLevel level, double cx, double cz, double r) {
		Path root = level.getServer().getWorldPath(LevelResource.ROOT);
		Path dir = root.resolve("dimensions/minecraft/overworld/region");
		if (!Files.isDirectory(dir)) dir = root.resolve("region");
		for (int rx = Math.floorDiv((int) (cx - r), 512); rx <= Math.floorDiv((int) (cx + r), 512); rx++)
			for (int rz = Math.floorDiv((int) (cz - r), 512); rz <= Math.floorDiv((int) (cz + r), 512); rz++)
				if (Files.exists(dir.resolve("r." + rx + "." + rz + ".mca"))) return true;
		return false;
	}
}
