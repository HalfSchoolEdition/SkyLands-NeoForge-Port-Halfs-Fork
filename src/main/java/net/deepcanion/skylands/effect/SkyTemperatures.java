package net.deepcanion.skylands.effect;

import java.util.Map;
import java.util.WeakHashMap;
import net.deepcanion.skylands.block.SkyCactusSpeciesBlock;
import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.registry.ModEffects;
import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Environmental temperature exposure in Arid and Lavic Sky Terrain.
 * Real sky biome keys avoid exposing players on the Overworld below.
 * Both heat and vanilla freezing fade in rather than starting instantly.
 */
public final class SkyTemperatures {
    // Minecraft day clock: 0 dawn, 6000 noon, 12000 dusk, 18000 midnight.
    // Include /time set day (1000) and /time set night (13000), leaving brief
    // twilight grace before dawn and after sunset.
    private static final int DAY_START = 0, DAY_END = 12000;
    private static final int NIGHT_START = 13000, NIGHT_END = 23000;
    public static final int HEAT_THRESHOLD = 280; // ~14 seconds to full overheating
    private static final int HEAT_DAMAGE_GRACE = 40; // 2 seconds AFTER full exposure
    private static final int HEAT_DAMAGE_INTERVAL = 54; // one hit every 2.7 seconds (~1.5x faster than 4s)
    private static final Map<ServerPlayer, Exposure> EXPOSURE = new WeakHashMap<>();

    private SkyTemperatures() { }
    private static final class Exposure {
        int heat;
        int fullHeatTicks;
        boolean sheltered;
        boolean hot;
        boolean cold;
    }

    public static boolean isDay(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= DAY_START && time < DAY_END;
    }
    public static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Even creative players need authoritative tooltip/config updates.
        TemperatureSettingsPayload.syncIfChanged(player);
        if (player.isCreative() || player.isSpectator()) {
            player.removeEffect(ModEffects.TOO_HOT);
            player.removeEffect(ModEffects.TOO_COLD);
            EXPOSURE.remove(player);
            return;
        }
        Exposure exposure = EXPOSURE.computeIfAbsent(player, unused -> new Exposure());
        boolean externalHeat = TemperatureCompatibility.externalHeat();
        boolean externalCold = TemperatureCompatibility.externalCold();
        // Stop obsolete HUD effects when another temperature system takes over.
        if (externalHeat) player.removeEffect(ModEffects.TOO_HOT);
        if (externalCold) player.removeEffect(ModEffects.TOO_COLD);
        if (player.tickCount % 20 == 0 || player.tickCount <= 1) {
            boolean arid = player.level().getBiome(player.blockPosition()).is(SkyBiomes.ARID);
            boolean lavic = player.level().getBiome(player.blockPosition()).is(SkyBiomes.LAVIC);
            long time = player.level().getDayTime();
            exposure.hot = TemperatureHazards.hot(player.level(), player.blockPosition(), time, player);
            exposure.cold = TemperatureHazards.cold(player.level(), player.blockPosition(), time);
            // Only SkyLands cacti shelter players in the original Arid/Lavic
            // biomes; optional vanilla-dimension hazards don't scan for them.
            exposure.sheltered = (arid || lavic)
                    && ((!externalHeat && exposure.hot) || (!externalCold && exposure.cold))
                    && nearProtectiveCactus(player.level(), player.blockPosition(),
                            exposure.cold, time);
            if (arid && exposure.cold && player.level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.1,
                        player.getZ(), 3, 2.4, 1.0, 2.4, 0.003);
        }
        boolean inGlaciated = player.level().getBiome(player.blockPosition()).is(SkyBiomes.GLACIATED);
        if (!externalCold && player.hasEffect(ModEffects.TOO_COLD)
                && ((inGlaciated && GameplayConfig.SPEC.isLoaded() && !GameplayConfig.GLACIATED_TOO_COLD.get())
                    || (!inGlaciated && (!exposure.cold || exposure.sheltered))
                    || player.hasEffect(ModEffects.WARMTH)
                    || TemperatureEquipment.preventsFreezing(player)
                    || player.isInPowderSnow)) {
            player.removeEffect(ModEffects.TOO_COLD);
        }
        boolean hot = !externalHeat && exposure.hot && !exposure.sheltered && !player.hasEffect(ModEffects.COOLING)
                && !TemperatureEquipment.preventsOverheating(player);
        boolean cold = !externalCold && exposure.cold && !exposure.sheltered
                && !TemperatureEquipment.preventsFreezing(player);
        if (cold && !player.isInPowderSnow) {
            VanillaFreezing.tickBiomeCold(player); // gradual ~14s freezing + 2s damage grace
        }
        if (hot) exposure.heat = Math.min(HEAT_THRESHOLD, exposure.heat
                + (player.level().getBiome(player.blockPosition()).is(SkyBiomes.LAVIC) ? 2 : 1));
        else exposure.heat = Math.max(0, exposure.heat - 3);
        if (hot && exposure.heat >= HEAT_THRESHOLD) {
            // Count only *continuous* time at full heat. Shelter, departure,
            // or neutral twilight resets the grace period immediately.
            exposure.fullHeatTicks++;
            // No potion swirls, but retain the HUD effect icon at full heat.
            if (player.tickCount % 20 == 0 || !player.hasEffect(ModEffects.TOO_HOT))
                player.addEffect(new MobEffectInstance(ModEffects.TOO_HOT, 45, 0, true, false, true));
            if (exposure.fullHeatTicks >= HEAT_DAMAGE_GRACE
                    && (exposure.fullHeatTicks - HEAT_DAMAGE_GRACE) % HEAT_DAMAGE_INTERVAL == 0)
                player.hurt(player.damageSources().onFire(), 2.0F);
        } else {
            exposure.fullHeatTicks = 0;
            if (player.hasEffect(ModEffects.TOO_HOT)) player.removeEffect(ModEffects.TOO_HOT);
        }
    }

    /** Shared by the server hazard and local heat-overlay prediction.
     * Scanned only once per second, never once per rendered frame. */
    public static boolean nearProtectiveCactus(net.minecraft.world.level.LevelReader level,
                                                BlockPos origin, boolean needWarmth, long dayTime) {
        for (int y = -4; y <= 4; y++) for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                if (x * x + z * z > 36) continue;
                BlockPos at = origin.offset(x, y, z);
                BlockState state = level.getBlockState(at);
                // Allow shelter near the base of a tall cactus, but require
                // an actual open top rather than a disconnected trunk.
                if (state.getBlock() instanceof SkyCactusSpeciesBlock cactus
                        && cactus.protectsCold() == needWarmth
                        && cactus.opensAt(dayTime)
                        && cactus.hasOpenTop(level, at)) return true;
            }
        return false;
    }
}
