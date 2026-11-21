package net.deepcanion.skylands.mixin;

import net.deepcanion.skylands.registry.ModWorldgen;
import net.deepcanion.skylands.worldgen.SkyTerrainFeature;
import net.deepcanion.skylands.worldgen.SkyTerrainThemeConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streams Reflowing v2.17.x can reject ConfiguredFeature.place() near waterways.
 *
 * SkyLands uses ONE configured feature call to build an entire chunk-local
 * vertical slice of floating terrain. Rejecting it produces a square hole
 * hundreds of blocks above an otherwise valid river.
 *
 * This mixin is registered in a SEPARATE, Streams Reflowing-only config (via
 * NeoForge's requiredMods setting). It returns early only for our primary
 * sky-terrain feature and directly calls the same FeaturePlaceContext used
 * by vanilla ConfiguredFeature.place(). Other features continue through the
 * normal ConfiguredFeature path, including Streams Reflowing's river checks.
 *
 * order=900 runs before Streams Reflowing's normal @Inject HEAD (order=1000).
 * It must not be applied unconditionally: without Streams Reflowing, SkyLands
 * should use the original configured-feature call path unchanged.
 */
@Mixin(value = ConfiguredFeature.class, priority = 1500)
public abstract class StreamsReflowingSkyTerrainMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true, order = 900)
    private void skylands$keepTerrainAboveStreams(WorldGenLevel level,
                                                  ChunkGenerator generator,
                                                  RandomSource random,
                                                  BlockPos origin,
                                                  CallbackInfoReturnable<Boolean> cir) {
        ConfiguredFeature<?, ?> configured = (ConfiguredFeature<?, ?>) (Object) this;
        if (configured.feature() != ModWorldgen.SKY_TERRAIN.get()) return;

        // Preserve ConfiguredFeature.place()'s normal FeaturePlaceContext,
        // including topFeature=Optional.of(this), the same RNG and config.
        // This executes exactly once; no extra pass or adjacent-chunk access.
        SkyTerrainFeature feature = (SkyTerrainFeature) configured.feature();
        SkyTerrainThemeConfiguration config = (SkyTerrainThemeConfiguration) configured.config();
        FeaturePlaceContext<SkyTerrainThemeConfiguration> context = new FeaturePlaceContext<>(
                Optional.of(configured), level, generator, random, origin, config);
        cir.setReturnValue(feature.place(context));
    }
}
