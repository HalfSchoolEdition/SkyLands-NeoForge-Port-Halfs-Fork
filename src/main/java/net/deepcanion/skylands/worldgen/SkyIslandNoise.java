package net.deepcanion.skylands.worldgen;

/**
 * Original deterministic value-noise helpers for SkyLands' floating terrain.
 * No world access or shared mutable state; neighboring chunks sample identical values.
 */
final class SkyIslandNoise {
    private SkyIslandNoise() { }

    static double noise2(double x, double z, long seed) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        double a = lerp(value(ix, 0, iz, seed), value(ix + 1, 0, iz, seed), fx);
        double b = lerp(value(ix, 0, iz + 1, seed), value(ix + 1, 0, iz + 1, seed), fx);
        return lerp(a, b, fz);
    }

    static double noise3(double x, double y, double z, long seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = smooth(x - ix), fy = smooth(y - iy), fz = smooth(z - iz);
        double a = lerp(value(ix, iy, iz, seed), value(ix + 1, iy, iz, seed), fx);
        double b = lerp(value(ix, iy, iz + 1, seed), value(ix + 1, iy, iz + 1, seed), fx);
        double c = lerp(value(ix, iy + 1, iz, seed), value(ix + 1, iy + 1, iz, seed), fx);
        double d = lerp(value(ix, iy + 1, iz + 1, seed), value(ix + 1, iy + 1, iz + 1, seed), fx);
        return lerp(lerp(a, b, fz), lerp(c, d, fz), fy);
    }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double value(int x, int y, int z, long seed) {
        long n = seed ^ ((long) x * 0x9E3779B97F4A7C15L)
                ^ ((long) y * 0xD1B54A32D192ED03L)
                ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        n = (n ^ (n >>> 30)) * 0xBF58476D1CE4E5B9L;
        n = (n ^ (n >>> 27)) * 0x94D049BB133111EBL;
        return (((n ^ (n >>> 31)) >>> 11) * 0x1.0p-52) - 1.0;
    }
}
