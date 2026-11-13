package net.deepcanion.skylands.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** A sky-grass surface, with the same random-tick lifecycle as vanilla grass. */
public class SpreadingSkySoilBlock extends SkySoilBlock {
    public SpreadingSkySoilBlock(Properties properties) { super(properties); }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                                        RandomSource random) {
        SpreadingSkyGrass.tick(state, level, pos, random);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction,
            BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) level.scheduleTick(pos, this, 1);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                                   RandomSource random) {
        if (SpreadingSkyGrass.covered(state, level, pos))
            level.setBlock(pos, SpreadingSkyGrass.bareStone(state), Block.UPDATE_CLIENTS);
    }
}
