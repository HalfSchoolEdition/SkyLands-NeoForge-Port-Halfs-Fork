package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded, chunk-local rendering of world-seeded procedural Lavic claws.
 * Geometry comes from the original island density, not neighboring chunks.
 * Each chunk independently renders its own 16x16 slice of every crossing horn.
 */
final class SkyTerrainLavicSpikes {
    private SkyTerrainLavicSpikes() { }

    static int decorate(WorldGenLevel level, long worldSeed, int chunkX, int chunkZ,
                        List<SkyIslandShape.Island> nearbyLavicIslands) {
        if (nearbyLavicIslands.isEmpty()) return 0;
        BitSet visited = new BitSet(16 * 16 * (SkyIslandShape.MAX_Y-SkyIslandShape.MIN_Y+1));
        ArrayList<BlockPos> exposed = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (SkyIslandShape.Island island : nearbyLavicIslands) {
            for (SkyLavicSpikeSites.Spike spike : SkyLavicSpikeSites.forIsland(worldSeed, island)) {
                if (!spike.intersects(chunkX,chunkZ) || spike.maxY() < SkyIslandShape.MIN_Y
                        || spike.minY() > SkyIslandShape.MAX_Y) continue;
                SkyLavicSpikeGeometry.Profile shape = spike.shape();
                List<SkyLavicSpikeGeometry.Vec3> path = SkyLavicSpikeGeometry.backbone(shape);
                int last = path.size()-1;
                for (int i=0; i<=last; i++) {
                    SkyLavicSpikeGeometry.Vec3 center = path.get(i);
                    double r = shape.radius(i/(double)last);
                    int radius = (int)Math.ceil(r+0.5);
                    int bx = spike.x()+(int)Math.floor(center.x());
                    int by = spike.y()+(int)Math.floor(center.y());
                    int bz = spike.z()+(int)Math.floor(center.z());
                    // Coarse reject first. Never inspect an unrelated chunk.
                    if (bx+radius < chunkX || bx-radius > chunkX+15
                            || bz+radius < chunkZ || bz-radius > chunkZ+15) continue;
                    for (int x=Math.max(chunkX,bx-radius);x<=Math.min(chunkX+15,bx+radius);x++) {
                        for (int z=Math.max(chunkZ,bz-radius);z<=Math.min(chunkZ+15,bz+radius);z++) {
                            double dx=x-(spike.x()+center.x());
                            double dz=z-(spike.z()+center.z());
                            double horiz=dx*dx+dz*dz;
                            if (horiz > (r+0.4)*(r+0.4)) continue;
                            for (int y=Math.max(SkyIslandShape.MIN_Y,by-radius);
                                     y<=Math.min(SkyIslandShape.MAX_Y,by+radius);y++) {
                                double dy=y-(spike.y()+center.y());
                                if (horiz+dy*dy>(r+0.4)*(r+0.4)) continue;
                                int index=((y-SkyIslandShape.MIN_Y)*16+(z-chunkZ))*16+(x-chunkX);
                                if (visited.get(index)) continue;
                                visited.set(index);
                                cursor.set(x,y,z);
                                BlockState old=level.getBlockState(cursor);
                                // Solid spikes must extend through molten lake
                                // volumes, rather than hovering above them.
                                // Other rock/structures remain protected.
                                if (!old.isAir() && !old.is(ModBlocks.MOLTEN_METAL.get())
                                        && !old.is(ModBlocks.SHORT_LAVIC_GRASS.get())
                                        && !old.is(ModBlocks.METALLIC_SHORTER_GRASS.get())) continue;
                                if (!level.ensureCanWrite(cursor)) continue;
                                BlockState material=material(spike.seed(),x,y,z);
                                if (level.setBlock(cursor,material,Block.UPDATE_CLIENTS)) {
                                    placed++;
                                    // Deferred foliage after every stone voxel has been placed.
                                    if (y<SkyIslandShape.MAX_Y && (SkyTerrainPatterns.mix(
                                            spike.seed(),x,y,z,0x31ab27L)&31L)==0L)
                                        exposed.add(cursor.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        for (BlockPos base:exposed) {
            BlockPos above=base.above();
            // At chunk edges, do not touch or load a neighboring chunk.
            if (above.getY()>SkyIslandShape.MAX_Y || !level.getBlockState(above).isAir()
                    || !level.ensureCanWrite(above)) continue;
            long h=SkyTerrainPatterns.mix(worldSeed,base.getX(),base.getY(),base.getZ(),0x6bbd18L);
            BlockState plant=SkyTerrainPatterns.unit(h)<.60
                    ? ModBlocks.SHORT_LAVIC_GRASS.get().defaultBlockState()
                    : ModBlocks.METALLIC_SHORTER_GRASS.get().defaultBlockState();
            if (plant.canSurvive(level,above)) level.setBlock(above,plant,Block.UPDATE_CLIENTS);
        }
        return placed;
    }

    private static BlockState material(long seed,int x,int y,int z) {
        double coarse=SkyTerrainPatterns.noise3(seed,x,y,z,6,0x4af1L);
        double fine=SkyTerrainPatterns.noise3(seed,x,y,z,3,0x4af2L);
        if (coarse>.73) return Blocks.BASALT.defaultBlockState();
        if (coarse+fine*.35>.88) return Blocks.BLACKSTONE.defaultBlockState();
        if (fine<.22) return Blocks.POLISHED_BASALT.defaultBlockState();
        return ModBlocks.LAVIC_SKYMETAL.get().defaultBlockState();
    }
}
