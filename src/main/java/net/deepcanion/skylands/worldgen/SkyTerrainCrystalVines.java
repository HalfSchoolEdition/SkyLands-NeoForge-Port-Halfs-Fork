package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.block.AttachedBlock;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Side-wall and underside-to-underside hanging bridges. The common geometry
 * also gives Swamp islands longer mangrove-leaf-only dangling vine spans.
 * Only blocks in the generating chunk are touched, even when the bridge
 * starts and ends in two different chunks.
 */
final class SkyTerrainCrystalVines {
    private SkyTerrainCrystalVines() { }

    static void decorate(WorldGenLevel level,long seed,int chunkX,int chunkZ) {
        var source=level.getLevel().getChunkSource();
        BiomeSource biomes=source.getGenerator().getBiomeSource();
        Climate.Sampler sampler=source.randomState().sampler();
        int cellX=Math.floorDiv(chunkX,SkyIslandShape.GRID);
        int cellZ=Math.floorDiv(chunkZ,SkyIslandShape.GRID);
        for(int gx=cellX-2;gx<=cellX+2;gx++)for(int gz=cellZ-2;gz<=cellZ+2;gz++) {
            if(!SkyIslandShape.hasSite(seed,gx,gz))continue;
            int sx=SkyIslandShape.siteX(seed,gx,gz),sz=SkyIslandShape.siteZ(seed,gx,gz);
            if(sx<chunkX-205||sx>chunkX+220||sz<chunkZ-205||sz>chunkZ+220)continue;
            Holder<Biome> biome=biomes.getNoiseBiome(QuartPos.fromBlock(sx),
                    QuartPos.fromBlock(350),QuartPos.fromBlock(sz),sampler);
            String theme=biome.unwrapKey().map(k -> k.location().equals(
                    SkyLands.id("floating_glaciated_islands"))?"glaciated"
                    :k.location().equals(SkyLands.id("floating_swamp_islands"))?"swamp":null).orElse(null);
            if(theme==null)continue;
            boolean swamp="swamp".equals(theme);
            for(SkyIslandBridgeGeometry.Path path:SkyIslandBridgeGeometry.paths(seed,gx,gz,theme)) {
                if(path.intersects(chunkX,chunkZ))render(level,seed,path,chunkX,chunkZ,swamp);
            }
        }
    }

    private static void render(WorldGenLevel level,long seed,SkyIslandBridgeGeometry.Path path,
                               int chunkX,int chunkZ,boolean swamp) {
        BlockState core=swamp
                ? Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true)
                : ModBlocks.CRISTALIZEDICE.get().defaultBlockState();
        BlockState foliage=ModBlocks.GLACIATED_LEAVES.get().defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT,true);
        BlockState spikes=ModBlocks.ICE_SPIKES.get().defaultBlockState();
        List<SkyIslandBridgeGeometry.Point> voxels=SkyIslandBridgeGeometry.voxels(path);
        List<BlockPos> backbone=new ArrayList<>();
        for(int i=0;i<voxels.size();i++) {
            var point=voxels.get(i);
            BlockPos p=new BlockPos(point.x(),point.y(),point.z());
            int radius=(path.thick() && i%5!=0)?1:0;
            for(int ox=-radius;ox<=radius;ox++)for(int oy=-radius;oy<=radius;oy++)
                for(int oz=-radius;oz<=radius;oz++) {
                    if(ox*ox+oy*oy+oz*oz>radius*radius)continue;
                    BlockPos q=p.offset(ox,oy,oz);
                    if(!local(q,chunkX,chunkZ)||!level.ensureCanWrite(q))continue;
                    BlockState present=level.getBlockState(q);
                    // Other intersecting bridges may have decorated this position
                    // with an ice spike/leaf already. Preserve the solid backbone
                    // so vines never end up with gaps at these crossings.
                    if(present.isAir() || (!swamp && (present.is(ModBlocks.ICE_SPIKES.get())
                            || present.is(ModBlocks.GLACIATED_LEAVES.get())))) {
                        level.setBlock(q,core,Block.UPDATE_CLIENTS);
                    }
                    if(!swamp && level.getBlockState(q).is(core.getBlock()))backbone.add(q);
                }
        }
        if(swamp)return; // Hanging Swamp bridges must contain mangrove leaves ONLY.
        for(BlockPos p:backbone)for(Direction d:Direction.values()) {
            BlockPos out=p.relative(d);
            if(!local(out,chunkX,chunkZ)||!level.ensureCanWrite(out)
                    ||!level.getBlockState(out).isAir())continue;
            long hash=SkyTerrainPatterns.mix(seed,p.getX(),p.getY(),p.getZ(),
                    path.key()^(d.ordinal()*0x5f356495L));
            double chance=unit(hash);
            if(chance<.19)level.setBlock(out,foliage,Block.UPDATE_CLIENTS);
            else if(chance<.985) {
                AttachFace face=d==Direction.UP?AttachFace.FLOOR
                        :d==Direction.DOWN?AttachFace.CEILING:AttachFace.WALL;
                Direction facing=d.getAxis().isHorizontal()?d
                        :Direction.from2DDataValue((int)(hash>>>29)&3);
                level.setBlock(out,spikes.setValue(AttachedBlock.FACE,face)
                        .setValue(AttachedBlock.FACING,facing),Block.UPDATE_CLIENTS);
            }
        }
    }
    private static boolean local(BlockPos p,int cx,int cz) {
        return p.getX()>=cx&&p.getX()<cx+16&&p.getZ()>=cz&&p.getZ()<cz+16
                &&p.getY()>SkyIslandShape.MIN_Y&&p.getY()<SkyIslandShape.MAX_Y;
    }
    private static double unit(long h) {
        h=(h^(h>>>33))*0xff51afd7ed558ccdL;
        h^=h>>>33;
        return ((h>>>11)&((1L<<53)-1))*0x1.0p-53;
    }
}
