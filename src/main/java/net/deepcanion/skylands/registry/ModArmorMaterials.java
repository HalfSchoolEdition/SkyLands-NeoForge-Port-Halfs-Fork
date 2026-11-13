package net.deepcanion.skylands.registry;

import java.util.EnumMap;
import java.util.List;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Standard Minecraft 1.21.1 armor materials, with swappable placeholder textures.
 * Both helmets have two armor points and iron-level 165 durability.
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyLands.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> FROST =
            MATERIALS.register("frost", () -> material("frost_helmet", true));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> FLAME =
            MATERIALS.register("flame", () -> material("flame_helmet", false));

    private static ArmorMaterial material(String texture, boolean cold) {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 2);
        defense.put(ArmorItem.Type.CHESTPLATE, 6);
        defense.put(ArmorItem.Type.LEGGINGS, 5);
        defense.put(ArmorItem.Type.BOOTS, 2);
        defense.put(ArmorItem.Type.BODY, 4);
        return new ArmorMaterial(defense, 9, SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(cold ? ModItems.PEARLECENT_CELESTIUM.get() : ModItems.PALE_CELESTIUM.get()),
                List.of(new ArmorMaterial.Layer(SkyLands.id(texture))), 0.0F, 0.0F);
    }

    private ModArmorMaterials() { }
}
