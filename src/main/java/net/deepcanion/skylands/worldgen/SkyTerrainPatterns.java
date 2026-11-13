package net.deepcanion.skylands.worldgen;

/** Deterministic, continuous patch noise. Never aligns its patches to chunk borders. */
final class SkyTerrainPatterns {
    private SkyTerrainPatterns() { }

    static long mix(long seed, int x, int y, int z, long salt) {
        long h = seed ^ salt ^ (long)x * 0x9e3779b97f4a7c15L
                ^ (long)y * 0xbf58476d1ce4e5b9L ^ (long)z * 0x94d049bb133111ebL;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }
    static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    private static double fade(double v) { return v*v*(3 - 2*v); }
    private static double lerp(double a, double b, double t) { return a+(b-a)*t; }

    static double noise2(long seed, int x, int z, int cell, long salt) {
        int cx=Math.floorDiv(x,cell), cz=Math.floorDiv(z,cell);
        double fx=fade(Math.floorMod(x,cell)/(double)cell), fz=fade(Math.floorMod(z,cell)/(double)cell);
        double a=unit(mix(seed,cx,0,cz,salt)), b=unit(mix(seed,cx+1,0,cz,salt));
        double c=unit(mix(seed,cx,0,cz+1,salt)), d=unit(mix(seed,cx+1,0,cz+1,salt));
        return lerp(lerp(a,b,fx),lerp(c,d,fx),fz);
    }

    static double noise3(long seed, int x, int y, int z, int cell, long salt) {
        int cx=Math.floorDiv(x,cell), cy=Math.floorDiv(y,cell), cz=Math.floorDiv(z,cell);
        double fx=fade(Math.floorMod(x,cell)/(double)cell);
        double fy=fade(Math.floorMod(y,cell)/(double)cell);
        double fz=fade(Math.floorMod(z,cell)/(double)cell);
        double a=lerp(unit(mix(seed,cx,cy,cz,salt)),unit(mix(seed,cx+1,cy,cz,salt)),fx);
        double b=lerp(unit(mix(seed,cx,cy+1,cz,salt)),unit(mix(seed,cx+1,cy+1,cz,salt)),fx);
        double c=lerp(unit(mix(seed,cx,cy,cz+1,salt)),unit(mix(seed,cx+1,cy,cz+1,salt)),fx);
        double d=lerp(unit(mix(seed,cx,cy+1,cz+1,salt)),unit(mix(seed,cx+1,cy+1,cz+1,salt)),fx);
        return lerp(lerp(a,b,fy),lerp(c,d,fy),fz);
    }

    /** Irregular, world-aligned clay deposits beneath Swamp wetland water.
     * Warped patch outlines make lobes, bites and narrow peninsulas; the
     * fine-scale noise breaks up otherwise perfect circles without turning
     * the deposits into an even random speckle field.
     */
    static boolean wetlandClay(long seed,int x,int z) {
        int wx=x+(int)Math.round(8*(noise2(seed,x,z,13,0x6ae61L)-.5)
                +4*(noise2(seed,x,z,5,0x6ae62L)-.5));
        int wz=z+(int)Math.round(8*(noise2(seed,x,z,13,0x6ae63L)-.5)
                +4*(noise2(seed,x,z,5,0x6ae64L)-.5));
        double area=patch(seed,wx,wz,22,0x6ae19L);
        double irregular=.16*(noise2(seed,x,z,6,0x6ae65L)-.5)
                +.12*(noise2(seed,x,z,3,0x6ae66L)-.5);
        return area>.27+irregular;
    }

    /** Organic ellipsoidal clusters of irregular sizes and separation; >0 means inside. */
    static double patch(long seed, int x, int z, int spacing, long salt) {
        int gx=Math.floorDiv(x,spacing),gz=Math.floorDiv(z,spacing);
        double strength=-100;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            int ix=gx+dx,iz=gz+dz;
            long key=mix(seed,ix,0,iz,salt);
            if(unit(key)<.20) continue;  // irregular empty gaps
            double cx=ix*spacing + spacing*(.20+.60*unit(mix(key,1,0,0,11)));
            double cz=iz*spacing + spacing*(.20+.60*unit(mix(key,2,0,0,11)));
            double rx=4+spacing*(.12+.22*unit(mix(key,3,0,0,11)));
            double rz=4+spacing*(.12+.22*unit(mix(key,4,0,0,11)));
            double vx=(x-cx)/rx,vz=(z-cz)/rz;
            double edge=1-vx*vx-vz*vz;
            if(edge>-.35) {
                edge+=.22*(noise2(seed,x,z,7,salt^0x3d7L)-.5);
                strength=Math.max(strength,edge);
            }
        }
        return strength;
    }
}
