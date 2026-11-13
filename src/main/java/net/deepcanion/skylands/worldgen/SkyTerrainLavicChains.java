package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Deterministic, chunk-owned taut chains between procedural Lavic island masses. */
final class SkyTerrainLavicChains {
    record Point(int x, int y, int z) { }
    record Chain(Point a, Point b, long key) {
        boolean intersects(int cx, int cz) {
            return Math.min(a.x,b.x)-5<=cx+15 && Math.max(a.x,b.x)+5>=cx
                    && Math.min(a.z,b.z)-5<=cz+15 && Math.max(a.z,b.z)+5>=cz;
        }
    }
    private SkyTerrainLavicChains() { }
    @FunctionalInterface
    interface NeighbourIslands {
        /** Return only Lavic island shapes; empty if site is absent or another biome. */
        List<SkyIslandShape.Island> resolve(int gx, int gz);
    }
    private record SiteKey(long seed,int gx,int gz,boolean neighbors) { }
    private static final ThreadLocal<LinkedHashMap<SiteKey,List<Chain>>> CACHE =
            ThreadLocal.withInitial(() -> new LinkedHashMap<>(128,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<SiteKey,List<Chain>> entry) {
                    return size()>128;
                }
            });

    /** Kept for standalone same-site geometry tests. Production uses neighbors. */
    static List<Chain> fromSite(long seed, int gx, int gz, List<SkyIslandShape.Island> islands) {
        return fromSite(seed,gx,gz,islands,null);
    }

    static List<Chain> fromSite(long seed, int gx, int gz, List<SkyIslandShape.Island> islands,
                                NeighbourIslands neighbors) {
        SiteKey key = new SiteKey(seed,gx,gz,neighbors!=null);
        var cached = CACHE.get().get(key);
        if (cached!=null) return cached;
        List<Chain> created=generateSite(seed,gx,gz,islands,neighbors);
        CACHE.get().put(key,created);
        return created;
    }

    private static List<Chain> generateSite(long seed,int gx,int gz,
            List<SkyIslandShape.Island> islands, NeighbourIslands neighbors) {
        if (islands.isEmpty()) return List.of();
        long key=SkyTerrainPatterns.mix(seed,gx,0,gz,0x462aa9L);
        ArrayList<Chain> found=new ArrayList<>(4);
        // Local main-to-satellite and satellite-to-satellite candidates.
        for(int i=1;i<islands.size() && found.size()<2;i++) {
            Chain c=tryConnect(islands.get(0),islands.get(i),islands,
                    SkyTerrainPatterns.mix(key,i,0,0,0x462aaaL));
            if(c!=null)found.add(c);
        }
        for(int i=1;i<islands.size() && found.size()<2;i++)
            for(int j=i+1;j<islands.size() && found.size()<2;j++) {
                Chain c=tryConnect(islands.get(i),islands.get(j),islands,
                        SkyTerrainPatterns.mix(key,i,j,0,0x462aabL));
                if(c!=null)found.add(c);
            }
        if(neighbors!=null) {
            // Canonical forward neighbors: every site-pair is owned by only one
            // grid site, irrespective of chunk generation order or worker thread.
            final int[][] directions={{1,0},{0,1},{1,1},{1,-1}};
            for(int dir=0;dir<directions.length && found.size()<3;dir++) {
                int nx=gx+directions[dir][0],nz=gz+directions[dir][1];
                if(!SkyIslandShape.hasSite(seed,nx,nz))continue;
                // Cheap center-distance reject BEFORE biome sampling/resolving.
                double siteDistance=Math.hypot(SkyIslandShape.siteX(seed,nx,nz)
                        -SkyIslandShape.siteX(seed,gx,gz),
                        SkyIslandShape.siteZ(seed,nx,nz)
                        -SkyIslandShape.siteZ(seed,gx,gz));
                if(siteDistance>320)continue;
                var adjacent=neighbors.resolve(nx,nz);
                if(adjacent==null || adjacent.isEmpty())continue;
                ArrayList<SkyIslandShape.Island> obstruction=new ArrayList<>(
                        islands.size()+adjacent.size());
                obstruction.addAll(islands);
                obstruction.addAll(adjacent);
                // Four ordered pair types cover the closest visible landmasses
                // without O(N^2) bridge searches for every adjacent site.
                int[][] pairs={{0,0},{0,1},{1,0},{1,1},{0,2},{2,0}};
                for(int p=0;p<pairs.length && found.size()<3;p++) {
                    int i=pairs[p][0],j=pairs[p][1];
                    if(i>=islands.size() || j>=adjacent.size())continue;
                    Chain c=tryConnect(islands.get(i),adjacent.get(j),obstruction,
                            SkyTerrainPatterns.mix(key,nx,nz,p,0x462adeL));
                    if(c!=null) { found.add(c); break; } // max one per neighbor
                }
            }
        }
        return List.copyOf(found);
    }

    private static Chain tryConnect(SkyIslandShape.Island first,SkyIslandShape.Island second,
                                    List<SkyIslandShape.Island> islands,long key) {
        double dx=second.centerX()-first.centerX();
        double dz=second.centerZ()-first.centerZ();
        double distance=Math.hypot(dx,dz);
        if(distance<29 || distance>280) return null;
        dx/=distance; dz/=distance;
        Point a=anchor(first,dx,dz,-1), b=anchor(second,-dx,-dz,-1);
        if(a==null || b==null) return null;
        if(Math.abs(a.y-b.y)>35) {
            Point candidate=anchor(second,-dx,-dz,a.y);
            if(candidate!=null && Math.abs(candidate.y-a.y)<Math.abs(b.y-a.y))b=candidate;
        }
        if(Math.abs(a.y-b.y)>55) {
            Point candidate=anchor(first,dx,dz,b.y);
            if(candidate!=null && Math.abs(candidate.y-b.y)<Math.abs(a.y-b.y))a=candidate;
        }
        double span=Math.hypot(b.x-a.x,b.z-a.z);
        if(span<18 || span>215 || Math.abs(a.y-b.y)>94) {
            return null;
        }
        // Reject if a different island or cliff intersects the middle of a link.
        // Samples use immutable geometry only; never access neighboring chunks.
        for(int n=3;n<=17;n+=2) {
            double t=n/20.0;
            int x=(int)Math.round(a.x+(b.x-a.x)*t);
            int y=(int)Math.round(a.y+(b.y-a.y)*t);
            int z=(int)Math.round(a.z+(b.z-a.z)*t);
            for(var check:islands)if(check.solid(x,y,z)) {
                return null;
            }
        }
        return new Chain(a,b,key);
    }

    private static Point anchor(SkyIslandShape.Island island,double dx,double dz,int targetY) {
        int centreY=(int)Math.round(island.centerY());
        double reach=island.primaryRadius();
        Point best=null;
        int bestDelta=Integer.MAX_VALUE;
        // Sample actual cliff-column TOPS, rather than fixed center-Y offsets.
        // Fewer candidate radii, and reuse a single column across all heights.
        for(double f=1.37;f>=.42;f-=.085) {
            int x=(int)Math.round(island.centerX()+dx*reach*f);
            int z=(int)Math.round(island.centerZ()+dz*reach*f);
            if(!island.touches(x,z))continue;
            var column=island.column(x,z);
            int top=-1;
            int low=Math.max(island.minY(),centreY-30);
            for(int y=island.maxY();y>=low;y--) {
                if(column.solid(y)) {top=y;break;}
            }
            if(top<0)continue;
            int px=x+(int)Math.round(dx*4),pz=z+(int)Math.round(dz*4);
            var beyond=island.column(px,pz);
            int[] belowTop={5,9,14,20,28};
            for(int offset:belowTop) {
                int y=top-offset;
                if(y<SkyIslandShape.MIN_Y+8 || y>SkyIslandShape.MAX_Y-8)continue;
                if(column.solid(y) && !beyond.solid(y)) {
                    if(targetY<0)return new Point(x,y,z);
                    int delta=Math.abs(y-targetY);
                    if(delta<bestDelta) {
                        best=new Point(x,y,z);
                        bestDelta=delta;
                        if(delta<=4)return best;
                    }
                }
            }
        }
        return best;
    }

    static int decorate(WorldGenLevel level,int chunkX,int chunkZ,List<Chain> chains) {
        if(chains.isEmpty())return 0;
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        BlockState brick=ModBlocks.LAVIC_SKYMETAL_BRICKS.get().defaultBlockState();
        BlockState polished=ModBlocks.POLISHED_LAVIC_SKYMETAL.get().defaultBlockState();
        int placed=0;
        for(Chain chain:chains) {
            if(!chain.intersects(chunkX,chunkZ))continue;
            double dx=chain.b.x-chain.a.x, dy=chain.b.y-chain.a.y,
                    dz=chain.b.z-chain.a.z;
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            double horizontal=Math.hypot(dx,dz);
            if(horizontal<1 || length<1)continue;
            double ux=dx/length, uy=dy/length, uz=dz/length;
            double sx=-dz/horizontal, sz=dx/horizontal;
            int count=Math.max(2,(int)Math.ceil(length/8.0));
            for(int link=0;link<count;link++) {
                // Links alternate between vertical and horizontal ellipses;
                // their overlapping ends look mechanically interlocked.
                boolean upright=link%2==0;
                double mid=(link+.5)/(double)count;
                double cx=chain.a.x+dx*mid;
                double cy=chain.a.y+dy*mid;
                double cz=chain.a.z+dz*mid;
                double half=length/count*.72, radius=2.8;
                Point previous=null;
                for(int step=0;step<=48;step++) {
                    double theta=step*2*Math.PI/48;
                    double along=half*Math.cos(theta),side=radius*Math.sin(theta);
                    Point current=new Point((int)Math.round(cx+ux*along+(upright?0:sx*side)),
                            (int)Math.round(cy+uy*along+(upright?side:0)),
                            (int)Math.round(cz+uz*along+(upright?0:sz*side)));
                    if(previous!=null) {
                        // Fill every Manhattan step between rounded ring samples;
                        // never leave diagonal-only, disconnected chain blocks.
                        int x=previous.x,y=previous.y,z=previous.z;
                        while(x!=current.x || y!=current.y || z!=current.z) {
                            if(x!=current.x)x+=Integer.signum(current.x-x);
                            else if(y!=current.y)y+=Integer.signum(current.y-y);
                            else z+=Integer.signum(current.z-z);
                            placed+=put(level,cursor,x,y,z,chunkX,chunkZ,brick);
                        }
                    }
                    placed+=put(level,cursor,current.x,current.y,current.z,
                            chunkX,chunkZ,(link%4==0 && step%12<2)?polished:brick);
                    previous=current;
                }
            }
            // Heavy metal anchor collars join the two real cliff faces.
            for(Point end:new Point[]{chain.a,chain.b}) {
                for(int x=end.x-2;x<=end.x+2;x++)for(int y=end.y-2;y<=end.y+2;y++) {
                    if(Math.abs(x-end.x)==2 && Math.abs(y-end.y)==2)continue;
                    for(int z=end.z-2;z<=end.z+2;z++) {
                        if(Math.abs(z-end.z)>1)continue;
                        if(Math.abs(x-end.x)+Math.abs(y-end.y)<2)continue;
                        placed+=put(level,cursor,x,y,z,chunkX,chunkZ,polished);
                    }
                }
            }
        }
        return placed;
    }

    private static int put(WorldGenLevel level,BlockPos.MutableBlockPos pos,int x,int y,int z,
                           int cx,int cz,BlockState material) {
        if(x<cx||x>cx+15||z<cz||z>cz+15||y<SkyIslandShape.MIN_Y||y>SkyIslandShape.MAX_Y)return 0;
        pos.set(x,y,z);
        if(!level.ensureCanWrite(pos) || !level.getBlockState(pos).isAir())return 0;
        return level.setBlock(pos,material,Block.UPDATE_CLIENTS)?1:0;
    }
}
