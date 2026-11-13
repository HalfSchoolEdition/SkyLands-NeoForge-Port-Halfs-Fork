package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cheap, immutable, per-worker, island-seeded Crystal Glacier sites. */
final class SkyGlacialCrystalSites {
    record Crystal(int x,int y,int z,int radius,int height,boolean arch,long key) { }
    private record Key(long seed,int x,int y,int z,int radius) { }
    private static final ThreadLocal<LinkedHashMap<Key,List<Crystal>>> CACHE =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(48,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key,List<Crystal>> entry) {
                    return size()>48;
                }
            });
    /** One outward-pointing crystal urchin. Every chunk reconstructs the same arms. */
    record Urchin(int x,int y,int z,double nx,double ny,double nz,
                  int arms,long key) {
        boolean intersects(int chunkX,int chunkZ) {
            // Upper bound: 24 block arm plus up to 4 blocks of shell/offset.
            return x+32>=chunkX && x-32<=chunkX+15
                    && z+32>=chunkZ && z-32<=chunkZ+15;
        }
    }

    private static final ThreadLocal<LinkedHashMap<Key,List<Urchin>>> URCHIN_CACHE =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(48,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key,List<Urchin>> entry) {
                    return size()>48;
                }
            });

    /**
     * New glassy-crystal urchins, deliberately independent from the existing
     * packed-ice formations so their distribution doesn't shift old sites.
     * Only the island's seed-stable solid field is consulted: never a chunk.
     */
    static List<Urchin> urchins(long seed,SkyIslandShape.Island island) {
        Key key=new Key(seed,(int)island.centerX(),(int)island.centerY(),
                (int)island.centerZ(),(int)Math.round(island.primaryRadius()*10));
        var cache=URCHIN_CACHE.get();
        List<Urchin> found=cache.get(key);
        if(found!=null)return found;
        java.util.Random rng=new java.util.Random(SkyTerrainPatterns.mix(seed,
                key.x(),key.y(),key.z(),0x7c15c441L));
        int attempts=island.primaryRadius()>=37 ? 17 : 6;
        int cap=island.primaryRadius()>=37 ? 2 : 1;
        ArrayList<Urchin> result=new ArrayList<>(cap);
        for(int i=0;i<attempts && result.size()<cap;i++) {
            double angle=rng.nextDouble()*Math.PI*2;
            double vertical=rng.nextDouble();
            // Surface-facing: top (50%), cliff/steep hillside (35%), underside (15%).
            double elevation=i%20<10 ? .47+.48*vertical
                    : i%20<17 ? -.22+.55*vertical : -.82+.34*vertical;
            double horizontal=Math.sqrt(1-elevation*elevation);
            double dx=Math.cos(angle)*horizontal, dy=elevation, dz=Math.sin(angle)*horizontal;
            int ax=0,ay=0,az=0;
            boolean inside=false,foundBoundary=false;
            // A ray from island centre encounters its true boundary, including
            // steep slopes and undersides. Never sample a WorldGenLevel chunk.
            for(double t=0;t<125;t+=1.5) {
                int x=(int)Math.round(island.centerX()+dx*t);
                int y=(int)Math.round(island.centerY()+dy*t);
                int z=(int)Math.round(island.centerZ()+dz*t);
                if(island.solid(x,y,z)) {
                    inside=true; ax=x; ay=y; az=z;
                } else if(inside && t>=island.primaryRadius()*.32) {
                    foundBoundary=true;break;
                }
            }
            // Conservative 27-block vertical envelope leaves room for angled
            // arms on walls and undersides without cutting off their tips.
            if(!foundBoundary || ay<SkyIslandShape.MIN_Y+27 || ay>SkyIslandShape.MAX_Y-27)continue;
            var district=SkyTerrainSubregions.glaciated(seed,ax,az);
            // Crystal-glacier district is the main home. Frozen plains get
            // occasional clusters; trees/forest only very rare surprises.
            double chance=.010*district.forest()+.085*district.plains()
                    +.54*district.crystals();
            if(rng.nextDouble()>=chance)continue;
            SkyIslandShape.LakeSample lake=island.column(ax,az).lake();
            if(lake!=null && lake.active() && lake.water())continue;
            // Gradient of solid -> air recovers the actual local surface normal
            // instead of assuming every formation grows straight upward.
            double nx=(island.solid(ax-2,ay,az)?1:0)
                    -(island.solid(ax+2,ay,az)?1:0);
            double ny=(island.solid(ax,ay-2,az)?1:0)
                    -(island.solid(ax,ay+2,az)?1:0);
            double nz=(island.solid(ax,ay,az-2)?1:0)
                    -(island.solid(ax,ay,az+2)?1:0);
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<.5 || (nx*dx+ny*dy+nz*dz)/length<.45) {
                nx=dx;ny=dy;nz=dz;
            } else {
                nx/=length;ny/=length;nz/=length;
            }
            // Root must genuinely be embedded in the native island.
            if(!island.solid(ax-(int)Math.round(nx*2),ay-(int)Math.round(ny*2),
                    az-(int)Math.round(nz*2)))continue;
            long signature=rng.nextLong();
            int arms=2+rng.nextInt(7); // 2-8 arms per formation
            result.add(new Urchin(ax,ay,az,nx,ny,nz,arms,signature));
        }
        found=List.copyOf(result);
        cache.put(key,found);
        return found;
    }

    /** A suspended, double-pointed packed-ice plumbob; optional detached shards. */
    record FloatingCrystal(int x,int y,int z,int radius,int top,int bottom,
                           double tiltX,double tiltZ,boolean shattered,long key) {
        boolean intersects(int chunkX,int chunkZ) {
            // Max 4 block body radius, 7 block fragment offsets, 3 block shard radius.
            return x+19>=chunkX && x-19<=chunkX+15
                    && z+19>=chunkZ && z-19<=chunkZ+15;
        }
    }
    private static final ThreadLocal<LinkedHashMap<Key,List<FloatingCrystal>>> FLOAT_CACHE =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(48,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key,List<FloatingCrystal>> entry) {
                    return size()>48;
                }
            });

    /**
     * Sparse suspended ice crystals, separate from the established ground
     * spike sites and the new crystalline urchins. Geometry and distribution
     * use the owning island only, so cross-chunk order cannot change a site.
     */
    static List<FloatingCrystal> floating(long seed,SkyIslandShape.Island island) {
        Key key=new Key(seed,(int)island.centerX(),(int)island.centerY(),
                (int)island.centerZ(),(int)Math.round(island.primaryRadius()*10));
        var cache=FLOAT_CACHE.get();
        List<FloatingCrystal> found=cache.get(key);
        if(found!=null)return found;
        java.util.Random rng=new java.util.Random(SkyTerrainPatterns.mix(seed,
                key.x(),key.y(),key.z(),0x4ce81019L));
        int attempts=island.primaryRadius()>=37 ? 18 : 6;
        int cap=island.primaryRadius()>=37 ? 2 : 1;
        ArrayList<FloatingCrystal> result=new ArrayList<>(cap);
        // Reuse cached ground sites to avoid an overcrowded skyline.
        List<Crystal> grounded=sites(seed,island);
        for(int i=0;i<attempts && result.size()<cap;i++) {
            double angle=2*Math.PI*rng.nextDouble();
            double dist=island.primaryRadius()*(.12+.64*Math.sqrt(rng.nextDouble()));
            int x=(int)Math.round(island.centerX()+Math.cos(angle)*dist);
            int z=(int)Math.round(island.centerZ()+Math.sin(angle)*dist);
            var district=SkyTerrainSubregions.glaciated(seed,x,z);
            // Crystal Glaciers are the recognizable home for these. Some
            // occasional frozen-plains specimens, almost none in forests.
            double chance=.001*district.forest()+.016*district.plains()
                    +.50*district.crystals();
            if(rng.nextDouble()>=chance)continue;
            // Even transitional forest edges should seldom have sky crystals.
            if(district.dominant()==SkyTerrainSubregions.GlaciatedType.FOREST
                    && rng.nextDouble()>.28)continue;
            var column=island.column(x,z);
            SkyIslandShape.LakeSample lake=column.lake();
            if(lake!=null && lake.active() && lake.water())continue;
            int surface=Integer.MIN_VALUE;
            for(int cy=island.maxY();cy>=island.minY();cy--) {
                if(column.solid(cy)){surface=cy;break;}
            }
            if(surface==Integer.MIN_VALUE)continue;
            int radius=2+rng.nextInt(3);
            int top=9+rng.nextInt(9); // 9-17 blocks above its waist
            int bottom=6+rng.nextInt(8); // asymmetric 6-13 block lower point
            int hover=4+rng.nextInt(19); // actual bottom 4-22 above the rock
            int y=surface+hover+bottom;
            if(y+top+2>=SkyIslandShape.MAX_Y || y-bottom-2<=SkyIslandShape.MIN_Y)continue;
            boolean crowded=false;
            for(Crystal c:grounded) {
                if((long)(c.x()-x)*(c.x()-x)+(long)(c.z()-z)*(c.z()-z)<16L*16L) {
                    crowded=true;break;
                }
            }
            if(crowded)continue;
            for(FloatingCrystal prev:result) {
                if((long)(prev.x()-x)*(prev.x()-x)+(long)(prev.z()-z)*(prev.z()-z)<22L*22L) {
                    crowded=true;break;
                }
            }
            if(crowded)continue;
            double tiltX=(rng.nextDouble()-.5)*.14;
            double tiltZ=(rng.nextDouble()-.5)*.14;
            boolean shattered=rng.nextInt(4)==0;
            result.add(new FloatingCrystal(x,y,z,radius,top,bottom,
                    tiltX,tiltZ,shattered,rng.nextLong()));
        }
        found=List.copyOf(result);
        cache.put(key,found);
        return found;
    }

    private SkyGlacialCrystalSites() { }

    static List<Crystal> sites(long seed,SkyIslandShape.Island island) {
        Key key=new Key(seed,(int)island.centerX(),(int)island.centerY(),
                (int)island.centerZ(),(int)Math.round(island.primaryRadius()*10));
        var cache=CACHE.get();
        List<Crystal> found=cache.get(key);
        if(found!=null)return found;
        // Prefer a handful of meaningful formations per main island to
        // dozens of small identical spikes in every chunk.
        java.util.Random rng=new java.util.Random(SkyTerrainPatterns.mix(seed,key.x(),key.y(),
                key.z(),0x671b339L));
        int attempts=island.primaryRadius()>37 ? 19 : 5;
        double radius=island.primaryRadius();
        ArrayList<Crystal> result=new ArrayList<>(10);
        for(int i=0;i<attempts;i++) {
            double angle=rng.nextDouble()*Math.PI*2;
            double distance=radius*(.10+.82*Math.sqrt(rng.nextDouble()));
            int x=(int)Math.round(island.centerX()+Math.cos(angle)*distance);
            int z=(int)Math.round(island.centerZ()+Math.sin(angle)*distance);
            var region=SkyTerrainSubregions.glaciated(seed,x,z);
            if(region.crystals()<.57 || rng.nextDouble()>.50*region.crystals()+.14)continue;
            SkyIslandShape.Column column=island.column(x,z);
            SkyIslandShape.LakeSample lake=column.lake();
            if(lake!=null && lake.active() && lake.water())continue;
            int y=-1;
            for(int cy=island.maxY();cy>=Math.max(island.minY(),SkyIslandShape.MIN_Y);cy--) {
                if(column.solid(cy)) {y=cy+1;break;}
            }
            if(y<0 || y+30>=SkyIslandShape.MAX_Y)continue;
            int size=2+rng.nextInt(3);
            int height=13+rng.nextInt(14);
            boolean arch=rng.nextInt(9)==0 && radius>35;
            long signature=rng.nextLong();
            if(arch) {
                // Ice arches must actually connect to their island on BOTH
                // ends, not form thin floating rings beyond the cliff edge.
                double axis=2*Math.PI*SkyTerrainPatterns.unit(signature);
                double span=7+size*1.25;
                for(int side=-1;side<=1;side+=2) {
                    int ax=(int)Math.round(x+side*Math.cos(axis)*span);
                    int az=(int)Math.round(z+side*Math.sin(axis)*span);
                    var anchor=island.column(ax,az);
                    if(!anchor.solid(y-1)&&!anchor.solid(y-2)&&!anchor.solid(y-3)) {
                        arch=false;
                        break;
                    }
                }
            }
            result.add(new Crystal(x,y,z,size,height,arch,signature));
            if(result.size()>=8)break;
        }
        found=List.copyOf(result);
        cache.put(key,found);
        return found;
    }

}
