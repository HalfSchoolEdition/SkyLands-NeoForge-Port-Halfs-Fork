package net.deepcanion.skylands.worldgen;

/**
 * Single world-coordinate terrain adjustment shared by three established sky
 * themes. All noise is evaluated once per island X/Z column, never per voxel.
 * Underlying island silhouettes, caves, lakes and underside formations remain.
 */
final class SkyTerrainGentleShaping {
    record Sample(double flatness, double elevation, double roughness) {
        static final Sample NONE = new Sample(0,0,0);
    }
    private SkyTerrainGentleShaping() { }

    static Sample sample(long seed,int x,int z, boolean glaciated,boolean cherry,boolean swamp) {
        if(glaciated) {
            var s=SkyTerrainSubregions.glaciated(seed,x,z);
            double jagged=SkyTerrainPatterns.noise2(seed,x,z,31,0x644101L)-.5;
            return new Sample(.81*s.plains()+.14*s.crystals(),
                    -1.6*s.plains()+(7.5+6*jagged)*s.crystals(), .78*s.crystals());
        }
        if(cherry) {
            var s=SkyTerrainSubregions.cherry(seed,x,z);
            // Jagged mountain belts INSIDE normal Cherry Grove districts;
            // meadows remain level and blossom thickets gently uneven.
            double belt=SkyTerrainPatterns.noise2(seed,x,z,112,0x642e01L);
            double mountain=s.grove()*Math.max(0,Math.min(1,(belt-.48)/.22));
            double ridge=Math.abs(SkyTerrainPatterns.noise2(seed,x,z,31,0x642e02L)-.5)*2;
            double sharp=Math.pow(ridge,1.6);
            return new Sample(.78*s.meadows()+.18*s.thickets(),
                    -1.5*s.meadows()+1.5*s.thickets()
                        +(9.0+27.0*sharp)*mountain,
                    .16*s.thickets()+1.7*mountain);
        }
        if(swamp) {
            var s=SkyTerrainSubregions.swamp(seed,x,z);
            return new Sample(.56*s.mire()+.22*s.thickets(),
                    -2.4*s.mire()+1.3*s.thickets(), .13*s.thickets());
        }
        return Sample.NONE;
    }
}
