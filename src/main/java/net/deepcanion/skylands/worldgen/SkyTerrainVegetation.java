package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Weighted, biome-specific SkyLands vegetation. Entries are the mod's existing
 * plants only; no vanilla substitutes and no biome-feature reruns. Weights
 * describe the relative selection frequency, not guaranteed spawned plants.
 */
final class SkyTerrainVegetation {
    private record Entry(String id, int weight) { }
    private static Entry item(String id, int weight) { return new Entry(id, weight); }

    // Theme order matches SkyTerrainFoliage and SkyTerrainFeature.
    private static final Entry[][] PALETTES = {
            // Golden: 42 / 25 / 15 / 12 / 6
            { item("golden_short_grass", 42), item("golden_grass", 25),
              item("golden_fern", 15), item("blow_flower", 12),
              item("smoke_flower_plant", 6) },
            // Glaciated: existing native grass and double-height fern
            { item("short_glaciated_grass", 50), item("tall_glaciated_grass", 28),
              item("glaciated_fern", 22) },
            // Arid: keep the small cactus rare relative to grasses
            { item("arid_grass", 55), item("small_arid_bush", 32),
              item("small_sky_cactus", 13) },
            // Lavic: tall custom mushrooms are the unusual form
            { item("short_lavic_grass", 45), item("metallic_shorter_grass", 35),
              item("lavic_mushrooms", 20) },
            // Swamp: avoid water-only plants until the islands have ponds
            { item("swamp_grass", 70), item("swamp_bush", 30) },
            // Cherry: the two existing distinct cherry grasses
            { item("cherry_short_grass", 60), item("cherry_grass", 40) }
    };

    // Forest understory favors native ferns and grasses. Plateau plants are
    // more restrained; Stage 2 will give these districts their actual cliffs.
    // The Meadows palette above stays the long-standing Golden default.
    private static final Entry[] GOLDEN_FOREST = {
            item("golden_short_grass", 24), item("golden_grass", 27),
            item("golden_fern", 36), item("blow_flower", 8),
            item("smoke_flower_plant", 5)
    };
    private static final Entry[] GOLDEN_PLATEAU = {
            item("golden_short_grass", 51), item("golden_grass", 27),
            item("golden_fern", 10), item("blow_flower", 8),
            item("smoke_flower_plant", 4)
    };

    /** Blend palette choices using the same weights as Golden tree generation. */
    static BlockState selectGolden(RandomSource random, SkyTerrainSubregions.GoldenSample region) {
        double roll = random.nextDouble();
        Entry[] entries = roll < region.forest() ? GOLDEN_FOREST
                : roll < region.forest() + region.plateau() ? GOLDEN_PLATEAU
                : PALETTES[0];
        return select(entries, random);
    }

    private SkyTerrainVegetation() { }

    static int themeIndex(String theme) {
        return switch (theme) {
            case "golden" -> 0;
            case "glaciated" -> 1;
            case "arid" -> 2;
            case "lavic" -> 3;
            case "swamp" -> 4;
            case "cherry" -> 5;
            default -> throw new IllegalArgumentException("Unknown sky vegetation theme: " + theme);
        };
    }

    static BlockState select(int theme, RandomSource random) {
        return select(PALETTES[theme], random);
    }

    private static BlockState select(Entry[] entries, RandomSource random) {
        int total = 0;
        for (Entry item : entries) total += item.weight();
        int roll = random.nextInt(total);
        for (Entry item : entries) {
            roll -= item.weight();
            if (roll < 0) {
                return BuiltInRegistries.BLOCK.get(SkyLands.id(item.id())).defaultBlockState();
            }
        }
        throw new IllegalStateException("Empty SkyLands vegetation palette");
    }
}
