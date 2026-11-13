package net.deepcanion.skylands.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Checks for real procedural Sky Terrain ABOVE a proposed ground-level fragment.
 * Uses the SAME deterministic site seeds and sky-biome anchor sampling as
 * SkyTerrainFeature, without loading nearby chunks or reading their blocks.
 */
final class FallenIslandSkyLink {
    private static final int NEAR = 40;
    // A few 2-D sample points out to 40 blocks: outside a real island's
    // footprint is allowed, but a nearby empty sky biome is not enough.
    private static final int[][] OFFSETS = {
            {0,0}, {16,0}, {-16,0}, {0,16}, {0,-16},
            {28,0}, {-28,0}, {0,28}, {0,-28},
            {28,28}, {28,-28}, {-28,28}, {-28,-28},
            {40,0}, {-40,0}, {0,40}, {0,-40}
    };

    private FallenIslandSkyLink() { }

    static String nearbyTheme(long seed, BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        int gridX = Math.floorDiv(x, SkyIslandShape.GRID);
        int gridZ = Math.floorDiv(z, SkyIslandShape.GRID);
        String nearestTheme = null;
        int nearestDistance2 = Integer.MAX_VALUE;
        for (int gx = gridX - 2; gx <= gridX + 2; gx++) {
            for (int gz = gridZ - 2; gz <= gridZ + 2; gz++) {
                if (!SkyIslandShape.hasSite(seed, gx, gz)) continue;
                int sx = SkyIslandShape.siteX(seed, gx, gz);
                int sz = SkyIslandShape.siteZ(seed, gx, gz);
                if (Math.abs(sx - x) > 290 || Math.abs(sz - z) > 290) continue;
                Holder<Biome> anchor = biomes.getNoiseBiome(QuartPos.fromBlock(sx),
                        QuartPos.fromBlock(350), QuartPos.fromBlock(sz), sampler);
                String theme = SkyTerrainFeature.themeOf(anchor);
                if (theme == null) continue;
                for (SkyIslandShape.Island island : SkyIslandShape.sites(seed, gx, gz, theme)) {
                    if (!island.intersects(x - NEAR, z - NEAR, x + NEAR, z + NEAR)) continue;
                    for (int[] offset : OFFSETS) {
                        int dist2 = offset[0] * offset[0] + offset[1] * offset[1];
                        if (dist2 >= nearestDistance2 || dist2 > NEAR * NEAR) continue;
                        int px = x + offset[0], pz = z + offset[1];
                        if (!island.touches(px, pz)) continue;
                        SkyIslandShape.Column column = island.column(px, pz);
                        // At least some actual sky rock must exist in this
                        // column. A biome match or expanded geometry bound alone
                        // can falsely identify huge areas of completely empty sky.
                        if (!hasRock(column, island)) continue;
                        nearestDistance2 = dist2;
                        nearestTheme = theme;
                        if (dist2 == 0) return nearestTheme;
                    }
                }
            }
        }
        return nearestTheme;
    }

    private static boolean hasRock(SkyIslandShape.Column column, SkyIslandShape.Island island) {
        int center = (int) Math.round(island.centerY());
        for (int dy : new int[]{0, -12, 12, -25, 25, -40}) {
            int y = center + dy;
            if (y >= island.minY() && y <= island.maxY() && column.solid(y)) return true;
        }
        return false;
    }
}
