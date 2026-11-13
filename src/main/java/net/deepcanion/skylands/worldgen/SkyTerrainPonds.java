package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small, shallow, individually sealed floating-island basins. Entire rim and
 * floor are checked BEFORE any water is placed: a failed attempt changes no
 * terrain and cannot become an accidental waterfall. Never reads outside the
 * feature's own chunk or overwrites a structure, plant, or other biome.
 */
final class SkyTerrainPonds {
    private SkyTerrainPonds() { }

    record Pond(BlockPos center, int radius, int waterY, boolean frozen) { }

    static Pond tryGenerate(WorldGenLevel level, long seed, int chunkX, int chunkZ, boolean frozen) {
        RandomSource rng = RandomSource.create(SkyTerrainPatterns.mix(seed, chunkX, 0, chunkZ,
                frozen ? 0x55a17fc2L : 0x1198cb3aL));
        if (rng.nextInt(frozen ? 4 : 3) != 0) return null;
        for (int attempt = 0; attempt < 5; attempt++) {
            int radius = 2 + rng.nextInt(3);
            int margin = radius + 2;
            int x = chunkX + margin + rng.nextInt(16 - 2 * margin);
            int z = chunkZ + margin + rng.nextInt(16 - 2 * margin);
            int y = findSurface(level, x, z, frozen, SkyIslandShape.MAX_Y);
            if (y < SkyIslandShape.MIN_Y + 8) continue;
            // Put the waterline just below the surrounding native grass cap.
            // The frozen version has a full ice lid and water beneath it.
            int waterY = y - 1;
            long form = SkyTerrainPatterns.mix(seed, x, y, z, 0x94fa21L);
            if (!canCarve(level, x, waterY, z, radius, form, frozen)) continue;
            carve(level, x, waterY, z, radius, form, frozen, rng);
            return new Pond(new BlockPos(x, y, z), radius, waterY, frozen);
        }
        return null;
    }

    static int findSurface(WorldGenLevel level, int x, int z, boolean frozen, int top) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = Math.min(SkyIslandShape.MAX_Y, top); y >= SkyIslandShape.MIN_Y; y--) {
            p.set(x, y, z);
            if (isTop(level.getBlockState(p), frozen) && level.getBlockState(p.above()).isAir())
                return y;
        }
        return -1;
    }

    private static boolean isTop(BlockState state, boolean frozen) {
        return frozen ? state.is(ModBlocks.GLACIATED_GRASS_BLOCK.get())
                : state.is(ModBlocks.SWAMP_GRASS_BLOCK.get());
    }

    private static boolean natural(BlockState state, boolean frozen) {
        if (isTop(state, frozen) || state.is(Blocks.DIORITE)) return true;
        return frozen
                ? state.is(ModBlocks.GLACIATED_SKYROCK.get()) || state.is(ModBlocks.GLACIATED_MOSSY_SKYROCK.get())
                : state.is(ModBlocks.SKYROCK.get()) || state.is(ModBlocks.SWAMP_MOSSY_SKYROCK.get());
    }

    private static boolean inside(int dx, int dz, int radius, long form) {
        double a = Math.atan2(dz, dx);
        double phase = SkyTerrainPatterns.unit(form) * Math.PI * 2;
        double distorted = radius * (.92 + .075 * Math.sin(a * 3 + phase)
                + .065 * Math.cos(a * 5 - phase));
        return dx * dx + dz * dz <= distorted * distorted;
    }

    private static boolean canCarve(WorldGenLevel level, int cx, int y, int cz,
                                     int radius, long form, boolean frozen) {
        // The ice lid floats over one additional water layer, also sealed by
        // the same natural-stone walls and two underlying solid floor layers.
        int bottom = frozen ? y - 1 : y;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                boolean fill = inside(dx, dz, radius, form);
                if (!fill && !adjacentToWater(dx, dz, radius, form)) continue;
                int x = cx + dx, z = cz + dz;
                for (int yy = bottom - 2; yy <= y + 3; yy++) {
                    p.set(x, yy, z);
                    if (!level.ensureCanWrite(p)) return false;
                }
                // All cells, including the outside ring, must be supported by
                // two intact layers. This also rejects cave ceilings and edges.
                p.set(x, bottom - 1, z);
                if (!natural(level.getBlockState(p), frozen)) return false;
                p.setY(bottom - 2);
                if (!natural(level.getBlockState(p), frozen)) return false;
                if (!fill) {
                    // Retain a complete, non-air ring at the WATER level.
                    p.setY(bottom);
                    if (!natural(level.getBlockState(p), frozen)) return false;
                    p.setY(y);
                    if (!natural(level.getBlockState(p), frozen)) return false;
                    continue;
                }
                // No artificial blocks, fluids, logs, structures or other
                // decorations may be excavated. Surface variation up to +2.
                for (int yy = bottom; yy <= y + 2; yy++) {
                    p.setY(yy);
                    BlockState old = level.getBlockState(p);
                    if (!old.isAir() && !natural(old, frozen)) return false;
                }
                p.setY(y + 3);
                if (!level.getBlockState(p).isAir()) return false;
            }
        }
        return true;
    }

    private static boolean adjacentToWater(int dx, int dz, int radius, long form) {
        return inside(dx + 1,dz,radius,form) || inside(dx - 1,dz,radius,form)
                || inside(dx,dz + 1,radius,form) || inside(dx,dz - 1,radius,form);
    }

    private static void carve(WorldGenLevel level, int cx, int y, int cz,
                              int radius, long form, boolean frozen, RandomSource rng) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (!inside(dx,dz,radius,form)) continue;
            int x = cx + dx, z = cz + dz;
            int bottom = frozen ? y - 1 : y;
            for (int yy = bottom; yy <= y + 2; yy++) {
                p.set(x, yy, z);
                if (natural(level.getBlockState(p), frozen))
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (frozen) {
                p.setY(y - 1);
                level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                p.setY(y);
                level.setBlock(p, rng.nextInt(11) == 0
                        ? Blocks.PACKED_ICE.defaultBlockState()
                        : Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else {
                p.setY(y);
                level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                if (rng.nextInt(5) == 0) {
                    p.setY(y + 1);
                    BlockState pad = Blocks.LILY_PAD.defaultBlockState();
                    if (level.getBlockState(p).isAir() && pad.canSurvive(level, p))
                        level.setBlock(p, pad, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
