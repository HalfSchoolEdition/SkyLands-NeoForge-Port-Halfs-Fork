package net.deepcanion.skylands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decorative SkyLands villager workstation with the physical behavior of a vanilla anvil.
 * Deliberately does NOT open the vanilla repair/name/combination menu.
 */
public final class CelestiumAnvilBlock extends FallingBlock {
    public static final MapCodec<CelestiumAnvilBlock> CODEC = simpleCodec(CelestiumAnvilBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // These boxes precisely follow the three cuboids in custom/celestiumanvil.json.
    // The model's top is one pixel taller than a standard Minecraft block (Y = 17).
    private static final VoxelShape BASE = Block.box(3, 0, 3, 13, 3, 13);
    private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
            BASE,
            Block.box(5, 3, 4, 11, 10, 12),
            Block.box(0, 10, 2, 16, 17, 14)
    );
    private static final VoxelShape EAST_WEST_SHAPE = Shapes.or(
            BASE,
            Block.box(4, 3, 5, 12, 10, 11),
            Block.box(2, 10, 0, 14, 17, 16)
    );

    public CelestiumAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH_SHAPE : EAST_WEST_SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected void falling(FallingBlockEntity fallingEntity) {
        // Same impact scaling and cap as a vanilla anvil (2 damage per fallen block, max 40).
        fallingEntity.setHurtsEntities(2.0F, 40);
    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().anvil(entity);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedState, FallingBlockEntity entity) {
        // Vanilla anvil landing sound / level event.
        level.levelEvent(1031, pos, 0);
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
        // Vanilla anvil breaking sound, if a falling anvil cannot settle and is destroyed.
        level.levelEvent(1029, pos, 0);
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return 0xA6A6A6;
    }
}
