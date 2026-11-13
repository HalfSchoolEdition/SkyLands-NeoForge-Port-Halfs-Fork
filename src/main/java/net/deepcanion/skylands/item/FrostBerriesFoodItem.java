package net.deepcanion.skylands.item;

import net.deepcanion.skylands.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Edible Frost Berries: a brief cooling boost, matching a raw Warmth Radish. */
public final class FrostBerriesFoodItem extends Item {
    public FrostBerriesFoodItem() {
        super(new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(1).saturationModifier(0.1F).alwaysEdible().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide()) {
            entity.addEffect(new MobEffectInstance(ModEffects.COOLING, 40, 0));
        }
        return result;
    }
}
