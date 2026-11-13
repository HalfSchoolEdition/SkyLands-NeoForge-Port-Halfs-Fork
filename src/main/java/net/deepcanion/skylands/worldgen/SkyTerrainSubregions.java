package net.deepcanion.skylands.worldgen;

/**
 * World-aligned, seed-stable *virtual* sky-terrain subregions. These are terrain
 * variants, not registered Minecraft biomes: the sky biome, weather, mobs and
 * other biome-dependent features stay unchanged.
 *
 * Every consumer samples the same (seed, world X, world Z), independent of its
 * chunk, feature RNG, and placement order. Additional sky themes can introduce
 * their own sampled variants here without creating new biome IDs.
 */
final class SkyTerrainSubregions {
    enum GoldenType { FOREST, MEADOWS, PLATEAU }

    /** Continuous weights for gradual transitions; their sum is always 1. */
    record GoldenSample(GoldenType dominant, double forest, double meadows, double plateau) {
        double weight(GoldenType type) {
            return switch (type) {
                case FOREST -> forest;
                case MEADOWS -> meadows;
                case PLATEAU -> plateau;
            };
        }
    }

    private SkyTerrainSubregions() { }

    static GoldenSample golden(long seed, int x, int z) {
        // Reuse the exact pre-existing Golden forestiness field so established
        // forest locations remain recognizable, rather than moving every tree.
        double forestNoise = SkyTerrainPatterns.noise2(seed, x, z, 152, 0x3d61e37bL);

        // Large, softly warped Plateau districts. The slower field makes each
        // district span multiple chunks, while the shorter field irregularizes
        // its edges. Both are sampled in WORLD coordinates (no chunk seams).
        int wx = x + (int) Math.round(30.0 *
                (SkyTerrainPatterns.noise2(seed, x, z, 96, 0x6f2b4d91L) - .5));
        int wz = z + (int) Math.round(30.0 *
                (SkyTerrainPatterns.noise2(seed, x, z, 96, 0x2dc849a7L) - .5));
        double plateauNoise = .78 * SkyTerrainPatterns.noise2(seed, wx, wz, 248, 0x37a19ed5L)
                + .22 * SkyTerrainPatterns.noise2(seed, wx, wz, 112, 0x63fb29a1L);

        // Forest and Plateau compete spatially, with Meadows filling the gaps.
        // Plateau has priority where the two fields overlap so Stage 2 can
        // blend the same Plateau weight into its terrain height modifier.
        double plateau = smoothstep(.54, .72, plateauNoise);
        double forest = (1.0 - plateau) * smoothstep(.38, .59, forestNoise);
        double meadows = Math.max(0.0, 1.0 - plateau - forest);
        GoldenType dominant = plateau >= forest && plateau >= meadows
                ? GoldenType.PLATEAU
                : forest >= meadows ? GoldenType.FOREST : GoldenType.MEADOWS;
        return new GoldenSample(dominant, forest, meadows, plateau);
    }

    enum AridType { GRASSLANDS, DUNES, ROCKY_HIGHLANDS }

    /** Grasslands, Dunes, Highlands: smooth, normalized world-coordinate weights. */
    record AridSample(AridType dominant, double grasslands, double dunes, double rockyHighlands) {
        double weight(AridType type) {
            return switch (type) {
                case GRASSLANDS -> grasslands;
                case DUNES -> dunes;
                case ROCKY_HIGHLANDS -> rockyHighlands;
            };
        }
    }

    static AridSample arid(long seed, int x, int z) {
        // Two independent multi-chunk districts with softly warped edges;
        // Grasslands retain the familiar Arid terrain wherever neither wins.
        int wx = x + (int)Math.round(28.0 *
                (SkyTerrainPatterns.noise2(seed, x, z, 104, 0x79dc8141L) - .5));
        int wz = z + (int)Math.round(28.0 *
                (SkyTerrainPatterns.noise2(seed, x, z, 104, 0x79dc8142L) - .5));
        double duneNoise = .76 * SkyTerrainPatterns.noise2(seed, wx, wz, 264, 0x79dc8143L)
                + .24 * SkyTerrainPatterns.noise2(seed, wx, wz, 128, 0x79dc8144L);
        double rockNoise = .77 * SkyTerrainPatterns.noise2(seed, wx, wz, 252, 0x79dc8145L)
                + .23 * SkyTerrainPatterns.noise2(seed, wx, wz, 116, 0x79dc8146L);

        // Highlands take priority only in their strongest cores; dunes can
        // expand into the remainder. Both fade into unchanged Grasslands.
        double rocky = smoothstep(.48, .66, rockNoise);
        double dunes = (1.0 - rocky) * smoothstep(.44, .62, duneNoise);
        double grasslands = Math.max(0.0, 1.0 - rocky - dunes);
        AridType dominant = rocky >= dunes && rocky >= grasslands
                ? AridType.ROCKY_HIGHLANDS
                : dunes >= grasslands ? AridType.DUNES : AridType.GRASSLANDS;
        return new AridSample(dominant, grasslands, dunes, rocky);
    }

    enum LavicType { PLATEAU, BURNT_FOREST, ASHEN_BARRENS, MOLTEN_RIFTS }

    /** Continuous forest/ash/volcanic districts within the same Lavic biome. */
    record LavicSample(LavicType dominant, double plateau, double forest,
                       double barrens, double rifts) {
        double weight(LavicType type) {
            return switch (type) {
                case PLATEAU -> plateau;
                case BURNT_FOREST -> forest;
                case ASHEN_BARRENS -> barrens;
                case MOLTEN_RIFTS -> rifts;
            };
        }
    }

    static LavicSample lavic(long seed, int x, int z) {
        // Multi-chunk, warped world-space regions. Reuse precisely the same
        // weights for topography, tree selection, spikes, and molten features.
        int wx = x + (int)Math.round(34.0 *
                (SkyTerrainPatterns.noise2(seed, x, z, 103, 0x6c91a11L) - .5));
        int wz = z + (int)Math.round(34.0 *
                (SkyTerrainPatterns.noise2(seed, x, z, 103, 0x6c91a12L) - .5));
        double volcanic = .79 * SkyTerrainPatterns.noise2(seed, wx, wz, 285, 0x6c91a13L)
                + .21 * SkyTerrainPatterns.noise2(seed, wx, wz, 127, 0x6c91a14L);
        double dead = .79 * SkyTerrainPatterns.noise2(seed, wx, wz, 263, 0x6c91a15L)
                + .21 * SkyTerrainPatterns.noise2(seed, wx, wz, 113, 0x6c91a16L);
        double trees = .78 * SkyTerrainPatterns.noise2(seed, wx, wz, 246, 0x6c91a17L)
                + .22 * SkyTerrainPatterns.noise2(seed, wx, wz, 108, 0x6c91a18L);
        // Volcanic hotspots win over the other terrains; deserted regions
        // interrupt forest belts; the normal plateau fills remaining space.
        double rifts = smoothstep(.49, .67, volcanic);
        double barrens = (1.0 - rifts) * smoothstep(.45, .63, dead);
        double forest = (1.0 - rifts - barrens) * smoothstep(.40, .59, trees);
        double plateau = Math.max(0.0, 1.0 - rifts - barrens - forest);
        LavicType dominant = rifts >= plateau && rifts >= forest && rifts >= barrens
                ? LavicType.MOLTEN_RIFTS
                : barrens >= plateau && barrens >= forest ? LavicType.ASHEN_BARRENS
                : forest >= plateau ? LavicType.BURNT_FOREST : LavicType.PLATEAU;
        return new LavicSample(dominant, plateau, forest, barrens, rifts);
    }

    private static double smoothstep(double low, double high, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - low) / (high - low)));
        return t * t * (3.0 - 2.0 * t);
    }

    enum GlaciatedType { FOREST, FROZEN_PLAINS, CRYSTAL_GLACIERS }

    record GlaciatedSample(GlaciatedType dominant, double forest,
                           double plains, double crystals) {
        double weight(GlaciatedType type) {
            return switch (type) {
                case FOREST -> forest;
                case FROZEN_PLAINS -> plains;
                case CRYSTAL_GLACIERS -> crystals;
            };
        }
    }

    static GlaciatedSample glaciated(long seed, int x, int z) {
        int wx = x + (int)Math.round(24 * (SkyTerrainPatterns.noise2(seed,x,z,96,0x641b01L)-.5));
        int wz = z + (int)Math.round(24 * (SkyTerrainPatterns.noise2(seed,x,z,96,0x641b02L)-.5));
        double plainsField = .81 * SkyTerrainPatterns.noise2(seed,wx,wz,236,0x641b03L)
                + .19 * SkyTerrainPatterns.noise2(seed,wx,wz,109,0x641b04L);
        double crystalField = .80 * SkyTerrainPatterns.noise2(seed,wx,wz,244,0x641b05L)
                + .20 * SkyTerrainPatterns.noise2(seed,wx,wz,114,0x641b06L);
        double crystals = smoothstep(.51,.68,crystalField);
        double plains = (1.0-crystals)*smoothstep(.44,.62,plainsField);
        double forest = Math.max(0.0,1.0-crystals-plains);
        GlaciatedType dominant = crystals>=forest && crystals>=plains
                ? GlaciatedType.CRYSTAL_GLACIERS
                : plains>=forest ? GlaciatedType.FROZEN_PLAINS : GlaciatedType.FOREST;
        return new GlaciatedSample(dominant,forest,plains,crystals);
    }

    enum CherryType { GROVE, PETAL_MEADOWS, BLOSSOM_THICKETS }

    record CherrySample(CherryType dominant, double grove, double meadows, double thickets) {
        double weight(CherryType type) {
            return switch (type) {
                case GROVE -> grove;
                case PETAL_MEADOWS -> meadows;
                case BLOSSOM_THICKETS -> thickets;
            };
        }
    }

    static CherrySample cherry(long seed, int x, int z) {
        int wx = x + (int)Math.round(25 * (SkyTerrainPatterns.noise2(seed,x,z,95,0x642b01L)-.5));
        int wz = z + (int)Math.round(25 * (SkyTerrainPatterns.noise2(seed,x,z,95,0x642b02L)-.5));
        double meadowField=.80*SkyTerrainPatterns.noise2(seed,wx,wz,242,0x642b03L)
                +.20*SkyTerrainPatterns.noise2(seed,wx,wz,105,0x642b04L);
        double thicketField=.79*SkyTerrainPatterns.noise2(seed,wx,wz,236,0x642b05L)
                +.21*SkyTerrainPatterns.noise2(seed,wx,wz,117,0x642b06L);
        double meadows=smoothstep(.49,.66,meadowField);
        double thickets=(1.0-meadows)*smoothstep(.49,.66,thicketField);
        double grove=Math.max(0.0,1.0-meadows-thickets);
        CherryType dominant=meadows>=grove && meadows>=thickets
                ? CherryType.PETAL_MEADOWS
                : thickets>=grove ? CherryType.BLOSSOM_THICKETS : CherryType.GROVE;
        return new CherrySample(dominant,grove,meadows,thickets);
    }

    enum SwampType { WETLANDS, DEEP_MIRE, MANGROVE_THICKETS }

    record SwampSample(SwampType dominant, double wetlands, double mire, double thickets) {
        double weight(SwampType type) {
            return switch (type) {
                case WETLANDS -> wetlands;
                case DEEP_MIRE -> mire;
                case MANGROVE_THICKETS -> thickets;
            };
        }
    }

    static SwampSample swamp(long seed,int x,int z) {
        int wx=x+(int)Math.round(26*(SkyTerrainPatterns.noise2(seed,x,z,101,0x643b01L)-.5));
        int wz=z+(int)Math.round(26*(SkyTerrainPatterns.noise2(seed,x,z,101,0x643b02L)-.5));
        double mireField=.80*SkyTerrainPatterns.noise2(seed,wx,wz,244,0x643b03L)
                +.20*SkyTerrainPatterns.noise2(seed,wx,wz,112,0x643b04L);
        double thicketField=.79*SkyTerrainPatterns.noise2(seed,wx,wz,253,0x643b05L)
                +.21*SkyTerrainPatterns.noise2(seed,wx,wz,108,0x643b06L);
        double mire=smoothstep(.50,.67,mireField);
        double thickets=(1.0-mire)*smoothstep(.46,.64,thicketField);
        double wetlands=Math.max(0.0,1.0-mire-thickets);
        SwampType dominant=mire>=wetlands && mire>=thickets
                ? SwampType.DEEP_MIRE
                : thickets>=wetlands ? SwampType.MANGROVE_THICKETS : SwampType.WETLANDS;
        return new SwampSample(dominant,wetlands,mire,thickets);
    }
}
