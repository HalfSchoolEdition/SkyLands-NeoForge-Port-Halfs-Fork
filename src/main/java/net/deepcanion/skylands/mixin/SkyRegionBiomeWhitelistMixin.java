package net.deepcanion.skylands.mixin;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.deepcanion.skylands.worldgen.SkyRegionThemeSelector;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional-on-surface-biome, single-theme sky regions.
 *
 * Lithostitched remains responsible for which sky-region disks exist. This hook
 * replaces (or suppresses) the selected biome within those disks, using a shared site
 * anchor and the (unmodified-at-this-height) biome source beneath the disk.
 * Nothing is queried at sky height while classifying a region.
 * A rejected disk returns its original ground biome at Y=96, rather than
 * letting the underlying climate layout select a random sky-biome theme.
 *
 * Target is named as a string to avoid a compile-time Lithostitched dependency.
 */
@Mixin(targets = "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.InjectorBiomeSource", remap = false)
public abstract class SkyRegionBiomeWhitelistMixin {
    // Matches skylands:sky_region_distance / sky_region_noise: unjittered
    // 0.0014-frequency cellular sites. Locations are in block coordinates.
    @Unique private static final double SKYLANDS$SITE_FREQUENCY = 0.0014;
    @Unique private static final int SKYLANDS$SAMPLE_Y_QUART = 24; // Y=96, safely under sky injection at Y=290
    @Unique private static final int SKYLANDS$OFFSET_BLOCKS = 128;
    @Unique private static final int SKYLANDS$MAX_CACHE_ENTRIES = 16384;

    @Unique private static final TagKey<Biome>[] SKYLANDS$THEME_TAGS = skylands$makeTags();
    @Unique private static final ResourceKey<Biome>[] SKYLANDS$SKY_KEYS = skylands$makeKeys();
    // This biome source is unique to the world's biome layout; do not cache
    // across worlds or use global static entries that could leak between seeds.
    @Unique private Map<Long, Integer> skylands$regionThemes;
    @Unique private volatile Holder<Biome>[] skylands$skyHolders;
    @Unique private static final ThreadLocal<Boolean> skylands$samplingGround = ThreadLocal.withInitial(() -> false);

    @SuppressWarnings("unchecked")
    @Unique private static TagKey<Biome>[] skylands$makeTags() {
        return new TagKey[] {
            TagKey.create(Registries.BIOME, SkyLands.id("sky_region/glaciated")),
            TagKey.create(Registries.BIOME, SkyLands.id("sky_region/lavic")),
            TagKey.create(Registries.BIOME, SkyLands.id("sky_region/swamp")),
            TagKey.create(Registries.BIOME, SkyLands.id("sky_region/arid")),
            TagKey.create(Registries.BIOME, SkyLands.id("sky_region/cherry")),
            TagKey.create(Registries.BIOME, SkyLands.id("sky_region/golden"))
        };
    }

    @SuppressWarnings("unchecked")
    @Unique private static ResourceKey<Biome>[] skylands$makeKeys() {
        return new ResourceKey[] {
            SkyBiomes.GLACIATED, SkyBiomes.LAVIC, SkyBiomes.SWAMP,
            SkyBiomes.ARID, SkyBiomes.CHERRY, SkyBiomes.GOLDEN
        };
    }

    // Initialise explicitly on the target rather than depending on constructor
    // field-initialiser merging across different Mixin implementations.
    @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
    private void skylands$init(CallbackInfo ci) {
        skylands$regionThemes = new ConcurrentHashMap<>();
    }

    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true, remap = false)
    private void skylands$applySkyRegionWhitelist(int quartX, int quartY, int quartZ,
                                                    Climate.Sampler sampler,
                                                    CallbackInfoReturnable<Holder<Biome>> cir) {
        // Do not change normal Overworld biomes or recursively classify them.
        if (quartY * 4 < 290 || skylands$samplingGround.get()) return;

        Holder<Biome> selected = cir.getReturnValue();
        if (!skylands$isSkyBiome(selected)) return;

        // Our existing Lithostitched injector already applies the exact disk
        // mask and site-value rarity test. We merely choose one theme per disk.
        int siteX = (int) Math.round(quartX * 4.0 * SKYLANDS$SITE_FREQUENCY);
        int siteZ = (int) Math.round(quartZ * 4.0 * SKYLANDS$SITE_FREQUENCY);
        long siteKey = ((long) siteX << 32) | (siteZ & 0xffffffffL);

        if (skylands$regionThemes.size() > SKYLANDS$MAX_CACHE_ENTRIES) {
            skylands$regionThemes.clear(); // bounded, deterministic cache; eviction cannot alter results
        }
        int theme = skylands$regionThemes.computeIfAbsent(siteKey,
                unused -> skylands$chooseTheme(siteX, siteZ, sampler));

        // -1 means this site was rejected: Golden was thinned without any
        // surface-compatible alternative, or all matching themes are disabled.
        // Leaving Lithostitched's original result in place would reintroduce
        // an arbitrary (and potentially incompatible) sky biome. Instead use
        // its ordinary, unmodified ground biome at this exact X/Z location.
        if (theme < 0) {
            skylands$samplingGround.set(true);
            try {
                cir.setReturnValue(((BiomeSource) (Object) this).getNoiseBiome(
                        quartX, SKYLANDS$SAMPLE_Y_QUART, quartZ, sampler));
            } finally {
                skylands$samplingGround.set(false);
            }
            return;
        }

        Holder<Biome>[] holders = skylands$getSkyHolders();
        if (holders != null && holders[theme] != null) {
            cir.setReturnValue(holders[theme]);
        }
    }

    @Unique private static boolean skylands$isSkyBiome(Holder<Biome> biome) {
        for (ResourceKey<Biome> key : SKYLANDS$SKY_KEYS) {
            if (biome.is(key)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Unique private Holder<Biome>[] skylands$getSkyHolders() {
        Holder<Biome>[] current = skylands$skyHolders;
        if (current != null) return current;
        Holder<Biome>[] loaded = (Holder<Biome>[]) new Holder<?>[SKYLANDS$SKY_KEYS.length];
        for (Holder<Biome> possible : ((BiomeSource) (Object) this).possibleBiomes()) {
            for (int i = 0; i < SKYLANDS$SKY_KEYS.length; i++) {
                if (possible.is(SKYLANDS$SKY_KEYS[i])) loaded[i] = possible;
            }
        }
        // All six themes are declared in the Lithostitched alternate layout.
        // In case another mod modifies that layout, preserve the original result
        // rather than returning an unregistered/unknown biome.
        skylands$skyHolders = loaded;
        return loaded;
    }

    @Unique private int skylands$chooseTheme(int siteX, int siteZ, Climate.Sampler sampler) {
        final double x = siteX / SKYLANDS$SITE_FREQUENCY;
        final double z = siteZ / SKYLANDS$SITE_FREQUENCY;
        // One region-wide decision, with the center weighted somewhat more
        // heavily, but outer samples still able to overturn a small mismatch.
        final int[][] samples = {
            {0, 0, 3},
            {-SKYLANDS$OFFSET_BLOCKS, 0, 1},
            {SKYLANDS$OFFSET_BLOCKS, 0, 1},
            {0, -SKYLANDS$OFFSET_BLOCKS, 1},
            {0, SKYLANDS$OFFSET_BLOCKS, 1}
        };
        int[] score = new int[SKYLANDS$THEME_TAGS.length];
        skylands$samplingGround.set(true);
        try {
            BiomeSource source = (BiomeSource) (Object) this;
            for (int[] sample : samples) {
                int quartX = Math.floorDiv((int) Math.floor(x + sample[0]), 4);
                int quartZ = Math.floorDiv((int) Math.floor(z + sample[1]), 4);
                Holder<Biome> ground = source.getNoiseBiome(quartX, SKYLANDS$SAMPLE_Y_QUART, quartZ, sampler);
                int category = skylands$classify(ground);
                score[category] += sample[2];
            }
        } finally {
            skylands$samplingGround.set(false);
        }
        // Every selected theme must have an actual matching sample. In
        // particular, never replace rejected Golden sites with a randomly
        // enabled theme that scored zero on all five surface samples.
        int[] weights = new int[SKYLANDS$THEME_TAGS.length];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = GameplayConfig.SKY_BIOME_WEIGHTS[i].get();
        }
        long hash = ((long) siteX * 0x9E3779B97F4A7C15L) ^ ((long) siteZ * 0xC2B2AE3D27D4EB4FL);
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        return SkyRegionThemeSelector.choose(score, weights, hash);
    }

    @Unique private static int skylands$classify(Holder<Biome> biome) {
        for (int i = 0; i < SKYLANDS$THEME_TAGS.length; i++) {
            if (biome.is(SKYLANDS$THEME_TAGS[i])) return i;
        }
        // Don't falsely classify humid jungles as Swamp or ordinary deserts
        // as Lavic. Unknown modded biomes can be assigned through datapack tags.
        return SKYLANDS$THEME_TAGS.length - 1;
    }
}
