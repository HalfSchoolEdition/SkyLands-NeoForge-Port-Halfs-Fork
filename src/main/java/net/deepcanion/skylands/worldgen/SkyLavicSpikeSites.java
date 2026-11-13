package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Lavic spike anchors reconstructed from the island density field.
 * No chunk access, neighbor loading or world writes. Every chunk sees identical
 * spikes for a particular island, so projecting horns can cross chunk borders.
 */
public final class SkyLavicSpikeSites {
    private SkyLavicSpikeSites() { }

    public record Spike(int x, int y, int z, long seed, SkyLavicSpikeGeometry.Profile shape,
                        int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean giant) {
        public boolean intersects(int cx, int cz) {
            return maxX >= cx && minX <= cx + 15 && maxZ >= cz && minZ <= cz + 15;
        }
    }

    // Only lightweight, immutable geometry is retained. Never cache worlds, chunks or blocks.
    private static final Map<Long, List<Spike>> CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,List<Spike>> eldest) {
            return size() > 256;
        }
    };

    public static List<Spike> forIsland(long worldSeed, SkyIslandShape.Island island) {
        int cx = (int)Math.round(island.centerX());
        int cy = (int)Math.round(island.centerY());
        int cz = (int)Math.round(island.centerZ());
        long key = SkyTerrainPatterns.mix(worldSeed, cx, cy, cz, 0x27b9c41L);
        synchronized (CACHE) {
            List<Spike> found = CACHE.get(key);
            if (found != null) return found;
        }
        List<Spike> result = build(island, worldSeed, key);
        synchronized (CACHE) {
            List<Spike> found = CACHE.get(key);
            if (found != null) return found;
            CACHE.put(key, result);
        }
        return result;
    }

    private static List<Spike> build(SkyIslandShape.Island island, long worldSeed, long key) {
        // Enough distinct sites to make cliff and underside horns prominent,
        // without an unbounded search for somewhere that will accept a spike.
        int attempts = island.primaryRadius() >= 37 ? 58 : 23;
        ArrayList<Spike> result = new ArrayList<>(attempts);
        int giantCount = 0; // A few major silhouettes, never a forest of huge claws.
        double cx = island.centerX(), cy = island.centerY(), cz = island.centerZ();
        for (int i=0; i<attempts; i++) {
            long seed = SkyTerrainPatterns.mix(key, i, 0, 0, 0x4431dL);
            double angle = 2 * Math.PI * SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed, 1, 0, 0, 1));
            double mix = SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed, 2, 0, 0, 1));
            // Top, cliff, underside. Use a broad range of outward normals.
            int face = i % 10;
            double elevation = face < 3 ? .58 + .37 * mix
                    : face < 7 ? (mix - .5) * .75 : -.58 - .37 * mix;
            double horizontal = Math.sqrt(1.0 - elevation*elevation);
            double dx = Math.cos(angle) * horizontal;
            double dz = Math.sin(angle) * horizontal;
            double dy = elevation;

            // Follow a ray from the island mass out into the sky. This finds
            // the REAL island boundary, not an arbitrary top-mounted NBT anchor.
            int ax=0, ay=0, az=0;
            boolean sawSolid = false, found = false;
            double distance = 0;
            for (;distance <= 124;distance += 2) {
                int x = (int)Math.floor(cx + dx*distance);
                int y = (int)Math.floor(cy + dy*distance);
                int z = (int)Math.floor(cz + dz*distance);
                boolean solid = island.solid(x,y,z);
                if (solid) {
                    sawSolid = true;
                    ax=x;ay=y;az=z;
                } else if (sawSolid && distance >= island.primaryRadius()*.27) {
                    found = true;
                    break;
                }
            }
            if (!found || ay < SkyIslandShape.MIN_Y + 6 || ay > SkyIslandShape.MAX_Y - 6) continue;
            if (distance < island.primaryRadius()*.30) continue;

            // Keep the first part embedded and the rest extending outward.
            // Secondary horns are smaller and occasionally grow beside a main horn.
            boolean secondary = i%7 == 0;
            SkyLavicSpikeGeometry.Profile shape = SkyLavicSpikeGeometry.create(
                    seed, dx, dy, dz, dy < -.42, Math.abs(dy) <= .42, secondary);
            // Only primary landmasses can host the rare 2-3x landmark claws.
            // Their base, curve and radius are uniformly reconstructed by every
            // chunk, so large formations can span chunk boundaries safely.
            boolean giant = !secondary && island.primaryRadius() >= 37 && giantCount < 5
                    && SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed, 19, 0, 0, 0x751aL)) < .30;
            if (giant) {
                double heightScale = 2.05 + .75 * SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(seed, 20, 0, 0, 0x751aL));
                double widthScale = 1.7 + .45 * SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(seed, 21, 0, 0, 0x751aL));
                shape = SkyLavicSpikeGeometry.giant(shape, heightScale, widthScale);
            }
            int ox = ax - (int)Math.round(dx*1.4);
            int oy = ay - (int)Math.round(dy*1.4);
            int oz = az - (int)Math.round(dz*1.4);
            if (!island.solid(ox,oy,oz)) continue;
            // Rifts retain the greatest concentration, but every Lavic landscape
            // now hosts a meaningful share of regular and giant claws. This gate affects only
            // procedural horns, never the island's permanent underside roots.
            var region = SkyTerrainSubregions.lavic(worldSeed, ox, oz);
            double density = .64 * region.plateau() + .55 * region.forest()
                    + .77 * region.barrens() + .99 * region.rifts();
            if (SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(
                    seed, ox, oy, oz, 0x531a21L)) >= density) continue;
            // Reject paths that re-enter the native island significantly after
            // leaving their root; do not chop the spike off at a chunk edge.
            boolean collision = false;
            for (double t=.35;t<=1.0;t+=.13) {
                SkyLavicSpikeGeometry.Vec3 p=shape.point(t);
                if(island.solid(ox+(int)Math.round(p.x()),oy+(int)Math.round(p.y()),oz+(int)Math.round(p.z()))) {
                    collision = true;
                    break;
                }
            }
            if (collision) continue;
            // A conservative true bound, including curvature and final radius.
            int radius = (int)Math.ceil(shape.baseRadius()+2);
            int minX=ox, minY=oy, minZ=oz, maxX=ox,maxY=oy,maxZ=oz;
            for(SkyLavicSpikeGeometry.Vec3 p:SkyLavicSpikeGeometry.backbone(shape)) {
                int x=ox+(int)Math.floor(p.x()),y=oy+(int)Math.floor(p.y()),z=oz+(int)Math.floor(p.z());
                minX=Math.min(minX,x-radius);maxX=Math.max(maxX,x+radius);
                minY=Math.min(minY,y-radius);maxY=Math.max(maxY,y+radius);
                minZ=Math.min(minZ,z-radius);maxZ=Math.max(maxZ,z+radius);
            }
            // Prevent the rare giants from being abruptly cut off at the
            // SkyLands generation band's vertical limits.
            if (minY < SkyIslandShape.MIN_Y + 2 || maxY > SkyIslandShape.MAX_Y - 2) continue;
            result.add(new Spike(ox,oy,oz,seed,shape,minX,minY,minZ,maxX,maxY,maxZ,giant));
            if (giant) giantCount++;
        }
        return List.copyOf(result);
    }
}
