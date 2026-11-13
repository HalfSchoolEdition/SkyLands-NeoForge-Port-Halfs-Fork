package net.deepcanion.skylands.mixin;

import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.registry.ModFluids;
import net.deepcanion.skylands.worldgen.WaterfallLimitPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops growth of long, free-falling water / molten-metal columns high in the
 * Overworld without introducing a water clone or permanently frozen fluid.
 *
 * Intercept spread itself instead of a single downward placement: vanilla can
 * otherwise spread sideways when downward placement is blocked, creating a
 * flat floating puddle at the bottom of a capped waterfall.
 *
 * Crucially, FlowingFluid.tick() still recomputes the existing fluid state
 * BEFORE calling spread(), so removing the source drains the column normally.
 * No new ticks are scheduled by this feature.
 */
@Mixin(FlowingFluid.class)
public abstract class HighSkyWaterfallMixin {
    private static final int SKYLANDS_WATER_SALT = 0x3E5AD216;
    private static final int SKYLANDS_METAL_SALT = 0x7B42E9A1;

    @Inject(method = "spread", at = @At("HEAD"), cancellable = true)
    private void skylands$capHighWaterfalls(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (level.isClientSide() || level.dimension() != Level.OVERWORLD || state.isEmpty()) return;

        Fluid fluid = state.getType();
        final int salt;
        if (fluid == Fluids.FLOWING_WATER || fluid == Fluids.WATER) {
            if (!GameplayConfig.LIMIT_SKY_WATER.get()) return;
            salt = SKYLANDS_WATER_SALT;
        } else if (fluid == ModFluids.FLOWING_MOLTEN_METAL.get()
                || fluid == ModFluids.MOLTEN_METAL.get()) {
            if (!GameplayConfig.LIMIT_SKY_MOLTEN_METAL.get()) return;
            salt = SKYLANDS_METAL_SALT;
        } else {
            return;
        }
        // Check FALLING only on the two known fluid families. Other mods may
        // have different custom fluid-state properties.
        if (state.isSource() || !state.getValue(FlowingFluid.FALLING)) return;
        // A waterfall that meets a solid shelf should still spread naturally
        // across that surface. Only cap its growth while falling through air.
        if (!level.isEmptyBlock(pos.below())) return;

        int limit = WaterfallLimitPolicy.columnLimit(pos.getX(), pos.getZ(),
                GameplayConfig.WATERFALL_MIN_LENGTH.get(),
                GameplayConfig.WATERFALL_MAX_LENGTH.get(), salt);
        // Fast rejection: ordinary low-altitude water never triggers a scan.
        // Count only FALLING, non-source blocks of this *same* liquid family.
        if (!WaterfallLimitPolicy.reachesSky(pos.getY(), limit,
                GameplayConfig.WATERFALL_SKY_START_Y.get())) return;

        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 1; i < limit; i++) {
            FluidState above = level.getFluidState(cursor.move(0, 1, 0));
            if (above.isEmpty() || !sameFamily(fluid, above.getType())
                    || above.isSource() || !above.getValue(FlowingFluid.FALLING)) return;
        }

        // The current block plus (limit - 1) falling blocks above it form a
        // full-height column. Deny both downward and sideways NEW spread.
        // Existing liquid still receives normal scheduled fluid updates.
        ci.cancel();
    }

    private static boolean sameFamily(Fluid first, Fluid second) {
        if (first == Fluids.FLOWING_WATER || first == Fluids.WATER) {
            return second == Fluids.FLOWING_WATER || second == Fluids.WATER;
        }
        return second == ModFluids.FLOWING_MOLTEN_METAL.get()
                || second == ModFluids.MOLTEN_METAL.get();
    }
}
