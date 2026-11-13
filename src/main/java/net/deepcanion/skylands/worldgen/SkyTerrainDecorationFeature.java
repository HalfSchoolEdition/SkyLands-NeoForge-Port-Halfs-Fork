package net.deepcanion.skylands.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Lightweight biome-specific grass and bush placement; no vanilla flora is introduced. */
public final class SkyTerrainDecorationFeature extends Feature<SkyTerrainThemeConfiguration> {
    public SkyTerrainDecorationFeature() { super(SkyTerrainThemeConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<SkyTerrainThemeConfiguration> context) {
        WorldGenLevel level=context.level();
        if(!Level.OVERWORLD.equals(level.getLevel().dimension())) return false;
        RandomSource random=context.random();
        SkyTerrainPalette palette=SkyTerrainPalette.forTheme(context.config().theme());
        int theme = SkyTerrainVegetation.themeIndex(context.config().theme());
        int x0=Math.floorDiv(context.origin().getX(),16)*16;
        int z0=Math.floorDiv(context.origin().getZ(),16)*16;
        BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
        int placed=0;
        for(int i=0;i<26;i++) {
            int x=x0+random.nextInt(16),z=z0+random.nextInt(16);
            // Sky islands can have several surfaces in the same column.
            for(int y=SkyIslandShape.MAX_Y;y>=301;y--) {
                at.set(x,y,z);
                // Arid dune sand can be several blocks deep, so check its
                // underlying rock rather than mistaking Golden sandy shores.
                boolean duneGround = false;
                if (theme == 2 && level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.SAND)) {
                    for (int depth=1;depth<=6;depth++) {
                        if (level.getBlockState(at.below(depth)).is(palette.core().getBlock())) {
                            duneGround = true;
                            break;
                        }
                    }
                }
                if(!palette.isTop(level.getBlockState(at)) && !duneGround) continue;
                BlockPos above=at.above();
                if(!level.getBlockState(above).isAir()) break;
                SkyTerrainSubregions.GoldenSample golden = theme == 0
                        ? SkyTerrainSubregions.golden(level.getSeed(), x, z) : null;
                if (golden != null && random.nextDouble() > 1.0 - .68*golden.plateau()) break;
                SkyTerrainSubregions.AridSample arid = theme == 2
                        ? SkyTerrainSubregions.arid(level.getSeed(), x, z) : null;
                if (arid != null && random.nextDouble() >
                        arid.grasslands() + .10*arid.dunes() + .07*arid.rockyHighlands()) break;
                BlockState plant = golden != null
                        ? SkyTerrainVegetation.selectGolden(random, golden)
                        : SkyTerrainVegetation.select(theme, random);
                if (!plant.canSurvive(level, above)) break;
                if (plant.getBlock() instanceof DoublePlantBlock) {
                    if (above.getY() + 1 < level.getMaxBuildHeight()
                            && level.ensureCanWrite(above.above())
                            && level.getBlockState(above.above()).isAir()) {
                        DoublePlantBlock.placeAt(level, plant, above,
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        placed++;
                    }
                } else if (level.setBlock(above, plant, Block.UPDATE_CLIENTS)) {
                    placed++;
                }
                break;
            }
        }
        return placed>0;
    }
}
