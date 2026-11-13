package net.deepcanion.skylands.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Editable from the main menu. Gameplay uses the server's copy; HUD state is sent explicitly. */
public final class GameplayConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue STAMINA_ENABLED;
    public static final ModConfigSpec.DoubleValue GLIDER_DURATION;
    public static final ModConfigSpec.DoubleValue STAMINA_REFILL;
    // Existing sky-biome hazards can be disabled independently by modpack creators.
    public static final ModConfigSpec.BooleanValue GLACIATED_TOO_COLD;
    public static final ModConfigSpec.BooleanValue LAVIC_TOO_HOT;
    public static final ModConfigSpec.BooleanValue ARID_DAY_TOO_HOT;
    public static final ModConfigSpec.BooleanValue ARID_NIGHT_TOO_COLD;
    // Extra Nether/Overworld hazards remain OFF by default.
    public static final ModConfigSpec.BooleanValue NETHER_TOO_HOT;
    public static final ModConfigSpec.BooleanValue OVERWORLD_COLD_FREEZING;
    public static final ModConfigSpec.BooleanValue GENERATE_FALLEN_ISLANDS;
    public static final ModConfigSpec.BooleanValue GENERATE_SURFACE_CAVES;
    public static final ModConfigSpec.IntValue SURFACE_CAVE_FREQUENCY;
    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_SKYLIGHT;
    public static final ModConfigSpec.BooleanValue LIMIT_SKY_WATER, LIMIT_SKY_MOLTEN_METAL;
    public static final ModConfigSpec.IntValue WATERFALL_SKY_START_Y;
    public static final ModConfigSpec.IntValue WATERFALL_MIN_LENGTH, WATERFALL_MAX_LENGTH;
    // Selection weights, NOT worldgen island structure densities. Relative to each other.
    public static final ModConfigSpec.IntValue[] SKY_BIOME_WEIGHTS = new ModConfigSpec.IntValue[6];
    public static final ModConfigSpec.IntValue SKY_SMALL, SKY_MEDIUM, SKY_LARGE;
    public static final ModConfigSpec.IntValue OUTSIDE_SMALL, OUTSIDE_MEDIUM, OUTSIDE_LARGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.translation("skylands.configuration.glider").push("glider");
        STAMINA_ENABLED = builder.comment("Enable glider stamina. Disable for unlimited gliding and no stamina HUD. Freefall is unchanged. In multiplayer the server's setting applies; your local setting is for your singleplayer worlds.")
                .translation("skylands.configuration.staminaEnabled").define("staminaEnabled", true);
        GLIDER_DURATION = builder.comment("Seconds to drain a full unenchanted glider stamina bar at 20 ticks/second. Default 12.5 preserves the old drain rate; the exhaustion cutoff ends usable flight slightly earlier. Lightweight still reduces drain at each level.")
                .translation("skylands.configuration.gliderDurationSeconds").defineInRange("gliderDurationSeconds", 12.5, 1.0, 3600.0);
        STAMINA_REFILL = builder.comment("Seconds to refill an empty stamina bar when not actively gliding. Default 6 preserves the old refill rate and exhaustion recovery threshold.")
                .translation("skylands.configuration.staminaRefillSeconds").defineInRange("staminaRefillSeconds", 6.0, 0.1, 600.0);
        builder.pop();
        builder.translation("skylands.configuration.temperatureHazards").push("temperatureHazards");
        GLACIATED_TOO_COLD = builder.comment(
                "Apply SkyLands Too Cold in Glaciated Sky. Enabled by default.",
                "Disabling this does not prevent ordinary vanilla powdered-snow freezing.")
                .translation("skylands.configuration.glaciatedTooCold").define("glaciatedTooCold", true);
        LAVIC_TOO_HOT = builder.comment(
                "Apply SkyLands Too Hot in Lavic Sky. Enabled by default.")
                .translation("skylands.configuration.lavicTooHot").define("lavicTooHot", true);
        ARID_DAY_TOO_HOT = builder.comment(
                "Apply SkyLands Too Hot during clear days in Arid Sky. Enabled by default.",
                "Rain and thunderstorms still prevent daytime Arid heat exposure.")
                .translation("skylands.configuration.aridDayTooHot").define("aridDayTooHot", true);
        ARID_NIGHT_TOO_COLD = builder.comment(
                "Apply SkyLands Too Cold during cold Arid Sky nights. Enabled by default.")
                .translation("skylands.configuration.aridNightTooCold").define("aridNightTooCold", true);
        NETHER_TOO_HOT = builder.comment(
                "Apply SkyLands Too Hot to players in the Nether. Disabled by default.",
                "Uses the existing 14-second buildup, Cooling protection, and temperature damage.",
                "Does not add fire or lava immunity. Server setting controls gameplay in multiplayer.")
                .translation("skylands.configuration.netherTooHot").define("netherTooHot", false);
        OVERWORLD_COLD_FREEZING = builder.comment(
                "Apply SkyLands biome freezing in naturally cold/snowy Overworld biomes. Disabled by default.",
                "Detects cold biomes by base temperature (<=0.15) and the skylands:freezing_biomes biome tag.",
                "Does not replace or double-apply vanilla powdered snow freezing.")
                .translation("skylands.configuration.overworldColdFreezing")
                .define("overworldColdFreezing", false);
        builder.pop();
        builder.translation("skylands.configuration.islandDensity").push("islandDensity");
        builder.translation("skylands.configuration.skyBiomes").push("skyBiomes");
        SKY_SMALL = density(builder, "small", "skySmallDensity");
        SKY_MEDIUM = density(builder, "medium", "skyMediumDensity");
        SKY_LARGE = density(builder, "large", "skyLargeDensity");
        builder.pop();
        builder.translation("skylands.configuration.otherBiomes").push("otherBiomes");
        OUTSIDE_SMALL = density(builder, "small", "outsideSmallDensity");
        OUTSIDE_MEDIUM = density(builder, "medium", "outsideMediumDensity");
        OUTSIDE_LARGE = density(builder, "large", "outsideLargeDensity");
        builder.pop(2);
        builder.translation("skylands.configuration.biomeSelection").push("biomeSelection");
        String[] biomeIds = {"glaciated", "lavic", "swamp", "arid", "cherry", "golden"};
        for (int i = 0; i < biomeIds.length; i++) {
            SKY_BIOME_WEIGHTS[i] = builder.comment(
                    "Relative selection weight: 100=baseline, 200=double, 0=disabled. Golden Sky's baseline is internally halved (200 restores its former weight).",
                    "Changes the MIX of sky biome types within the existing Lithostitched sky-region sites, NOT the total number or spacing of sky regions.",
                    "World/server restart required; existing chunks do not change.")
                    .translation("skylands.configuration.biomeWeight." + biomeIds[i])
                    .worldRestart().defineInRange(biomeIds[i], 100, 0, 300);
        }
        builder.pop();
        builder.translation("skylands.configuration.groundWorldgen").push("groundWorldgen");
        GENERATE_FALLEN_ISLANDS = builder.comment("Allow very rare fallen SkyLands islands to generate on ordinary Overworld surfaces. Enabled by default; disable to keep surface terrain unchanged. Requires a world/server restart; affects newly generated chunks only.")
                .translation("skylands.configuration.generateFallenIslands").worldRestart().define("generateFallenIslands", true);
        builder.pop();
        builder.translation("skylands.configuration.surfaceCaves").push("surfaceCaves");
        GENERATE_SURFACE_CAVES = builder.comment(
                "Generate occasional natural cave entrances, hillside tunnels and rare rock arches in floating islands.",
                "Site-seeded density carving; no extra post-generation block carving pass.",
                "Disable to restore the original Dev31 island cave geometry. Requires a world/server restart.")
                .translation("skylands.configuration.surfaceCavesEnabled")
                .worldRestart().define("enabled", true);
        SURFACE_CAVE_FREQUENCY = builder.comment(
                "Frequency relative to normal: 0 disables entrances, 100 is default, 200 is approximately twice as likely.",
                "Capped at one entrance group per normal island; 300 is the maximum.",
                "Changes affect newly generated chunks only. Restart after changing.")
                .translation("skylands.configuration.surfaceCavesFrequency")
                .worldRestart().defineInRange("frequency", 100, 0, 300);
        builder.pop();
        // Preserve the existing TOML section and key so disabled worlds stay disabled.
        // The obsolete mode selector is intentionally not registered.
        builder.translation("skylands.configuration.skylightExperiment").push("skylightExperiment");
        EXPERIMENTAL_SKYLIGHT = builder.comment(
                "Enable split skylight beneath high SkyLands islands (default: true).",
                "When enabled, upper islands remain opaque and caves remain shaded,",
                "while an additional sky-light source restores daylight beneath Y=240.",
                "Disable for entirely vanilla skylight. A world restart is required;",
                "previously saved lighting may need relighting after switching modes.")
                .translation("skylands.configuration.experimentalSkylight")
                .worldRestart().define("experimentalSkylight", true);
        builder.pop();
        builder.translation("skylands.configuration.skyWaterfalls").push("skyWaterfalls");
        LIMIT_SKY_WATER = builder.comment(
                "Limit long, free-falling vanilla-water waterfalls that begin high above the Overworld.",
                "No replacement water is registered. Natural water, waterlogging and shader textures remain vanilla.",
                "WARNING: player-made waterfalls on extremely tall mountains may also be limited.")
                .translation("skylands.configuration.limitSkyWater").define("limitSkyWater", true);
        LIMIT_SKY_MOLTEN_METAL = builder.comment(
                "Limit long, free-falling SkyLands molten-metal waterfalls by the same mechanism.")
                .translation("skylands.configuration.limitSkyMoltenMetal").define("limitSkyMoltenMetal", true);
        WATERFALL_SKY_START_Y = builder.comment(
                "Only restrict columns whose top falling-liquid block is at or above this Y level.",
                "This is NOT a horizontal cutoff plane. Low-altitude vanilla waterfalls remain unrestricted.")
                .translation("skylands.configuration.waterfallSkyStartY")
                .defineInRange("skyStartY", 240, -64, 1024);
        WATERFALL_MIN_LENGTH = builder.comment(
                "Minimum number of consecutive falling-liquid blocks per high-sky waterfall.",
                "A deterministic X/Z hash chooses a length between this and maxFallingLength.")
                .translation("skylands.configuration.waterfallMinLength")
                .defineInRange("minFallingLength", 30, 4, 128);
        WATERFALL_MAX_LENGTH = builder.comment(
                "Maximum number of consecutive falling-liquid blocks per high-sky waterfall.",
                "When lower than minFallingLength, the two bounds are sorted automatically.")
                .translation("skylands.configuration.waterfallMaxLength")
                .defineInRange("maxFallingLength", 40, 4, 128);
        builder.pop();
        SPEC = builder.build();
    }

    private static ModConfigSpec.IntValue density(ModConfigSpec.Builder builder, String name, String translation) {
        return builder.comment("Island/structure frequency relative to normal: 100 = default, 50 = roughly half, 200 = roughly double, 300 = roughly triple, 0 = disabled. Does not change island size, biome coverage, vegetation, ores or existing chunks. Large structures generate as a whole. Restart the world/server after changing; only newly generated chunks are affected.")
                .translation("skylands.configuration." + translation).worldRestart().defineInRange(name, 100, 0, 300);
    }

    private GameplayConfig() { }
}
