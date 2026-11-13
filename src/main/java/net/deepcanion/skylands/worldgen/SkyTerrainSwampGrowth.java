package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Mangrove growth inside large, terrain-integrated wetlands. These are NOT
 * shore-only trees around the old chunk-sized pond decoration. A successful
 * tree grows from a mud island surrounded by shallow water. Only the current
 * generating chunk is inspected; non-wetland Swamp forest is unchanged.
 */
final class SkyTerrainSwampGrowth {
    private SkyTerrainSwampGrowth() { }

    static void inWetlands(WorldGenLevel level, ChunkGenerator generator, long seed,
                           int chunkX, int chunkZ) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,chunkX,0,chunkZ,0x3423abe1L));
        var features=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> mangrove=features.get(ResourceLocation.withDefaultNamespace("mangrove"));
        // The tree-planting candidates are deliberately well inside their
        // chunk. Nearby chunks independently populate the remaining lake.
        ConfiguredFeature<?, ?> tall=features.get(ResourceLocation.withDefaultNamespace("tall_mangrove"));
        boolean placedTall=false;
        if (mangrove != null) for(int attempt=0;attempt<6;attempt++) {
            int x=chunkX+5+rng.nextInt(6),z=chunkZ+5+rng.nextInt(6);
            var region=SkyTerrainSubregions.swamp(seed,x,z);
            // Same capped number of expensive tree-generation attempts as
            // before in ordinary Wetlands. Thickets may get two additional
            // retries; open Deep Mire lakes stay mostly treeless.
            if(attempt>=4 && region.thickets()<.52)continue;
            if(rng.nextDouble()>.70*region.wetlands()+.16*region.mire()+.92*region.thickets())continue;
            int y=waterAt(level,x,z);
            if(y<0)continue;
            BlockPos water=new BlockPos(x,y,z), base=water.above(),floor=water.below();
            if(!level.ensureCanWrite(base)||!level.getBlockState(base).isAir())continue;
            // One tall vanilla mangrove (when available) per chunk at most;
            // all other wetland trees remain regular mangroves.
            boolean wantTall=!placedTall && tall!=null && region.thickets()>.49
                    && rng.nextFloat()<.52F*region.thickets();
            ConfiguredFeature<?, ?> selected=wantTall ? tall : mangrove;
            BlockState before=level.getBlockState(water);
            level.setBlock(water,Blocks.MUD.defaultBlockState(),Block.UPDATE_CLIENTS);
            if (selected.place(level,generator,rng,base)) {
                if(wantTall)placedTall=true;
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                    if(dx*dx+dz*dz>5 || rng.nextFloat()>.48F)continue;
                    BlockPos p=floor.offset(dx,0,dz);
                    if(level.ensureCanWrite(p) && level.getBlockState(p).is(Blocks.MUD))
                        level.setBlock(p,Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(),Block.UPDATE_CLIENTS);
                }
            } else {
                level.setBlock(water,before,Block.UPDATE_CLIENTS);
            }
        }
        // Two-block-tall visual reeds: the registered Seagrass block occupies
        // only the water tile, while its existing crossed model rises above it.
        // Keep these in groups, and never replace a tree root or lily pad.
        BlockState reed = ModBlocks.SWAMP_WATER_PLANT.get().defaultBlockState();
        for (int i=0; i<35; i++) {
            int x=chunkX+rng.nextInt(16), z=chunkZ+rng.nextInt(16);
            if (SkyTerrainPatterns.patch(seed,x,z,18,0x418caL)<.23 || rng.nextInt(3)==0) continue;
            var district=SkyTerrainSubregions.swamp(seed,x,z);
            if(rng.nextDouble()<.34*district.mire())continue;
            int y=waterAt(level,x,z);
            if (y<0) continue;
            BlockPos p=new BlockPos(x,y,z);
            if (level.ensureCanWrite(p) && level.getBlockState(p).is(Blocks.WATER)
                    && level.getBlockState(p.above()).isAir() && reed.canSurvive(level,p))
                level.setBlock(p,reed,Block.UPDATE_CLIENTS);
        }
        // Lily pads and occasional open patches remain between the trees.
        for(int i=0;i<55;i++) {
            int x=chunkX+rng.nextInt(16),z=chunkZ+rng.nextInt(16);
            int y=waterAt(level,x,z);
            if(y<0 || rng.nextInt(5)!=0)continue;
            BlockPos p=new BlockPos(x,y+1,z);
            BlockState pad=Blocks.LILY_PAD.defaultBlockState();
            if(level.ensureCanWrite(p) && level.getBlockState(p).isAir() && pad.canSurvive(level,p))
                level.setBlock(p,pad,Block.UPDATE_CLIENTS);
        }
    }

    private static int waterAt(WorldGenLevel level,int x,int z) {
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int y=SkyIslandShape.MAX_Y-2;y>=SkyIslandShape.MIN_Y+12;y--) {
            pos.set(x,y,z);
            if(!level.getBlockState(pos).is(Blocks.WATER)
                    || !level.getBlockState(pos.above()).isAir())continue;
            BlockState beneath=level.getBlockState(pos.below());
            // Never mistake a stream in another sky biome for Swamp wetland.
            if(beneath.is(Blocks.MUD)||beneath.is(Blocks.CLAY)||beneath.is(ModBlocks.SKYROCK.get())
                    ||beneath.is(ModBlocks.SWAMP_MOSSY_SKYROCK.get()))return y;
        }
        return -1;
    }
}
