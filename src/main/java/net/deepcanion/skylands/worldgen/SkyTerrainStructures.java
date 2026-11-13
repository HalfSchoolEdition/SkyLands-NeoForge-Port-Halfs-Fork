package net.deepcanion.skylands.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

/** Sparse, non-jigsaw ruins and handcrafted decorations on procedural island tops. */
final class SkyTerrainStructures {
    private static final SkyTerrainTemplates.Entry[] GOLDEN = {
            // These two giant stumps were copied off-centre in WorldEdit; use
            // the horizontal footprint centre, but preserve the copied Y offset.
            SkyTerrainTemplates.decoration("golden_gianttreestump_01", 4, 2, 5, 3),
            SkyTerrainTemplates.decoration("golden_structure_gianttreestump_01", 4, 4, 5, 2),
            SkyTerrainTemplates.decoration("golden_structure_arch_01", 5, 0, 1, 2),
            SkyTerrainTemplates.decoration("golden_structure_pavillon_01", 3, 0, 3, 2)
    };
    private static final SkyTerrainTemplates.Entry[] CHERRY = {
            SkyTerrainTemplates.decoration("cherry_structure_pavillon_01", 5, 2, 5, 2),
            SkyTerrainTemplates.decoration("cherry_structure_pavillon_02", 6, 2, 5, 1)
    };
    private static final SkyTerrainTemplates.Entry LAVIC_FALLEN_BURNT_TREE =
            SkyTerrainTemplates.decoration("lavic_tree_burned_fallen_01", 4, 0, 4, 1);
    private static final SkyTerrainTemplates.Entry[] LAVIC_HUGE_SPIRES = {
            SkyTerrainTemplates.decoration("lavic_spire_huge0", 22, 0, 20, 1),
            SkyTerrainTemplates.decoration("lavic_spire_huge1", 22, 0, 20, 1),
            SkyTerrainTemplates.decoration("lavic_spire_huge2", 22, 0, 20, 1),
            SkyTerrainTemplates.decoration("lavic_spire_huge3", 22, 0, 20, 1)
    };

    private SkyTerrainStructures() { }

    static void decorateHuge(WorldGenLevel level, RandomSource random,
                             BlockPos groundAir, int chunkX, int chunkZ) {
        SkyTerrainTemplates.placeHugeSpire(level, random,
                SkyTerrainTemplates.select(LAVIC_HUGE_SPIRES, random),
                groundAir, chunkX, chunkZ);
    }

    /** Called before the trees so decorations can occupy a clear island clearing. */
    static boolean decorate(WorldGenLevel level, RandomSource random, int theme,
                            BlockPos groundAir, int chunkX, int chunkZ) {
        SkyTerrainTemplates.Entry[] choices;
        if (theme == 0) choices = GOLDEN;
        else if (theme == 5) choices = CHERRY;
        else if (theme == 3) {
            // Rare fallen burned trunk, not a regular tree or an oak.
            return random.nextInt(5) == 0 && SkyTerrainTemplates.place(level, random,
                    LAVIC_FALLEN_BURNT_TREE, groundAir, chunkX, chunkZ);
        } else return false;
        SkyTerrainTemplates.Entry chosen = SkyTerrainTemplates.select(choices, random);
        return SkyTerrainTemplates.place(level, random, chosen, groundAir, chunkX, chunkZ);
    }
}
