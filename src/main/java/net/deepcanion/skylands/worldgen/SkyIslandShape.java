package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Original deterministic, multi-scale volumetric geometry for floating islands.
 * A site owns its whole island; chunk boundaries and biome transitions must never
 * be used as clipping planes. No other Minecraft state is needed to sample a voxel.
 */
public final class SkyIslandShape {
    public static final int GRID = 144;
    public static final int MIN_Y = 270;
    public static final int MAX_Y = 510;

    private SkyIslandShape() { }

    public enum Profile { ROUNDED, CRAG, TERRACE, FRACTURED, BOWL }

    private static long siteKey(long seed, int gx, int gz) {
        return mix(seed ^ ((long) gx * 0x9E3779B97F4A7C15L)
                ^ ((long) gz * 0xC2B2AE3D27D4EB4FL));
    }
    public static int siteX(long seed, int gx, int gz) {
        return gx * GRID + 18 + (int) (unit(siteKey(seed, gx, gz), 2) * 108);
    }
    public static int siteZ(long seed, int gx, int gz) {
        return gz * GRID + 18 + (int) (unit(siteKey(seed, gx, gz), 3) * 108);
    }
    public static boolean hasSite(long seed, int gx, int gz) {
        return unit(siteKey(seed, gx, gz), 1) >= 0.20;
    }

    // The same world-seeded island site is consulted from many neighboring chunks
    // and several independent features. Keep only a small, per-worker LRU of
    // immutable geometry. The seed and theme are part of the key, so different
    // worlds and biomes never share shapes, and no Level/Chunk references remain.
    private record SiteCacheKey(long seed, int gridX, int gridZ, String theme,
                                boolean surfaceCaves, int caveFrequency) { }
    /** Worldgen-only knobs. Snapshot in the cache key; settings can change at a world restart. */
    private record SurfaceCaveSettings(boolean enabled, int frequency) { }
    private static volatile SurfaceCaveSettings surfaceCaveSettings = new SurfaceCaveSettings(true, 100);

    public static void configureSurfaceCaves(boolean enabled, int frequency) {
        int clamped = Math.max(0, Math.min(300, frequency));
        SurfaceCaveSettings current = surfaceCaveSettings;
        if (current.enabled() != enabled || current.frequency() != clamped)
            surfaceCaveSettings = new SurfaceCaveSettings(enabled, clamped);
    }
    private static final int SITE_CACHE_LIMIT = 64;
    private static final ThreadLocal<LinkedHashMap<SiteCacheKey, List<Island>>> SITE_CACHE =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(SITE_CACHE_LIMIT, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<SiteCacheKey, List<Island>> entry) {
                    return size() > SITE_CACHE_LIMIT;
                }
            });

    public static List<Island> sites(long worldSeed, int gridX, int gridZ, String theme) {
        SurfaceCaveSettings settings = surfaceCaveSettings;
        SiteCacheKey cacheKey = new SiteCacheKey(worldSeed, gridX, gridZ, theme,
                settings.enabled(), settings.frequency());
        var cache = SITE_CACHE.get();
        List<Island> cached = cache.get(cacheKey);
        if (cached != null) return cached;
        List<Island> generated = generateSites(worldSeed, gridX, gridZ, theme,
                settings.enabled() ? settings.frequency() : 0);
        cache.put(cacheKey, generated);
        return generated;
    }

    private static List<Island> generateSites(long worldSeed, int gridX, int gridZ, String theme,
                                             int caveFrequency) {
        long key = siteKey(worldSeed, gridX, gridZ);
        if (unit(key, 1) < 0.20) return List.of();
        int x = siteX(worldSeed, gridX, gridZ);
        int z = siteZ(worldSeed, gridX, gridZ);
        int y = 356 + (int) (unit(key, 4) * 62);
        double radius = 43 + unit(key, 5) * 37;
        ArrayList<Island> result = new ArrayList<>(5);
        Island main = create(x, y, z, radius, key, profile(theme, key), theme, true, worldSeed, caveFrequency);
        result.add(main);
        // The surface, not the island's nominal centre, is the visible reference.
        // Especially in Glaciated and Swamp terrain the main shelf rises well
        // above a small satellite's top. Sample it ONCE per cached site.
        int mainTop = centralSurfaceY(main);
        int satellites = 1 + (int) (unit(key, 6) * 3);
        for (int i = 0; i < satellites; i++) {
            double angle = unit(key, 10 + i) * Math.PI * 2;
            double distance = radius * 0.83 + 18 + unit(key, 20 + i) * 34;
            int sx = x + (int) Math.round(Math.cos(angle) * distance);
            int sz = z + (int) Math.round(Math.sin(angle) * distance);
            long sk = mix(key + i * 1777L + 0x24c5171dL);
            double satelliteRadius = 12 + unit(key, 30 + i) * 20;
            Profile satelliteShape = satelliteProfile(theme, sk);
            int sy = satelliteCenterY(y, mainTop, sk, theme, satelliteRadius, satelliteShape,
                    -22 + (int) (unit(key, 40 + i) * 46));
            result.add(create(sx, sy, sz, satelliteRadius,
                    sk, satelliteShape, theme, false, worldSeed, caveFrequency));
        }
        return List.copyOf(result);
    }

    /** Actual highest rock at the anchor column, once during site creation. */
    private static int centralSurfaceY(Island island) {
        int x = (int)Math.round(island.centerX());
        int z = (int)Math.round(island.centerZ());
        Column column = island.column(x,z);
        for (int y = island.maxY(); y >= island.minY(); y--)
            if (column.solid(y)) return y;
        return (int)Math.round(island.centerY() + 28);
    }

    /**
     * Choose the SMALL island's intended surface height relative to the main
     * rock shelf, then translate it to a centre height. Unlike a flat bonus to
     * centre-Y, this works with tall Glaciated/Swamp main terrain.
     */
    private static int satelliteCenterY(int mainY, int mainTop, long key, String theme,
                                        double radius, Profile profile, int oldOffset) {
        double tier=unit(key,591);
        if(tier < .18) return mainY + oldOffset; // retain some genuinely low satellites
        int surfaceDifference;
        if(tier < .60) surfaceDifference=-11 + (int)(unit(key,592)*30); // roughly level
        else if(tier < .85) surfaceDifference=18 + (int)(unit(key,592)*24);
        else if(tier < .96) surfaceDifference=41 + (int)(unit(key,592)*25);
        else surfaceDifference=65 + (int)(unit(key,592)*22); // exceptional high islands

        double upper = 13 + radius*.14 + unit(key,63)*5;
        if(profile==Profile.CRAG)upper*=1.25;
        else if(profile==Profile.TERRACE)upper*=.83;
        // Allow for the typical top-surface warp/lobe variation. No extra
        // island sampling or allocations are necessary for this estimate.
        int expectedTopAboveCentre=(int)Math.round(upper+8);
        int desiredCentre=mainTop+surfaceDifference-expectedTopAboveCentre;
        // Leave room for island caps, glacial ice and other sky structures.
        int reserve = switch(theme) {
            case "arid" -> 78;
            case "cherry" -> 62;
            case "golden" -> 49;
            case "lavic" -> 46;
            case "glaciated" -> 48;
            default -> 44;
        };
        return Math.max(MIN_Y+38,Math.min(desiredCentre,MAX_Y-reserve));
    }

    private static Island create(int x, int y, int z, double radius, long key, Profile profile,
                                 String theme, boolean primary, long worldSeed, int caveFrequency) {
        ArrayList<Lobe> lobes = new ArrayList<>(9);
        ArrayList<Cavity> cuts = new ArrayList<>(9);
        ArrayList<Spire> roots = new ArrayList<>(8);
        double rotation = unit(key, 60) * Math.PI * 2;
        double ax = radius * (0.83 + unit(key, 61) * 0.35);
        double az = radius * (0.61 + unit(key, 62) * 0.46);
        double upper = 13 + radius * .14 + unit(key, 63) * 5;
        double lower = 20 + radius * .31 + unit(key, 64) * 10;
        switch (profile) {
            case CRAG -> { upper *= 1.25; lower *= 1.22; ax *= .92; }
            case TERRACE -> { ax *= 1.13; az *= 1.12; upper *= .83; lower *= .88; }
            case FRACTURED -> { ax *= .92; az *= .92; lower *= 1.19; }
            case BOWL -> { upper *= 1.05; lower *= 1.03; }
            default -> { }
        }
        lobes.add(new Lobe(x, y, z, ax, az, upper, lower, rotation, key, profile));

        // Broad asymmetric ridges, offset hills, and terraces reshape the silhouette.
        // Not all secondary lobes are below the main mass: some form high ridgelines.
        int attachments = radius > 40 ? 3 + (int) (unit(key, 65) * 3) : 1 + (int) (unit(key, 65) * 2);
        if (profile == Profile.FRACTURED) attachments++;
        for (int i = 0; i < attachments; i++) {
            double angle = rotation + unit(key, 70 + i) * Math.PI * 2;
            double distance = radius * (.37 + unit(key, 80 + i) * .49);
            double lx = x + Math.cos(angle) * distance;
            double lz = z + Math.sin(angle) * distance;
            double ly = y - 20 + unit(key, 90 + i) * 39;
            double size = radius * (.22 + unit(key, 100 + i) * .31);
            long lk = mix(key + (i + 1L) * 83492791L);
            lobes.add(new Lobe(lx, ly, lz, size * (.9 + unit(key, 120 + i) * .75),
                    size * (.72 + unit(key, 130 + i) * .55),
                    7 + size * (.24 + unit(key, 140 + i) * .4),
                    11 + size * (.42 + unit(key, 150 + i) * .7),
                    angle, lk, profile));
        }

        // Erosion pockets and side-opened voids break otherwise continuous walls.
        if (radius >= 40 && unit(key, 160) < .85) {
            double angle = rotation + unit(key, 161) * Math.PI * 2;
            double off = radius * (.40 + unit(key, 162) * .22);
            cuts.add(new Cavity(x + Math.cos(angle) * off, y - 8,
                    z + Math.sin(angle) * off, radius * .26,
                    6 + radius * .10, radius * .20, angle, mix(key ^ 0xb2bc77L)));
        }
        if (radius >= 47 && unit(key, 163) < .62) {
            double angle = rotation + unit(key, 164) * Math.PI * 2;
            double off = radius * (.32 + unit(key, 165) * .32);
            cuts.add(new Cavity(x + Math.cos(angle) * off, y - 4 - unit(key, 166) * 12,
                    z + Math.sin(angle) * off, 12 + radius * .25,
                    5 + radius * .065, 8 + radius * .13, angle, mix(key ^ 0x901111afL)));
        }
        if (radius >= 42 && unit(key, 167) < .52) {
            double angle = rotation + unit(key, 168) * Math.PI * 2;
            double off = radius * (.55 + unit(key, 169) * .26);
            cuts.add(new Cavity(x + Math.cos(angle) * off, y + 8,
                    z + Math.sin(angle) * off, radius * .19, 12 + radius * .10,
                    radius * .13, angle, mix(key ^ 0x993df26bL)));
        }
        // Vertical flutes: multiple overlapping, individually warped hanging rock
        // buttresses. Their heights, widths and directions are independent of
        // the ellipsoid, so the lower silhouette is no longer a smooth bowl.
        if (radius > 39) {
            int count = 5 + (int)(unit(key, 191) * 6);
            if (profile == Profile.CRAG || profile == Profile.FRACTURED) count += 2;
            for (int i = 0; i < count; i++) {
                double angle = rotation + i * 2.399963229728653 + (unit(key, 200+i)-.5)*.55;
                double reach = radius * (.20 + unit(key, 220+i) * .53);
                double sx = x + Math.cos(angle) * reach;
                double sz = z + Math.sin(angle) * reach;
                double width = 7 + radius * (.065 + unit(key, 240+i) * .14);
                double length = 19 + radius * (.27 + unit(key, 260+i) * .62);
                roots.add(new Spire(sx, y + 4 + unit(key, 280+i)*6, sz,
                    width, length, angle, mix(key + i * 0x4f1bbcdcbL)));
            }
        }
        // Large-scale underside excavations and transverse hollows, not just
        // small enclosed round caves. Several open out of a cliff or underside.
        if (radius > 42) {
            int count = 2 + (int)(unit(key, 301)*3);
            for (int i = 0; i < count; i++) {
                double angle = rotation + i * 2.4 + unit(key, 310+i);
                double off = radius * (.39 + .43 * unit(key, 330+i));
                double depth = 11 + radius * (.14 + .14 * unit(key, 350+i));
                cuts.add(new Cavity(x + Math.cos(angle)*off,
                    y - depth, z + Math.sin(angle)*off,
                    radius * (.21 + .13*unit(key, 370+i)),
                    10 + radius * (.16 + .13 * unit(key, 390+i)),
                    radius * (.10 + .13*unit(key, 410+i)), angle,
                    mix(key ^ (0x165667b3L + i * 773L))));
            }
        }
        if (profile == Profile.BOWL && radius > 30) {
            cuts.add(new Cavity(x, y + upper * .88, z, radius * .31,
                    upper * .46, radius * .31, rotation, mix(key ^ 0x876ad19bL)));
        }
        List<Lobe> finishedLobes = List.copyOf(lobes);
        List<Cavity> finishedCuts = List.copyOf(cuts);
        List<Spire> finishedRoots = List.copyOf(roots);
        List<Tunnel> worms = List.copyOf(makeWorms(x, y, z, radius, key));
        Lake lake = Lake.create(x, y, z, radius, Math.min(ax, az), key, theme, primary,worldSeed);
        CavePool cavePool = CavePool.create(x, y, z, radius, Math.min(ax, az), key, theme, primary);
        // An outlet is part of the SAME immutable, site-seeded density shape as
        // the lake. Probe the unmodified rock once when making the site so the
        // channel can reach a real cliff, not end halfway across the meadow.
        // Frozen lakes remain sealed; underground Lavic pools are independent.
        if (lake != null && !lake.frozen && unit(key, 690) < .995) {
            Island uncarved = new Island(finishedLobes, finishedCuts, finishedRoots,
                    worms, profile, null, cavePool, worldSeed, false, false, false, false, false, false);
            lake = lake.withSpillways(uncarved);
        }
        Island unmodified = new Island(finishedLobes, finishedCuts, finishedRoots,
                worms, profile, lake, cavePool, worldSeed,
                "golden".equals(theme), "arid".equals(theme), "lavic".equals(theme),
                "glaciated".equals(theme), "cherry".equals(theme), "swamp".equals(theme));
        if (caveFrequency <= 0) return unmodified;
        // This is a site-generation operation only, never per voxel or per chunk.
        // Preflight against the untouched island and its lakes before adding any cuts.
        List<SkySurfaceCaveSites.Segment> surface = SkySurfaceCaveSites.generate(
                unmodified, key, theme, primary, caveFrequency);
        if (surface.isEmpty()) return unmodified;
        ArrayList<Tunnel> carved = new ArrayList<>(worms.size() + surface.size());
        carved.addAll(worms);
        for (var segment : surface) carved.add(new Tunnel(segment.x0(), segment.y0(), segment.z0(),
                segment.x1(), segment.y1(), segment.z1(), segment.radius(), segment.verticalScale()));
        return new Island(finishedLobes, finishedCuts, finishedRoots,
                List.copyOf(carved), profile, lake, cavePool, worldSeed,
                "golden".equals(theme), "arid".equals(theme), "lavic".equals(theme),
                "glaciated".equals(theme), "cherry".equals(theme), "swamp".equals(theme));
    }

    /**
     * A few winding, largely horizontal passages. Built from the site seed,
     * never from chunk coordinates, so every chunk samples the SAME tunnels.
     * Unlike vanilla carvers this does not remove existing trees/structures:
     * it changes only the pre-placement island density mask.
     */
    private static List<Tunnel> makeWorms(double x, double y, double z, double radius, long key) {
        ArrayList<Tunnel> result = new ArrayList<>();
        if (radius < 24 || unit(key, 490) < .16) return result;
        int count = radius >= 40 ? 2 + (unit(key, 491) < .42 ? 1 : 0) : 1;
        for (int path = 0; path < count; path++) {
            double theta = 2 * Math.PI * unit(key, 500 + path);
            double dx = Math.cos(theta), dz = Math.sin(theta);
            double bx = -dz, bz = dx;
            double side = (unit(key, 510 + path) - .5) * radius * .48;
            double start = -radius * (.55 + .19 * unit(key, 520 + path));
            double end = radius * (.53 + .33 * unit(key, 530 + path));
            double floor = y - 7 - 8 * unit(key, 540 + path);
            double bend = 5 + 9 * unit(key, 550 + path);
            double phase = unit(key, 560 + path) * Math.PI * 2;
            double rad = 2.15 + 1.20 * unit(key, 570 + path);
            double lastX = 0, lastY = 0, lastZ = 0;
            for (int i = 0; i <= 9; i++) {
                double t = i / 9.0;
                double along = start + (end - start) * t;
                double cross = side + bend * Math.sin(t * Math.PI * 1.45 + phase)
                        - bend * Math.sin(phase) * (1 - t);
                double px = x + dx * along + bx * cross;
                double pz = z + dz * along + bz * cross;
                double py = floor + 4.4 * Math.sin(t * Math.PI * 2 + phase)
                        + (unit(key, 580 + path) - .5) * 9 * t;
                if (i > 0) result.add(new Tunnel(lastX, lastY, lastZ, px, py, pz,
                        rad * (.88 + .25 * Math.sin(t * 6 + phase))));
                lastX = px; lastY = py; lastZ = pz;
            }
        }
        return result;
    }

    /** Bounded line segment sampled at a single column, only then tested by Y. */
    private record Tunnel(double x0, double y0, double z0,
                          double x1, double y1, double z1, double radius,
                          double verticalScale) {
        Tunnel(double x0, double y0, double z0,
               double x1, double y1, double z1, double radius) {
            this(x0,y0,z0,x1,y1,z1,radius,1.0);
        }
        TunnelSlice column(int x, int z) {
            // Bounding box first: distant passages do no projection or trig.
            if (x < Math.min(x0,x1)-radius || x > Math.max(x0,x1)+radius
                    || z < Math.min(z0,z1)-radius || z > Math.max(z0,z1)+radius) return null;
            double dx = x1 - x0, dz = z1 - z0;
            double length = dx * dx + dz * dz;
            double t = length < 1e-8 ? 0 : Math.max(0, Math.min(1,
                    ((x - x0) * dx + (z - z0) * dz) / length));
            double px = x0 + dx * t, pz = z0 + dz * t;
            double d2 = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (d2 > radius * radius) return null;
            return new TunnelSlice(y0 + (y1 - y0) * t, radius * radius - d2, verticalScale);
        }
    }
    private record TunnelSlice(double y, double remainingRadius2, double verticalScale) {
        boolean contains(int y) {
            double dy = (y - this.y) / verticalScale;
            return dy * dy <= remainingRadius2;
        }
    }

    private static Profile profile(String theme, long key) {
        double v = unit(key, 110);
        return switch (theme) {
            case "glaciated" -> v < .42 ? Profile.CRAG : v < .68 ? Profile.FRACTURED : Profile.ROUNDED;
            case "arid" -> v < .53 ? Profile.TERRACE : v < .75 ? Profile.ROUNDED : Profile.BOWL;
            case "lavic" -> v < .40 ? Profile.CRAG : v < .78 ? Profile.FRACTURED : Profile.BOWL;
            case "swamp" -> v < .48 ? Profile.ROUNDED : v < .88 ? Profile.TERRACE : Profile.BOWL;
            case "cherry" -> v < .75 ? Profile.ROUNDED : Profile.TERRACE;
            default -> v < .52 ? Profile.ROUNDED : v < .72 ? Profile.TERRACE : v < .89 ? Profile.FRACTURED : Profile.BOWL;
        };
    }
    private static Profile satelliteProfile(String theme, long key) {
        double v = unit(key, 111);
        if ("arid".equals(theme)) return v < .62 ? Profile.TERRACE : Profile.ROUNDED;
        if ("glaciated".equals(theme) || "lavic".equals(theme)) return v < .58 ? Profile.CRAG : Profile.ROUNDED;
        return v < .79 ? Profile.ROUNDED : Profile.FRACTURED;
    }

    public static final class Island {
        private final List<Lobe> lobes;
        private final List<Cavity> cavities;
        private final List<Spire> roots;
        private final List<Tunnel> worms;
        private final Lake lake;
        private final CavePool cavePool;
        private final Profile profile;
        private final long worldSeed;
        private final boolean golden, arid, lavic, glaciated, cherry, swamp;
        private final double minX, maxX, minZ, maxZ;
        private final int minY, maxY;

        private Island(List<Lobe> lobes, List<Cavity> cavities, List<Spire> roots,
                       List<Tunnel> worms, Profile profile, Lake lake, CavePool cavePool,
                       long worldSeed, boolean golden, boolean arid, boolean lavic,
                       boolean glaciated,boolean cherry,boolean swamp) {
            this.lobes = lobes; this.cavities = cavities;
            this.roots = roots; this.worms = worms; this.profile = profile; this.lake = lake;
            this.cavePool = cavePool; this.worldSeed = worldSeed; this.golden = golden; this.arid = arid; this.lavic = lavic;
            this.glaciated=glaciated;this.cherry=cherry;this.swamp=swamp;
            double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
            double near = Double.POSITIVE_INFINITY, far = Double.NEGATIVE_INFINITY;
            int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
            for (Lobe l : lobes) {
                // Conservative bounds include even the largest footprint/surface warp.
                double extent = Math.max(l.rx, l.rz) * 1.85 + 12;
                left = Math.min(left, l.x - extent);
                right = Math.max(right, l.x + extent);
                near = Math.min(near, l.z - extent);
                far = Math.max(far, l.z + extent);
                bottom = Math.min(bottom, (int) Math.floor(l.y - l.lower - 23));
                top = Math.max(top, (int) Math.ceil(l.y + l.upper + 23));
            }
            for (Spire r : roots) {
                left = Math.min(left, r.x - r.width*2 - 8);
                right = Math.max(right, r.x + r.width*2 + 8);
                near = Math.min(near, r.z - r.width*2 - 8);
                far = Math.max(far, r.z + r.width*2 + 8);
                bottom = Math.min(bottom, (int)Math.floor(r.top-r.height-8));
                top = Math.max(top, (int)Math.ceil(r.top+8));
            }
            minX = left; maxX = right; minZ = near; maxZ = far;
            // Highlands can rise above the old lobe top; bound the extra cap
            // conservatively, without enlarging the island's X/Z footprint.
            minY = Math.max(MIN_Y, bottom); maxY = Math.min(MAX_Y, top + (arid ? 48 : lavic ? 20 : 0) + (glaciated ? 18 : 0));
        }
        public Profile profile() { return profile; }
        public Lake lake() { return lake; }
        public CavePool cavePool() { return cavePool; }
        public double centerX() { return lobes.get(0).x; }
        public double centerZ() { return lobes.get(0).z; }
        public double centerY() { return lobes.get(0).y; }
        public double primaryRadius() { return Math.min(lobes.get(0).rx,lobes.get(0).rz); }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public boolean intersects(int x0, int z0, int x1, int z1) {
            return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1 && minY <= maxY;
        }
        public boolean touches(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
        /** Cache XY-independent terms once per column; this is the hot path in worldgen. */
        public Column column(int x, int z) {
            // Skip expensive theme noise in broad empty corners of the island's
            // AABB; this preflight uses the same lobe bounds as l.column().
            SkyTerrainPlateaus.Sample plateau = SkyTerrainPlateaus.NONE;
            SkyTerrainAridShaping.Sample aridShape = SkyTerrainAridShaping.NONE;
            SkyTerrainLavicShaping.Sample lavicShape = SkyTerrainLavicShaping.NONE;
            SkyTerrainGentleShaping.Sample gentleShape = SkyTerrainGentleShaping.Sample.NONE;
            boolean sampledTerrain = false;
            ArrayList<LobeColumn> active = new ArrayList<>(lobes.size());
            for (Lobe l : lobes) {
                if (!l.mayContainXZ(x, z)) continue;
                if (!sampledTerrain) {
                    if (golden) plateau = SkyTerrainPlateaus.sample(worldSeed, x, z, lake);
                    if (arid) aridShape = SkyTerrainAridShaping.sample(worldSeed, x, z);
                    if (lavic) lavicShape = SkyTerrainLavicShaping.sample(worldSeed, x, z);
                    if (glaciated || cherry || swamp)
                        gentleShape = SkyTerrainGentleShaping.sample(worldSeed,x,z,glaciated,cherry,swamp);
                    sampledTerrain = true;
                }
                LobeColumn column = l.column(x, z, plateau, aridShape, lavicShape, gentleShape);
                if (column != null) active.add(column);
            }
            ArrayList<SpireColumn> hanging = new ArrayList<>(roots.size());
            for (Spire r : roots) {
                SpireColumn c = r.column(x,z);
                if (c != null) hanging.add(c);
            }
            ArrayList<TunnelSlice> passages = new ArrayList<>();
            for (Tunnel worm : worms) {
                TunnelSlice cross = worm.column(x, z);
                if (cross != null) passages.add(cross);
            }
            return new Column(x, z, active, hanging, cavities, passages,
                    lake == null ? null : lake.sample(x,z),
                    cavePool == null ? null : cavePool.sample(x,z), minY, maxY);
        }
        public boolean solid(int x, int y, int z) {
            return touches(x, z) && y >= minY && y <= maxY && column(x, z).solid(y);
        }
    }

    /**
     * A lake is carved into the island's existing terrain, then its banks blend
     * back to the original height field, much like structure terrain adaptation.
     * Every coordinate is sampled from its world seed: no chunk boundary seams.
     */
    public static final class Lake {
        public final int waterY;
        public final boolean frozen;
        public final String theme;
        private final double x, z, rx, rz;
        private final long key;
        private final long worldSeed;
        // Only a tiny minority of lakes own a channel. Store its preflighted
        // footprint as immutable packed X/Z positions, making per-column lookup
        // constant-time and independent of neighboring chunk generation order.
        private final Set<Long> outlets;
        private Lake(double x, double z, double rx, double rz, int waterY,
                     String theme, long key, long worldSeed) {
            this(x, z, rx, rz, waterY, theme, key, worldSeed, Set.of());
        }
        private Lake(double x, double z, double rx, double rz, int waterY,
                     String theme, long key,long worldSeed, Set<Long> outlets) {
            this.x=x; this.z=z; this.rx=rx; this.rz=rz; this.waterY=waterY;
            this.theme=theme; this.frozen="glaciated".equals(theme); this.key=key;
            this.worldSeed=worldSeed;
            this.outlets=outlets;
        }
        static Lake create(int x,int y,int z,double radius,double stableWidth,long key,String theme,
                           boolean primary,long worldSeed) {
            // Lavic lakes are more common than before; allow slightly smaller
            // primary landmasses without changing the contained lake bowl.
            if (!primary || radius < ("lavic".equals(theme) ? 45 : 47)) return null;
            boolean frozen="glaciated".equals(theme);
            boolean swamp="swamp".equals(theme);
            boolean golden="golden".equals(theme);
            boolean cherry="cherry".equals(theme);
            boolean lavic="lavic".equals(theme);
            if (!frozen && !swamp && !golden && !cherry && !lavic) return null;
            // Swamp wetlands are frequent; big frozen lakes are landmarks.
            // Smaller Golden/Cherry lakes and molten-metal lakes are occasional.
            // Deep Mire centers are more likely to own a sizeable wetland,
            // while familiar Swamp Wetlands keep their original 53% chance.
            double mire=swamp ? SkyTerrainSubregions.swamp(worldSeed,x,z).mire() : 0.0;
            double chance=frozen?.30:swamp?.53+.20*mire:golden?.29:cherry?.33:.49;
            if (unit(key,601)>chance) return null;
            double shiftX=(unit(key,602)-.5)*radius*.13;
            double shiftZ=(unit(key,603)-.5)*radius*.13;
            double scale=frozen?.51+.11*unit(key,604)
                    :swamp?.55+.12*unit(key,604)+.09*mire
                    :lavic?.31+.11*unit(key,604)
                    :.37+.13*unit(key,604);
            int waterY=y+(int)Math.round(8+radius*.12+unit(key,605)*3);
            double lakeRadius=Math.min(lavic?28:40,stableWidth*scale);
            return new Lake(x+shiftX,z+shiftZ,lakeRadius,
                    lakeRadius*(.81+.19*unit(key,606)),waterY,theme,key,worldSeed);
        }
        /**
         * Try a handful of different compass bearings, retaining at most one
         * genuine spillway (occasionally two). The shore opening must be both
         * connected to the lake and supported by existing island rock, and it
         * must end where the sky cliff really falls away. If none qualifies,
         * this lake simply remains closed.
         */
        private Lake withSpillways(Island uncarved) {
            Set<Long> cells = new HashSet<>();
            int wanted = unit(key, 691) < .24 ? 3 : unit(key, 692) < .80 ? 2 : 1;
            java.util.ArrayList<Double> usedAngles = new java.util.ArrayList<>(3);
            for (int candidate = 0; candidate < 24 && wanted > 0; candidate++) {
                double angle = 2 * Math.PI * unit(key, 701 + candidate);
                boolean tooClose = false;
                for (double previousAngle : usedAngles) {
                    if (Math.cos(angle - previousAngle) > .50) { tooClose = true; break; }
                }
                if (tooClose) continue;
                int width = 1 + (int)(unit(key, 740 + candidate) * 3); // 1-3 block outlets
                Set<Long> path = makeSpillway(uncarved, angle, width, candidate);
                if (path.isEmpty()) continue;
                cells.addAll(path);
                usedAngles.add(angle);
                wanted--;
            }
            if (cells.isEmpty()) return this;
            return new Lake(x, z, rx, rz, waterY, theme, key, worldSeed, Set.copyOf(cells));
        }

        private Set<Long> makeSpillway(Island uncarved, double angle, int width, int salt) {
            double forwardX = Math.cos(angle), forwardZ = Math.sin(angle);
            double sideX = -forwardZ, sideZ = forwardX;
            double shoreRadius = 1.0 / Math.hypot(forwardX / rx, forwardZ / rz);
            double start = shoreRadius * .63; // well inside the lake itself
            double maxDistance = Math.min(108, shoreRadius + 85);
            double phase = 2 * Math.PI * unit(key, 770 + salt);
            ArrayList<int[]> centers = new ArrayList<>();
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            boolean reachedCliff = false;
            for (double d = start; d <= maxDistance; d += .5) {
                // Gentle, sub-block bends rather than a perfectly straight cut.
                double bend = .65 * Math.sin(d * .105 + phase)
                        + .45 * Math.sin(d * .21 + phase * 1.7);
                int px = (int)Math.round(x + forwardX*d + sideX*bend);
                int pz = (int)Math.round(z + forwardZ*d + sideZ*bend);
                if (px == lastX && pz == lastZ) continue;
                // Connect diagonal raster steps with a cardinal block, so a
                // one-wide channel still carries real flowing fluid.
                int[][] points = lastX != Integer.MIN_VALUE && px != lastX && pz != lastZ
                        ? new int[][]{{px, lastZ}, {px, pz}}
                        : new int[][]{{px, pz}};
                for (int[] point : points) {
                    int cx = point[0], cz = point[1];
                    if (!uncarved.touches(cx, cz)) return Set.of();
                    Column raw = uncarved.column(cx, cz);
                    // Require sturdy native terrain directly under the channel
                    // floor. Never create thin, hovering ribbons of new rock.
                    boolean supported = raw.originalSolid(waterY - 2)
                            && raw.originalSolid(waterY - 3);
                    if (!supported) {
                        if (d < shoreRadius * 1.30 || centers.size() < 12)
                            return Set.of();
                        // Verify a real drop, not just a shallow surface divot.
                        boolean clearDrop = true;
                        for (int k = 1; k <= 3; k++) {
                            int nx = (int)Math.round(cx + forwardX * k);
                            int nz = (int)Math.round(cz + forwardZ * k);
                            Column ahead = uncarved.column(nx, nz);
                            if (ahead.originalSolid(waterY - 2)
                                    || ahead.originalSolid(waterY - 6)
                                    || ahead.originalSolid(waterY - 12)) {
                                clearDrop = false;
                                break;
                            }
                        }
                        if (!clearDrop) return Set.of();
                        reachedCliff = true;
                        break;
                    }
                    // Do not cut a deep artificial trench through a hill or
                    // mountain just to make a lake overflow.
                    if (d > shoreRadius * 1.12 && raw.originalSolid(waterY + 14))
                        return Set.of();
                    if (centers.isEmpty() || centers.get(centers.size()-1)[0] != cx
                            || centers.get(centers.size()-1)[1] != cz)
                        centers.add(new int[]{cx, cz});
                }
                lastX = px;
                lastZ = pz;
                if (reachedCliff) break;
            }
            if (!reachedCliff) return Set.of();

            Set<Long> path = new HashSet<>();
            for (int step = 0; step < centers.size(); step++) {
                int[] center = centers.get(step);
                // Keep a continuous channel of 1-4 blocks, with slightly
                // broken edges and a narrower lip at the actual drop. Keep
                // the center block regardless of width/noise for connectivity.
                int localWidth = width;
                if (step >= centers.size() - 5 && width > 2) localWidth--;
                int left = (localWidth - 1) / 2;
                int right = localWidth / 2;
                path.add(pack(center[0], center[1]));
                for (int offset = -left; offset <= right; offset++) {
                    if (offset == 0) continue;
                    // Tiny nicks along the edges keep broad outlets from
                    // looking like perfectly straight, square canals.
                    if (step > 3 && step < centers.size() - 5
                            && unit(key ^ (long)step * 0x7b9b91L, 810 + offset + salt) < .09)
                        continue;
                    int px = (int)Math.round(center[0] + sideX * offset);
                    int pz = (int)Math.round(center[1] + sideZ * offset);
                    Column raw = uncarved.column(px, pz);
                    if (raw.originalSolid(waterY - 2)
                            && raw.originalSolid(waterY - 3))
                        path.add(pack(px, pz));
                }
            }
            return path;
        }

        private static long pack(int x, int z) {
            return ((long)x << 32) ^ (z & 0xffffffffL);
        }

        /** Preserve the lake, spillway and entire near-shore rock profile. */
        double plateauClearance(int wx, int wz) {
            // Keep the entire preflighted spillway floor at its original Y,
            // including its channel beyond the normal near-shore radius.
            if (outlets.contains(pack(wx, wz))) return 0.0;
            double radius = Math.hypot((wx-x)/rx, (wz-z)/rz);
            double transition = Math.max(0.0, Math.min(1.0, (radius - 1.62) / .86));
            return transition * transition * (3.0 - 2.0 * transition);
        }

        public LakeSample sample(int wx,int wz) {
            double dx=(wx-x)/rx, dz=(wz-z)/rz;
            double r=Math.sqrt(dx*dx+dz*dz);
            boolean outlet = outlets.contains(pack(wx, wz));
            if(r>1.95 && !outlet) return null;
            // Several world-aligned frequencies and low-amplitude radial lobes
            // prevent repeated elliptical shorelines. All samples are seeded
            // from the island, so the lake remains seamless across chunks.
            double broad=SkyIslandNoise.noise2(wx*.048,wz*.048,key^0x5a98bL);
            double medium=SkyIslandNoise.noise2(wx*.105,wz*.105,key^0x961bcL);
            double fine=SkyIslandNoise.noise2(wx*.235,wz*.235,key^0xa6512L);
            double angle=Math.atan2(dz,dx);
            double lobes=.055*Math.sin(angle*5.0+unit(key,687)*6.283185307179586)
                    +.035*Math.sin(angle*9.0+unit(key,688)*6.283185307179586);
            double distance=r-.15*broad-.085*medium-.035*fine-lobes;
            // A noise-warped transition makes the shore fade into
            // the untouched island surface instead of a fixed-height skirt.
            double ramp=1.38;
            if(distance<1.0) {
                double centre=Math.max(0,1-distance*distance);
                int depth=switch(theme) {
                    case "swamp" -> {
                        // The Deep Mire's larger water bodies are actually deeper;
                        // swamp wetlands retain their historical shallow floor.
                        var region=SkyTerrainSubregions.swamp(worldSeed,wx,wz);
                        yield 1+(int)Math.round((2.0+2.1*centre)*region.mire()
                                +.20*region.thickets());
                    }
                    case "glaciated" -> 2+(int)Math.round(6*Math.pow(centre,1.25));
                    case "lavic" -> 1+(int)Math.round(3*Math.pow(centre,1.25));
                    default -> 2+(int)Math.round(4*Math.pow(centre,1.25));
                };
                // Frozen lakes have precisely one ice roof, all the volume
                // below it is WATER. The floor is solid natural rock.
                int floor=waterY-depth;
                if ("swamp".equals(theme)) {
                    // Small, deterministic mud hummocks interrupt deep-water
                    // patches, but only in true mire cores and far from outlets.
                    var region=SkyTerrainSubregions.swamp(worldSeed,wx,wz);
                    if (region.mire()>.55 && r<.83) {
                        double mud=SkyTerrainPatterns.noise2(worldSeed,wx,wz,13,0x643c11L);
                        double grains=SkyTerrainPatterns.noise2(worldSeed,wx,wz,5,0x643c12L);
                        if (mud+.25*grains> .83 + .12*(1.0-region.mire()))
                            return new LakeSample(true,false,waterY,waterY,false,theme,0,false);
                    }
                }
                return new LakeSample(true,true,floor,waterY,frozen,theme,0,false);
            }
            // A preflighted channel removes the raised lake bank down to one
            // block below the shared waterline. Vanilla liquid then flows over
            // the terminal rock lip into open sky.
            if (outlet) return new LakeSample(true,true,waterY-1,waterY,false,theme,0,true);
            if (distance>ramp) return null;
            double t=Math.min(1,(distance-1.0)/(ramp-1.0));
            double blend=t*t*(3-2*t);
            // The actual top will be interpolated toward the natural surface
            // by Column, where original terrain density is available.
            return new LakeSample(true,false,waterY,waterY,frozen,theme,blend,false);
        }
    }
    public record LakeSample(boolean active,boolean water,int top,int waterY,
                             boolean frozen,String theme,double blend,boolean outlet) {
        LakeSample withTop(int adjustedTop) {
            return new LakeSample(active,water,adjustedTop,waterY,frozen,theme,blend,outlet);
        }
    }

    /**
     * Site-seeded Lavic molten caverns. The liquid basin has a sealed, warped
     * footprint; its floor is uneven below the common liquid level. Above the
     * molten metal the room swells into irregular alcoves, develops rocky ribs
     * and closes under a varied ceiling, not a cylindrical extrusion.
     * All sampling is world-coordinate based and independent of chunk order.
     */
    public static final class CavePool {
        private final double x,z,rx,rz,cos,sin,exitLength;
        private final int floorY;
        private final long key;
        private CavePool(double x,double z,double rx,double rz,int floorY,
                         double angle,double exitLength,long key) {
            this.x=x;this.z=z;this.rx=rx;this.rz=rz;this.floorY=floorY;
            this.cos=Math.cos(angle);this.sin=Math.sin(angle);
            this.exitLength=exitLength;this.key=key;
        }
        static CavePool create(int x,int y,int z,double radius,double stableWidth,
                               long key,String theme,boolean primary) {
            if(!primary||!"lavic".equals(theme)||radius<49||unit(key,670)>.65)return null;
            double cx=x+(unit(key,671)-.5)*radius*.16;
            double cz=z+(unit(key,672)-.5)*radius*.16;
            double r=Math.min(stableWidth*.34,9+unit(key,673)*10);
            return new CavePool(cx,cz,r,r*(.82+.30*unit(key,674)),
                    y-12-(int)(unit(key,675)*10),unit(key,676)*Math.PI*2,
                    stableWidth*1.40,key);
        }
        public CaveSample sample(int wx,int wz) {
            double dx=wx-x,dz=wz-z;
            // Two independent low-frequency warps stop the shoreline from
            // tracing an ellipse even before the angular scallops are added.
            double warpedX=dx + 2.3*SkyIslandNoise.noise2(wx*.067,wz*.067,key^0x6c271L)
                    + .9*SkyIslandNoise.noise2(wx*.19,wz*.19,key^0x6c272L);
            double warpedZ=dz + 2.3*SkyIslandNoise.noise2(wx*.067,wz*.067,key^0x6c273L)
                    + .9*SkyIslandNoise.noise2(wx*.19,wz*.19,key^0x6c274L);
            double theta=Math.atan2(warpedZ/rz,warpedX/rx);
            double phase=unit(key,680)*Math.PI*2;
            double irregular=1.0
                    + .11*Math.sin(theta*3+phase)
                    + .065*Math.sin(theta*7-phase*.67)
                    + .12*SkyIslandNoise.noise2(wx*.081,wz*.081,key^0x6c275L)
                    + .045*SkyIslandNoise.noise2(wx*.23,wz*.23,key^0x6c276L);
            double distance=Math.hypot(warpedX/rx,warpedZ/rz)/irregular;
            boolean inside=distance<1.0;
            // A wide solid collar persists at every molten-metal elevation.
            // Upper dry ledges may project out over it without draining it.
            boolean rim=distance>=1.0 && distance<1.42;
            double along=dx*cos+dz*sin;
            double across=-dx*sin+dz*cos;
            double sway=2.2*Math.sin(along*.055+phase)
                    +1.1*SkyIslandNoise.noise2(wx*.09,wz*.09,key^0x6c277L);
            double passageWidth=1.65+.80*(.5+.5*SkyIslandNoise.noise2(wx*.11,wz*.11,key^0x6c278L));
            boolean exit=along>rx*.75 && along<exitLength && Math.abs(across-sway)<passageWidth;
            if(!inside&&!rim&&!exit)return null;
            int liquidY=floorY+1;
            double floorNoise=SkyIslandNoise.noise2(wx*.13,wz*.13,key^0x6c279L);
            // Deeper, softly stepped stone basins under the molten metal;
            // shoreline columns always retain a solid, shallow lip.
            double depthFactor=Math.max(0.0,1.0-distance*distance);
            int floorDepth=inside ? (int)Math.round(depthFactor*(2.35+.85*floorNoise)) : 0;
            int localFloorY=floorY-Math.max(0,floorDepth);
            double roofBroad=SkyIslandNoise.noise2(wx*.10,wz*.10,key^0x6c27aL);
            double roofDetail=SkyIslandNoise.noise2(wx*.25,wz*.25,key^0x6c27bL);
            int roof=(int)Math.round(8.7+3.2*roofBroad+2.2*roofDetail
                    -3.0*Math.max(0.0,distance-.52));
            int ceilingY=liquidY+Math.max(5,Math.min(14,roof));
            double wallPhase=phase+2.6*SkyIslandNoise.noise2(wx*.12,wz*.12,key^0x6c27cL);
            return new CaveSample(inside,rim,exit,localFloorY,liquidY,ceilingY,
                    distance,wallPhase);
        }
    }
    public record CaveSample(boolean inside,boolean rim,boolean exit,
                             int floorY,int liquidY,int ceilingY,
                             double distance,double wallPhase) {
        /** An irregular dry chamber wall, wider at some heights than others. */
        public boolean chamberAir(int y) {
            if(y<=liquidY)return inside && y>floorY;
            if(y>ceilingY)return false;
            double height=Math.max(1.0,ceilingY-liquidY);
            double t=(y-liquidY)/height;
            double bulge=.29*Math.sin(Math.PI*t);
            double upperTaper=.13*t*t;
            double ribs=.060*Math.sin(y*.77+wallPhase)
                    +.043*Math.sin(y*1.46-wallPhase*.83);
            return distance<1.0+bulge-upperTaper+ribs;
        }
    }

    public static final class Column {
        private final int x, z;
        private final List<LobeColumn> lobes;
        private final List<Cavity> cavities;
        private final List<SpireColumn> roots;
        private final List<TunnelSlice> worms;
        private final LakeSample lake;
        private final CaveSample cave;
        // Conservative per-column Y limits for original, uncarved rock only.
        // Lake floors and cave collars may be solid beyond these limits.
        private final int rockMinY, rockMaxY;
        public LakeSample lake() { return lake; }
        public CaveSample cave() { return cave; }
        private Column(int x, int z, List<LobeColumn> lobes,
                       List<SpireColumn> roots, List<Cavity> cavities, List<TunnelSlice> worms,
                       LakeSample sampledLake,CaveSample sampledCave,int minY,int maxY) {
            this.x = x; this.z = z; this.lobes = lobes;
            this.roots = roots; this.cavities = cavities; this.worms = worms;
            this.cave = sampledCave;
            int rockMin = Integer.MAX_VALUE;
            int rockMax = Integer.MIN_VALUE;
            for (LobeColumn l : lobes) {
                rockMin = Math.min(rockMin, l.minPossibleY);
                rockMax = Math.max(rockMax, l.maxPossibleY);
            }
            for (SpireColumn root : roots) {
                rockMin = Math.min(rockMin, (int)Math.floor(root.top - root.height));
                rockMax = Math.max(rockMax, (int)Math.ceil(root.top));
            }
            this.rockMinY = rockMin;
            this.rockMaxY = rockMax;
            if(sampledLake!=null && !sampledLake.water()) {
                int original=sampledLake.waterY()+1;
                for(int y=maxY-1;y>=minY;y--) {
                    if(originalSolid(y)) { original=y; break; }
                }
                // The first dry beach is level with the WATER block, not one
                // block above it. This also allows sugar cane to see water on
                // the same Y as its supporting sand on Cherry shores.
                int shore=sampledLake.waterY();
                double blend=sampledLake.blend();
                double coarse=SkyIslandNoise.noise2(x*.115,z*.115,0x149ac3L ^
                        ((long)sampledLake.waterY()*0x9e3779b97f4a7c15L));
                double fine=SkyIslandNoise.noise2(x*.28,z*.28,0x6de1acL ^
                        ((long)sampledLake.waterY()*0x94d049bb133111ebL));
                // Keep immediate waterfront dry and flat, while nearby outer
                // banks rise/fall by individual blocks and reconnect to hills.
                int uneven=(int)Math.round((coarse*1.4+fine*.75)
                        *Math.min(1.0,blend*2.2));
                int blended=Math.max(shore,(int)Math.round(
                        shore+(original-shore)*blend)+uneven);
                this.lake=sampledLake.withTop(blended);
            } else this.lake=sampledLake;
        }
        private boolean originalSolid(int y) {
            // Eliminate empty heights before the more expensive density tests.
            if (y < rockMinY || y > rockMaxY) return false;
            // Only the sign of the maximum density matters. Once a lobe (or
            // hanging root) is solid, computing more expensive warped density
            // samples cannot change that decision. Preserve the separate cave
            // and tunnel carving passes exactly as before.
            boolean rock=false;
            for (LobeColumn l : lobes) {
                if (l.density(y) > 0) { rock=true; break; }
            }
            if (!rock) {
                for (SpireColumn root : roots) {
                    if (root.density(y) > 0) { rock=true; break; }
                }
            }
            if (!rock) return false;
            for(Cavity c:cavities)if(c.contains(x,y,z))return false;
            for(TunnelSlice worm:worms)if(worm.contains(y))return false;
            return true;
        }
        public boolean solid(int y) {
            // The lake is part of the density mask, before foliage and structures.
            // Its central plateau has a sealed rock floor and a perfectly level
            // waterline, but a warped irregular shoreline. Each chunk samples
            // the same geometry independently, so large lakes have no seams.
            if (lake != null && lake.active()) {
                if (lake.outlet()) {
                    // The outlet only replaces the top few layers. Keep the
                    // island's natural underside instead of manufacturing a
                    // 12-block-deep rock wall underneath a spillway.
                    if (y > lake.top()) return false;
                    if (y >= lake.top() - 2) return true;
                    return originalSolid(y);
                }
                // Carve away the original island height above the lake's
                // new bank/floor, even when a natural ridge used to stand
                // there. Otherwise buried upper rock can block the water or
                // cover shore-level sand, preventing sugar cane growth.
                if (y > lake.top()) return false;
                if (y >= lake.top() - 12) return true;
            }
            if (cave != null) {
                // The dry access passage can intersect the stone collar only
                // above the molten surface; never open a hole at liquid level.
                if (cave.exit() && y>=cave.liquidY()+3 && y<=cave.liquidY()+7)
                    return false;
                if ((cave.inside() || cave.rim())
                        && y>=cave.floorY()-4 && y<=cave.ceilingY()+2) {
                    if (cave.inside() && y<=cave.floorY())return true;
                    if (y<=cave.liquidY()+2) {
                        // Variable-depth molten basin, solid protective rim.
                        return !cave.inside() || !cave.chamberAir(y);
                    }
                    if (y<=cave.ceilingY())return !cave.chamberAir(y);
                    return true; // intact upper roof, including above alcoves
                }
            }
            return originalSolid(y);
        }
    }

    private static final class Lobe {
        final double x, y, z, rx, rz, upper, lower, cos, sin;
        final double phase, plateau;
        final long key;
        final Profile profile;
        Lobe(double x, double y, double z, double rx, double rz, double upper,
             double lower, double angle, long key, Profile profile) {
            this.x=x; this.y=y; this.z=z; this.rx=rx; this.rz=rz;
            this.upper=upper; this.lower=lower; this.cos=Math.cos(angle); this.sin=Math.sin(angle);
            this.key=key; this.phase=(key & 0xffffL) * .00014; this.profile=profile;
            this.plateau=(profile == Profile.TERRACE ? 3.4 : profile == Profile.CRAG ? 1.9 : 2.35);
        }
        boolean mayContainXZ(int wx, int wz) {
            return Math.abs(wx - x) <= rx * 1.85 + 12
                    && Math.abs(wz - z) <= rz * 1.85 + 12;
        }
        LobeColumn column(int wx, int wz, SkyTerrainPlateaus.Sample plateauSample,
                          SkyTerrainAridShaping.Sample aridSample,
                          SkyTerrainLavicShaping.Sample lavicSample,
                          SkyTerrainGentleShaping.Sample gentleSample) {
            double dx=wx-x, dz=wz-z;
            if (!mayContainXZ(wx, wz)) return null;
            double px=cos*dx+sin*dz, pz=-sin*dx+cos*dz;
            double angle=Math.atan2(pz/rz,px/rx);
            // Silhouette deformation at 3 scales: eroded coves + scalloped cliffs + fine nicks.
            double broad=SkyIslandNoise.noise2(wx*.020,wz*.020,key ^ 0x45bc6eL);
            double medium=SkyIslandNoise.noise2(wx*.071,wz*.071,key ^ 0x4b6291L);
            double fine=SkyIslandNoise.noise2(wx*.167,wz*.167,key ^ 0x3f13c1L);
            double scallop=.115*Math.sin(3*angle+phase)+.068*Math.sin(7*angle-phase)
                    +.032*Math.sin(13*angle+phase*1.7);
            double edge=1 + .28*broad + .115*medium + .045*fine + scallop;
            double radial=(px*px/(rx*rx)+pz*pz/(rz*rz))/(edge*edge);
            // Uneven terrain heights and ridgelines; slopes no longer track pure ellipsoid.
            double topOffset=7.2*SkyIslandNoise.noise2(wx*.031,wz*.031,key ^ 0x8a31dfL)
                    +3.1*SkyIslandNoise.noise2(wx*.100,wz*.100,key ^ 0x4cd21aL);
            // Strongly displaced underside floor: unlike tiny surface roughness,
            // this changes the island's entire lower silhouette by tens of blocks.
            double bottomOffset=17.0*SkyIslandNoise.noise2(wx*.021,wz*.021,key ^ 0x31927bL)
                + 11.0*SkyIslandNoise.noise2(wx*.057,wz*.057,key ^ 0x6b12bbL)
                + 3.0*SkyIslandNoise.noise2(wx*.149,wz*.149,key ^ 0x201a4bL);
            return new LobeColumn(wx,wz,y,upper,lower,radial,topOffset,bottomOffset,plateau,key,profile,
                    plateauSample,aridSample,lavicSample,gentleSample);
        }
    }

    private static final class LobeColumn {
        final int x,z;
        final double center,upper,lower,radial,topOffset,bottomOffset,power;
        final double horizontalGouge, undersideWarp, strata;
        final double plateauStrength, plateauElevation, surfaceRadial;
        final double duneStrength, duneElevation, rockyStrength, rockyElevation;
        final double lavicStrength, lavicFlatness, lavicElevation, lavicRift;
        final double gentleFlatness, gentleElevation, gentleRoughness;
        final int minPossibleY, maxPossibleY;
        final long key;
        final Profile profile;
        LobeColumn(int x,int z,double center,double upper,double lower,double radial,
                   double topOffset,double bottomOffset,double power,long key,Profile profile,
                   SkyTerrainPlateaus.Sample plateauSample,
                   SkyTerrainAridShaping.Sample aridSample,
                          SkyTerrainLavicShaping.Sample lavicSample,
                          SkyTerrainGentleShaping.Sample gentleSample) {
            this.x=x;this.z=z;this.center=center;this.upper=upper;this.lower=lower;
            this.radial=radial;this.topOffset=topOffset;this.bottomOffset=bottomOffset;
            this.power=power;this.key=key;this.profile=profile;
            this.plateauStrength=plateauSample.weight();
            this.plateauElevation=plateauSample.elevation();
            this.duneStrength=aridSample.dunes();
            this.duneElevation=aridSample.duneElevation();
            this.rockyStrength=aridSample.rocky();
            this.rockyElevation=aridSample.rockyElevation();
            this.lavicStrength=lavicSample.plateau()+lavicSample.forest()
                    +lavicSample.barrens()+lavicSample.rifts();
            this.lavicFlatness=lavicSample.flatness();
            this.lavicElevation=lavicSample.elevation();
            this.lavicRift=lavicSample.rifts();
            this.gentleFlatness=gentleSample.flatness();
            this.gentleElevation=gentleSample.elevation();
            this.gentleRoughness=gentleSample.roughness();
            // Deliberately wide, conservative envelopes: at 2.5 height radii
            // no original surface cap/underside can overcome its negative shell.
            // Preserve all existing density, carving and noise formulas.
            double highAnchor = topOffset;
            if (plateauStrength > 0.0)
                highAnchor = Math.max(highAnchor, plateauElevation);
            else if (duneStrength > 0.0 || rockyStrength > 0.0) {
                highAnchor = Math.max(highAnchor, duneElevation + .14 * topOffset);
                highAnchor = Math.max(highAnchor, rockyElevation + .34 * topOffset);
            } else if (lavicStrength > 0.0)
                highAnchor = Math.max(highAnchor, lavicElevation + .18 * topOffset);
            else if (gentleFlatness > 0.0 || gentleRoughness > 0.0)
                highAnchor = Math.max(highAnchor, gentleElevation + .22 * topOffset);
            this.minPossibleY = (int)Math.floor(center + bottomOffset - 2.5 * lower - 2.0);
            this.maxPossibleY = (int)Math.ceil(center + highAnchor + 2.5 * upper + 2.0);
            if (plateauStrength > 0.0 || duneStrength > 0.0 || rockyStrength > 0.0
                    || lavicStrength > 0.0 || gentleFlatness > 0.0 || gentleRoughness > 0.0) {
                // Flatten broad central decks without changing the island's
                // exterior footprint; the rim still forms a natural cliff.
                double inner = Math.max(0.0, Math.min(1.0, (radial - .42) / .52));
                double flatInterior = 1.0 - inner * inner * (3.0 - 2.0 * inner);
                this.surfaceRadial = radial * (1.0 - flatInterior *
                        (.79 * plateauStrength + .59 * duneStrength + .24 * rockyStrength
                                + .62 * lavicFlatness + .71 * gentleFlatness));
            } else {
                this.surfaceRadial = radial;
            }
            // Precompute broad underside domain warping and narrow split lines.
            // These are sampled once per (x,z) column rather than for every voxel.
            double split = Math.abs(SkyIslandNoise.noise2(x*.027,z*.027,key^0x77bd5bL)
                + .32*SkyIslandNoise.noise2(x*.079,z*.079,key^0x74b1bdL));
            horizontalGouge = Math.max(0, .22-split)/.22;
            undersideWarp = .42*SkyIslandNoise.noise2(x*.024,z*.024,key^0x315c1eL)
                + .22*SkyIslandNoise.noise2(x*.061,z*.061,key^0x928afaL);
            strata = SkyIslandNoise.noise2(x*.036,z*.036,key^0x991ef0L);
        }
        double density(int y) {
            if (y < minPossibleY || y > maxPossibleY) return -5;
            double vertical=y-center;
            boolean below=vertical<0;
            double heightRadius=below ? lower : upper;
            // Raised plateau tops must remain connected to the underlying
            // island. Blend the original top profile into a ONE-SIDED terrace
            // height, rather than moving an ellipsoid upward (which can leave
            // floating slabs with a hollow space around the old center Y).
            // The lower half still uses precisely its original density.
            double norm;
            if (below) {
                norm = Math.abs((vertical - bottomOffset) / lower);
            } else {
                double original = Math.abs((vertical - topOffset) / upper);
                if (plateauStrength > 0.0) {
                    // Unchanged Golden Plateau behavior.
                    norm = original + plateauStrength * (Math.max(0.0,
                            (vertical - plateauElevation) / upper) - original);
                } else if (duneStrength > 0.0 || rockyStrength > 0.0) {
                    // Dune caps smooth out small lobe roughness; rocky caps
                    // retain more exposed stone texture and harder crests.
                    double duneNorm = Math.max(0.0,
                            (vertical - duneElevation - .14 * topOffset) / upper);
                    double rockyNorm = Math.max(0.0,
                            (vertical - rockyElevation - .34 * topOffset) / upper);
                    norm = (1.0 - duneStrength - rockyStrength) * original
                            + duneStrength * duneNorm + rockyStrength * rockyNorm;
                } else if (lavicStrength > 0.0) {
                    double flat = Math.max(0.0,
                            (vertical - lavicElevation - .18 * topOffset) / upper);
                    norm = (1.0 - lavicFlatness) * original + lavicFlatness * flat;
                } else if (gentleFlatness > 0.0 || gentleRoughness > 0.0) {
                    // Soft plains/meadows and mildly raised Crystal Glacier
                    // ridges; original cliff faces and undersides are retained.
                    double influence=Math.min(.86, gentleFlatness + .28*gentleRoughness);
                    double flat=Math.max(0.0,
                            (vertical - gentleElevation - .22*topOffset) / upper);
                    norm=(1.0-influence)*original+influence*flat;
                } else norm = original;
            }
            double down=below ? Math.min(1, -vertical/lower) : 0;
            // On the underside, layering and domain warping operate independently
            // of the ellipsoidal profile. Deep flutes interrupt the smooth bowl.
            double shell=1-(below ? radial : surfaceRadial)-Math.pow(norm,below ? 1.65 : power);
            if(below) shell += down * (.44*undersideWarp + .14*strata);
            double ribs=.040*Math.sin(y*.17 + x*.081 + z*.046 + (key&1023)*.015)
                +.030*Math.sin(y*.39-x*.13+z*.094);
            double ledge=below ? down*.07*Math.sin(y*.43+strata*5) : 0;
            double near=shell+ribs+ledge - (below ? down*.52*horizontalGouge : 0);
            if (near> .61 || near<-.62) return near;
            // Multi-frequency warped carving only in the uncertain shell.
            // The lower-half noise forms negative concavities and broken strata,
            // not merely tiny bumps added to a smooth ellipsoid.
            double broad=SkyIslandNoise.noise3(x*.044,y*.052,z*.044,key^0x1b9e3cL);
            double detail=SkyIslandNoise.noise3(x*.112,y*.123,z*.112,key^0x8c21feL);
            double micro=SkyIslandNoise.noise3(x*.244,y*.257,z*.244,key^0x79a3ecL);
            double strength=profile==Profile.CRAG || profile==Profile.FRACTURED ? 1.30 : 1.0;
            if (!below) strength *= 1.0 - .33 * plateauStrength;
            if (!below) strength *= 1.0 - .69 * duneStrength + .35 * rockyStrength;
            if (!below) strength *= 1.0 - .54 * lavicFlatness + .21 * lavicRift;
            if (!below) strength *= 1.0 - .54 * gentleFlatness + .46 * gentleRoughness;
            double warped=near + strength*(.18*broad+.075*detail+.027*micro);
            if (below) {
                // A narrow warped ridged field removes chunks from underside cliffs.
                double scar=Math.abs(SkyIslandNoise.noise3(
                    x*.063+broad*.19,y*.057,z*.063-broad*.19,key^0x10ad2eL));
                double cut=Math.max(0, .17-scar)/.17;
                warped -= down * .50 * cut;
                warped += down*.07*Math.abs(detail);
            }
            return warped;
        }
    }

    /** Original descending rock buttress, sampled without neighboring chunk writes. */
    private static final class Spire {
        final double x, top, z, width, height, cos, sin;
        final long key;
        Spire(double x,double top,double z,double width,double height,double angle,long key) {
            this.x=x;this.top=top;this.z=z;this.width=width;this.height=height;
            this.cos=Math.cos(angle);this.sin=Math.sin(angle);this.key=key;
        }
        SpireColumn column(int wx,int wz) {
            double dx=wx-x,dz=wz-z;
            if(Math.abs(dx)>width*2+7 || Math.abs(dz)>width*2+7) return null;
            double a=dx*cos+dz*sin,b=-dx*sin+dz*cos;
            return new SpireColumn(a,b,wx,wz,top,width,height,key);
        }
    }
    private static final class SpireColumn {
        final double a,b,top,width,height;
        final int x,z;
        final long key;
        SpireColumn(double a,double b,int x,int z,double top,double width,double height,long key) {
            this.a=a;this.b=b;this.x=x;this.z=z;this.top=top;
            this.width=width;this.height=height;this.key=key;
        }
        double density(int y) {
            double t=(top-y)/height;
            if(t<0 || t>1) return -5;
            double twist=2.1*Math.sin(t*5.2+(key&255)*.05)*t;
            double longAxis=a + twist;
            double crossAxis=b - twist*.44;
            double r=width*(.12 + .95*Math.pow(1-t,1.37));
            double radial=Math.sqrt((longAxis*longAxis)/(r*r*1.44)
                +(crossAxis*crossAxis)/(r*r*.58));
            double shell=1-radial;
            if(shell> .22 || shell<-.30) return shell;
            double n=SkyIslandNoise.noise3(x*.112,y*.105,z*.112,key^0x5c193L);
            return shell + .19*n+.07*Math.sin(y*.40+x*.18+z*.15);
        }
    }

    private static final class Cavity {
        final double x,y,z,rx,ry,rz,cos,sin;
        final long phase;
        Cavity(double x,double y,double z,double rx,double ry,double rz,double rotation,long key) {
            this.x=x;this.y=y;this.z=z;this.rx=rx;this.ry=ry;this.rz=rz;
            this.cos=Math.cos(rotation);this.sin=Math.sin(rotation);this.phase=key&255L;
        }
        boolean contains(int wx,int wy,int wz) {
            double dx=wx-x,dz=wz-z;
            if (Math.abs(dx)>rx+rz+4 || Math.abs(dz)>rx+rz+4 || Math.abs(wy-y)>ry+4) return false;
            double px=(cos*dx+sin*dz)/rx, pz=(-sin*dx+cos*dz)/rz,py=(wy-y)/ry;
            double n=.085*SkyIslandNoise.noise3(wx*.105,wy*.108,wz*.105,phase);
            return px*px+py*py+pz*pz<1+n;
        }
    }

    private static long mix(long n) {
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return n^(n>>>31);
    }
    private static double unit(long key,long channel) {
        return (mix(key+channel*0x9e3779b97f4a7c15L)>>>11)*0x1.0p-53;
    }
}
