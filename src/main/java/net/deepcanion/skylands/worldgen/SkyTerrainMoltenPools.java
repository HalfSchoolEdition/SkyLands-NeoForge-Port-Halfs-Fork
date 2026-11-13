package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Underground Lavic molten-metal lakes. Their flat liquid surface lies inside
 * an irregular, roughly cylindrical stone cavity, much like the lava pockets
 * found near the bottom of the vanilla Overworld.
 *
 * Everything is preflighted before writing: a continuous stone collar at the
 * liquid level and two solid support layers keep the real flowing metal inside
 * the island, even where a cave opens into the top of the chamber. Never write
 * beyond the generating chunk and never replace non-native blocks.
 */
final class SkyTerrainMoltenPools {
    private SkyTerrainMoltenPools() { }

    static void decorate(WorldGenLevel level, long worldSeed, int chunkX, int chunkZ) {
        RandomSource random = RandomSource.create(SkyTerrainPatterns.mix(
                worldSeed, chunkX, 0, chunkZ, 0x45cd98a71L));
        if (random.nextInt(9) != 0) return;

        for (int attempt = 0; attempt < 80; attempt++) {
            int radius = 3 + random.nextInt(3); // Occasional minor pools; new site-seeded caverns are much larger.
            int extent = radius + 2; // includes the natural rock collar.
            int free = 16 - extent * 2;
            if (free <= 0) continue;
            int x = chunkX + extent + random.nextInt(free);
            int z = chunkZ + extent + random.nextInt(free);
            int y = SkyIslandShape.MIN_Y + 16 + random.nextInt(
                    SkyIslandShape.MAX_Y - SkyIslandShape.MIN_Y - 32);
            boolean caveFloor = attempt % 2 == 0;
            SkyTerrainMoltenBasin.Basin basin = SkyTerrainMoltenBasin.makeBasin(random.nextLong(), x, z, radius, extent);
            if (canPlace(level, x, y, z, basin, caveFloor)) {
                place(level, x, y, z, basin);
                return; // no more than one pool in this generating chunk
            }
        }
    }

    private static boolean rock(BlockState state) {
        return state.is(ModBlocks.LAVIC_SKYMETAL.get());
    }

    private static boolean canPlace(WorldGenLevel level, int x, int y, int z,
                                    SkyTerrainMoltenBasin.Basin basin, boolean caveFloor) {
        BlockPos center = new BlockPos(x, y, z);
        if (!rock(level.getBlockState(center.below()))) return false;
        if (caveFloor != level.getBlockState(center).isAir()) return false;

        for (int dx = -basin.extent(); dx <= basin.extent(); dx++) {
            for (int dz = -basin.extent(); dz <= basin.extent(); dz++) {
                boolean inside = basin.inside(dx, dz);
                if (!inside && !basin.rim(dx, dz)) continue;
                BlockPos p = center.offset(dx, 0, dz);
                if (!level.ensureCanWrite(p) || !level.ensureCanWrite(p.below(2))) return false;

                // Both the liquid's outer rim and all its floor blocks must be
                // native stone. Fluid cannot escape sideways or through caves
                // directly below the basin, including its jagged shoreline.
                if (!rock(level.getBlockState(p.below()))
                        || !rock(level.getBlockState(p.below(2)))) return false;
                if (!inside) continue;

                for (int h = 0; h < basin.height(dx, dz); h++) {
                    BlockPos at = p.above(h);
                    if (!level.ensureCanWrite(at)) return false;
                    BlockState state = level.getBlockState(at);
                    if (!rock(state) && !(caveFloor && state.isAir())) return false;
                }
                // In sealed pockets the ceiling remains intact. Cave-floor
                // pools may already open directly into a natural cave chamber.
                if (!caveFloor && !rock(level.getBlockState(p.above(basin.height(dx, dz)))))
                    return false;
            }
        }
        return true;
    }

    private static void place(WorldGenLevel level, int x, int y, int z, SkyTerrainMoltenBasin.Basin basin) {
        BlockState molten = ModBlocks.MOLTEN_METAL.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos center = new BlockPos(x, y, z);
        // Remove only native Lavic stone; preserve any previously open caves.
        for (int dx = -basin.extent(); dx <= basin.extent(); dx++) {
            for (int dz = -basin.extent(); dz <= basin.extent(); dz++) {
                if (!basin.inside(dx, dz)) continue;
                BlockPos p = center.offset(dx, 0, dz);
                for (int h = 0; h < basin.height(dx, dz); h++) {
                    BlockPos at = p.above(h);
                    if (rock(level.getBlockState(at)))
                        level.setBlock(at, air, Block.UPDATE_CLIENTS);
                }
            }
        }
        // All sources share the same Y, producing a lake rather than a stack
        // of metal columns. The rocky shoreline is never touched.
        for (int dx = -basin.extent(); dx <= basin.extent(); dx++) {
            for (int dz = -basin.extent(); dz <= basin.extent(); dz++) {
                if (basin.inside(dx, dz))
                    level.setBlock(center.offset(dx, -1, dz), molten, Block.UPDATE_CLIENTS);
            }
        }
    }
}
