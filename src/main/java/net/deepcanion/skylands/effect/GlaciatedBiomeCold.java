package net.deepcanion.skylands.effect;

import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Expose players to ordinary vanilla freezing throughout the high-sky Glaciated biome.
 * A shared biome-cold controller offsets vanilla's two-tick thaw with an
 * alternating 2/3-tick chill, providing about fourteen seconds to fully freeze.
 * After a further two seconds, vanilla freeze damage occurs every four seconds.
 * On departure, vanilla handles gradual thaw without a lingering effect.
 */
public final class GlaciatedBiomeCold {
    private GlaciatedBiomeCold() { }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator() || !player.canFreeze()) return;

        // Powder snow already accumulates vanilla freeze ticks, so don't double it.
        // Warmth protection and player immunity are checked by VanillaFreezing.
        if (TemperatureCompatibility.externalCold() || player.isInPowderSnow
                || (GameplayConfig.SPEC.isLoaded() && !GameplayConfig.GLACIATED_TOO_COLD.get())
                || !player.level().getBiome(player.blockPosition()).is(SkyBiomes.GLACIATED)) return;

        VanillaFreezing.tickBiomeCold(player);
    }
}
