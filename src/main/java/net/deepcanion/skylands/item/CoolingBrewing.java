package net.deepcanion.skylands.item;

import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/** Awkward Potion + SkyLands ingredient -> custom drinkable potion.
 * Standard component-aware brewing recipes expose their inputs and outputs
 * to recipe viewers, unlike opaque anonymous IBrewingRecipe handlers.
 */
public final class CoolingBrewing {
    private CoolingBrewing() { }

    public static void register(RegisterBrewingRecipesEvent event) {
        // Vanilla Ingredient.of(Items.POTION) would also match unrelated potions.
        // The partial component ingredient requires Awkward contents, while
        // permitting unrelated extra components on a modded potion stack.
        Ingredient awkwardPotion = DataComponentIngredient.of(false,
                PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
        event.getBuilder().addRecipe(awkwardPotion,
                Ingredient.of(ModItems.FROST_BERRIES.get()),
                new ItemStack(ModItems.COOLING_POTION.get()));
    }
}
