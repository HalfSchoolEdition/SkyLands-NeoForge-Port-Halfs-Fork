package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Natural asymmetric Golden Skyrock boulders, usually forming mixed-size outcrops. */
final class SkyTerrainBoulders {
    private SkyTerrainBoulders() { }

    static boolean place(WorldGenLevel level, RandomSource random, BlockPos groundAir,
                         int chunkX, int chunkZ) {
        return placeCluster(level, random, groundAir, chunkX, chunkZ, false);
    }

    /** Smaller, predominantly plain Skyrock clusters on Arid Grasslands. */
    static boolean placeArid(WorldGenLevel level, RandomSource random, BlockPos groundAir,
                             int chunkX, int chunkZ) {
        return placeCluster(level, random, groundAir, chunkX, chunkZ, true);
    }

    private static boolean placeCluster(WorldGenLevel level, RandomSource random, BlockPos groundAir,
                                        int chunkX, int chunkZ, boolean arid) {
        int radius = arid ? 3 + random.nextInt(5) : 3 + random.nextInt(8);
        // Golden: 6-20 blocks wide; Arid: 6-14 blocks wide.
        // Only rocks created by THIS formation may overlap one another. We must
        // never use a generic Skyrock check here: that would let outcrops cut
        // through existing cliffs, buildings, or previously placed formations.
        Set<Long> formation = new HashSet<>();
        if (!placeOne(level, random, groundAir, chunkX, chunkZ, radius, formation, false, arid))
            return false;

        int companions = SkyGoldenBoulderProfile.companions(random.nextDouble(), random.nextDouble());
        if (arid) companions = Math.min(1, companions);
        double baseAngle = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < companions; i++) {
            int smaller = 2 + random.nextInt(Math.max(2, Math.min(5, radius - 1)));
            // Several candidates for each companion, because a valid clearing
            // can be blocked by trees, structures or a nearby steep cliff.
            for (int attempt = 0; attempt < 7; attempt++) {
                boolean stack = !arid && radius >= 4 && random.nextFloat() < .24F;
                double angle = baseAngle + i * 2.399963229728653
                        + (random.nextDouble() - .5) * (.5 + attempt * .17);
                double distance = SkyGoldenBoulderProfile.companionDistance(
                        radius, smaller, stack, random.nextDouble());
                int x = groundAir.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = groundAir.getZ() + (int) Math.round(Math.sin(angle) * distance);
                BlockPos nearby = stack
                        ? boulderTop(level, x, z, groundAir.getY(), chunkX, chunkZ, formation)
                        : arid ? aridSurface(level, x, z, groundAir.getY(), chunkX, chunkZ)
                               : goldenSurface(level, x, z, groundAir.getY(), chunkX, chunkZ);
                if (nearby != null && placeOne(level, random, nearby, chunkX, chunkZ,
                        smaller, formation, stack, arid)) break;
            }
        }
        return true;
    }

    private static boolean insidePlacementBounds(int x, int z, int chunkX, int chunkZ) {
        // WorldGenRegion grants one surrounding chunk of writable space. Avoid
        // searching for anchors too far outside the generating chunk.
        return x >= chunkX - 10 && x < chunkX + 26
                && z >= chunkZ - 10 && z < chunkZ + 26;
    }

    private static BlockPos goldenSurface(WorldGenLevel level, int x, int z, int referenceY,
                                          int chunkX, int chunkZ) {
        if (!insidePlacementBounds(x, z, chunkX, chunkZ)) return null;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int y = Math.min(referenceY + 8, SkyIslandShape.MAX_Y - 2);
                y >= Math.max(referenceY - 9, SkyIslandShape.MIN_Y + 3); y--) {
            probe.set(x, y, z);
            if (!level.ensureCanWrite(probe) || !level.getBlockState(probe).isAir()) continue;
            BlockState soil = level.getBlockState(probe.below());
            if (soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.PODZOL)
                    || soil.is(Blocks.DIRT) || soil.is(Blocks.COARSE_DIRT)
                    || soil.is(BuiltInRegistries.BLOCK.get(SkyLands.id("golden_grass_block"))))
                return probe.immutable();
        }
        return null;
    }

    private static BlockPos aridSurface(WorldGenLevel level, int x, int z, int referenceY,
                                        int chunkX, int chunkZ) {
        if (!insidePlacementBounds(x,z,chunkX,chunkZ)) return null;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int y=Math.min(referenceY+7,SkyIslandShape.MAX_Y-2);
                 y>=Math.max(referenceY-8,SkyIslandShape.MIN_Y+3);y--) {
            probe.set(x,y,z);
            if (!level.ensureCanWrite(probe) || !level.getBlockState(probe).isAir()) continue;
            if (level.getBlockState(probe.below()).is(
                    BuiltInRegistries.BLOCK.get(SkyLands.id("arid_grass_block"))))
                return probe.immutable();
        }
        return null;
    }

    /** Look for a genuine upper surface of a boulder from this same cluster. */
    private static BlockPos boulderTop(WorldGenLevel level, int x, int z, int referenceY,
                                       int chunkX, int chunkZ, Set<Long> formation) {
        if (!insidePlacementBounds(x, z, chunkX, chunkZ)) return null;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int highest = Math.min(referenceY + 19, SkyIslandShape.MAX_Y - 2);
        for (int y = highest; y > referenceY + 1; y--) {
            probe.set(x, y, z);
            if (!level.ensureCanWrite(probe) || !level.getBlockState(probe).isAir()) continue;
            if (formation.contains(probe.below().asLong())) return probe.immutable();
        }
        return null;
    }

    private static boolean placeOne(WorldGenLevel level, RandomSource random, BlockPos groundAir,
                                    int chunkX, int chunkZ, int radius, Set<Long> formation,
                                    boolean stacked, boolean arid) {
        int verticalRadius = Math.max(2, Math.round(radius * (.60F + .25F * random.nextFloat())));
        int cx = groundAir.getX(), cz = groundAir.getZ(), cy = groundAir.getY() + verticalRadius / 3;
        long seed = random.nextLong();
        int reach = radius + 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ArrayList<Long> solidPositions = new ArrayList<>();
        boolean overlapsFormation = false;
        // Compute each noisy voxel only ONCE, and validate the WHOLE boulder
        // before writing anything. A failed companion must leave no sliced rock.
        for (int x = cx - reach; x <= cx + reach; x++)
            for (int z = cz - reach; z <= cz + reach; z++)
                for (int y = cy - verticalRadius - 3; y <= cy + verticalRadius + 3; y++) {
                    if (!SkyBoulderGeometry.solid(seed, cx, cy, cz, radius, verticalRadius, x, y, z))
                        continue;
                    if (x < chunkX - 16 || x >= chunkX + 32
                            || z < chunkZ - 16 || z >= chunkZ + 32
                            || y < SkyIslandShape.MIN_Y || y >= level.getMaxBuildHeight()) return false;
                    pos.set(x, y, z);
                    if (!level.ensureCanWrite(pos)) return false;
                    long key = pos.asLong();
                    if (formation.contains(key)) {
                        overlapsFormation = true;
                    } else {
                        BlockState found = level.getBlockState(pos);
                        // Allow natural Golden terrain only BELOW the anchor;
                        // above-ground foreign rock, trees and structures remain protected.
                        if (arid) {
                            if (!found.isAir() && !(y <= groundAir.getY() && naturalArid(found)))
                                return false;
                        } else {
                            if (!found.isAir() && !(y <= groundAir.getY() && naturalGolden(found)))
                                return false;
                        }
                    }
                    solidPositions.add(key);
                }
        if (solidPositions.isEmpty() || (stacked && !overlapsFormation)) return false;

        BlockState stone = BuiltInRegistries.BLOCK.get(SkyLands.id("skyrock")).defaultBlockState();
        BlockState cobbled = BuiltInRegistries.BLOCK.get(SkyLands.id("cobbled_skyrock")).defaultBlockState();
        BlockState moss = BuiltInRegistries.BLOCK.get(SkyLands.id("golden_mossy_skyrock")).defaultBlockState();
        boolean placed = false;
        for (long key : solidPositions) {
            if (formation.contains(key)) continue; // Keep the older rock intact at overlap.
            pos.set(BlockPos.of(key));
            double roll = SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,
                    pos.getX(), pos.getY(), pos.getZ(), 0x8231L));
            BlockState block = arid
                    ? (roll < .16 ? cobbled : stone)
                    : (roll < .10 ? moss : roll < .40 ? cobbled : stone);
            level.setBlock(pos, block, Block.UPDATE_CLIENTS);
            formation.add(key);
            placed = true;
        }
        return placed;
    }

    private static boolean naturalArid(BlockState state) {
        return state.is(BuiltInRegistries.BLOCK.get(SkyLands.id("arid_grass_block")))
                || state.is(BuiltInRegistries.BLOCK.get(SkyLands.id("sky_sandstone")))
                || state.is(BuiltInRegistries.BLOCK.get(SkyLands.id("mossy_sky_sandstone")));
    }

    private static boolean naturalGolden(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(BuiltInRegistries.BLOCK.get(SkyLands.id("skyrock")))
                || state.is(BuiltInRegistries.BLOCK.get(SkyLands.id("golden_grass_block")))
                || state.is(BuiltInRegistries.BLOCK.get(SkyLands.id("golden_mossy_skyrock")));
    }
}
