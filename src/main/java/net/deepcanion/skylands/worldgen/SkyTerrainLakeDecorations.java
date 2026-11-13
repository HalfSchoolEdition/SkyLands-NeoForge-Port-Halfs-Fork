package net.deepcanion.skylands.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Small lakeside accents. Only Cherry freshwater lakes receive these plants. */
final class SkyTerrainLakeDecorations {
    private SkyTerrainLakeDecorations() { }

    static void decorate(WorldGenLevel level,long seed,int chunkX,int chunkZ) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,chunkX,0,chunkZ,0x4c123a01L));
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        // Most chunks contain no Cherry islands; avoid scanning those chunks.
        if(!level.getBiome(cursor.set(chunkX+8,385,chunkZ+8)).is(SkyBiomes.CHERRY))return;
        for(int attempt=0;attempt<50;attempt++) {
            int x=chunkX+1+rng.nextInt(14), z=chunkZ+1+rng.nextInt(14);
            if(!level.getBiome(cursor.set(x,385,z)).is(SkyBiomes.CHERRY))continue;
            // The high-altitude lakes are uncommon; do not touch Overworld ponds.
            for(int y=SkyIslandShape.MAX_Y-4;y>=SkyIslandShape.MIN_Y+12;y--) {
                cursor.set(x,y,z);
                BlockState surface=level.getBlockState(cursor);
                if(surface.is(Blocks.WATER) && level.getBlockState(cursor.above()).isAir()) {
                    if(rng.nextInt(7)!=0 || !hasShore(level,x,y,z,chunkX,chunkZ))break;
                    BlockPos pos=cursor.above().immutable();
                    BlockState pad=Blocks.LILY_PAD.defaultBlockState();
                    if(level.ensureCanWrite(pos)&&pad.canSurvive(level,pos))
                        level.setBlock(pos,pad,Block.UPDATE_CLIENTS);
                    break;
                }
                if(surface.is(Blocks.SAND) && level.getBlockState(cursor.above()).isAir()) {
                    // Cane needs a water source level with the sand, not
                    // merely a beach somewhere within the wider lake ramp.
                    if(!touchesWater(level,x,y,z,chunkX,chunkZ))break;
                    BlockPos start=cursor.above().immutable();
                    if(rng.nextInt(3)==0 && level.ensureCanWrite(start)) {
                        BlockState cane=Blocks.SUGAR_CANE.defaultBlockState();
                        if(cane.canSurvive(level,start)) {
                            int height=1+rng.nextInt(3);
                            for(int i=0;i<height;i++) {
                                BlockPos at=start.above(i);
                                if(!level.ensureCanWrite(at)||!level.getBlockState(at).isAir())break;
                                level.setBlock(at,cane,Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    private static boolean touchesWater(WorldGenLevel level,int x,int y,int z,int chunkX,int chunkZ) {
        for(Direction dir:Direction.Plane.HORIZONTAL) {
            int xx=x+dir.getStepX(),zz=z+dir.getStepZ();
            if(xx<chunkX||xx>=chunkX+16||zz<chunkZ||zz>=chunkZ+16)continue;
            if(level.getBlockState(new BlockPos(xx,y,zz)).is(Blocks.WATER))return true;
        }
        return false;
    }

    private static boolean hasShore(WorldGenLevel level,int x,int y,int z,int chunkX,int chunkZ) {
        // Only test neighboring positions within the generating chunk.
        for(Direction dir:Direction.Plane.HORIZONTAL) {
            int xx=x+dir.getStepX()*2,zz=z+dir.getStepZ()*2;
            if(xx<chunkX||xx>=chunkX+16||zz<chunkZ||zz>=chunkZ+16)continue;
            BlockState s=level.getBlockState(new BlockPos(xx,y,zz));
            if(!s.is(Blocks.WATER))return true;
        }
        return false;
    }
}
