package net.deepcanion.skylands.item;

import net.deepcanion.skylands.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Drinkable cooling potion: prevents SkyLands' custom Too Hot exposure. */
public final class CoolingPotionItem extends Item {
    public CoolingPotionItem() {
        super(new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(0).saturationModifier(0F).alwaysEdible().build())
                .stacksTo(16));
    }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide()) entity.addEffect(new MobEffectInstance(ModEffects.COOLING, 3600, 0));
        if (entity instanceof Player player && player.getAbilities().instabuild) return result;
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (result.isEmpty()) return bottle;
        if (!level.isClientSide() && entity instanceof Player player && !player.getInventory().add(bottle))
            player.drop(bottle, false);
        return result;
    }
}
