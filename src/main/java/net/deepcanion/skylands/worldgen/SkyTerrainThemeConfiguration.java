package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** JSON: {"theme":"golden"}. Future biome-specific options belong here. */
public record SkyTerrainThemeConfiguration(String theme) implements FeatureConfiguration {
    public static final Codec<SkyTerrainThemeConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("theme").forGetter(SkyTerrainThemeConfiguration::theme)
    ).apply(instance, SkyTerrainThemeConfiguration::new));
}
