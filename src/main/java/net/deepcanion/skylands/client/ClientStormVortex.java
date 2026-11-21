package net.deepcanion.skylands.client;

import net.deepcanion.skylands.registry.ModParticles;
import net.deepcanion.skylands.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Client-only storm visuals, called once per game tick by active StormBlockEntity.
 * animateTick is intentionally not used: it randomly samples blocks within a small
 * radius, producing bursts and preventing effects from appearing at long range.
 *
 * Emit exactly 16 cloud and 32 snowflake particles per 4-tick emission pulse.
 * This is deliberately spread across 4 ticks, rather than adding 48 heavy
 * particles every tick, to retain a dense vortex without exhausting the
 * vanilla particle cap or rendering thousands of giant cloud sprites.
 */
public final class ClientStormVortex {
    private static final double RANGE_SQUARED = 100.0 * 100.0;
    private static final int CLOUDS_PER_PULSE = 16;
    private static final int SNOWFLAKES_PER_PULSE = CLOUDS_PER_PULSE * 2;
    private static final int PULSE_INTERVAL = 4;

    private ClientStormVortex() { }

    public static void tick(Level level, BlockPos pos) {
        if (!level.isClientSide()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() != level) return;
        final double cx = pos.getX() + 0.5;
        final double cy = pos.getY() + 0.5;
        final double cz = pos.getZ() + 0.5;
        if (player.distanceToSqr(cx, cy, cz) > RANGE_SQUARED) return;

        long time = level.getGameTime();
        // Offset storm blocks against one another to avoid synchronized bursts.
        if (Math.floorMod(time + pos.asLong(), PULSE_INTERVAL) != 0) return;
        var rand = level.random;

        for (int i = 0; i < CLOUDS_PER_PULSE + SNOWFLAKES_PER_PULSE; i++) {
            boolean cloud = i < CLOUDS_PER_PULSE;
            double relativeY = rand.nextDouble() * 20.0 - 10.0;
            double heightFraction = (relativeY + 10.0) / 20.0;
            double radius = 2.5 + 7.5 * heightFraction;
            // Uniform angular distribution per component, with modest jitter.
            int index = cloud ? i : i - CLOUDS_PER_PULSE;
            int count = cloud ? CLOUDS_PER_PULSE : SNOWFLAKES_PER_PULSE;
            double angle = time * 0.11 + relativeY * 0.63
                    + index * (Math.PI * 2.0 / count) + rand.nextDouble() * 0.35;
            var type = cloud
                    ? (i % 2 == 0 ? ModParticles.STORM_CLOUD.get() : ModParticles.STORM_CLOUD_1.get())
                    : ModParticles.STORM_SNOWFLAKE.get();
            // Unlike addParticle, this bypasses vanilla's close-range visibility gate.
            // We already cap generation to 100 blocks from the local player above.
            level.addAlwaysVisibleParticle(type, false,
                    cx + Math.cos(angle) * radius,
                    cy + relativeY,
                    cz + Math.sin(angle) * radius,
                    cx, cy, cz);
        }
        // Keep the old ambient wind sound subtle and sporadic, without using
        // randomized per-block animateTick calls.
        if (rand.nextInt(250) == 0 && player.distanceToSqr(cx, cy, cz) < 32 * 32) {
            level.playLocalSound(cx, cy, cz, ModSounds.POWERFUL_WIND.get(),
                    SoundSource.BLOCKS, 0.3F, 1.2F, false);
        }
    }
}
