package net.deepcanion.skylands.registry;
import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.effect.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.*;
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyLands.MOD_ID);
    public static final DeferredHolder<MobEffect, HiddenEffect> HIDDEN = EFFECTS.register("hidden", HiddenEffect::new);
    public static final DeferredHolder<MobEffect, WarmthEffect> WARMTH = EFFECTS.register("warmth", WarmthEffect::new);
    public static final DeferredHolder<MobEffect, CoolingEffect> COOLING = EFFECTS.register("cooling", CoolingEffect::new);
    public static final DeferredHolder<MobEffect, TooHotEffect> TOO_HOT = EFFECTS.register("too_hot", TooHotEffect::new);
    public static final DeferredHolder<MobEffect, TooColdEffect> TOO_COLD = EFFECTS.register("too_cold", TooColdEffect::new);
    private ModEffects() { }
}
