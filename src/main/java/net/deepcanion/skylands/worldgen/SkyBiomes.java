package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class SkyBiomes {
    public static final ResourceKey<Biome> GOLDEN = key("floating_golden_islands");
    public static final ResourceKey<Biome> GLACIATED = key("floating_glaciated_islands");
    public static final ResourceKey<Biome> LAVIC = key("floating_lavic_islands");
    public static final ResourceKey<Biome> ARID = key("floating_arid_islands");
    public static final ResourceKey<Biome> CHERRY = key("floating_cherry_grove_islands");
    public static final ResourceKey<Biome> SWAMP = key("floating_swamp_islands");
    private static ResourceKey<Biome> key(String id) { return ResourceKey.create(Registries.BIOME, SkyLands.id(id)); }
    private SkyBiomes() { }
}
