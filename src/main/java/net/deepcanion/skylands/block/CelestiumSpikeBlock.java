package net.deepcanion.skylands.block;

import java.util.function.Function;
import net.deepcanion.skylands.entity.RisingCelestiumSpikeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * All four Celestium spike variants share these support rules, without changing
 * the unrelated wall-mounted antennae, crystal vines, or other decorative spikes.
 *
 * Floor spikes are buoyant: removing their supporting block launches the spike
 * upwards. Ceiling spikes also rise, then try to attach to the next solid ceiling.
 */
public final class CelestiumSpikeBlock extends WaterloggedAttachedBlock {
    public CelestiumSpikeBlock(Properties properties, Function<BlockState, VoxelShape> shape) {
        super(properties, shape);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Only the top and bottom of a full block are valid attachment surfaces.
        // In particular, never silently reinterpret wall placement as a floor spike.
        if (context.getClickedFace().getAxis() != Direction.Axis.Y) return null;
        BlockState candidate = super.getStateForPlacement(context);
        return candidate != null && candidate.canSurvive(context.getLevel(), context.getClickedPos())
                ? candidate : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        AttachFace face = state.getValue(FACE);
        if (face == AttachFace.WALL) return false; // old saves can still contain this orientation
        BlockPos support = face == AttachFace.CEILING ? pos.above() : pos.below();
        // Full collision cubes, not fences/slabs/other spikes or decorative blocks.
        BlockState supportState = level.getBlockState(support);
        return supportState.isCollisionShapeFullBlock(level, support) || supportState.is(BlockTags.WALLS);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState result = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        Direction supportDirection = state.getValue(FACE) == AttachFace.CEILING
                ? Direction.UP : Direction.DOWN;
        if ((direction == supportDirection || state.getValue(FACE) == AttachFace.WALL)
                && !state.canSurvive(level, pos)) {
            // Defer to the normal block-tick stage: never spawn entities directly
            // from neighbor updates, worldgen placement or client rendering.
            level.scheduleTick(pos, this, 1);
            // Keep the spike block alive until the scheduled rise. Returning AIR
            // here would bypass the entity conversion for thin wall supports.
            return state;
        }
        return result;
    }

    @Override
    protected void neighborChanged(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
        super.neighborChanged(state, level, pos, block, fromPos, moved);
        // Some wall blocks issue a neighbor notification without an updateShape
        // callback. Both paths must schedule the same rising conversion.
        if (!level.isClientSide() && !state.canSurvive(level, pos))
            level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this) || state.canSurvive(level, pos)) return;
        if (state.getValue(FACE) == AttachFace.FLOOR || state.getValue(FACE) == AttachFace.CEILING) {
            // Remove the source block first to avoid duplication. Retain water if
            // the original spike occupied a waterlogged position.
            if (level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL)) {
                RisingCelestiumSpikeEntity.spawn(level, pos, state);
            }
        } else {
            // Only legacy wall-mounted spikes drop: this old orientation is no longer placeable.
            level.destroyBlock(pos, true);
        }
    }
}
