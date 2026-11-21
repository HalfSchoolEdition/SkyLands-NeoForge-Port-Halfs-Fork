package net.deepcanion.skylands.item;

import java.util.List;
import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Places cloud particle emitters against surfaces; mining them uses normal block breaking. */
public final class CloudStaffItem extends Item {
    public CloudStaffItem() {
        super(new Item.Properties().durability(256));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("item.skylands.cloud_staff.tooltip").withStyle(ChatFormatting.GRAY));
    }

    /** Smoke Flowers are the repair material for this loot-only tool. */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ModItems.SMOKE_FLOWER.get())
                || super.isValidRepairItem(stack, repairCandidate);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        // A selected cloud is treated as a surface, not replaced with itself.
        // Other replaceable blocks (such as grass) can be replaced directly.
        BlockPos target = clickedState.is(ModBlocks.CLOUD_BLOCK.get()) || !clickedState.canBeReplaced()
                ? clicked.relative(context.getClickedFace()) : clicked;
        if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)) return InteractionResult.FAIL;
        if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.PASS;
        if (!level.getFluidState(target).isEmpty()) return InteractionResult.PASS;
        if (!level.mayInteract(player, target)
                || !player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            if (!level.setBlock(target, ModBlocks.CLOUD_BLOCK.get().defaultBlockState(), 3)) return InteractionResult.FAIL;
            level.playSound(null, target, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 0.7F,
                    0.95F + level.random.nextFloat() * 0.15F);
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
