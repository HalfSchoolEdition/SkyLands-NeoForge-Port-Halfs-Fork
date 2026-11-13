package net.deepcanion.skylands.worldgen;

/** Pure geometry for irregular but connected molten-metal lake footprints. */
final class SkyTerrainMoltenBasin {
    private SkyTerrainMoltenBasin() { }
    private static final int[] STEP_X = {1, -1, 0, 0};
    private static final int[] STEP_Z = {0, 0, 1, -1};

    // A mask includes an additional margin for the stone rim around the liquid.
    record Basin(int extent, boolean[][] liquid, byte[][] ceiling) {
        boolean inside(int dx, int dz) {
            return liquid[dx + extent][dz + extent];
        }
        int height(int dx, int dz) {
            return ceiling[dx + extent][dz + extent];
        }
        boolean rim(int dx, int dz) {
            if (inside(dx, dz)) return false;
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    if (Math.abs(dx + ox) <= extent && Math.abs(dz + oz) <= extent
                            && inside(dx + ox, dz + oz)) return true;
                }
            }
            return false;
        }
    }

    /** Per-cell roughness, elongated axes, and uneven chamber ceiling. */
    static Basin makeBasin(long seed, int x, int z, int radius, int extent) {
        int size = 2 * extent + 1;
        boolean[][] liquid = new boolean[size][size];
        byte[][] ceiling = new byte[size][size];
        double xScale = .89 + .21 * SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(seed, 1, 0, 0, 0x38f8L));
        double zScale = .89 + .21 * SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(seed, 2, 0, 0, 0x38f8L));
        int mainHeight = 3 + (int) (SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(seed, 3, 0, 0, 0x38f8L)) * 2);

        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                int wx = x + dx, wz = z + dz;
                double distance = Math.hypot(dx / xScale, dz / zScale);
                double smooth = SkyTerrainPatterns.noise2(seed, wx, wz, 3, 0x765cdL) - .5;
                double rough = SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(seed, wx, 0, wz, 0x765ceL)) - .5;
                // Always retain a connected central body; raggedness belongs
                // mostly to the shoreline rather than creating isolated puddles.
                boolean wet = distance < radius - 1.0
                        || distance < radius + 1.4 * smooth + .55 * rough;
                if (!wet) continue;
                liquid[dx + extent][dz + extent] = true;
                double ceilingNoise = SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(seed, wx, 0, wz, 0x765cfL));
                int height = mainHeight + (ceilingNoise > .78 ? 1 : ceilingNoise < .18 ? -1 : 0);
                if (distance > radius - .4) height = Math.max(2, height - 1);
                ceiling[dx + extent][dz + extent] = (byte) height;
            }
        }
        // Rare edge protrusions can be disconnected by the rough shoreline.
        // Keep only the connected lake containing the centre: never create
        // isolated single-source metal puddles outside the protected basin.
        boolean[][] connected = new boolean[size][size];
        int[] queue = new int[size * size];
        int head = 0, tail = 0;
        queue[tail++] = extent * size + extent;
        connected[extent][extent] = true;
        while (head < tail) {
            int packed = queue[head++];
            int ix = packed / size, iz = packed % size;
            for (int d = 0; d < 4; d++) {
                int nx = ix + STEP_X[d], nz = iz + STEP_Z[d];
                if (nx < 0 || nz < 0 || nx >= size || nz >= size
                        || connected[nx][nz] || !liquid[nx][nz]) continue;
                connected[nx][nz] = true;
                queue[tail++] = nx * size + nz;
            }
        }
        for (int ix = 0; ix < size; ix++) {
            for (int iz = 0; iz < size; iz++) {
                if (!connected[ix][iz]) {
                    liquid[ix][iz] = false;
                    ceiling[ix][iz] = 0;
                }
            }
        }
        return new Basin(extent, liquid, ceiling);
    }

}
