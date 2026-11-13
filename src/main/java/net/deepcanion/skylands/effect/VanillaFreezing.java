package net.deepcanion.skylands.effect;

import net.deepcanion.skylands.registry.ModEffects;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

/** Applies vanilla powder-snow freezing without a SkyLands MobEffect.
 * Vanilla controls the frost vignette, frozen hearts, damage, armour immunity
 * and subsequent thawing. Only the source of cold is custom.
 */
public final class VanillaFreezing {
    private VanillaFreezing() { }

    private static final int COLD_DAMAGE_GRACE = 40; // 2s after fully frozen
    private static final int COLD_DAMAGE_INTERVAL = 54; // one hit every 2.7 seconds (~1.5x faster than 4s)
    private static final Map<ServerPlayer, BiomeExposure> BIOME_EXPOSURE = new WeakHashMap<>();

    private static final class BiomeExposure {
        int lastTick = -1;
        int coldTicks;
        int fullFreezeTicks;
    }


    /** Add freezing ticks, capped at the target's vanilla freeze threshold. */
    public static void chill(LivingEntity entity, int ticks) {
        if (ticks <= 0 || entity.level().isClientSide() || !entity.isAlive()
                || !entity.canFreeze() || entity.hasEffect(ModEffects.WARMTH)
                || TemperatureEquipment.preventsFreezing(entity)) return;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return;

        entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze(), entity.getTicksFrozen() + ticks));
    }

    /** One tick of sky-biome cold. Vanilla's base tick already thaws by two
     * outside powdered snow; alternate +3 and +2 to achieve approximately
     * half a freeze tick of net buildup per game tick (~14s total).
     *
     * A missed tick (leaving the biome, finding warmth/cactus shelter or
     * switching to Creative) resets the damage grace timer. Powder snow keeps
     * its own vanilla damage and never receives a second damage hit here.
     */
    public static void tickBiomeCold(ServerPlayer player) {
        if (player.isInPowderSnow || !player.canFreeze()
                || player.isCreative() || player.isSpectator()
                || player.hasEffect(ModEffects.WARMTH)
                || TemperatureEquipment.preventsFreezing(player)) return;

        BiomeExposure state = BIOME_EXPOSURE.computeIfAbsent(player, ignored -> new BiomeExposure());
        if (state.lastTick == player.tickCount) return; // At most once per player tick
        if (state.lastTick != player.tickCount - 1) {
            state.coldTicks = 0;
            state.fullFreezeTicks = 0;
        }
        state.lastTick = player.tickCount;
        // Vanilla's -2 baseline is already applied earlier in this tick.
        chill(player, (state.coldTicks++ & 1) == 0 ? 3 : 2);
        if (player.isFullyFrozen()) {
            state.fullFreezeTicks++;
            if (player.tickCount % 20 == 0 || !player.hasEffect(ModEffects.TOO_COLD))
                player.addEffect(new MobEffectInstance(ModEffects.TOO_COLD, 45, 0, true, false, true));
        } else {
            state.fullFreezeTicks = 0;
            if (player.hasEffect(ModEffects.TOO_COLD)) player.removeEffect(ModEffects.TOO_COLD);
        }
        hurtFromBiomeCold(player);
    }

    /** Vanilla only inflicts freezing damage while actually inside powdered
     * snow. SkyLands must separately inflict vanilla-typed freezing damage
     * after two seconds of uninterrupted full biome exposure, every 2.7
     * seconds thereafter, respecting the vanilla gamerule and immunity.
     */
    public static void hurtFromBiomeCold(ServerPlayer player) {
        BiomeExposure state = BIOME_EXPOSURE.get(player);
        if (state == null || state.lastTick != player.tickCount
                || state.fullFreezeTicks < COLD_DAMAGE_GRACE
                || (state.fullFreezeTicks - COLD_DAMAGE_GRACE) % COLD_DAMAGE_INTERVAL != 0
                || !player.isFullyFrozen() || !player.canFreeze() || player.isInPowderSnow
                || player.isCreative() || player.isSpectator()
                || player.hasEffect(ModEffects.WARMTH)
                || TemperatureEquipment.preventsFreezing(player)
                || !player.level().getGameRules().getBoolean(GameRules.RULE_FREEZE_DAMAGE)) return;
        player.hurt(player.damageSources().freeze(), 2.0F);
    }
}
