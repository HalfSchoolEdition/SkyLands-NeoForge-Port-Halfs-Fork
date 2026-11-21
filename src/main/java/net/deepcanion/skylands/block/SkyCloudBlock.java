package net.deepcanion.skylands.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.world.level.block.state.BlockState;

/** Noncolliding cloud shell. Cull internal faces between matching clouds. */
public final class SkyCloudBlock extends Block {
    public SkyCloudBlock(Properties properties) { super(properties); }
    // These existing blocks persist in worlds but never render a cube.
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    // Like vanilla light/barrier blocks, only the special tool can select the block.
    // This shape is for targeting only; collisions stay disabled by block properties.
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(ModItems.CLOUD_STAFF.get()) ? Shapes.block() : Shapes.empty();
    }

    @Override public void animateTick(BlockState state, net.minecraft.world.level.Level level,
                                      net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!net.deepcanion.skylands.config.CloudConfig.SPEC.isLoaded()
                || !net.deepcanion.skylands.config.CloudConfig.CLOUD_BLOCK_PARTICLES.get()
                || random.nextFloat() >= 0.30F) return;
        level.addParticle(net.deepcanion.skylands.registry.ModParticles.CLOUD.get(),
                pos.getX() + 0.5 + (random.nextDouble() * 4 - 2),
                pos.getY() + random.nextDouble() * 1.5,
                pos.getZ() + 0.5 + (random.nextDouble() * 4 - 2),
                0.1 + random.nextDouble() * 0.9, 0, 0.1 + random.nextDouble() * 0.9);
    }

    @Override protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }
}
