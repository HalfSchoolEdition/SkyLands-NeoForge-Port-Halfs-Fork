package net.deepcanion.skylands.effect;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.registry.ModEffects;
import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Optional server-side temperature adapters. Does not link against optional mod classes.
 * Selecting an adapter suppresses only the corresponding SkyLands hazard;
 * it cannot and does not disable temperature logic inside third-party mods.
 *
 * Priority (per heat/cold direction): Cold Sweat > Legendary Survival Overhaul
 * > Scorchful (heat) / Frostiful (cold) > SkyLands fallback.
 */
public final class TemperatureCompatibility {
    public enum Mode { SKYLANDS, COLD_SWEAT, LSO, THERMOO }

    private static final ResourceLocation HEAT_ENV = SkyLands.id("temperature/heat_environment");
    private static final ResourceLocation COLD_ENV = SkyLands.id("temperature/cold_environment");
    private static final ResourceLocation HEAT_HELMET = SkyLands.id("temperature/heat_helmet");
    private static final ResourceLocation COLD_HELMET = SkyLands.id("temperature/cold_helmet");
    private static final ResourceLocation HEAT_POTION = SkyLands.id("temperature/heat_potion");
    private static final ResourceLocation COLD_POTION = SkyLands.id("temperature/cold_potion");
    private static final ResourceLocation HEAT_CACTUS = SkyLands.id("temperature/heat_cactus");
    private static final ResourceLocation COLD_CACTUS = SkyLands.id("temperature/cold_cactus");

    private static boolean warnedColdSweat, warnedThermoo, warnedLso, warnedLsoAcceleration;
    private static Method thermooAddTemperature, thermooGetTemperature;
    private static Method coldSweatAdd, coldSweatGet, lsoWorldTemperature;
    private static Method lsoGetAttachment, lsoGetTarget, lsoGetCurrent, lsoSetTarget, lsoAddTemperature;
    private static Field lsoMinModification, lsoMaxModification;
    private static Object coldSweatCore;
    private TemperatureCompatibility() { }

    private static boolean setting(ModConfigSpec.BooleanValue setting) {
        return !GameplayConfig.SPEC.isLoaded() || setting.get();
    }

    public static Mode heatMode() {
        if (ModList.get().isLoaded("cold_sweat") && setting(GameplayConfig.COLD_SWEAT_INTEGRATION))
            return Mode.COLD_SWEAT;
        if (ModList.get().isLoaded("legendarysurvivaloverhaul") && setting(GameplayConfig.LSO_INTEGRATION))
            return Mode.LSO;
        if (ModList.get().isLoaded("scorchful") && ModList.get().isLoaded("thermoo")
                && setting(GameplayConfig.SCORCHFUL_INTEGRATION)) return Mode.THERMOO;
        return Mode.SKYLANDS;
    }

    public static Mode coldMode() {
        if (ModList.get().isLoaded("cold_sweat") && setting(GameplayConfig.COLD_SWEAT_INTEGRATION))
            return Mode.COLD_SWEAT;
        if (ModList.get().isLoaded("legendarysurvivaloverhaul") && setting(GameplayConfig.LSO_INTEGRATION))
            return Mode.LSO;
        if (ModList.get().isLoaded("frostiful") && ModList.get().isLoaded("thermoo")
                && setting(GameplayConfig.FROSTIFUL_INTEGRATION)) return Mode.THERMOO;
        return Mode.SKYLANDS;
    }

    public static boolean externalHeat() { return heatMode() != Mode.SKYLANDS; }
    public static boolean externalCold() { return coldMode() != Mode.SKYLANDS; }

    /** The local client uses the server-synced choice rather than guessing from its own config. */
    public static boolean externalHeat(Level level, Player player) {
        if (level.isClientSide && player != null
                && player.getPersistentData().contains(TemperatureSettingsPayload.CLIENT_EXTERNAL_HEAT))
            return player.getPersistentData().getBoolean(TemperatureSettingsPayload.CLIENT_EXTERNAL_HEAT);
        return externalHeat();
    }

    public static boolean helmetsEnabled(LivingEntity entity) {
        if (entity.level().isClientSide && entity instanceof Player player
                && player.getPersistentData().contains(TemperatureSettingsPayload.CLIENT_HELMETS_ENABLED))
            return player.getPersistentData().getBoolean(TemperatureSettingsPayload.CLIENT_HELMETS_ENABLED);
        return setting(GameplayConfig.TEMPERATURE_HELMETS_ENABLED);
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (player.isSpectator() || player.isCreative()) {
            clearAttributes(player);
            return;
        }
        Mode heat = heatMode();
        Mode cold = coldMode();
        if (heat == Mode.SKYLANDS && cold == Mode.SKYLANDS) {
            // Preserve the original per-player cost when no adapter is active.
            // Remove leftovers if a server administrator has toggled integration off.
            if (ModList.get().isLoaded("thermoo") || ModList.get().isLoaded("legendarysurvivaloverhaul"))
                clearAttributes(player);
            return;
        }
        boolean hotSky = player.level().getBiome(player.blockPosition()).is(SkyBiomes.LAVIC)
                || player.level().getBiome(player.blockPosition()).is(SkyBiomes.ARID);
        boolean coldSky = player.level().getBiome(player.blockPosition()).is(SkyBiomes.GLACIATED)
                || player.level().getBiome(player.blockPosition()).is(SkyBiomes.ARID);
        long dayTime = player.level().getDayTime();
        boolean heatExposure = hotSky && TemperatureHazards.skyHotCandidate(
                player.level(), player.blockPosition(), dayTime, player);
        boolean coldExposure = coldSky && (
                (player.level().getBiome(player.blockPosition()).is(SkyBiomes.GLACIATED)
                        && setting(GameplayConfig.GLACIATED_TOO_COLD))
                || TemperatureHazards.cold(player.level(), player.blockPosition(), dayTime));

        // Preserve the source bush's open/closed cycle and original protection radius.
        boolean coolCactus = heatExposure && SkyTemperatures.nearProtectiveCactus(
                player.level(), player.blockPosition(), false, dayTime);
        boolean warmCactus = coldExposure && !player.level().getBiome(player.blockPosition()).is(SkyBiomes.GLACIATED)
                && SkyTemperatures.nearProtectiveCactus(player.level(), player.blockPosition(), true, dayTime);
        boolean helmetHeat = TemperatureEquipment.preventsOverheating(player);
        boolean helmetCold = TemperatureEquipment.preventsFreezing(player);
        boolean potionHeat = player.hasEffect(ModEffects.COOLING);
        boolean potionCold = player.hasEffect(ModEffects.WARMTH);
        boolean protectsHeat = helmetHeat || potionHeat || coolCactus;
        boolean protectsCold = helmetCold || potionCold || warmCactus;

        if (heat == Mode.COLD_SWEAT || cold == Mode.COLD_SWEAT) {
            // Cold Sweat attaches WORLD-temperature modifiers when these effects
            // are added. Amplifier 0 was far too weak for high-island climates;
            // keep the native icons hidden and also enforce directional safety.
            if (cold == Mode.COLD_SWEAT && protectsCold) nativeEffect(player, "cold_sweat:warmth", 3);
            if (heat == Mode.COLD_SWEAT && protectsHeat) nativeEffect(player, "cold_sweat:frigidness", 3);
        }
        if (heat == Mode.LSO || cold == Mode.LSO) {
            if (cold == Mode.LSO && protectsCold) nativeEffect(player, "legendarysurvivaloverhaul:cold_resistance", 3);
            if (heat == Mode.LSO && protectsHeat) nativeEffect(player, "legendarysurvivaloverhaul:heat_resistance", 3);
            if (cold == Mode.LSO && potionCold) nativeEffect(player, "legendarysurvivaloverhaul:hot_drink", 3);
            if (heat == Mode.LSO && potionHeat) nativeEffect(player, "legendarysurvivaloverhaul:cold_drink", 3);
        }

        // COLD SWEAT: fixed WORLD offsets instead of repeatedly adding to CORE.
        // The old CORE += 0.1 every second caused an unwanted, permanent drift.
        boolean lavic = player.level().getBiome(player.blockPosition()).is(SkyBiomes.LAVIC);
        boolean glaciated = player.level().getBiome(player.blockPosition()).is(SkyBiomes.GLACIATED);
        // Cold Sweat's world temperature is measured in MC units (1 MC = 45 F
        // of differential). The old +/-0.6 Arid offset shifted its thermometer
        // only ~27 F, leaving days mild and nights barely below its comfort
        // range at high altitudes. Make Arid genuinely extreme while leaving
        // the confirmed-good Lavic and Glaciated offsets unchanged.
        attribute(player, "cold_sweat:world_temperature", HEAT_ENV,
                heat == Mode.COLD_SWEAT && heatExposure && !protectsHeat ? (lavic ? 0.40 : 1.50) : 0);
        attribute(player, "cold_sweat:world_temperature", COLD_ENV,
                cold == Mode.COLD_SWEAT && coldExposure && !protectsCold ? (glaciated ? -0.40 : -1.70) : 0);
        // Protection must actually stop damage and reverse temperature buildup,
        // not just display a Warmth/Frigidness icon under severe ambient heat.
        attribute(player, "cold_sweat:cold_resistance", COLD_HELMET,
                cold == Mode.COLD_SWEAT && protectsCold ? 1.0 : 0);
        attribute(player, "cold_sweat:heat_resistance", HEAT_HELMET,
                heat == Mode.COLD_SWEAT && protectsHeat ? 1.0 : 0);
        attribute(player, "cold_sweat:base_temperature", COLD_POTION,
                cold == Mode.COLD_SWEAT && protectsCold ? 14.0 : 0);
        attribute(player, "cold_sweat:base_temperature", HEAT_POTION,
                heat == Mode.COLD_SWEAT && protectsHeat ? -14.0 : 0);
        if (heat == Mode.COLD_SWEAT || cold == Mode.COLD_SWEAT)
            adjustColdSweatCore(player, heat == Mode.COLD_SWEAT && heatExposure,
                    cold == Mode.COLD_SWEAT && coldExposure,
                    heat == Mode.COLD_SWEAT && protectsHeat,
                    cold == Mode.COLD_SWEAT && protectsCold);

        // THERMOO/FROSTIFUL/SCORCHFUL: an explicit bounded change to the real
        // Thermoo temperature, not merely a HUD status effect. Restrict rapid
        // exposure to our Sky biomes; normal Thermoo regions remain unchanged.
        if (heat == Mode.THERMOO && heatExposure && !protectsHeat)
            addThermooTemperature(player, lavic ? 220 : 160);
        if (cold == Mode.THERMOO && coldExposure && !protectsCold)
            addThermooTemperature(player, glaciated ? -220 : -160);
        if ((heat == Mode.THERMOO && protectsHeat) || (cold == Mode.THERMOO && protectsCold))
            recoverThermooTemperature(player, heat == Mode.THERMOO && protectsHeat,
                    cold == Mode.THERMOO && protectsCold);
        attribute(player, "thermoo:generic.heat_resistance", HEAT_HELMET,
                heat == Mode.THERMOO && helmetHeat ? 10 : 0);
        attribute(player, "thermoo:generic.frost_resistance", COLD_HELMET,
                cold == Mode.THERMOO && helmetCold ? 10 : 0);
        attribute(player, "thermoo:generic.heat_resistance", HEAT_POTION,
                heat == Mode.THERMOO && potionHeat ? 10 : 0);
        attribute(player, "thermoo:generic.frost_resistance", COLD_POTION,
                cold == Mode.THERMOO && potionCold ? 10 : 0);
        attribute(player, "thermoo:generic.environment_heat_resistance", HEAT_CACTUS,
                heat == Mode.THERMOO && coolCactus ? 1 : 0);
        attribute(player, "thermoo:generic.environment_frost_resistance", COLD_CACTUS,
                cold == Mode.THERMOO && warmCactus ? 1 : 0);

        // LEGENDARY SURVIVAL OVERHAUL: in the Overworld, its default altitude
        // modifier is -6 C per 64 blocks above sea level. At Y=320 this is
        // roughly -24 C, so a simple +6 Lavic boost left every sky island
        // freezing. Compensate the complete environment before setting the
        // Sky-specific target; no per-tick accumulation or global config edits.
        double heating = 0, cooling = 0;
        if (heat == Mode.LSO || cold == Mode.LSO) {
            double environment = lsoWorldTemperature(player);
            if (!Double.isNaN(environment) && isSkyBiome(player)) {
                double target = lsoSkyTarget(player, lavic, glaciated);
                double difference = Math.max(-70, Math.min(70, target - environment));
                heating = Math.max(0, difference);
                cooling = Math.min(0, difference);
            }
        }
        attribute(player, "legendarysurvivaloverhaul:heating_temperature", HEAT_ENV, heating);
        attribute(player, "legendarysurvivaloverhaul:cooling_temperature", COLD_ENV, cooling);
        attribute(player, "legendarysurvivaloverhaul:heat_resistance", HEAT_HELMET,
                heat == Mode.LSO && helmetHeat ? 23 : 0);
        attribute(player, "legendarysurvivaloverhaul:cold_resistance", COLD_HELMET,
                cold == Mode.LSO && helmetCold ? 23 : 0);
        attribute(player, "legendarysurvivaloverhaul:heat_resistance", HEAT_POTION,
                heat == Mode.LSO && potionHeat ? 26 : 0);
        attribute(player, "legendarysurvivaloverhaul:cold_resistance", COLD_POTION,
                cold == Mode.LSO && potionCold ? 26 : 0);
        attribute(player, "legendarysurvivaloverhaul:heat_resistance", HEAT_CACTUS,
                heat == Mode.LSO && coolCactus ? 20 : 0);
        attribute(player, "legendarysurvivaloverhaul:cold_resistance", COLD_CACTUS,
                cold == Mode.LSO && warmCactus ? 20 : 0);
        // LSO normally takes one incremental body-temperature step every 20
        // ticks (default). Take one matching additional step in SkyLands biomes
        // for approximately 2x convergence, without changing global LSO config.
        if ((heat == Mode.LSO || cold == Mode.LSO) && isSkyBiome(player))
            accelerateLsoTemperature(player, player.level().getBiome(player.blockPosition()).is(SkyBiomes.ARID));
    }

    private static boolean isSkyBiome(ServerPlayer player) {
        var biome = player.level().getBiome(player.blockPosition());
        return biome.is(SkyBiomes.LAVIC) || biome.is(SkyBiomes.GLACIATED)
                || biome.is(SkyBiomes.ARID) || biome.is(SkyBiomes.GOLDEN)
                || biome.is(SkyBiomes.CHERRY) || biome.is(SkyBiomes.SWAMP);
    }

    private static double lsoSkyTarget(ServerPlayer player, boolean lavic, boolean glaciated) {
        var biome = player.level().getBiome(player.blockPosition());
        long time = player.level().getDayTime();
        if (lavic && setting(GameplayConfig.LAVIC_TOO_HOT)) return 38.0;
        if (glaciated && setting(GameplayConfig.GLACIATED_TOO_COLD)) return 6.0;
        if (biome.is(SkyBiomes.ARID)) {
            if (TemperatureHazards.skyHotCandidate(player.level(), player.blockPosition(), time, player))
                return 36.0;
            if (TemperatureHazards.cold(player.level(), player.blockPosition(), time))
                return 7.0;
        }
        return 20.0; // compensate high-altitude freezing outside hazard biomes
    }

    private static double lsoWorldTemperature(ServerPlayer player) {
        try {
            if (lsoWorldTemperature == null) {
                Class<?> api = Class.forName("sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil");
                lsoWorldTemperature = api.getMethod("getWorldTemperature", Level.class, BlockPos.class);
            }
            return ((Number) lsoWorldTemperature.invoke(null, player.level(), player.blockPosition())).doubleValue();
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedLso) {
                SkyLands.LOGGER.warn("SkyLands could not read LSO world temperature", exception);
                warnedLso = true;
            }
            return Double.NaN;
        }
    }

    /** Add one extra copy of LSO's own default per-second temperature step.
     * Read its configurable min/max step via reflection so alternate mod
     * settings retain the same approximate acceleration factor.
     */
    private static void accelerateLsoTemperature(ServerPlayer player, boolean arid) {
        try {
            if (lsoGetAttachment == null) {
                Class<?> lookup = Class.forName("sfiomn.legendarysurvivaloverhaul.util.AttachmentUtil");
                Class<?> attachment = Class.forName("sfiomn.legendarysurvivaloverhaul.common.attachments.temperature.TemperatureAttachment");
                Class<?> baked = Class.forName("sfiomn.legendarysurvivaloverhaul.config.Config$Baked");
                lsoGetAttachment = lookup.getMethod("getTempAttachment", Player.class);
                lsoGetTarget = attachment.getMethod("getTargetTemperatureLevel");
                lsoGetCurrent = attachment.getMethod("getTemperatureLevel");
                lsoSetTarget = attachment.getMethod("setTargetTemperatureLevel", float.class);
                lsoAddTemperature = attachment.getMethod("addTemperatureLevel", float.class);
                lsoMinModification = baked.getField("minTemperatureModification");
                lsoMaxModification = baked.getField("maxTemperatureModification");
            }
            Object attachment = lsoGetAttachment.invoke(null, player);
            double target = ((Number) lsoGetTarget.invoke(attachment)).doubleValue();
            if (arid) {
                // LSO calculates temperature from all modifiers, including a
                // severe elevation penalty. The sky-environment attribute
                // alone has not reliably overridden that penalty in Arid Sky.
                // Explicitly steer its existing attachment to the local sky
                // target, preserving LSO's own HUD, effects and damage rules.
                target = lsoSkyTarget(player, false, false);
                lsoSetTarget.invoke(attachment, (float) target);
            }
            double current = ((Number) lsoGetCurrent.invoke(attachment)).doubleValue();
            double distance = Math.abs(target - current);
            if (distance < 0.001 || !Double.isFinite(distance)) return;
            double min = ((Number) lsoMinModification.get(null)).doubleValue();
            double max = ((Number) lsoMaxModification.get(null)).doubleValue();
            double naturalStep = Math.min(distance, min + Math.min(distance, 40.0) * (max - min) / 40.0);
            if (!Double.isFinite(naturalStep) || naturalStep <= 0) return;
            // LSO itself takes one step per configured interval. Add one more
            // for ~2x recovery/exposure in SkyLands. Arid gets a second small
            // corrective step because LSO may recompute its own altitude-based
            // target later in this same tick (event listener ordering).
            double correction = Math.min(distance, naturalStep * (arid ? 2.0 : 1.0));
            float extra = (float) Math.copySign(correction, target - current);
            lsoAddTemperature.invoke(attachment, extra);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedLsoAcceleration) {
                SkyLands.LOGGER.warn("SkyLands could not accelerate LSO body temperature; keeping its normal update rate", exception);
                warnedLsoAcceleration = true;
            }
        }
    }

    private static void recoverThermooTemperature(ServerPlayer player, boolean heat, boolean cold) {
        try {
            if (thermooGetTemperature == null) {
                Class<?> api = Class.forName("com.github.thedeathlycow.thermoo.api.temperature.TemperatureAware");
                thermooGetTemperature = api.getMethod("thermoo$getTemperature");
            }
            int current = ((Number) thermooGetTemperature.invoke(player)).intValue();
            if (heat && current > 0) addThermooTemperature(player, -Math.min(current, 340));
            else if (cold && current < 0) addThermooTemperature(player, Math.min(-current, 340));
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedThermoo) {
                SkyLands.LOGGER.warn("SkyLands could not read Thermoo temperature", exception);
                warnedThermoo = true;
            }
        }
    }

    private static void adjustColdSweatCore(ServerPlayer player, boolean heat, boolean cold,
                                           boolean protectHeat, boolean protectCold) {
        try {
            if (coldSweatAdd == null || coldSweatGet == null) {
                Class<?> temperature = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
                Class<?> trait = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
                coldSweatCore = trait.getField("CORE").get(null);
                coldSweatAdd = temperature.getMethod("add", LivingEntity.class, trait, double.class);
                coldSweatGet = temperature.getMethod("get", LivingEntity.class, trait);
            }
            double core = ((Number) coldSweatGet.invoke(null, player, coldSweatCore)).doubleValue();
            double impulse = 0;
            if (protectHeat && core > 0) impulse = -Math.min(5.0, core);
            else if (protectCold && core < 0) impulse = Math.min(5.0, -core);
            else if (!protectHeat && heat) impulse = 2.5;
            else if (!protectCold && cold) impulse = -2.5;
            if (impulse != 0) coldSweatAdd.invoke(null, player, coldSweatCore, impulse);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedColdSweat) {
                SkyLands.LOGGER.warn("SkyLands could not access Cold Sweat core temperature API", exception);
                warnedColdSweat = true;
            }
        }
    }

    private static void nativeEffect(ServerPlayer player, String effectId, int amplifier) {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effectId)).orElse(null);
        if (effect != null) {
            // Hide duplicate particle swirls/HUD icons: SkyLands' own beneficial
            // effect remains the visible source of protection.
            player.addEffect(new MobEffectInstance(effect, 50, amplifier, true, false, false));
        }
    }

    private static void attribute(ServerPlayer player, String attributeId, ResourceLocation modifierId, double amount) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attributeId)).orElse(null);
        if (holder == null) return;
        AttributeInstance instance = player.getAttribute(holder);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(modifierId);
        if (amount == 0) {
            if (current != null) instance.removeModifier(modifierId);
        } else if (current == null || current.amount() != amount) {
            if (current != null) instance.removeModifier(modifierId);
            instance.addTransientModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void clearAttributes(ServerPlayer player) {
        attribute(player, "legendarysurvivaloverhaul:heating_temperature", HEAT_ENV, 0);
        attribute(player, "legendarysurvivaloverhaul:cooling_temperature", COLD_ENV, 0);
        attribute(player, "thermoo:generic.heat_resistance", HEAT_HELMET, 0);
        attribute(player, "thermoo:generic.frost_resistance", COLD_HELMET, 0);
        attribute(player, "thermoo:generic.heat_resistance", HEAT_POTION, 0);
        attribute(player, "thermoo:generic.frost_resistance", COLD_POTION, 0);
        attribute(player, "thermoo:generic.environment_heat_resistance", HEAT_CACTUS, 0);
        attribute(player, "thermoo:generic.environment_frost_resistance", COLD_CACTUS, 0);
        attribute(player, "cold_sweat:world_temperature", HEAT_ENV, 0);
        attribute(player, "cold_sweat:world_temperature", COLD_ENV, 0);
        attribute(player, "cold_sweat:cold_resistance", COLD_HELMET, 0);
        attribute(player, "cold_sweat:heat_resistance", HEAT_HELMET, 0);
        attribute(player, "cold_sweat:base_temperature", COLD_POTION, 0);
        attribute(player, "cold_sweat:base_temperature", HEAT_POTION, 0);
        attribute(player, "legendarysurvivaloverhaul:heat_resistance", HEAT_HELMET, 0);
        attribute(player, "legendarysurvivaloverhaul:cold_resistance", COLD_HELMET, 0);
        attribute(player, "legendarysurvivaloverhaul:heat_resistance", HEAT_POTION, 0);
        attribute(player, "legendarysurvivaloverhaul:cold_resistance", COLD_POTION, 0);
        attribute(player, "legendarysurvivaloverhaul:heat_resistance", HEAT_CACTUS, 0);
        attribute(player, "legendarysurvivaloverhaul:cold_resistance", COLD_CACTUS, 0);
    }

    private static void addThermooTemperature(ServerPlayer player, int amount) {
        try {
            if (thermooAddTemperature == null)
                thermooAddTemperature = Class.forName("com.github.thedeathlycow.thermoo.api.temperature.TemperatureAware")
                        .getMethod("thermoo$addTemperature", int.class);
            thermooAddTemperature.invoke(player, amount);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedThermoo) {
                SkyLands.LOGGER.warn("SkyLands could not access Thermoo temperature API; skipping its biome offsets", exception);
                warnedThermoo = true;
            }
        }
    }

}
