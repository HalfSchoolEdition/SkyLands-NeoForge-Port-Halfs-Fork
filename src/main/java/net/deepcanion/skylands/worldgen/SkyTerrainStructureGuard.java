package net.deepcanion.skylands.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Predicts procedural sky terrain during structure-start selection without
 * generating/loading neighboring chunks. Uses exactly the same site/biome
 * selection as SkyTerrainFeature. This is a deliberately conservative sampling
 * guard: an island structure should be rejected rather than hollowed out by
 * another landmass. It has no effect on normal surface structures.
 */
final class SkyTerrainStructureGuard {
    private SkyTerrainStructureGuard() { }

    static boolean wouldIntersect(long seed, BiomeSource biomeSource, Climate.Sampler sampler,
                                  int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ) {
        if (maxY < 270 || minY > SkyIslandShape.MAX_Y) return false;
        int gx0 = Math.floorDiv(minX, SkyIslandShape.GRID) - 2;
        int gx1 = Math.floorDiv(maxX, SkyIslandShape.GRID) + 2;
        int gz0 = Math.floorDiv(minZ, SkyIslandShape.GRID) - 2;
        int gz1 = Math.floorDiv(maxZ, SkyIslandShape.GRID) + 2;
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                if (!SkyIslandShape.hasSite(seed, gx, gz)) continue;
                int sx = SkyIslandShape.siteX(seed, gx, gz);
                int sz = SkyIslandShape.siteZ(seed, gx, gz);
                String theme = SkyTerrainFeature.themeOf(biomeSource.getNoiseBiome(
                        QuartPos.fromBlock(sx), QuartPos.fromBlock(350),
                        QuartPos.fromBlock(sz), sampler));
                if (theme == null) continue;
                for (SkyIslandShape.Island island : SkyIslandShape.sites(seed, gx, gz, theme)) {
                    if (!island.intersects(minX, minZ, maxX, maxZ)
                            || island.minY() > maxY || island.maxY() < minY) continue;
                    int bottom = Math.max(minY, island.minY());
                    int top = Math.min(maxY, island.maxY());
                    // Sample the structure's potential volume. A coarse 16-block
                    // lattice catches bulk intersections without evaluating
                    // every voxel or requiring already-generated chunks.
                    for (int x = minX; x <= maxX; x += 16) {
                        for (int z = minZ; z <= maxZ; z += 16) {
                            if (!island.touches(x, z)) continue;
                            SkyIslandShape.Column column = island.column(x, z);
                            for (int y = bottom; y <= top; y += 12) {
                                if (column.solid(y)) return true;
                            }
                            if (column.solid(top)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
