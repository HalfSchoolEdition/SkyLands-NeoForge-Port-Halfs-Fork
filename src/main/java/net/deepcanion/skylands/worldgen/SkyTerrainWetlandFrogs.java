package net.deepcanion.skylands.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Sparse initial frogs beside flooded Swamp wetlands. Vanilla natural spawning
 * remains active via the sky biome and frogs_spawnable_on block tag, but the
 * normal passive-mob cap can make new isolated sky islands look uninhabited.
 * This bounded pass adds an occasional small group only to genuinely wet
 * shores during initial chunk generation; no ticking entity spawner is added.
 */
final class SkyTerrainWetlandFrogs {
    private SkyTerrainWetlandFrogs() { }

    static void decorate(WorldGenLevel level, long seed, int chunkX, int chunkZ) {
        RandomSource rng = RandomSource.create(SkyTerrainPatterns.mix(
                seed, chunkX, 0, chunkZ, 0x6f46159L));
        if (rng.nextInt(7) != 0) return;
        BlockPos.MutableBlockPos water = new BlockPos.MutableBlockPos();
        for (int attempt=0; attempt<16; attempt++) {
            int x=chunkX+2+rng.nextInt(12), z=chunkZ+2+rng.nextInt(12);
            for (int y=SkyIslandShape.MAX_Y-4; y>=SkyIslandShape.MIN_Y+12; y--) {
                water.set(x,y,z);
                if (!level.getBlockState(water).is(Blocks.WATER)
                        || !level.getBlockState(water.above()).isAir()
                        || !level.getBiome(water).is(SkyBiomes.SWAMP)) continue;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos ground=water.relative(direction);
                    if (ground.getX()<chunkX || ground.getX()>=chunkX+16
                            || ground.getZ()<chunkZ || ground.getZ()>=chunkZ+16)
                        continue;
                    var state=level.getBlockState(ground);
                    if (!state.is(Blocks.MUD) && !state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                            && !state.is(net.deepcanion.skylands.registry.ModBlocks.SWAMP_GRASS_BLOCK.get()))
                        continue;
                    BlockPos spawn=ground.above();
                    if (!level.ensureCanWrite(spawn) || !level.getBlockState(spawn).isAir()
                            || !level.getBlockState(spawn.above()).isAir()) continue;
                    Frog frog=EntityType.FROG.create(level.getLevel());
                    if (frog==null) return;
                    frog.moveTo(spawn.getX()+.5, spawn.getY(), spawn.getZ()+.5,
                            rng.nextFloat()*360F, 0F);
                    level.addFreshEntity(frog);
                    return; // one initial frog per chosen wetland chunk
                }
                break; // only consider the highest open wetland water surface
            }
        }
    }
}
