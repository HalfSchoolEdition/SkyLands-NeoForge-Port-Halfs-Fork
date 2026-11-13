package net.deepcanion.skylands.worldgen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Chunk-only stone-arch, mesa-stack and wind-eroded hoodoo placement. */
final class SkyTerrainAridFormations {
    private SkyTerrainAridFormations() { }

    static int decorate(WorldGenLevel level,long seed,int chunkX,int chunkZ,
                        List<SkyIslandShape.Island> aridIslands) {
        if(aridIslands.isEmpty())return 0;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        int placed=0;
        for(SkyIslandShape.Island island:aridIslands) {
            for(var form:SkyAridRockFormationSites.forIsland(seed,island)) {
                if(!form.intersects(chunkX,chunkZ))continue;
                int reach=form.reach();
                for(int x=Math.max(chunkX,form.x()-reach);x<=Math.min(chunkX+15,form.x()+reach);x++) {
                    for(int z=Math.max(chunkZ,form.z()-reach);z<=Math.min(chunkZ+15,form.z()+reach);z++) {
                        double warp=SkyAridTerracottaPalette.warp(seed,x,z);
                        double broad=form.kind()==SkyAridRockFormationSites.ARCH
                                ? SkyTerrainPatterns.noise2(seed,x,z,21,0x4a719fL) : 0.0;
                        for(int y=Math.max(form.minY(),SkyIslandShape.MIN_Y);
                                y<=Math.min(form.maxY(),SkyIslandShape.MAX_Y);y++) {
                            if(!form.solid(x,y,z))continue;
                            pos.set(x,y,z);
                            if(!level.ensureCanWrite(pos))continue;
                            // Natural sandstone remains underneath, but never
                            // erase trees, other worldgen structures or fluids.
                            if(!level.getBlockState(pos).isAir())continue;
                            BlockState material=form.kind()==SkyAridRockFormationSites.ARCH
                                    ? SkyAridTerracottaPalette.archStone(y,broad,warp)
                                    : SkyAridTerracottaPalette.stone(y,warp);
                            if(level.setBlock(pos,material,Block.UPDATE_CLIENTS))placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }
}
