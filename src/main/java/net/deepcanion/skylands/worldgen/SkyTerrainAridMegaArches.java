package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;

/** Rare, enormous terracotta natural arches spanning separate Arid island lobes.
 * Shapes are discovered once per seed/site and rendered only in the owning chunk;
 * no cross-chunk writes or neighbor-chunk queries are required. */
final class SkyTerrainAridMegaArches {
    record Point(int x,int y,int z) { }
    record Arch(Point a,Point b,int rise,long seed) {
        boolean intersects(int cx,int cz) {
            return Math.min(a.x,b.x)-16<=cx+15 && Math.max(a.x,b.x)+16>=cx
                    && Math.min(a.z,b.z)-16<=cz+15 && Math.max(a.z,b.z)+16>=cz;
        }
    }
    private record Key(long seed,int gx,int gz) { }
    private static final ThreadLocal<LinkedHashMap<Key,List<Arch>>> CACHE =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(100,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key,List<Arch>> e) {
                    return size()>96;
                }
            });
    private SkyTerrainAridMegaArches() { }

    static List<Arch> fromSite(long seed,int gx,int gz,List<SkyIslandShape.Island> islands) {
        Key key=new Key(seed,gx,gz);
        var cache=CACHE.get();
        var old=cache.get(key);
        if(old!=null)return old;
        List<Arch> found=create(seed,gx,gz,islands);
        cache.put(key,found);
        return found;
    }
    private static List<Arch> create(long seed,int gx,int gz,List<SkyIslandShape.Island> islands) {
        if(islands.size()<2)return List.of();
        long roll=SkyTerrainPatterns.mix(seed,gx,0,gz,0x4a719bL);
        if(SkyTerrainPatterns.unit(roll)>.43)return List.of();
        SkyIslandShape.Island main=islands.get(0);
        ArrayList<Arch> output=new ArrayList<>(1);
        for(int i=1;i<islands.size();i++) {
            SkyIslandShape.Island other=islands.get(i);
            double dx=other.centerX()-main.centerX(),dz=other.centerZ()-main.centerZ();
            double len=Math.hypot(dx,dz);
            if(len<55 || len>152)continue;
            dx/=len;dz/=len;
            int ax=(int)Math.round(main.centerX()+dx*main.primaryRadius()*.69);
            int az=(int)Math.round(main.centerZ()+dz*main.primaryRadius()*.69);
            int bx=(int)Math.round(other.centerX()-dx*other.primaryRadius()*.49);
            int bz=(int)Math.round(other.centerZ()-dz*other.primaryRadius()*.49);
            double highland=Math.max(SkyTerrainSubregions.arid(seed,ax,az).rockyHighlands(),
                    SkyTerrainSubregions.arid(seed,bx,bz).rockyHighlands());
            if(highland<.43)continue;
            int ay=surface(main,ax,az),by=surface(other,bx,bz);
            if(ay<0 || by<0 || Math.abs(ay-by)>39)continue;
            double span=Math.hypot(ax-bx,az-bz);
            if(span<28 || span>100)continue;
            int rise=18+(int)(SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(roll,i,0,0,0x4a719cL))*16);
            if(Math.max(ay,by)+rise+5>SkyIslandShape.MAX_Y)continue;
            // Reject if the span passes through an unrelated island mass.
            boolean obstructed=false;
            for(int t=2;t<=8;t++) {
                double step=t/10.0;
                int x=(int)Math.round(ax+(bx-ax)*step);
                int y=(int)Math.round(ay+(by-ay)*step+4*rise*step*(1-step));
                int z=(int)Math.round(az+(bz-az)*step);
                for(var island:islands) if(island.solid(x,y,z)) {obstructed=true;break;}
                if(obstructed)break;
            }
            if(obstructed)continue;
            output.add(new Arch(new Point(ax,ay,az),new Point(bx,by,bz),rise,
                    SkyTerrainPatterns.mix(roll,i,0,0,0x4a719dL)));
            break;
        }
        return List.copyOf(output);
    }
    private static int surface(SkyIslandShape.Island island,int x,int z) {
        if(!island.touches(x,z))return -1;
        var column=island.column(x,z);
        for(int y=Math.min(SkyIslandShape.MAX_Y-3,island.maxY());y>=island.centerY()-12;y--)
            if(column.solid(y) && column.solid(y-2))return y;
        return -1;
    }

    static int decorate(WorldGenLevel level,long seed,int cx,int cz,List<Arch> arches) {
        if(arches.isEmpty())return 0;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        int count=0;
        for(Arch arch:arches) {
            if(!arch.intersects(cx,cz))continue;
            double dx=arch.b.x-arch.a.x,dz=arch.b.z-arch.a.z;
            double span=Math.hypot(dx,dz);
            if(span<1)continue;
            double sx=-dz/span,sz=dx/span;
            // Wide, naturally eroded mesa arch: long nearly-flat crest, asymmetrical
            // flanks and strong taper from anchored feet to upper shoulders.
            int steps=(int)Math.ceil(span*2.0);
            double peak=Math.max(arch.a.y,arch.b.y)+arch.rise;
            double leftRamp=.23+.13*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(arch.seed,0,0,0,0x4a71a1L));
            double rightRamp=.24+.14*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(arch.seed,1,0,0,0x4a71a2L));
            double sweep=SkyTerrainPatterns.unit(arch.seed)*6.283185307179586;
            for(int step=0;step<=steps;step++) {
                double t=step/(double)steps;
                // A slowly wandering centerline breaks the rigid bridge silhouette.
                // Both end points remain anchored; the middle may bow sideways.
                double bend=(SkyTerrainPatterns.noise2(seed,
                        (int)Math.round(arch.a.x+dx*t),(int)Math.round(arch.a.z+dz*t),
                        37,0x4a71b1L)-.5)*8.0*Math.sin(Math.PI*t);
                double x=arch.a.x+dx*t+sx*bend,z=arch.a.z+dz*t+sz*bend;
                double approach=Math.min(1.0,Math.max(0.0,t/leftRamp));
                double departure=Math.min(1.0,Math.max(0.0,(1-t)/rightRamp));
                double plateau=Math.min(approach*approach*(3-2*approach),
                        departure*departure*(3-2*departure));
                // One shoulder can rise earlier and higher than the other;
                // the crest is a ragged, asymmetrical mesa rather than a smooth arc.
                double largeErosion=(SkyTerrainPatterns.noise2(seed,(int)Math.round(x),
                        (int)Math.round(z),43,0x4a71b2L)-.5);
                double baseline=arch.a.y+(arch.b.y-arch.a.y)*t;
                // A gently tilted, broad mesa cap rather than a perfect circle.
                double crestWarp=(SkyTerrainPatterns.noise2(seed,
                        (int)Math.round(x),(int)Math.round(z),24,0x4a71a3L)-.5)*3.6;
                double roof=baseline+(peak-baseline)*plateau
                        +(3.4*(t-.42)+largeErosion*7.0)*plateau +crestWarp*plateau;
                double endStrength=Math.pow(Math.max(0,1-Math.min(t,1-t)/.27),1.55);
                double shoulder=Math.pow(Math.max(0,1-Math.abs(t-.28)/.18),2)
                        +Math.pow(Math.max(0,1-Math.abs(t-.72)/.18),2);
                double irregular=SkyTerrainPatterns.noise2(seed,(int)Math.round(x),
                        (int)Math.round(z),19,0x4a719eL)-.5;
                double footBulge=Math.pow(Math.max(0,1-t/.23),2)
                        *(3.2+2.5*SkyTerrainPatterns.unit(arch.seed^0x416abeL))
                        +Math.pow(Math.max(0,1-(1-t)/.28),2)
                        *(4.4+3.3*SkyTerrainPatterns.unit(arch.seed^0x24b8caL));
                double halfWidth=5.5+5.8*endStrength+footBulge+1.85*shoulder
                        +3.8*largeErosion +2.1*irregular
                        +.8*Math.sin(t*9+sweep);
                double depth=8.0+14.8*endStrength+2.5*shoulder
                        +3.2*largeErosion+2.6*irregular;
                int xi=(int)Math.round(x),zi=(int)Math.round(z);
                // Wide feet and offset centerline need a larger bounded scan.
                for(int xx=Math.max(cx,xi-21);xx<=Math.min(cx+15,xi+21);xx++)
                    for(int zz=Math.max(cz,zi-21);zz<=Math.min(cz+15,zi+21);zz++) {
                        double along=(xx-x)*dx/span+(zz-z)*dz/span;
                        if(Math.abs(along)>.85)continue;
                        double lateralWarp=(SkyTerrainPatterns.noise2(seed,xx,zz,11,0x4a71a4L)-.5)*3.3;
                        double transverseShift=1.9*Math.sin(t*5+sweep)
                                +2.2*largeErosion*Math.sin(Math.PI*t);
                        double cross=(xx-x)*sx+(zz-z)*sz-transverseShift;
                        // Deep, non-repeating erosion along the outer rock edges.
                        double asymmetry=1.8*Math.sin(t*7.5+sweep+cross*.14);
                        double edge=halfWidth+lateralWarp+asymmetry
                                +.55*Math.sin(t*17+cross*.48+sweep);
                        if(Math.abs(cross)>Math.max(2.5,edge))continue;
                        double roofDetail=(SkyTerrainPatterns.noise2(seed,xx,zz,9,0x4a71a5L)-.5)*3.6
                                +(SkyTerrainPatterns.noise2(seed,xx,zz,26,0x4a71b3L)-.5)*3.2;
                        double topFace=roof+roofDetail;
                        double underside=topFace-depth*(.89+.16*(1-Math.pow(
                                cross/Math.max(2.5,edge),2)))
                                +1.9*(SkyTerrainPatterns.noise2(seed,xx,zz,14,0x4a71b4L)-.5);
                        double broad=SkyTerrainPatterns.noise2(seed,xx,zz,21,0x4a719fL);
                        double warp=SkyAridTerracottaPalette.warp(seed,xx,zz);
                        int bottom=Math.max(SkyIslandShape.MIN_Y,(int)Math.floor(underside));
                        int top=Math.min(SkyIslandShape.MAX_Y,(int)Math.floor(topFace));
                        for(int yy=bottom;yy<=top;yy++) {
                            if(yy+0.5<underside)continue;
                            p.set(xx,yy,zz);
                            if(level.ensureCanWrite(p) && level.getBlockState(p).isAir()
                                    && level.setBlock(p,SkyAridTerracottaPalette.archStone(
                                            yy,broad,warp),Block.UPDATE_CLIENTS))count++;
                        }
                    }
            }
        }
        return count;
    }
}
