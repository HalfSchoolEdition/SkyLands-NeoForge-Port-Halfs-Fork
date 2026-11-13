package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Occasional genuinely flowing waterfall sources at exposed Cherry island rims.
 * All placement checks are within the generating chunk. Natural lake outlets
 * are generated separately by the immutable island lake geometry. */
final class SkyTerrainCherryFalls {
    private SkyTerrainCherryFalls() { }
    static int decorate(WorldGenLevel level,long seed,int cx,int cz) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,cx,0,cz,0x642f17L));
        if(rng.nextInt(17)!=0)return 0;
        for(int attempt=0;attempt<12;attempt++) {
            int x=cx+3+rng.nextInt(10),z=cz+3+rng.nextInt(10);
            for(int y=SkyIslandShape.MAX_Y-3;y>SkyIslandShape.MIN_Y+18;y--) {
                BlockPos ground=new BlockPos(x,y,z);
                BlockState material=level.getBlockState(ground);
                if(!material.is(ModBlocks.CHERRY_GRASS_BLOCK.get())
                        && !material.is(Blocks.GRASS_BLOCK))continue;
                if(material.is(Blocks.GRASS_BLOCK)
                        && !level.getBlockState(ground.below()).is(ModBlocks.SKYROCK.get())
                        && !level.getBlockState(ground.below()).is(ModBlocks.CHERRY_MOSSY_SKYROCK.get()))continue;
                BlockPos source=ground.above();
                if(!level.ensureCanWrite(source)||!level.getBlockState(source).isAir())break;
                for(Direction face:Direction.Plane.HORIZONTAL) {
                    BlockPos edge=source.relative(face);
                    if(edge.getX()<cx+1 || edge.getX()>cx+14
                            || edge.getZ()<cz+1 || edge.getZ()>cz+14)continue;
                    if(!level.getBlockState(edge).isAir()
                            ||!level.getBlockState(edge.below(3)).isAir()
                            ||!level.getBlockState(edge.below(6)).isAir())continue;
                    if(level.setBlock(source,Blocks.WATER.defaultBlockState(),Block.UPDATE_CLIENTS)) {
                        level.scheduleTick(source,level.getFluidState(source).getType(),1);
                        return 1;
                    }
                }
                break;
            }
        }
        return 0;
    }
}
