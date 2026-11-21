package net.deepcanion.skylands.effect;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Shared biome rules for SkyLands hazards and opt-in vanilla-dimension hazards.
 * This does not force-load chunks; clients only use the synced server config
 * when evaluating the optional Nether visual overlay.
 */
public final class TemperatureHazards {
    private static final TagKey<Biome> EXTRA_COLD_BIOMES =
            TagKey.create(Registries.BIOME, SkyLands.id("freezing_biomes"));

    private TemperatureHazards() { }

    public static boolean hot(Level level, BlockPos pos, long dayTime, Player player) {
        if (TemperatureCompatibility.externalHeat(level, player)) return false;
        return skyHotCandidate(level, pos, dayTime, player);
    }

    /** Candidate native heat exposure, used by optional temperature adapters too. */
    public static boolean skyHotCandidate(Level level, BlockPos pos, long dayTime, Player player) {
        Holder<Biome> biome = level.getBiome(pos);
        return (biome.is(SkyBiomes.LAVIC)
                    && skyHeatEnabled(level, player, TemperatureSettingsPayload.CLIENT_LAVIC_HEAT,
                            GameplayConfig.LAVIC_TOO_HOT))
                || (biome.is(SkyBiomes.ARID) && SkyTemperatures.isDay(dayTime)
                        && !level.isRaining() && !level.isThundering()
                        && skyHeatEnabled(level, player, TemperatureSettingsPayload.CLIENT_ARID_DAY_HEAT,
                                GameplayConfig.ARID_DAY_TOO_HOT))
                || (Level.NETHER.equals(level.dimension()) && extraNetherHeat(level, player));
    }

    public static boolean cold(Level level, BlockPos pos, long dayTime) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(SkyBiomes.ARID) && SkyTemperatures.isNight(dayTime))
            return !GameplayConfig.SPEC.isLoaded() || GameplayConfig.ARID_NIGHT_TOO_COLD.get();
        if (!Level.OVERWORLD.equals(level.dimension()) || !GameplayConfig.SPEC.isLoaded()
                || !GameplayConfig.OVERWORLD_COLD_FREEZING.get()) return false;
        // Preserve sky-biome behavior: Glaciated is already handled by
        // GlaciatedBiomeCold, and other sky biomes must not change behavior.
        if (biome.is(SkyBiomes.GLACIATED) || biome.is(SkyBiomes.ARID)
                || biome.is(SkyBiomes.LAVIC) || biome.is(SkyBiomes.GOLDEN)
                || biome.is(SkyBiomes.CHERRY) || biome.is(SkyBiomes.SWAMP)) return false;
        // Minecraft's base-temperature snow threshold also catches suitably
        // cold modded biomes. Datapacks can tag any additional exceptions.
        return biome.value().getBaseTemperature() <= 0.15F || biome.is(EXTRA_COLD_BIOMES);
    }

    /** The client predicts heat overlays using server-authoritative toggles. */
    private static boolean skyHeatEnabled(Level level, Player player, String key,
                                          ModConfigSpec.BooleanValue setting) {
        if (level.isClientSide && player != null) {
            var data = player.getPersistentData();
            if (data.contains(key)) return data.getBoolean(key);
        }
        return !GameplayConfig.SPEC.isLoaded() || setting.get();
    }

    private static boolean extraNetherHeat(Level level, Player player) {
        if (level.isClientSide && player != null) {
            var data = player.getPersistentData();
            if (data.contains(TemperatureSettingsPayload.CLIENT_NETHER_HEAT))
                return data.getBoolean(TemperatureSettingsPayload.CLIENT_NETHER_HEAT);
        }
        return GameplayConfig.SPEC.isLoaded() && GameplayConfig.NETHER_TOO_HOT.get();
    }
}
