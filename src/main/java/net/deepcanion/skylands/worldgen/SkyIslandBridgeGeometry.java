package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure deterministic geometry for hanging terrain-to-terrain bridges. */
public final class SkyIslandBridgeGeometry {
    private SkyIslandBridgeGeometry() { }
    public record Point(int x,int y,int z) { }
    private record SiteKey(long seed,int gx,int gz,String theme) { }
    // Worldgen revisits the same site from many chunks. A small per-thread
    // bounded cache prevents rebuilding hundreds of geometry candidates for
    // every 16x16 chunk. Keys include seed and theme; no Level is retained.
    private static final ThreadLocal<LinkedHashMap<SiteKey,List<Path>>> RECENT =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(256,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<SiteKey,List<Path>> entry) {
                    return size()>256;
                }
            });
    public record Path(Point a,Point b,double sag,double sway,double phase,
                       boolean thick,long key) {
        public double x(double t) {
            double dx=b.x-a.x,dz=b.z-a.z,span=Math.max(1,Math.hypot(dx,dz));
            double sideways=sway*Math.sin(Math.PI*t)
                    *(.70*Math.sin(2*Math.PI*t+phase)+.30*Math.sin(4*Math.PI*t+phase*.6));
            return a.x+dx*t-dz/span*sideways;
        }
        public double z(double t) {
            double dx=b.x-a.x,dz=b.z-a.z,span=Math.max(1,Math.hypot(dx,dz));
            double sideways=sway*Math.sin(Math.PI*t)
                    *(.70*Math.sin(2*Math.PI*t+phase)+.30*Math.sin(4*Math.PI*t+phase*.6));
            return a.z+dz*t+dx/span*sideways;
        }
        public double y(double t) {
            return a.y*(1-t)+b.y*t-sag*Math.sin(Math.PI*t)
                    *(1+.08*Math.sin(4*Math.PI*t+phase));
        }
        public boolean intersects(int cx,int cz) {
            return Math.min(a.x,b.x)-20<=cx+15 && Math.max(a.x,b.x)+20>=cx
                    && Math.min(a.z,b.z)-20<=cz+15 && Math.max(a.z,b.z)+20>=cz;
        }
    }

    /** Face-connected voxel trail, including the connectors between rounded curve samples. */
    public static List<Point> voxels(Path path) {
        double span=Math.hypot(path.b().x()-path.a().x(),path.b().z()-path.a().z());
        int samples=Math.max(1,(int)Math.ceil(span*1.9));
        ArrayList<Point> result=new ArrayList<>(samples*2);
        Point previous=path.a();
        result.add(previous);
        for(int i=1;i<=samples;i++) {
            double t=i/(double)samples;
            Point next=i==samples?path.b():new Point((int)Math.round(path.x(t)),
                    (int)Math.round(path.y(t)),(int)Math.round(path.z(t)));
            // Every move changes ONE coordinate, making consecutive blocks
            // face-adjacent, even when rounding changes two or three axes.
            int x=previous.x(),y=previous.y(),z=previous.z();
            while(x!=next.x()||y!=next.y()||z!=next.z()) {
                if(x!=next.x())x+=Integer.signum(next.x()-x);
                else if(y!=next.y())y+=Integer.signum(next.y()-y);
                else z+=Integer.signum(next.z()-z);
                result.add(new Point(x,y,z));
            }
            previous=next;
        }
        return result;
    }

    public static List<Path> paths(long seed,int gx,int gz,String theme) {
        SiteKey key=new SiteKey(seed,gx,gz,theme);
        var recent=RECENT.get();
        List<Path> cached=recent.get(key);
        if(cached!=null)return cached;
        List<Path> generated=generatePaths(seed,gx,gz,theme);
        List<Path> immutable=List.copyOf(generated);
        recent.put(key,immutable);
        return immutable;
    }

    private static List<Path> generatePaths(long seed,int gx,int gz,String theme) {
        List<SkyIslandShape.Island> islands=SkyIslandShape.sites(seed,gx,gz,theme);
        if(islands.isEmpty())return List.of();
        boolean swamp="swamp".equals(theme);
        ArrayList<Path> paths=new ArrayList<>();
        var main=islands.get(0);
        for(int i=1;i<islands.size();i++) {
            var satellite=islands.get(i);
            // More independently seeded side/underside anchors per satellite.
            for(int strand=0;strand<5;strand++) {
                long key=mix(seed,gx,gz,i*37+strand*13+9);
                if(strand>=3 && unit(key)<(swamp?.25:.32))continue;
                Path p=connection(main,satellite,key,strand%3!=0);
                if(p!=null && clearOfAll(p,islands))paths.add(p);
            }
        }
        // Many small drapes around different edges of the main island;
        // because they are individually collision-checked they don't become
        // a repeated, solid fringe or punch through the island itself.
        for(int i=0;i<(swamp?18:27);i++) {
            long key=mix(seed,gx,gz,0x539b+i*17L);
            Path p=loop(main,key);
            if(p!=null && clearOfAll(p,islands))paths.add(p);
        }
        // A few smaller underside drapes on satellite islands too.
        for(int i=1;i<islands.size();i++) {
            for(int n=0;n<5;n++) {
                long key=mix(seed,gx,gz,0x73f1+i*11L+n*17L);
                Path p=loop(islands.get(i),key);
                if(p!=null && clearOfAll(p,islands))paths.add(p);
            }
        }
        return paths;
    }

    private static boolean clearOfAll(Path path,List<SkyIslandShape.Island> islands) {
        for(int i=2;i<=18;i+=2) {
            double t=i/20.0;
            int x=(int)Math.round(path.x(t)),y=(int)Math.round(path.y(t)),z=(int)Math.round(path.z(t));
            for(var island:islands)if(island.solid(x,y,z))return false;
        }
        return true;
    }
    private static Path connection(SkyIslandShape.Island a,SkyIslandShape.Island b,long key,boolean low) {
        double dx=b.centerX()-a.centerX(),dz=b.centerZ()-a.centerZ();
        double span=Math.hypot(dx,dz);
        if(span<24||span>190)return null;
        dx/=span;dz/=span;
        // Low variants attach to the underside; the normal variants attach to
        // the side cliff, not the top grass surface.
        Point pa=face(a,dx,dz,low,unit(key)*.42-.21);
        Point pb=face(b,-dx,-dz,low,unit(key>>>11)*.42-.21);
        return make(a,b,pa,pb,key,low,false);
    }
    private static Path loop(SkyIslandShape.Island island,long key) {
        double angle=unit(key)*Math.PI*2;
        double separation=1.12+unit(key>>>13)*1.15;
        Point a=face(island,Math.cos(angle),Math.sin(angle),true,0);
        Point b=face(island,Math.cos(angle+separation),Math.sin(angle+separation),true,0);
        return make(island,island,a,b,key,true,true);
    }
    private static Point face(SkyIslandShape.Island island,double dx,double dz,
                              boolean underside,double sideShift) {
        double sideX=-dz,sideZ=dx,radius=island.primaryRadius();
        int centreY=(int)Math.round(island.centerY());
        for(double t:new double[]{1.08,1.00,.92,.85,.77,.69,.61,.53,.45}) {
            int x=(int)Math.round(island.centerX()+dx*radius*t+sideX*sideShift*radius);
            int z=(int)Math.round(island.centerZ()+dz*radius*t+sideZ*sideShift*radius);
            if(!island.touches(x,z))continue;
            var c=island.column(x,z);
            if(underside) {
                for(int drop:new int[]{16,23,31,9,39,46}) {
                    for(int offset=-3;offset<=3;offset++) {
                        int y=centreY-drop+offset;
                        if(y<SkyIslandShape.MIN_Y+6||y>SkyIslandShape.MAX_Y-3)continue;
                        if(c.solid(y)&&!c.solid(y-1)&&c.solid(y+2))return new Point(x,y-1,z);
                    }
                }
            } else {
                int ox=Math.abs(dx)>=Math.abs(dz)?(dx>=0?1:-1):0;
                int oz=ox==0?(dz>=0?1:-1):0;
                for(int drop:new int[]{9,15,23,4,30,38}) {
                    for(int offset=-2;offset<=2;offset++) {
                        int y=centreY-drop+offset;
                        if(y<SkyIslandShape.MIN_Y+6||y>SkyIslandShape.MAX_Y-3)continue;
                        if(c.solid(y)&&c.solid(y+2)
                                &&!island.solid(x+ox,y,z+oz))return new Point(x+ox,y,z+oz);
                    }
                }
            }
        }
        return null;
    }
    private static Path make(SkyIslandShape.Island a,SkyIslandShape.Island b,
                             Point start,Point end,long key,boolean low,boolean sameIsland) {
        if(start==null||end==null)return null;
        double span=Math.hypot(end.x-start.x,end.z-start.z);
        if(span<28||span>150)return null;
        // Compact drapes: undersides should have an obvious dip but must not
        // reach far below the island like the old 70-85 block loops.
        double sag=sameIsland ? 17+span*(.12+.08*unit(key>>>17))
                : (low?13:10)+span*(.10+.10*unit(key>>>17));
        sag=Math.max(sag,Math.abs(start.y-end.y)*.5+8);
        if(sag>(sameIsland?48:46))return null;
        double sway=2+6*unit(key>>>29),phase=unit(key>>>38)*Math.PI*2;
        boolean thick=unit(key>>>9)>.79; // about 1/5 are thicker variants
        Path path=new Path(start,end,sag,sway,phase,thick,key);
        if(path.y(.5)<SkyIslandShape.MIN_Y+8)return null;
        // Check the WHOLE curve, not just its midpoint. Reject arcs which
        // double back through solid sky terrain or have no actual air gap.
        int clear=0;
        for(int i=2;i<=18;i+=2) {
            double t=i/20.0;
            int x=(int)Math.round(path.x(t)), y=(int)Math.round(path.y(t)), z=(int)Math.round(path.z(t));
            if(a.solid(x,y,z)||b.solid(x,y,z))return null;
            clear++;
        }
        return clear>=8?path:null;
    }
    private static long mix(long seed,int gx,int gz,long salt) {
        long h=seed ^ (long)gx*0x9e3779b97f4a7c15L ^ (long)gz*0xbf58476d1ce4e5b9L ^ salt*0x94d049bb133111ebL;
        h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;
        h=(h^(h>>>27))*0x94d049bb133111ebL;
        return h^(h>>>31);
    }
    private static double unit(long h) {
        h=(h^(h>>>33))*0xff51afd7ed558ccdL;
        h^=h>>>33;
        return ((h>>>11)&((1L<<53)-1))*0x1.0p-53;
    }
}
