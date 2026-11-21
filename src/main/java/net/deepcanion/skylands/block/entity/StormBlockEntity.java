package net.deepcanion.skylands.block.entity;

import net.deepcanion.skylands.registry.ModBlockEntities;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Migrates previously placed deactivated storms while preserving their inactive state. */
public final class StormBlockEntity extends BlockEntity {
    public StormBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORM_BLOCK.get(), pos, state);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            if (state.is(ModBlocks.DEACTIVATED_STORM_BLOCK.get()))
                server.scheduleTick(worldPosition, ModBlocks.DEACTIVATED_STORM_BLOCK.get(), 1);
            else if (state.is(ModBlocks.STORM_BLOCK.get())
                    && state.getValue(net.deepcanion.skylands.block.StormBlock.LOCKED)
                    && !state.getValue(net.deepcanion.skylands.block.StormBlock.ACTIVE))
                server.scheduleTick(worldPosition, ModBlocks.STORM_BLOCK.get(), 1);
        }
    }
    /** Client-only storm visuals; this is never run by the server ticker. */
    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos,
            BlockState state, StormBlockEntity blockEntity) {
        if (state.is(ModBlocks.STORM_BLOCK.get())
                && state.getValue(net.deepcanion.skylands.block.StormBlock.ACTIVE)) {
            net.deepcanion.skylands.client.ClientStormVortex.tick(level, pos);
        }
    }
}
