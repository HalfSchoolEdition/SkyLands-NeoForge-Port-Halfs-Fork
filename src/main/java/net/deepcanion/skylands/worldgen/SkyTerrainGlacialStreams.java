package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Early, deliberately modest glacial streams: short winding, shallow channels
 * in naturally level ice-island meadows, plus rare *intentional* rim waterfalls.
 * No arbitrary water-source placement within cave systems or island undersides.
 * Larger rivers crossing island/chunk boundaries are a separate later goal.
 */
final class SkyTerrainGlacialStreams {
    private SkyTerrainGlacialStreams() { }

    static void decorate(WorldGenLevel level,long seed,int chunkX,int chunkZ) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,chunkX,0,chunkZ,0x49227efcL));
        if(rng.nextInt(15)==0) {
            for(int attempt=0;attempt<7;attempt++) {
                if(tryStream(level,rng,chunkX,chunkZ))break;
            }
        }
        if(rng.nextInt(24)==0) {
            for(int attempt=0;attempt<24;attempt++) {
                if(tryWaterfall(level,rng,chunkX,chunkZ))break;
            }
        }
    }

    private static boolean natural(BlockState s) {
        return s.is(ModBlocks.GLACIATED_GRASS_BLOCK.get())
                || s.is(ModBlocks.GLACIATED_SKYROCK.get())
                || s.is(ModBlocks.GLACIATED_MOSSY_SKYROCK.get()) || s.is(Blocks.DIORITE);
    }

    private static boolean tryStream(WorldGenLevel level,RandomSource rng,int cx,int cz) {
        int x0=cx+7+rng.nextInt(3), z0=cz+7+rng.nextInt(3);
        int height=SkyTerrainPonds.findSurface(level,x0,z0,true,SkyIslandShape.MAX_Y);
        if(height<SkyIslandShape.MIN_Y+6)return false;
        Direction flow=Direction.from2DDataValue(rng.nextInt(4));
        Direction side=flow.getClockWise();
        double phase=rng.nextDouble()*Math.PI*2;
        List<BlockPos> channel=new ArrayList<>();
        Set<Long> footprint=new HashSet<>();
        int length=7+rng.nextInt(3);
        for(int step=0;step<length;step++) {
            int wiggle=(int)Math.round(Math.sin(step*.64+phase)*1.3);
            int x=x0+flow.getStepX()*step+side.getStepX()*wiggle;
            int z=z0+flow.getStepZ()*step+side.getStepZ()*wiggle;
            if(x<cx+2||x>cx+13||z<cz+2||z>cz+13)return false;
            BlockPos p=new BlockPos(x,height,z);
            if(!footprint.add(p.asLong()))continue;
            if(!level.ensureCanWrite(p) || !level.getBlockState(p.above()).isAir()
                    || !level.getBlockState(p).is(ModBlocks.GLACIATED_GRASS_BLOCK.get())
                    || !natural(level.getBlockState(p.below()))
                    || !natural(level.getBlockState(p.below(2))))return false;
            channel.add(p);
        }
        if(channel.size()<5)return false;
        // Ensure shore blocks are solid at the waterline; reject cliffs and
        // openings instead of creating unwanted cascading leaks.
        for(BlockPos p:channel)for(Direction dir:Direction.Plane.HORIZONTAL) {
            BlockPos neighbor=p.relative(dir);
            if(!footprint.contains(neighbor.asLong()) && !natural(level.getBlockState(neighbor)))
                return false;
        }
        for(BlockPos p:channel) {
            level.setBlock(p,Blocks.WATER.defaultBlockState(),Block.UPDATE_CLIENTS);
            // Ice along occasional shore tiles makes a glacial brook look
            // integrated without freezing the entire water channel.
            if(rng.nextInt(7)==0) {
                BlockPos rim=p.relative(side);
                if(level.ensureCanWrite(rim)
                        && level.getBlockState(rim).is(ModBlocks.GLACIATED_GRASS_BLOCK.get()))
                    level.setBlock(rim,Blocks.PACKED_ICE.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }

    private static boolean tryWaterfall(WorldGenLevel level,RandomSource rng,int cx,int cz) {
        int x=cx+3+rng.nextInt(10), z=cz+3+rng.nextInt(10);
        int y=SkyTerrainPonds.findSurface(level,x,z,true,SkyIslandShape.MAX_Y);
        if(y<SkyIslandShape.MIN_Y+15)return false;
        BlockPos p=new BlockPos(x,y,z);
        if(!natural(level.getBlockState(p.below()))
                || !natural(level.getBlockState(p.below(2))))return false;
        if(!level.ensureCanWrite(p)||!level.getBlockState(p.above()).isAir())return false;
        for(Direction d:Direction.Plane.HORIZONTAL) {
            BlockPos edge=p.relative(d);
            if(!level.ensureCanWrite(edge) || !level.getBlockState(edge).isAir())continue;
            if(!level.getBlockState(edge.below(2)).isAir()
                    || !level.getBlockState(edge.below(5)).isAir())continue;
            // A single intentional source at the top of an exposed cliff.
            // Vanilla fluid rules handle the subsequent waterfall naturally.
            if(level.setBlock(p,Blocks.WATER.defaultBlockState(),Block.UPDATE_CLIENTS)) {
                level.scheduleTick(p,level.getFluidState(p).getType(),1);
                return true;
            }
            return false;
        }
        return false;
    }
}
