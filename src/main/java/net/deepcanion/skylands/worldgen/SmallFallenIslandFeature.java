package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Small fragments of hanging Sky Terrain that have fallen tip-first into the
 * Overworld. Broad, almost level upper shelves and sharply tapered rocky sides:
 * approximately 2/3 above the ground and the final 1/3 embedded below it.
 * Placement requires an actual procedural Sky Terrain landmass overhead/nearby.
 */
public final class SmallFallenIslandFeature extends Feature<NoneFeatureConfiguration> {
    public SmallFallenIslandFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!Level.OVERWORLD.equals(level.getLevel().dimension())
                || !GameplayConfig.GENERATE_FALLEN_ISLANDS.get()) return false;
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        int cx = origin.getX(), cz = origin.getZ();
        int ground = groundSurface(level, cx, cz);
        if (ground < level.getMinBuildHeight() + 18 || ground > 225) return false;

        long seed = level.getSeed();
        var source = level.getLevel().getChunkSource();
        String theme = FallenIslandSkyLink.nearbyTheme(seed,
                source.getGenerator().getBiomeSource(), source.randomState().sampler(), cx, cz);
        if (theme == null) return false;
        // The original sparse feature attempts, combined with the sky-rock
        // proximity requirement, made non-Lavic fragments very difficult to
        // discover. Leave the Lavic rate close to its existing value while
        // giving all five other native variants more opportunities.
        if ("lavic".equals(theme) && SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(seed, cx, 0, cz, 0x41a9ccL)) >= 1.0 / 3.0)
            return false;
        BlockPos center = new BlockPos(cx, ground - 1, cz);
        BlockState floor = level.getBlockState(center);
        if (!floor.getFluidState().isEmpty() || floor.is(BlockTags.LOGS)
                || floor.is(BlockTags.LEAVES) || !floor.isSolidRender(level, center)) return false;

        // A long, exposed rock spike, not the old 3-block-tall mound with the
        // pointed portion buried. Vary the footprint and the X/Z tip location.
        int rx = 6 + random.nextInt(4), rz = 6 + random.nextInt(4);
        int totalHeight = 20 + random.nextInt(10);
        int embed = (int) Math.round(totalHeight / 3.0);
        int exposed = totalHeight - embed;
        // At an ocean site the fragment must be embedded in the sea floor
        // and remain entirely beneath the water. Never build a turf platform
        // floating on the water surface or crossing a shoreline.
        boolean submerged = level.getFluidState(new BlockPos(cx, ground, cz)).is(FluidTags.WATER);
        double leanX = (random.nextDouble() - .5) * 3.0;
        double leanZ = (random.nextDouble() - .5) * 3.0;
        int minX = cx - rx - 2, maxX = cx + rx + 2;
        int minZ = cz - rz - 2, maxZ = cz + rz + 2;
        int minY = ground - embed - 3, maxY = ground + exposed + 4;
        if (minY <= level.getMinBuildHeight() || maxY >= level.getMaxBuildHeight()) return false;
        for (int x : new int[]{minX, maxX}) for (int z : new int[]{minZ, maxZ}) {
            if (!level.ensureCanWrite(new BlockPos(x, minY, z))
                    || !level.ensureCanWrite(new BlockPos(x, maxY, z))) return false;
        }

        // Sample and validate the original ground ONCE. Changes during rock
        // placement must never affect the heights of subsequent columns.
        int[][] originalGround = new int[maxX - minX + 1][maxZ - minZ + 1];
        double[][] footprint = new double[maxX - minX + 1][maxZ - minZ + 1];
        BlockPos.MutableBlockPos clearance = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            double dx = x - cx, dz = z - cz;
            double warpedX = dx + (SkyTerrainPatterns.noise2(seed, x, z, 5, 0x48a61L) - .5) * 2.0;
            double warpedZ = dz + (SkyTerrainPatterns.noise2(seed, x, z, 5, 0x48a62L) - .5) * 2.0;
            double d = Math.sqrt(warpedX * warpedX / (rx * rx) + warpedZ * warpedZ / (rz * rz));
            footprint[x - minX][z - minZ] = d;
            // Only the irregular footprint is occupied by rock. The former
            // rectangular preflight rejected entire forest/swamp sites merely
            // because an unrelated tree was near the fragment's edge.
            if (d >= 1.0) continue;
            int local = groundSurface(level, x, z);
            if (local < level.getMinBuildHeight() + 5 || Math.abs(local - ground) > 4) return false;
            BlockPos p = new BlockPos(x, local - 1, z);
            BlockState state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.LEAVES)) return false;
            if (level.getFluidState(new BlockPos(x, local, z)).is(FluidTags.WATER) != submerged)
                return false; // do not straddle coastlines or make a water platform
            // This feature now runs AFTER vanilla trees so foreign/oak
            // trunks and canopies must not be embedded in the falling rock.
            int edgeDrop = d > .85 ? 2 : d > .69 ? 1 : 0;
            int roughTop = (int) Math.round((SkyTerrainPatterns.noise2(seed, x, z, 4, 0x541abcL) - .5) * 2.4);
            int columnTop = ground + exposed + roughTop - edgeDrop;
            // Foliage overhanging the fragment's uppermost air is harmless.
            // Only reject logs/leaves that the rock would physically intersect.
            for (int y = local; y < columnTop; y++) {
                clearance.set(x, y, z);
                BlockState existing = level.getBlockState(clearance);
                if (existing.is(BlockTags.LOGS) || existing.is(BlockTags.LEAVES)
                        || existing.hasBlockEntity()) return false;
                // Reject shallow/partly flooded water columns; a submerged
                // fragment must not protrude through the ocean surface.
                if (submerged && !existing.getFluidState().is(FluidTags.WATER))
                    return false;
            }
            originalGround[x - minX][z - minZ] = local;
        }

        SkyTerrainPalette palette = SkyTerrainPalette.forTheme(theme);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> upperShelves = new ArrayList<>();
        int changed = 0;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            double dx = x - cx, dz = z - cz;
            double d = footprint[x - minX][z - minZ];
            if (d >= 1.0) continue;
            double body = 1.0 - d;
            // The upper face stays nearly flat, with minor rock breaks and
            // slightly lowered edges. The underside tapers to a single point.
            int edgeDrop = d > .85 ? 2 : d > .69 ? 1 : 0;
            int roughTop = (int) Math.round((SkyTerrainPatterns.noise2(seed, x, z, 4, 0x541abcL) - .5) * 2.4);
            int top = ground + exposed + roughTop - edgeDrop;
            double tipX = (dx + leanX * (1.0 - body)) / rx;
            double tipZ = (dz + leanZ * (1.0 - body)) / rz;
            double taper = Math.max(0, 1.0 - Math.sqrt(tipX * tipX + tipZ * tipZ));
            double secondary = SkyTerrainPatterns.noise2(seed, x, z, 4, 0x541c72L) - .5;
            double ridge = (SkyTerrainPatterns.noise2(seed, x, z, 7, 0x542ea2L) - .5) * 2.4;
            int depth = Math.max(1, (int) Math.round(totalHeight * Math.pow(taper, .80)
                    + secondary * 3.0 * taper + ridge * Math.sqrt(taper)));
            int bottom = top - depth;
            // Exposed slope can extend right down to the original surface;
            // the center point remains sunk approximately one-third underground.
            for (int y = bottom; y < top; y++) {
                pos.set(x, y, z);
                BlockState current = level.getBlockState(pos);
                if ((!submerged && !current.getFluidState().isEmpty()) || current.hasBlockEntity()
                        || current.is(BlockTags.LOGS) || current.is(BlockTags.LEAVES)
                        || current.is(Blocks.BEDROCK)) continue;
                BlockState result = y == top - 1 ? palette.top()
                        : y >= top - 3 ? palette.soil() : palette.core();
                if (result == palette.core() && FallenIslandProcessor.isRareOreVein(
                        pos, 0x146bc0L + SkyTerrainVegetation.themeIndex(theme) * 0x892f31L))
                    result = palette.ore();
                if (level.setBlock(pos, result, Block.UPDATE_CLIENTS)) changed++;
            }
            // Only actual exposed native top surfaces can receive flora.
            if (!submerged && top > originalGround[x - minX][z - minZ] + 1)
                upperShelves.add(new BlockPos(x, top, z));
            // Underside crystal accents sit on visible overhanging slopes,
            // not invisibly at the point buried under normal terrain.
            int localGround = originalGround[x - minX][z - minZ];
            if (bottom > localGround + 2 && d > .20 && d < .90) {
                long h = SkyTerrainPatterns.mix(seed, x, bottom, z, 0x551cadL);
                if (SkyTerrainPatterns.unit(h) < .21) {
                    pos.set(x, bottom - 1, z);
                    if (level.ensureCanWrite(pos) && (level.getBlockState(pos).isAir()
                            || (submerged && level.getFluidState(pos).is(FluidTags.WATER)))
                            && level.getBlockState(pos.above()).is(palette.core().getBlock()))
                        level.setBlock(pos, palette.spike(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Decorate *this* fragment directly. The old unrelated 9-position
        // chunk scan frequently missed small fragments altogether.
        if (changed > 0 && !submerged)
            FallenIslandDecorFeature.decorateFragment(level, context.chunkGenerator(),
                    random, theme, upperShelves, cx, cz);
        return changed > 0;
    }

    /** The world-surface heightmap may include floating terrain >Y=290.
     * Search beneath it without generating/loading another chunk. */
    static int groundSurface(WorldGenLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (top > 225) {
            BlockPos atCap = new BlockPos(x, 225, z);
            if (level.getBlockState(atCap).isSolidRender(level, atCap)) return Integer.MIN_VALUE;
        }
        int start = Math.min(225, top - 1);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int y = start; y >= Math.max(level.getMinBuildHeight() + 8, -32); y--) {
            probe.set(x, y, z);
            BlockState state = level.getBlockState(probe);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                    || !state.getFluidState().isEmpty()) continue;
            if (state.isSolidRender(level, probe)) return y + 1;
        }
        return Integer.MIN_VALUE;
    }
}
