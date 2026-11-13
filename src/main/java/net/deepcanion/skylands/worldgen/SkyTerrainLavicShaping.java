package net.deepcanion.skylands.worldgen;

/** Continuous Lavic surface shaping; original undersides remain untouched. */
final class SkyTerrainLavicShaping {
    record Sample(double plateau, double forest, double barrens, double rifts,
                  double elevation) {
        double flatness() { return .81 * plateau + .27 * forest + .87 * barrens + .48 * rifts; }
    }
    static final Sample NONE = new Sample(0, 0, 0, 0, 0);
    private SkyTerrainLavicShaping() { }

    static Sample sample(long seed, int x, int z) {
        var s = SkyTerrainSubregions.lavic(seed, x, z);
        // Lava-rift crests are uneven and raised; ash fields and the classic
        // lake plateaus are broad and shallow. Blend by world-space weights.
        double broad = SkyTerrainPatterns.noise2(seed, x, z, 72, 0x71a5f31L);
        double ridges = Math.abs(SkyTerrainPatterns.noise2(seed, x, z, 39, 0x71a5f32L) - .5);
        double elevation = s.plateau() * (2.4 + broad * 3.1)
                + s.forest() * (4.5 + broad * 5.1)
                + s.barrens() * (1.3 + broad * 2.5)
                + s.rifts() * (12.0 + broad * 17.0 + ridges * 12.0);
        return new Sample(s.plateau(), s.forest(), s.barrens(), s.rifts(), elevation);
    }
}
