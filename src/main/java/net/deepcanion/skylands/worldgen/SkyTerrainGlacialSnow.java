package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sparse-depth natural snow over exposed Glaciated island surfaces and the
 * handcrafted tree canopies. Run after island foliage, never in vanilla ground
 * biomes below the islands. Every snow decision is world-coordinate seeded.
 */
final class SkyTerrainGlacialSnow {
    private SkyTerrainGlacialSnow() { }

    static void decorate(WorldGenLevel level, long seed, int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        int upper=Math.min(level.getMaxBuildHeight()-2,SkyIslandShape.MAX_Y+50);
        // Glaciated foliage may overhang a neighboring island. Snow may only
        // accumulate on explicitly Glaciated blocks, never on any generic
        // leaves, ice or diorite sharing the same vertical x/z column.
        for(int x=chunkX;x<chunkX+16;x++)for(int z=chunkZ;z<chunkZ+16;z++) {
            for(int y=upper-1;y>=SkyIslandShape.MIN_Y;y--) {
                cursor.set(x,y,z);
                BlockState support=level.getBlockState(cursor);
                boolean foliage=support.is(ModBlocks.GLACIATED_LEAVES.get());
                boolean rock=support.is(ModBlocks.GLACIATED_GRASS_BLOCK.get())
                        ||support.is(ModBlocks.GLACIATED_SKYROCK.get())
                        ||support.is(ModBlocks.GLACIATED_MOSSY_SKYROCK.get())
                        ||support.is(ModBlocks.GLACIATED_LOG.get())
                        ||support.is(ModBlocks.GLACIATED_WOOD.get());
                if(!foliage&&!rock)continue;
                BlockPos above=cursor.above();
                if(!level.getBlockState(above).isAir()||!level.ensureCanWrite(above))continue;
                long hash=SkyTerrainPatterns.mix(seed,x,y,z,0x3c12fbL);
                double probability=SkyTerrainPatterns.unit(hash);
                double coverage=foliage?.81:.94;
                if(probability>=coverage)continue;
                int layers=1+(SkyTerrainPatterns.unit(hash^0x59342L)>.84?1:0)
                        +(SkyTerrainPatterns.unit(hash^0x75a61L)>.96?1:0);
                BlockState snow=Blocks.SNOW.defaultBlockState()
                        .setValue(SnowLayerBlock.LAYERS,layers);
                if(snow.canSurvive(level,above))
                    level.setBlock(above,snow,Block.UPDATE_CLIENTS);
            }
        }
    }
}
