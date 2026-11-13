package net.deepcanion.skylands.worldgen;

/**
 * Cheap, deterministic Golden boulder distribution policy. All probabilities
 * blend through the existing world-aligned virtual sub-region weights.
 * Geometry and placement remain the responsibility of SkyTerrainBoulders.
 */
final class SkyGoldenBoulderProfile {
    private SkyGoldenBoulderProfile() { }

    /** Probability for each of two independent candidate sites per chunk. */
    static double attemptChance(SkyTerrainSubregions.GoldenSample region) {
        return .32 * region.forest() + .30 * region.meadows() + .06 * region.plateau();
    }

    /** A majority of formations include 1-3 companions, usually just one. */
    static int companions(double groupRoll, double sizeRoll) {
        if (groupRoll >= .84) return 0;
        if (sizeRoll < .60) return 1;
        if (sizeRoll < .91) return 2;
        return 3;
    }

    /** Radial distance from the main rock: smaller for partial stacked rocks. */
    static double companionDistance(int mainRadius, int childRadius, boolean stacked, double roll) {
        if (stacked) return mainRadius * (.35 + .26 * roll);
        return mainRadius * (.80 + .34 * roll) + childRadius * (.27 + .43 * roll);
    }
}
