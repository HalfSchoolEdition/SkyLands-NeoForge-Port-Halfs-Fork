package net.deepcanion.skylands.worldgen;

/** World-aligned river ribbons and fissure pools in Molten Rifts, no chunk RNG. */
final class SkyTerrainLavicHydrology {
    record Sample(boolean molten, boolean magma) { }
    static final Sample NONE = new Sample(false, false);
    private SkyTerrainLavicHydrology() { }
    static Sample sample(long seed, int x, int z, SkyTerrainSubregions.LavicSample region) {
        if (region.rifts() < .38) return NONE;
        double rift = region.rifts();
        double warp = SkyTerrainPatterns.noise2(seed, x, z, 104, 0x5cf711L) - .5;
        // Two slowly bending meanders intersect: broad enough to read as
        // rivers, with irregular little basins wherever they meet.
        double a = Math.abs(Math.sin(x * .050 + z * .024 + warp * 4.5));
        double b = Math.abs(Math.sin(z * .062 - x * .019 + warp * 3.4));
        double width = .055 + .115 * rift;
        boolean river = a < width || b < width * .61;
        double ponds = SkyTerrainPatterns.patch(seed, x, z, 48, 0x5cf712L);
        boolean pool = ponds > .68 && rift > .59;
        boolean lava = (river || pool) && rift > .46;
        double rockChoice = SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x5cf713L));
        boolean magma = !lava && (a < width * 2.6 || b < width * 1.9 || ponds > .48)
                && rockChoice < .10 + .30 * rift;
        return new Sample(lava, magma);
    }
}
