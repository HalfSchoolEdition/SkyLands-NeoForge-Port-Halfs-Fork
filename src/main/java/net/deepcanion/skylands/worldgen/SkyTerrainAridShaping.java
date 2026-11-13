package net.deepcanion.skylands.worldgen;

/**
 * World-aligned, single-column Arid cap shaping. Dunes are smoothly rolling
 * sand hills; Highlands consist of stacked, broad badlands mesas, steep ledges
 * and fractured ridges. Neither alters island undersides or biome IDs.
 */
final class SkyTerrainAridShaping {
    record Sample(double dunes, double rocky, double duneElevation, double rockyElevation) { }
    static final Sample NONE = new Sample(0.0, 0.0, 0.0, 0.0);

    private SkyTerrainAridShaping() { }

    static Sample sample(long seed, int x, int z) {
        var regions = SkyTerrainSubregions.arid(seed, x, z);
        if (regions.dunes() < .005 && regions.rockyHighlands() < .005) return NONE;

        double duneHeight = 0.0;
        if (regions.dunes() >= .005) {
            // Slowly curving dune ridges; no steps or sharp white-noise peaks.
            double warp = SkyTerrainPatterns.noise2(seed, x, z, 86, 0x5b96d111L) - .5;
            double wave = Math.sin((x * .085 + z * .038) + warp * 3.1);
            double longWave = SkyTerrainPatterns.noise2(seed, x, z, 72, 0x5b96d112L);
            duneHeight = 4.0 + 7.2 * (.5 + .5 * wave) + 4.8 * longWave;
        }

        double rockyHeight = 0.0;
        if (regions.rockyHighlands() >= .005) {
            // Broad badlands mesas. The terrace bands are deliberately wide
            // and level, separated by steep but slightly weathered steps.
            // Irregular, world-space eroded gullies break their outlines up.
            double broad = SkyTerrainPatterns.noise2(seed, x, z, 112, 0x5b96d113L);
            double mesa = SkyTerrainPatterns.noise2(seed, x, z, 65, 0x5b96d114L);
            double ridges = Math.abs(SkyTerrainPatterns.noise2(seed, x, z, 53, 0x5b96d118L) - .5) * 2.0;
            double buttes = Math.max(0.0, Math.min(1.0,
                    (SkyTerrainPatterns.patch(seed, x, z, 95, 0x5b96d115L) + .18) * .9));
            double raw = 9.0 + 21.0 * broad + 12.0 * mesa
                    + 11.0 * ridges + 13.0 * buttes;
            double band = Math.floor(raw / 8.25);
            double phase = raw / 8.25 - band;
            double terrace = 8.25 * (band + smoothstep(.12, .86, phase));
            // Very low amplitude erosion keeps the shelf tops mostly flat,
            // instead of reverting to rounded hills between the cliffs.
            double erosion = SkyTerrainPatterns.noise2(seed, x, z, 23, 0x5b96d119L) - .5;
            rockyHeight = terrace + 2.3 * erosion;
        }
        return new Sample(regions.dunes(), regions.rockyHighlands(), duneHeight, rockyHeight);
    }

    private static double smoothstep(double low, double high, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - low) / (high - low)));
        return t * t * (3.0 - 2.0 * t);
    }
}
