package net.deepcanion.skylands.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** HUD indicator for full heat exposure. SkyTemperatures owns the damage
 * timing so the player receives the complete buildup and grace period before
 * the first hit, independently of the global entity tick clock.
 */
public final class TooHotEffect extends MobEffect {
    public TooHotEffect() { super(MobEffectCategory.HARMFUL, 0xE37727); }
}
