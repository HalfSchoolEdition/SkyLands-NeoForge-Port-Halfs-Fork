package net.deepcanion.skylands.block;

import net.deepcanion.skylands.worldgen.SkySaplingGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla sapling growth ticks/bonemeal, with handcrafted template growth at stage 1. */
public final class SkySaplingBlock extends SaplingBlock {
    private final SkySaplingGrowth.Kind kind;

    public SkySaplingBlock(TreeGrower grower, SkySaplingGrowth.Kind kind, Properties properties) {
        super(grower, properties);
        this.kind = kind;
    }

    @Override
    protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
        return soil.is(PlantSupport.SOILS) || super.mayPlaceOn(soil, level, pos);
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
            return;
        }
        // No fallback to the old procedural tree configurations: a blocked
        // handcrafted tree simply stays a sapling and may retry later.
        SkySaplingGrowth.grow(level, pos, this, kind, random);
    }
}
