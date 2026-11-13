package net.deepcanion.skylands.worldgen;

import java.util.List;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Places polygonal basalt cliff columns into Sky Terrain, chunk locally. */
final class SkyTerrainBasaltColumns {
    private SkyTerrainBasaltColumns() { }

    static int decorate(WorldGenLevel level, long seed, int chunkX, int chunkZ,
                        List<SkyIslandShape.Island> goldenIslands) {
        List<SkyTerrainBasaltGeometry.Formation> formations =
                SkyTerrainBasaltGeometry.forChunk(seed, chunkX, chunkZ, goldenIslands);
        if (formations.isEmpty()) return 0;

        BlockState basalt = Blocks.BASALT.defaultBlockState();
        Block goldenGrass = BuiltInRegistries.BLOCK.get(SkyLands.id("golden_grass_block"));
        Block goldenRock = BuiltInRegistries.BLOCK.get(SkyLands.id("skyrock"));
        Block goldenMoss = BuiltInRegistries.BLOCK.get(SkyLands.id("golden_mossy_skyrock"));
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int placed = 0;

        for (SkyTerrainBasaltGeometry.Formation formation : formations) {
            for (SkyTerrainBasaltGeometry.Pillar pillar : formation.pillars()) {
                int x0 = Math.max(chunkX, pillar.minX());
                int x1 = Math.min(chunkX + 15, pillar.maxX());
                int z0 = Math.max(chunkZ, pillar.minZ());
                int z1 = Math.min(chunkZ + 15, pillar.maxZ());
                if (x0 > x1 || z0 > z1) continue;
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (!pillar.contains(x, z) || !formation.island().touches(x, z)) continue;
                        // A nearby basin's water and shore contours must not
                        // be cut by an extruding cliff column.
                        SkyIslandShape.Lake lake = formation.island().lake();
                        if (lake != null && lake.plateauClearance(x, z) < .88) continue;
                        for (int y = Math.max(pillar.bottom(), level.getMinBuildHeight());
                             y <= pillar.top() && y < level.getMaxBuildHeight(); y++) {
                            at.set(x, y, z);
                            BlockState existing = level.getBlockState(at);
                            // Only fill air or replace natural Golden terrain;
                            // never destroy other sky biomes, plants, ores,
                            // structures, fluids or pre-existing decorations.
                            if (!existing.isAir() && !existing.is(goldenGrass)
                                    && !existing.is(goldenRock) && !existing.is(goldenMoss))
                                continue;
                            if (level.setBlock(at, basalt, Block.UPDATE_CLIENTS)) placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }
}
