package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic cliff-attached basalt colonnades for Golden terrain. The
 * geometry and its eligibility use only world seed and SkyIslandShape, never
 * the live block state or neighboring chunks. Every chunk reconstructs the
 * same polygonal columns and writes only its own voxels.
 */
final class SkyTerrainBasaltGeometry {
    static final int CELL = 34;
    static final int MARGIN = 32; // Curved/staggered formations can extend beyond the old straight-line bounds.
    private static final int[][] DIRECTIONS = {
            {1, 0}, {0, 1}, {-1, 0}, {0, -1},
            {1, 1}, {-1, 1}, {-1, -1}, {1, -1}
    };

    private SkyTerrainBasaltGeometry() { }

    /** Each pillar is an irregular convex 5-7-gon extruded between flat Y caps. */
    record Pillar(double[] px, double[] pz, int bottom, int top,
                  int minX, int maxX, int minZ, int maxZ) {
        boolean contains(int x, int z) {
            double tx = x + .5, tz = z + .5;
            boolean inside = false;
            for (int i = 0, j = px.length - 1; i < px.length; j = i++) {
                if ((pz[i] > tz) != (pz[j] > tz)
                        && tx < (px[j] - px[i]) * (tz - pz[i]) /
                        (pz[j] - pz[i]) + px[i]) inside = !inside;
            }
            return inside;
        }

        boolean touchesChunk(int chunkX, int chunkZ) {
            return maxX >= chunkX && minX <= chunkX + 15
                    && maxZ >= chunkZ && minZ <= chunkZ + 15;
        }
    }

    record Formation(int anchorX, int anchorZ, SkyIslandShape.Island island,
                     SkyTerrainSubregions.GoldenType region, List<Pillar> pillars) { }

    static List<Formation> forChunk(long seed, int chunkX, int chunkZ,
                                    List<SkyIslandShape.Island> goldenIslands) {
        if (goldenIslands.isEmpty()) return List.of();
        ArrayList<Formation> result = new ArrayList<>();
        int minGX = Math.floorDiv(chunkX - MARGIN, CELL);
        int maxGX = Math.floorDiv(chunkX + 15 + MARGIN, CELL);
        int minGZ = Math.floorDiv(chunkZ - MARGIN, CELL);
        int maxGZ = Math.floorDiv(chunkZ + 15 + MARGIN, CELL);
        for (int gx = minGX; gx <= maxGX; gx++) {
            for (int gz = minGZ; gz <= maxGZ; gz++) {
                long key = SkyTerrainPatterns.mix(seed, gx, 0, gz, 0x762b1e9aL);
                SplittableRandom random = new SplittableRandom(key);
                int sx = gx * CELL + 7 + random.nextInt(20);
                int sz = gz * CELL + 7 + random.nextInt(20);
                SkyTerrainSubregions.GoldenSample region = SkyTerrainSubregions.golden(seed, sx, sz);
                // Plateau cliffs dominate, Meadows get occasional formations,
                // and established Podzol Forests remain mostly untouched.
                double chance = .77 * region.plateau() + .22 * region.meadows()
                        + .035 * region.forest();
                if (random.nextDouble() >= chance) continue;

                for (SkyIslandShape.Island island : goldenIslands) {
                    if (!island.touches(sx, sz)) continue;
                    Formation site = tryFormation(sx, sz, island, region, random);
                    if (site != null) {
                        boolean intersects = false;
                        for (Pillar p : site.pillars()) {
                            if (p.touchesChunk(chunkX, chunkZ)) {
                                intersects = true;
                                break;
                            }
                        }
                        if (intersects) result.add(site);
                        // A site belongs to at most one island; prevent duplicate
                        // formations where satellite landmasses overlap.
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static Formation tryFormation(int sx, int sz,
                                          SkyIslandShape.Island island,
                                          SkyTerrainSubregions.GoldenSample region,
                                          SplittableRandom random) {
        // Never intrude into Golden lake basins or their spillway channels.
        if (island.lake() != null && island.lake().plateauClearance(sx, sz) < .88)
            return null;
        int top = highestRock(island, sx, sz);
        if (top < SkyIslandShape.MIN_Y + 20) return null;

        int direction = -1, drop = 0;
        int start = random.nextInt(DIRECTIONS.length);
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int d = (start + i) % DIRECTIONS.length;
            int dx = DIRECTIONS[d][0], dz = DIRECTIONS[d][1];
            int aheadX = sx + dx * 9, aheadZ = sz + dz * 9;
            int neighbor = island.touches(aheadX, aheadZ)
                    ? highestRock(island, aheadX, aheadZ) : Integer.MIN_VALUE;
            int fall = neighbor == Integer.MIN_VALUE ? 48 : top - neighbor;
            if (fall > drop) {
                direction = d;
                drop = fall;
            }
        }
        // Require an actual island edge or a visibly steep terrace, never a
        // gentle Meadow slope. Keep very small rock ledges free of basalt.
        if (direction < 0 || drop < 8) return null;
        int dx = DIRECTIONS[direction][0], dz = DIRECTIONS[direction][1];
        double invLength = 1.0 / Math.sqrt(dx * dx + dz * dz);
        double outX = dx * invLength, outZ = dz * invLength;
        double sideX = -outZ, sideZ = outX;

        int companions = 2 + random.nextInt(4); // 3-6 pillars per formation.
        ArrayList<Pillar> pillars = new ArrayList<>(companions + 1);
        double mainX = sx + outX * (1.0 + random.nextDouble() * 1.5);
        double mainZ = sz + outZ * (1.0 + random.nextDouble() * 1.5);
        pillars.add(polygon(random, mainX, mainZ, 4.1 + 1.6 * random.nextDouble(),
                top - random.nextInt(3), 17 + random.nextInt(16)));

        // Mix bowed arcs, irregular staggered groups and the occasional
        // roughly straight colonnade. The shared curve is applied to each
        // companion's center, not to individual vertices: every column still
        // has a flat top/bottom and a consistent 5-7-sided cross-section.
        int layout = random.nextInt(4); // 0-1: arc, 2: staggered, 3: scattered line
        double bend = (random.nextBoolean() ? 1 : -1) * (.40 + random.nextDouble() * .26);
        double phase = random.nextDouble() * Math.PI * 2.0;
        double spacing = 3.7 + random.nextDouble() * .4;
        for (int i = 1; i <= companions; i++) {
            int order = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1);
            double along = order * spacing + (random.nextDouble() - .5) * 2.6;
            double outward = -1.0 + random.nextDouble() * 4.0;
            if (layout <= 1) {
                // A visible concave/convex arc of columns rather than a line.
                outward += bend * along * along / 14.0;
            } else if (layout == 2) {
                // Broken/staggered cliff-edge groups, never a ruler-straight row.
                outward += Math.sin(order * 1.55 + phase) * 2.5;
            }
            double cx = mainX + sideX * along + outX * outward;
            double cz = mainZ + sideZ * along + outZ * outward;
            // A few columns are tall, others terminate much higher or lower.
            // Each column has its own perfectly flat upper and lower ends.
            int cap = top - 1 - random.nextInt(5) + random.nextInt(3);
            int length = 10 + random.nextInt(19) + Math.min(6, drop / 9);
            pillars.add(polygon(random, cx, cz, 2.5 + random.nextDouble() * 1.9,
                    cap, length));
        }
        return new Formation(sx, sz, island, region.dominant(), List.copyOf(pillars));
    }

    private static Pillar polygon(SplittableRandom random, double cx, double cz,
                                  double radius, int top, int length) {
        int sides = 5 + random.nextInt(3);
        double rotation = random.nextDouble(0.0, Math.PI * 2.0);
        double[] px = new double[sides], pz = new double[sides];
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < sides; i++) {
            double angle = rotation + (Math.PI * 2.0 * i / sides);
            double vertexRadius = radius * (.91 + random.nextDouble() * .18);
            px[i] = cx + Math.cos(angle) * vertexRadius;
            pz[i] = cz + Math.sin(angle) * vertexRadius;
            minX = Math.min(minX, (int) Math.floor(px[i]));
            maxX = Math.max(maxX, (int) Math.ceil(px[i]));
            minZ = Math.min(minZ, (int) Math.floor(pz[i]));
            maxZ = Math.max(maxZ, (int) Math.ceil(pz[i]));
        }
        int safeTop = Math.min(SkyIslandShape.MAX_Y, top);
        return new Pillar(px, pz, Math.max(SkyIslandShape.MIN_Y, safeTop - length),
                safeTop, minX, maxX, minZ, maxZ);
    }

    private static int highestRock(SkyIslandShape.Island island, int x, int z) {
        if (!island.touches(x, z)) return Integer.MIN_VALUE;
        SkyIslandShape.Column column = island.column(x, z);
        for (int y = island.maxY(); y >= island.minY(); y--) {
            if (column.solid(y)) return y;
        }
        return Integer.MIN_VALUE;
    }
}
