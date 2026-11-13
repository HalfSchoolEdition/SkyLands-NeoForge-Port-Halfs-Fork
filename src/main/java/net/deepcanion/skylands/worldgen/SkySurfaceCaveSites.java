package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Preflighted surface-facing caves for the existing SkyIslandShape density mask.
 *
 * Site geometry is calculated once (and cached in SkyIslandShape.sites), not once
 * per chunk/voxel. It samples the *uncarved* terrain, so the entry really joins
 * a lower natural shelf to a higher, solid cliff. No WorldGenLevel / chunk reads.
 *
 * Long passages are composed of a handful of bounded capsule segments. They
 * can exit an opposite cliff, making a naturally roofed arch or through-tunnel.
 */
final class SkySurfaceCaveSites {
    record Segment(double x0, double y0, double z0, double x1, double y1, double z1,
                   double radius, double verticalScale) { }

    private SkySurfaceCaveSites() { }

    static List<Segment> generate(SkyIslandShape.Island raw, long key, String theme,
                                  boolean primary, int frequency) {
        if (frequency <= 0 || raw.primaryRadius() < (primary ? 30 : 21)) return List.of();
        double base = switch (theme) {
            case "arid" -> .78;
            case "cherry" -> .69;
            case "glaciated" -> .64;
            case "lavic" -> .53;
            case "golden" -> .53;
            case "swamp" -> .30;
            default -> .42;
        };
        if (!primary) base *= .20; // avoid shredding small floating islands
        double chance = Math.min(.96, base * frequency / 100.0);
        if (unit(key, 1050) >= chance) return List.of();

        // Probe only a few rays. The candidate requires an exposed low shelf
        // beside higher *solid* rock, not just a coordinate near the island edge.
        final double cx = raw.centerX(), cz = raw.centerZ();
        final double radius = raw.primaryRadius();
        final int searchTop = Math.min(raw.maxY(), (int) Math.ceil(raw.centerY() +
                ("arid".equals(theme) ? 105 : "cherry".equals(theme) ? 88 : 70)));
        final int searchBottom = Math.max(raw.minY() + 8, (int) raw.centerY() - 27);
        for (int attempt = 0; attempt < 7; attempt++) {
            double angle = 2.0 * Math.PI * unit(key, 1060 + attempt);
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double outer = radius * (.69 + unit(key, 1080 + attempt) * .32);
            double inner = radius * (.22 + unit(key, 1100 + attempt) * .15);
            int outX = (int) Math.round(cx + dx * outer);
            int outZ = (int) Math.round(cz + dz * outer);
            int inX = (int) Math.round(cx + dx * inner);
            int inZ = (int) Math.round(cz + dz * inner);
            if (!raw.touches(outX,outZ) || !raw.touches(inX,inZ)) continue;
            if (nearLiquids(raw,outX,outZ) || nearLiquids(raw,inX,inZ)) continue;
            int outerTop = surface(raw,outX,outZ,searchTop,searchBottom);
            int innerTop = surface(raw,inX,inZ,searchTop,searchBottom);
            if (outerTop < searchBottom || innerTop < outerTop + 9) continue;
            int entryY = outerTop + 3;
            if (entryY < raw.minY()+12 || entryY > raw.maxY()-8) continue;
            if (raw.solid(outX,entryY,outZ) || !raw.solid(inX,entryY,inZ)
                    || !raw.solid(inX,entryY+3,inZ)) continue;

            // Before accepting, inspect the route and its immediate shoulders.
            // Don't pierce a lake, its spillway, a frozen pond or a molten cave.
            double side = (unit(key, 1120+attempt) - .5) * 7;
            double px = -dz, pz = dx;
            boolean safe = true;
            for (int step=0;step<=7;step++) {
                double t=step/7.0;
                double along=outer+(inner-outer)*t;
                double drift=side*Math.sin(Math.PI*t);
                int wx=(int)Math.round(cx+dx*along+px*drift);
                int wz=(int)Math.round(cz+dz*along+pz*drift);
                if (nearLiquids(raw,wx,wz)
                        || nearLiquids(raw,wx+(int)Math.round(px*5),wz+(int)Math.round(pz*5))
                        || nearLiquids(raw,wx-(int)Math.round(px*5),wz-(int)Math.round(pz*5))) {
                    safe=false;break;
                }
            }
            if (!safe) continue;

            // Once in a while, a broad straight-ish tunnel continues across
            // the ridge and breaks through another exposed wall. Otherwise
            // it ends as a dry curving cave within the hillside.
            boolean through = primary && radius > 47 && unit(key, 1140+attempt) < .22;
            double farSide = -radius * (.67 + .17*unit(key,1141+attempt));
            if (through) {
                // A through-cut traverses much more rock than a normal cave.
                // Inspect its entire route, including the half beyond the
                // interior endpoint, before allowing it to become an arch.
                for (int step=0;step<=15;step++) {
                    double t=step/15.0;
                    double along=outer+(farSide-outer)*t;
                    double drift=side*.65*Math.sin(Math.PI*t);
                    int wx=(int)Math.round(cx+dx*along+px*drift);
                    int wz=(int)Math.round(cz+dz*along+pz*drift);
                    if (nearLiquids(raw,wx,wz)
                            || nearLiquids(raw,wx+(int)Math.round(px*6),wz+(int)Math.round(pz*6))
                            || nearLiquids(raw,wx-(int)Math.round(px*6),wz-(int)Math.round(pz*6))) {
                        through=false;
                        break;
                    }
                }
            }
            double end = through ? farSide : inner;
            int slices = through ? 13 : 7;
            double slope = (unit(key, 1145+attempt)-.5) * (through ? 4 : 10);
            double wobble = side * (through ? .65 : 1.0);
            List<Segment> cuts = new ArrayList<>(slices);
            double lastX=0,lastY=0,lastZ=0;
            for (int i=0;i<=slices;i++) {
                double t=i/(double)slices;
                double along=outer+(end-outer)*t;
                double drift=wobble*Math.sin(Math.PI*t);
                double x=cx+dx*along+px*drift;
                double z=cz+dz*along+pz*drift;
                double y=entryY+slope*t + 1.4*Math.sin(t*Math.PI);
                if (i!=0) {
                    double mid=(i-.5)/(double)slices;
                    // Wide, slightly oval mouth; narrower bend; a small back
                    // chamber. Through-tunnels have wider central cutouts which
                    // sometimes make naturally roofed rock arches.
                    double width = through ? 4.3 + 2.0*Math.sin(Math.PI*mid)
                            : 2.65 + 1.8*Math.pow(1-mid,3) + 1.1*Math.pow(mid,5);
                    double height = through ? .95 : 1.08 + .25*(1-mid);
                    cuts.add(new Segment(lastX,lastY,lastZ,x,y,z,width,height));
                }
                lastX=x;lastY=y;lastZ=z;
            }
            return List.copyOf(cuts);
        }
        return List.of();
    }

    private static int surface(SkyIslandShape.Island island,int x,int z,int top,int bottom) {
        SkyIslandShape.Column c=island.column(x,z);
        // Coarse-to-fine highest rock lookup, per site (never per generated voxel).
        for(int y=top;y>=bottom;y-=3) {
            if(!c.solid(y))continue;
            for(int yy=Math.min(y+2,top);yy>y;yy--)if(c.solid(yy))return yy;
            return y;
        }
        return bottom-1;
    }

    private static boolean nearLiquids(SkyIslandShape.Island raw,int x,int z) {
        if (raw.lake()!=null) {
            SkyIslandShape.LakeSample sample=raw.lake().sample(x,z);
            if (sample!=null && sample.active()) return true;
        }
        if (raw.cavePool()!=null) {
            SkyIslandShape.CaveSample cave=raw.cavePool().sample(x,z);
            if (cave!=null && (cave.inside() || cave.rim())) return true;
        }
        return false;
    }
    private static double unit(long key,long channel) {
        long n=key + channel*0x9e3779b97f4a7c15L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return ((n^(n>>>31))>>>11)*0x1.0p-53;
    }
}
