package net.deepcanion.skylands.effect;

import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Only the helmet slot grants protection. Does not change vanilla freezing or fire damage. */
public final class TemperatureEquipment {
    private TemperatureEquipment() { }

    public static boolean preventsFreezing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.FROST_HELMET.get());
    }

    public static boolean preventsOverheating(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.FLAME_HELMET.get());
    }
}
