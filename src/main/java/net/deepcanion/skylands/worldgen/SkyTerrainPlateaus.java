package net.deepcanion.skylands.worldgen;

/**
 * Golden plateau cap shaping, sampled once per X/Z column in SkyIslandShape.
 * Existing Golden Meadows/Forest and all other sky themes retain their original
 * geometry. Every value uses world coordinates, seed and continuous noise;
 * chunks never read each other, so terraces cross chunk boundaries cleanly.
 *
 * This does not place basalt (Stage 3) or modify boulders (Stage 4).
 */
final class SkyTerrainPlateaus {
    record Sample(double weight, double elevation) { }
    static final Sample NONE = new Sample(0.0, 0.0);

    private SkyTerrainPlateaus() { }

    static Sample sample(long seed, int x, int z, SkyIslandShape.Lake lake) {
        double plateau = SkyTerrainSubregions.golden(seed, x, z).plateau();
        if (plateau < .01) return NONE;
        // Lakes and their new spillways must keep the original uninterrupted
        // shoreline, so don't terrace the near-shore or outlet columns.
        double weight = plateau * (lake == null ? 1.0 : lake.plateauClearance(x, z));
        if (weight < .01) return NONE;

        // The broad field provides successive altitude bands; smaller folds
        // keep their margins organic without pixelated/chunk-aligned seams.
        double broad = SkyTerrainPatterns.noise2(seed, x, z, 83, 0x6dd4f2a1L);
        double detail = SkyTerrainPatterns.noise2(seed, x, z, 43, 0x6dd4f2a2L);
        double height = (broad * .76 + detail * .24 - .5) * 4.3;
        double band = Math.floor(height);
        double position = height - band;
        // Wide level treads and narrow rises, but no true discontinuities.
        double rampFraction = smoothstep(.32, .68, position);
        double terraced = (band + rampFraction) * 9.0;

        // Some naturally winding transition slopes reconnect successive decks.
        // In these patches we recover a smoother version of the height field.
        double slopeField = SkyTerrainPatterns.noise2(seed, x, z, 108, 0x6dd4f2a3L);
        double slopeMix = .78 * smoothstep(.53, .73, slopeField);
        double elevation = terraced * (1.0 - slopeMix) + height * 9.0 * slopeMix;
        return new Sample(weight, elevation);
    }

    private static double smoothstep(double a, double b, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }
}
