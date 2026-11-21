package net.deepcanion.skylands.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/** Keeps the original mysterious key tooltip, while marking its unfinished purpose clearly. */
public final class MasterKeyItem extends MaterialItem {
    public MasterKeyItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC)
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true),
                "item.skylands.master_key.tooltip", false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("item.skylands.master_key.wip_tooltip")
                .withStyle(ChatFormatting.RED));
    }
}
