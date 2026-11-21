package net.deepcanion.skylands.block;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/** Block jump factor is per block ID, not per blockstate; restore the old powered factor via event. */
public final class SkyLauncherJump {
    private SkyLauncherJump() { }
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        // Vanilla jumpFromGround has already applied the unpowered factor (3).
        var state = entity.level().getBlockState(entity.getOnPos());
        if (!state.is(ModBlocks.SKY_LAUNCHER.get()) || !state.getValue(SkyLauncherBlock.POWERED)) return;
        Vec3 velocity = entity.getDeltaMovement();
        // Multiply the baseline jump velocity by 23/3 to retain old active-launcher strength.
        entity.setDeltaMovement(velocity.x, velocity.y * (23.0D / 3.0D), velocity.z);
    }
}
