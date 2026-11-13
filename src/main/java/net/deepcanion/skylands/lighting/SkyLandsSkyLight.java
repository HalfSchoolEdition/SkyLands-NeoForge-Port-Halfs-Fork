package net.deepcanion.skylands.lighting;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Split sky-light source policy. The marker tag identifies high island terrain
 * in a chunk; it never changes a block's opacity, skylight propagation,
 * map color, collision or light-blocking behavior.
 */
public final class SkyLandsSkyLight {
    public static final TagKey<Block> HIGH_ISLAND_MARKERS = TagKey.create(
            Registries.BLOCK, SkyLands.id("split_skylight_island_markers"));

    private SkyLandsSkyLight() { }

    /** One restart-required toggle; when disabled the sky engine is vanilla. */
    public static boolean splitMode() {
        return GameplayConfig.EXPERIMENTAL_SKYLIGHT.get();
    }
}
