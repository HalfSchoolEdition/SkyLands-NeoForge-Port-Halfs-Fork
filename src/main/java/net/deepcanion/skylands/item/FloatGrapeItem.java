package net.deepcanion.skylands.item;

import net.deepcanion.skylands.entity.FloatGrapeProjEntity;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** One throwable Float Grape can be re-hung beneath leaves, never multiplied. */
public final class FloatGrapeItem extends SkyThrowableItem {
    public FloatGrapeItem() {
        super(new Item.Properties(), 50,
                (world, user) -> FloatGrapeProjEntity.shoot(world, user, world.getRandom()));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        // Placement only on the underside of leaf blocks. Normal right-click in air
        // or on other faces remains a throw; no crouching or special plant item.
        if (context.getClickedFace() != Direction.DOWN || context.getPlayer() == null)
            return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos support = context.getClickedPos();
        BlockState leaves = level.getBlockState(support);
        if (!leaves.is(BlockTags.LEAVES) && !(leaves.getBlock() instanceof LeavesBlock))
            return InteractionResult.PASS;
        BlockPos target = support.below();
        if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)
                || !level.getBlockState(target).canBeReplaced()
                || !level.getFluidState(target).isEmpty()) return InteractionResult.PASS;
        if (!level.mayInteract(context.getPlayer(), target)
                || !context.getPlayer().mayUseItemAt(target, Direction.DOWN, context.getItemInHand()))
            return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.setBlock(target, ModBlocks.FLOAT_GRAPE_PLANT.get().defaultBlockState(), 3);
            level.playSound(null, target, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
