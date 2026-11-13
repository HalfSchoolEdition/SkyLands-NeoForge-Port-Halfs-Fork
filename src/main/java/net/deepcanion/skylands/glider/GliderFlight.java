package net.deepcanion.skylands.glider;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.registry.ModBlocks;
import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The server owns stamina; the local client predicts only movement from synced state. */
public final class GliderFlight {
    public static final String STAMINA = "skylands_glider_stamina";
    public static final String EXHAUSTED = "skylands_glider_exhausted";
    public static final String ACTIVE = "skylands_glider_active";
    public static final String STAMINA_ENABLED = "skylands_glider_stamina_enabled";
    private static final ResourceKey<Enchantment> LIGHTWEIGHT = ResourceKey.create(Registries.ENCHANTMENT, SkyLands.id("lightweight"));
    private static boolean eligible(Player player) {
        return player.isAlive() && !player.onGround() && !player.isSpectator() && !player.getAbilities().flying
                && !player.isPassenger() && !player.isInWaterOrBubble() && !player.isInLava() && !player.isFallFlying()
                && player.getMainHandItem().is(ModItems.GLIDER.get());
    }
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        FreefallFlight.tick(player);
        var data = player.getPersistentData();
        if (player.level().isClientSide()) {
            if (player.isLocalPlayer() && data.getBoolean(ACTIVE) && eligible(player)) move(player);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        boolean initialized = data.contains(STAMINA);
        boolean modeInitialized = data.contains(STAMINA_ENABLED);
        boolean previousMode = data.getBoolean(STAMINA_ENABLED);
        float stamina = initialized ? Math.clamp(data.getFloat(STAMINA), 0, 6) : 6;
        float previousStamina = stamina;
        boolean previousExhaustion = data.getBoolean(EXHAUSTED);
        boolean exhausted = data.getBoolean(EXHAUSTED), previous = data.getBoolean(ACTIVE);
        boolean staminaEnabled = GameplayConfig.STAMINA_ENABLED.get();
        boolean active = eligible(player) && (!staminaEnabled || (!exhausted && stamina > 0.2F));
        if (!staminaEnabled) {
            stamina = 6;
            exhausted = false;
            if (active) move(player);
        } else if (active) {
            int level = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(LIGHTWEIGHT)
                    .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, player.getMainHandItem())).orElse(0);
            float durationScale = (float) (12.5 / GameplayConfig.GLIDER_DURATION.get());
            stamina = Math.max(0, stamina - (0.024F - 0.004F * Math.clamp(level, 0, 5)) * durationScale);
            move(player);
            if (stamina <= 0.2F) exhausted = true;
        } else {
            if (eligible(player) && stamina <= 0.2F) exhausted = true;
            stamina = Math.min(6, stamina + 0.05F * (float) (6.0 / GameplayConfig.STAMINA_REFILL.get()));
            if (stamina > 5) exhausted = false;
        }
        data.putFloat(STAMINA, stamina); data.putBoolean(EXHAUSTED, exhausted); data.putBoolean(ACTIVE, active);
        data.putBoolean(STAMINA_ENABLED, staminaEnabled);
        if (!initialized || !modeInitialized || previousMode != staminaEnabled || active != previous || exhausted != previousExhaustion || (previousStamina < 6 && stamina >= 6)
                || (player.tickCount % 5 == 0 && (active || stamina < 6)))
            PacketDistributor.sendToPlayer(serverPlayer, new GliderPayload(stamina, exhausted, active, staminaEnabled));
        if (previous && !active) player.hurtMarked = true;
        FlightAnimationState.tick(serverPlayer);
    }
    /** Force an initial authoritative mode update even when a saved bar is already full. */
    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().getPersistentData().remove(STAMINA_ENABLED);
    }
    private static void move(Player player) {
        boolean lift = false;
        for (int depth = 1; depth <= 4; depth++)
            if (player.level().getBlockState(player.blockPosition().below(depth)).is(ModBlocks.MECHANICAL_GEYSER.get())) { lift = true; break; }
        Vec3 motion = player.getDeltaMovement();
        // Native gravity stays enabled: no sticky no-gravity state on item changes or logout.
        double vertical = lift ? Math.max(motion.y, 0.12) : Math.max(motion.y, -0.04);
        player.setDeltaMovement(motion.x, vertical, motion.z);
        player.fallDistance = 0;
    }
    private GliderFlight() { }
}
