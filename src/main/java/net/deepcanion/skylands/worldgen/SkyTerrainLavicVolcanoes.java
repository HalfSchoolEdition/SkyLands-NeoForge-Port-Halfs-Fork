package net.deepcanion.skylands.worldgen;

import java.util.List;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Rare self-contained vents, rendered in the owning 16x16 chunk only. */
final class SkyTerrainLavicVolcanoes {
    private SkyTerrainLavicVolcanoes() { }

    static int decorate(WorldGenLevel level, long seed, int chunkX, int chunkZ,
                        List<SkyIslandShape.Island> islands) {
        int changed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState rock = ModBlocks.LAVIC_SKYMETAL.get().defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState molten = ModBlocks.MOLTEN_METAL.get().defaultBlockState();
        for (SkyIslandShape.Island island : islands) {
            if (island.primaryRadius() < 30) continue; // no satellite mini-volcanoes
            long key = SkyTerrainPatterns.mix(seed, (int)island.centerX(),
                    (int)island.centerY(), (int)island.centerZ(), 0x7216aL);
            for (int n = 0; n < 5; n++) {
                long h = SkyTerrainPatterns.mix(key, n, 0, 0, 0x7216bL);
                double angle = Math.PI * 2 * SkyTerrainPatterns.unit(h);
                double reach = island.primaryRadius() * (.20 + .45 * SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(h, 2, 0, 0, 0x7216cL)));
                int cx = (int)Math.round(island.centerX() + Math.cos(angle) * reach);
                int cz = (int)Math.round(island.centerZ() + Math.sin(angle) * reach);
                int radius = 5 + (int)(4 * SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(h, 3, 0, 0, 0x7216dL)));
                if (cx + radius < chunkX || cx - radius > chunkX + 15
                        || cz + radius < chunkZ || cz - radius > chunkZ + 15) continue;
                var region = SkyTerrainSubregions.lavic(seed, cx, cz);
                if (region.rifts() < .60 || SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(h, 4, 0, 0, 0x7216eL)) > .58) continue;
                var column = island.column(cx, cz);
                var lake = column.lake();
                if (lake != null && lake.water()) continue;
                int base = -1;
                for (int y = Math.min(island.maxY(), SkyIslandShape.MAX_Y-14);
                         y >= island.minY(); y--) {
                    if (column.solid(y) && !column.solid(y+1)) { base=y; break; }
                }
                if (base < 0 || base + 13 > SkyIslandShape.MAX_Y) continue;
                int height = 5 + (int)(5 * SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(h, 5, 0, 0, 0x7216fL)));
                double notch = Math.PI * 2 * SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(h, 6, 0, 0, 0x72170L));
                for (int x=Math.max(chunkX,cx-radius);x<=Math.min(chunkX+15,cx+radius);x++) {
                    for (int z=Math.max(chunkZ,cz-radius);z<=Math.min(chunkZ+15,cz+radius);z++) {
                        double dx=x-cx,dz=z-cz;
                        double d=Math.hypot(dx,dz);
                        double irregular=.80 * (SkyTerrainPatterns.noise2(seed,x,z,11,0x72171L)-.5);
                        if (d > radius + irregular) continue;
                        double crater = 1.5;
                        double theta = Math.atan2(dz,dx);
                        double turn = Math.atan2(Math.sin(theta-notch),Math.cos(theta-notch));
                        boolean spill = Math.abs(turn)<.28 && d>=crater;
                        int top = base + (int)Math.round(1 + height * Math.pow(
                                Math.max(0,1-d/radius),.72));
                        // Raised ring and open lava-filled crater; one low lip
                        // supplies a molten spillway down the cone exterior.
                        int lip = base + height - 1;
                        if (d < crater) top = lip;
                        else if (d < crater + 1.55) top = Math.max(top,lip+1);
                        if (spill && d < crater + 3.0) top = Math.min(top,lip);
                        int bottom = Math.max(base-2,SkyIslandShape.MIN_Y);
                        for (int y=bottom;y<=top;y++) {
                            if(y<SkyIslandShape.MIN_Y || y>SkyIslandShape.MAX_Y)continue;
                            pos.set(x,y,z);
                            if (!level.ensureCanWrite(pos)) continue;
                            BlockState old = level.getBlockState(pos);
                            if (!old.isAir() && !old.is(ModBlocks.LAVIC_SKYMETAL_GRASS.get())
                                    && !old.is(ModBlocks.SHORT_LAVIC_GRASS.get())
                                    && !old.is(ModBlocks.METALLIC_SHORTER_GRASS.get())) continue;
                            boolean source = y==top && (d < crater || (spill && d < crater+3));
                            long grain = SkyTerrainPatterns.mix(seed,x,y,z,0x72172L);
                            BlockState state = source ? molten : y==top && (grain&7L)<3
                                    ? magma : (grain&7L)<3 ? basalt : rock;
                            if(level.setBlock(pos,state,Block.UPDATE_CLIENTS)) {
                                changed++;
                                if (source && (grain & 7L)==0L)
                                    level.scheduleTick(pos,level.getFluidState(pos).getType(),2);
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }
}
