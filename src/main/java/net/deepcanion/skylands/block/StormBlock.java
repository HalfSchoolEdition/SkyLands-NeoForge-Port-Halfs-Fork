package net.deepcanion.skylands.block;

import net.deepcanion.skylands.registry.ModBlocks;
import net.deepcanion.skylands.registry.ModItems;
import net.deepcanion.skylands.registry.ModParticles;
import net.deepcanion.skylands.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One storm device with three reachable states:
 *   locked & active (natural generation / Creative),
 *   unlocked & inactive (key used / unpowered),
 *   unlocked & active (redstone powered).
 * Old deactivated block IDs remain as world migration stubs.
 */
public final class StormBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
    private final boolean legacyDisabled;

    public StormBlock(boolean legacyDisabled, Properties properties) {
        super(properties);
        this.legacyDisabled = legacyDisabled;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, !legacyDisabled).setValue(LOCKED, !legacyDisabled));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, LOCKED);
    }

    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new net.deepcanion.skylands.block.entity.StormBlockEntity(pos, state);
    }

    public static ItemStack unlockedItem() {
        ItemStack stack = new ItemStack(ModBlocks.STORM_BLOCK.get());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LOCKED, false));
        return stack;
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(LOCKED) ? new ItemStack(ModBlocks.STORM_BLOCK.get()) : unlockedItem();
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockItemStateProperties itemProperties = context.getItemInHand().get(DataComponents.BLOCK_STATE);
        boolean locked = itemProperties == null || !Boolean.FALSE.equals(itemProperties.get(LOCKED));
        return defaultBlockState().setValue(LOCKED, locked)
                .setValue(ACTIVE, locked || context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Locked storm blocks are not minable in Survival; Creative still bypasses hardness.
        // Unlocked: roughly half the usual mining time (effective hardness ~3 vs 6).
        return state.getValue(LOCKED) ? 0.0F : Math.min(1F,
                super.getDestroyProgress(state, player, level, pos) * 2.0F);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (legacyDisabled) {
            if (level.getBlockState(pos).is(this)) {
                level.setBlock(pos, ModBlocks.STORM_BLOCK.get().defaultBlockState()
                        .setValue(LOCKED, false).setValue(ACTIVE, level.hasNeighborSignal(pos)), 3);
            }
        } else if (state.getValue(LOCKED) && !state.getValue(ACTIVE)) {
            // v0.9.5 pre-unlock-save migration: inactive existing storms had only ACTIVE=false.
            level.setBlock(pos, state.setValue(LOCKED, false).setValue(ACTIVE, level.hasNeighborSignal(pos)), 3);
        }
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide() && !state.getValue(LOCKED) && !old.is(this))
            neighborChanged(state, level, pos, this, pos, moved);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
        if (!level.isClientSide() && !legacyDisabled && !state.getValue(LOCKED)) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(ACTIVE)) level.setBlock(pos, state.setValue(ACTIVE, powered), 3);
        }
    }

    /**
     * Vanilla's animateTick randomly samples nearby blocks and never runs at 100 blocks.
     * Use a client block-entity ticker instead for consistent distant storm effects.
     */
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                    Level level, BlockState state,
                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!level.isClientSide() || !type.equals(net.deepcanion.skylands.registry.ModBlockEntities.STORM_BLOCK.get()))
            return null;
        return (clientLevel, blockPos, blockState, be) ->
                net.deepcanion.skylands.block.entity.StormBlockEntity.clientTick(
                        clientLevel, blockPos, blockState, (net.deepcanion.skylands.block.entity.StormBlockEntity) be);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (legacyDisabled || !state.getValue(LOCKED) || !stack.is(ModItems.DIAMOND_LEVEL_1_KEY.get()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!level.setBlock(pos, state.setValue(LOCKED, false).setValue(ACTIVE, level.hasNeighborSignal(pos)), 3))
            return ItemInteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, pos, ModSounds.MECHANISM_DEACTIVATION.get(), SoundSource.BLOCKS, 1F, 1F);
        return ItemInteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (legacyDisabled || !state.getValue(LOCKED)) return InteractionResult.PASS;
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(ModItems.DIAMOND_LEVEL_1_KEY.get())) {
            ItemInteractionResult result = useItemOn(offhand, state, level, pos, player, InteractionHand.OFF_HAND, hit);
            return result == ItemInteractionResult.FAIL ? InteractionResult.FAIL : InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.skylands.storm_locked",
                        ModItems.DIAMOND_LEVEL_1_KEY.get().getDescription()), true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
