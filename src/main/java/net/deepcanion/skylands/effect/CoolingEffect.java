package net.deepcanion.skylands.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Prevents SkyLands Too Hot buildup; heat exposure logic checks this directly. */
public final class CoolingEffect extends MobEffect {
    public CoolingEffect() { super(MobEffectCategory.BENEFICIAL, 0x73D9EF); }
}
