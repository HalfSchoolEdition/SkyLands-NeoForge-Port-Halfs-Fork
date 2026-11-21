package net.deepcanion.skylands.item;

import net.deepcanion.skylands.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/** Edible Frost Berries: a brief cooling boost, matching a raw Warmth Radish. */
public final class FrostBerriesFoodItem extends Item {
    public FrostBerriesFoodItem() {
        super(new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(1).saturationModifier(0.1F).alwaysEdible()
                .effect(() -> new MobEffectInstance(ModEffects.COOLING, 40, 0), 1.0F)
                .build()));
    }
}
