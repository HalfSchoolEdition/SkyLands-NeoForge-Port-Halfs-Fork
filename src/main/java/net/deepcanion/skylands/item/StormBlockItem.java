package net.deepcanion.skylands.item;

import java.util.List;
import net.deepcanion.skylands.block.StormBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/** One Storm Block item ID, with a lock-state component on collected copies. */
public final class StormBlockItem extends BlockItem {
    public StormBlockItem(Block block, Item.Properties properties) { super(block, properties); }

    public static boolean isUnlocked(ItemStack stack) {
        BlockItemStateProperties states = stack.get(DataComponents.BLOCK_STATE);
        return states != null && Boolean.FALSE.equals(states.get(StormBlock.LOCKED));
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipComponents, flag);
        boolean unlocked = isUnlocked(stack);
        tooltipComponents.add(Component.literal(unlocked ? "Unlocked" : "Locked")
                .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (unlocked) tooltipComponents.add(Component.literal("Can be powered with redstone.")
                .withStyle(ChatFormatting.GRAY));
    }
}
