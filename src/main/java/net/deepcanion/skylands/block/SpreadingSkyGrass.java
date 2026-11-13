package net.deepcanion.skylands.block;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla-like grass spreading between compatible SkyLands surfaces. */
final class SpreadingSkyGrass {
    private SpreadingSkyGrass() { }

    static BlockState bareStone(BlockState grass) {
        Block block = grass.getBlock();
        if (block == ModBlocks.GLACIATED_GRASS_BLOCK.get())
            return ModBlocks.GLACIATED_SKYROCK.get().defaultBlockState();
        if (block == ModBlocks.LAVIC_SKYMETAL_GRASS.get())
            return ModBlocks.LAVIC_SKYMETAL.get().defaultBlockState();
        if (block == ModBlocks.ARID_GRASS_BLOCK.get())
            return ModBlocks.SKY_SANDSTONE.get().defaultBlockState();
        return ModBlocks.SKYROCK.get().defaultBlockState();
    }

    /** Slabs and stairs do not fully occlude light, but they still cover the
     * grass surface. Do not allow the native grasses to grow directly under
     * these partial blocks (including modded slab/stair variants). */
    private static boolean physicallyCovered(BlockState above, ServerLevel level, BlockPos top) {
        return above.is(BlockTags.SLABS) || above.is(BlockTags.STAIRS)
                || above.isSolidRender(level, top) || !above.getFluidState().isEmpty();
    }

    static boolean covered(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos top = pos.above();
        BlockState above = level.getBlockState(top);
        // Sky cactus is native vegetation rooted in Arid Grass, not a roof.
        // Converting its supporting soil to sandstone makes the cactus break
        // the next time its survival check runs. Preserve the soil beneath
        // both new species and existing legacy cactus trunks.
        if (state.is(ModBlocks.ARID_GRASS_BLOCK.get())
                && (above.getBlock() instanceof SkyCactusSpeciesBlock
                    || above.is(ModBlocks.SKY_CACTUS.get())
                    || above.is(ModBlocks.SKY_CACTUS_BRANCH.get())
                    || above.is(ModBlocks.SKY_CACTUS_TOP.get()))) return false;
        return physicallyCovered(above, level, top)
                || level.getMaxLocalRawBrightness(top) < 4;
    }

    static void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos top = pos.above();
        if (covered(state, level, pos)) {
            level.setBlock(pos, bareStone(state), Block.UPDATE_CLIENTS);
            return;
        }
        if (level.getMaxLocalRawBrightness(top) < 9) return;
        Block base = bareStone(state).getBlock();
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos target = pos.offset(random.nextInt(3) - 1,
                    random.nextInt(3) - 1, random.nextInt(3) - 1);
            if (!level.getBlockState(target).is(base)) continue;
            BlockPos targetTop = target.above();
            BlockState cover = level.getBlockState(targetTop);
            if (physicallyCovered(cover, level, targetTop)
                    || level.getMaxLocalRawBrightness(targetTop) < 9) continue;
            BlockState spread = state;
            if (state.hasProperty(FacingBlock.FACING))
                spread = state.setValue(FacingBlock.FACING,
                        Direction.from2DDataValue(random.nextInt(4)));
            level.setBlock(target, spread, Block.UPDATE_CLIENTS);
        }
    }
}
