package net.deepcanion.skylands.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** SkyLands HUD indicator for sustained environmental cold; the shared
 * gradual biome-freezing controller owns timing, damage and protections.
 * Vanilla powdered snow is independent and does not add this effect.
 */
public final class TooColdEffect extends MobEffect {
    public TooColdEffect() { super(MobEffectCategory.HARMFUL, 0x9AEEFF); }
}
