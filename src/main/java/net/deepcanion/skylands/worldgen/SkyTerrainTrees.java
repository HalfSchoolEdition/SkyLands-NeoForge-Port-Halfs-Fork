package net.deepcanion.skylands.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

/** Handcrafted native trees and WorldEdit-authored trees, never jigsaw structures. */
final class SkyTerrainTrees {
    private static final SkyTerrainTemplates.Entry[] GOLDEN = {
            SkyTerrainTemplates.tree("golden_leaves_tree0", 2, 0, 1, 5),
            SkyTerrainTemplates.tree("golden_leaves_tree1", 2, 0, 2, 5),
            SkyTerrainTemplates.tree("golden_leaves_tree2", 3, 0, 3, 5),
            SkyTerrainTemplates.tree("golden_tree_01", 2, 0, 2, 6),
            SkyTerrainTemplates.tree("golden_tree_02", 2, 0, 1, 6),
            SkyTerrainTemplates.tree("golden_tree_big_01", 6, 1, 7, 1),
            SkyTerrainTemplates.tree("golden_tree_fruit_01", 3, 0, 3, 4),
            SkyTerrainTemplates.tree("golden_tree_fruit_big_01", 3, 2, 2, 2)
    };
    private static final SkyTerrainTemplates.Entry[] GLACIATED = {
            SkyTerrainTemplates.tree("ice_tree", 4, 0, 5, 4),
            SkyTerrainTemplates.tree("glacial_tree_01", 2, 0, 2, 3),
            SkyTerrainTemplates.tree("glacial_tree_02", 1, 0, 3, 3),
            SkyTerrainTemplates.tree("glacial_tree_03", 7, 3, 9, 4),
            SkyTerrainTemplates.tree("glacial_tree_big_01", 5, 2, 2, 6),
            SkyTerrainTemplates.tree("glacial_tree_big_02", 11, 3, 10, 6),
            SkyTerrainTemplates.tree("glacial_tree_giant_01", 16, 4, 16, 3)
    };
    // Intentionally remove the old "lavic_tree" template from terrain generation.
    private static final SkyTerrainTemplates.Entry[] LAVIC = {
            SkyTerrainTemplates.tree("lavic_tree_01", 1, 0, 3, 3),
            SkyTerrainTemplates.tree("lavic_tree_02", 3, 0, 3, 4),
            SkyTerrainTemplates.tree("lavic_tree_03", 2, 0, 5, 4),
            SkyTerrainTemplates.tree("lavic_tree_04", 2, 0, 3, 4),
            SkyTerrainTemplates.tree("lavic_tree_big_01", 12, 3, 7, 6),
            // Leafless burned variants, placed among ordinary Lavic trees.
            SkyTerrainTemplates.tree("lavic_tree_burned_01", 4, 0, 4, 2),
            SkyTerrainTemplates.tree("lavic_tree_burned_02", 2, 0, 3, 2),
            SkyTerrainTemplates.tree("lavic_tree_burned_03", 3, 0, 5, 2)
    };
    private static final SkyTerrainTemplates.Entry[] LAVIC_LEAFY = {
            SkyTerrainTemplates.tree("lavic_tree_01", 1, 0, 3, 3),
            SkyTerrainTemplates.tree("lavic_tree_02", 3, 0, 3, 4),
            SkyTerrainTemplates.tree("lavic_tree_03", 2, 0, 5, 4),
            SkyTerrainTemplates.tree("lavic_tree_04", 2, 0, 3, 4),
            SkyTerrainTemplates.tree("lavic_tree_big_01", 12, 3, 7, 6)
    };
    private static final SkyTerrainTemplates.Entry[] LAVIC_LEAFLESS = {
            SkyTerrainTemplates.tree("lavic_tree_burned_01", 4, 0, 4, 2),
            SkyTerrainTemplates.tree("lavic_tree_burned_02", 2, 0, 3, 2),
            SkyTerrainTemplates.tree("lavic_tree_burned_03", 3, 0, 5, 2)
    };
    static boolean placeLavic(WorldGenLevel level, RandomSource random, BlockPos root,
                              int chunkX, int chunkZ, double leaflessChance) {
        SkyTerrainTemplates.Entry[] entries = random.nextDouble() < leaflessChance
                ? LAVIC_LEAFLESS : LAVIC_LEAFY;
        return SkyTerrainTemplates.place(level, random,
                SkyTerrainTemplates.select(entries, random), root, chunkX, chunkZ);
    }
    private static final SkyTerrainTemplates.Entry[] SWAMP = {
            SkyTerrainTemplates.tree("swamp_tree_01", 6, 0, 3, 3),
            SkyTerrainTemplates.tree("swamp_tree_02", 3, 0, 3, 4),
            SkyTerrainTemplates.tree("swamp_tree_03", 3, 0, 3, 4),
            SkyTerrainTemplates.tree("swamp_tree_04", 2, 0, 2, 4),
            SkyTerrainTemplates.tree("swamp_tree_05", 4, 0, 5, 3)
    };
    private static final SkyTerrainTemplates.Entry[] CHERRY = {
            SkyTerrainTemplates.tree("cherry_tree_01", 3, 0, 7, 9),
            SkyTerrainTemplates.tree("cherry_tree_02", 3, 0, 7, 9),
            SkyTerrainTemplates.tree("cherry_tree_small_01", 1, 0, 2, 2),
            SkyTerrainTemplates.tree("cherry_tree_small_02", 1, 0, 1, 2)
    };

    // Restrict ground fragments to compact *native* variants. Large and giant
    // Sky Terrain trees would dwarf or collide with these small rock fragments.
    private static final SkyTerrainTemplates.Entry[][] FALLEN = {
            { SkyTerrainTemplates.tree("golden_leaves_tree0", 2, 0, 1, 3),
              SkyTerrainTemplates.tree("golden_leaves_tree1", 2, 0, 2, 3),
              SkyTerrainTemplates.tree("golden_tree_01", 2, 0, 2, 3),
              SkyTerrainTemplates.tree("golden_tree_02", 2, 0, 1, 3) },
            { SkyTerrainTemplates.tree("glacial_tree_01", 2, 0, 2, 4),
              SkyTerrainTemplates.tree("glacial_tree_02", 1, 0, 3, 3),
              SkyTerrainTemplates.tree("ice_tree", 4, 0, 5, 2) },
            {}, // Arid cactus spires are placed by SkyTerrainCacti
            { SkyTerrainTemplates.tree("lavic_tree_01", 1, 0, 3, 5),
              SkyTerrainTemplates.tree("lavic_tree_02", 3, 0, 3, 4),
              SkyTerrainTemplates.tree("lavic_tree_04", 2, 0, 3, 4),
              SkyTerrainTemplates.tree("lavic_tree_burned_01", 4, 0, 4, 2),
              SkyTerrainTemplates.tree("lavic_tree_burned_02", 2, 0, 3, 2) },
            { SkyTerrainTemplates.tree("swamp_tree_02", 3, 0, 3, 4),
              SkyTerrainTemplates.tree("swamp_tree_03", 3, 0, 3, 4),
              SkyTerrainTemplates.tree("swamp_tree_04", 2, 0, 2, 4) },
            { SkyTerrainTemplates.tree("cherry_tree_small_01", 1, 0, 2, 5),
              SkyTerrainTemplates.tree("cherry_tree_small_02", 1, 0, 1, 5) }
    };

    /** True ground-level native trees, never the miniature vanilla substitutes. */
    static boolean placeFallen(WorldGenLevel level, RandomSource random, int theme,
                               BlockPos root, int chunkX, int chunkZ) {
        if (theme < 0 || theme >= FALLEN.length || FALLEN[theme].length == 0) return false;
        return SkyTerrainTemplates.placeGroundedTree(level, random,
                SkyTerrainTemplates.select(FALLEN[theme], random), root, chunkX, chunkZ);
    }

    private SkyTerrainTrees() { }

    static boolean place(WorldGenLevel level, RandomSource random, int theme, BlockPos root,
                         int chunkX, int chunkZ) {
        SkyTerrainTemplates.Entry[] choices = switch (theme) {
            case 0 -> GOLDEN;
            case 1 -> GLACIATED;
            case 3 -> LAVIC;
            case 4 -> SWAMP;
            case 5 -> CHERRY;
            default -> null;
        };
        return choices != null && SkyTerrainTemplates.place(level, random,
                SkyTerrainTemplates.select(choices, random), root, chunkX, chunkZ);
    }
}
