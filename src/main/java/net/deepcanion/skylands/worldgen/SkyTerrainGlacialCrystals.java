package net.deepcanion.skylands.worldgen;

import java.util.List;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rare, deterministic clusters of giant ice shards and occasional arch-shaped
 * glaciers within Crystal Glacier virtual districts. All positions are based on
 * the owning island and world seed; each invocation writes ONLY its own chunk.
 * No neighbor chunks are queried or generated. Per-worker geometry caching
 * prevents repeat surface searches across nearby chunks.
 */
final class SkyTerrainGlacialCrystals {
    private static final int MAX_REACH = 17;
    private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState CORE = ModBlocks.CRISTALIZEDICE.get().defaultBlockState();
    private SkyTerrainGlacialCrystals() { }

    static int decorate(WorldGenLevel level,long seed,int chunkX,int chunkZ,
                        List<SkyIslandShape.Island> islands) {
        int placed=0;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for (SkyIslandShape.Island island:islands) {
            for (SkyGlacialCrystalSites.Crystal crystal:SkyGlacialCrystalSites.sites(seed,island)) {
                if (crystal.x()-MAX_REACH>chunkX+15 || crystal.x()+MAX_REACH<chunkX
                        || crystal.z()-MAX_REACH>chunkZ+15 || crystal.z()+MAX_REACH<chunkZ) continue;
                if (crystal.arch()) placed+=arch(level,chunkX,chunkZ,pos,crystal);
                else placed+=cluster(level,chunkX,chunkZ,pos,crystal);
            }
            for(SkyGlacialCrystalSites.FloatingCrystal site:SkyGlacialCrystalSites.floating(seed,island)) {
                if(site.intersects(chunkX,chunkZ))
                    placed+=floating(level,chunkX,chunkZ,pos,site);
            }
            for(SkyGlacialCrystalSites.Urchin site:SkyGlacialCrystalSites.urchins(seed,island)) {
                if(site.intersects(chunkX,chunkZ))
                    placed+=urchin(level,chunkX,chunkZ,pos,site);
            }
        }
        return placed;
    }

    private static int cluster(WorldGenLevel level,int chunkX,int chunkZ,
                               BlockPos.MutableBlockPos pos,SkyGlacialCrystalSites.Crystal c) {
        int count=0;
        // A central long crystalline shard and shorter staggered fragments.
        for(int j=0;j<4;j++) {
            long hash=SkyTerrainPatterns.mix(c.key(),j,0,0,0x671b442L);
            double angle=SkyTerrainPatterns.unit(hash)*Math.PI*2;
            long detail=SkyTerrainPatterns.mix(hash,j,1,0,0x671b443L);
            double reach=j==0 ? 0 : 2.0+3.1*SkyTerrainPatterns.unit(detail);
            double centerX=c.x()+Math.cos(angle)*reach;
            double centerZ=c.z()+Math.sin(angle)*reach;
            int radius=j==0 ? c.radius() : Math.max(1,c.radius()-1);
            int height=j==0 ? c.height() : 5+(int)(c.height()*.65*
                    SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(detail,0,2,0,0x671b444L)));
            double tiltX=(SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(detail,1,2,0,0x671b445L))-.5)*.28;
            double tiltZ=(SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(detail,2,2,0,0x671b446L))-.5)*.28;
            int xmin=Math.max(chunkX,(int)Math.floor(centerX-radius-4));
            int xmax=Math.min(chunkX+15,(int)Math.ceil(centerX+radius+4));
            int zmin=Math.max(chunkZ,(int)Math.floor(centerZ-radius-4));
            int zmax=Math.min(chunkZ+15,(int)Math.ceil(centerZ+radius+4));
            // Below the surface the same profile tapers into a buried point.
            // On hillsides the formerly flat circular base now looks like a
            // natural double-ended crystal, not an unsupported shelf.
            int rootDepth=Math.max(6,(int)Math.round(height*.60));
            for(int y=-rootDepth;y<height;y++) {
                double t=y<0 ? -y/(double)rootDepth : y/(double)height;
                double r=Math.max(.50,radius*Math.pow(1-t,1.12));
                double px=centerX+tiltX*y, pz=centerZ+tiltZ*y;
                for(int x=xmin;x<=xmax;x++)for(int z=zmin;z<=zmax;z++) {
                    if((x-px)*(x-px)+(z-pz)*(z-pz)>r*r)continue;
                    if(put(level,pos,x,c.y()+y,z,c.key()+j*131L))count++;
                }
            }
        }
        return count;
    }

    /**
     * Suspended faceted packed-ice crystals with unequal upward/downward tips.
     * One in four sites additionally scatters 2-4 detached double-pointed shards.
     * All fragment coordinates derive from the immutable site key; every chunk
     * independently reconstructs the same voxels and writes only inside itself.
     */
    private static int floating(WorldGenLevel level,int chunkX,int chunkZ,
                                BlockPos.MutableBlockPos pos,SkyGlacialCrystalSites.FloatingCrystal c) {
        if(!c.shattered())return floatingShard(level,chunkX,chunkZ,pos,c.x(),c.y(),c.z(),
                c.radius(),c.top(),c.bottom(),c.tiltX(),c.tiltZ(),c.key());
        // A shattered plumbob is actually separated: the two unequal pointed
        // halves have drifted apart, with smaller splinters suspended nearby.
        // Each half tapers toward its fracture rather than showing a flat cut.
        int placed=floatingShard(level,chunkX,chunkZ,pos,
                c.x()+.8,c.y()+c.top()*.4,c.z()-.5,
                c.radius(),Math.max(4,(int)Math.ceil(c.top()*.60)),2,
                c.tiltX(),c.tiltZ(),c.key());
        placed+=floatingShard(level,chunkX,chunkZ,pos,
                c.x()-1.0,c.y()-c.bottom()*.4,c.z()+.7,
                Math.max(1,c.radius()-1),2,Math.max(3,(int)Math.ceil(c.bottom()*.60)),
                c.tiltX()-.05,c.tiltZ()+.05,c.key()^0x14ce72L);
        int fragments=2+(int)(SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(c.key(),1,0,0,0x5c7211L))*3); // 2-4
        for(int i=0;i<fragments;i++) {
            long hash=SkyTerrainPatterns.mix(c.key(),i,1,0,0x5c7222L);
            double angle=2*Math.PI*SkyTerrainPatterns.unit(hash);
            double dist=4+4*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,1,0,0,0x5c7233L));
            double x=c.x()+Math.cos(angle)*dist;
            double z=c.z()+Math.sin(angle)*dist;
            double y=c.y()-4+8*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,2,0,0,0x5c7244L));
            int top=3+(int)(5*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,3,0,0,0x5c7255L)));
            int bottom=3+(int)(5*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,4,0,0,0x5c7266L)));
            double tiltX=c.tiltX()+(SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,5,0,0,0x5c7277L))-.5)*.25;
            double tiltZ=c.tiltZ()+(SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,6,0,0,0x5c7288L))-.5)*.25;
            placed+=floatingShard(level,chunkX,chunkZ,pos,x,y,z,1+(i%2),
                    top,bottom,tiltX,tiltZ,hash);
        }
        return placed;
    }

    private static int floatingShard(WorldGenLevel level,int chunkX,int chunkZ,
                                     BlockPos.MutableBlockPos pos,
                                     double cx,double cy,double cz,int radius,
                                     int top,int bottom,double tiltX,double tiltZ,long key) {
        int placed=0;
        // A diamond-oriented square cross section produces four legible faces,
        // not the circular silhouette of the existing grounded ice spires.
        double theta=Math.PI/4+SkyTerrainPatterns.unit(key)*Math.PI/2;
        double cosine=Math.cos(theta),sine=Math.sin(theta);
        for(int dy=-bottom;dy<=top;dy++) {
            double fraction=dy<0 ? -dy/(double)bottom : dy/(double)top;
            double width=radius*Math.pow(Math.max(0,1-fraction),.86);
            double layerX=cx+tiltX*dy,layerZ=cz+tiltZ*dy;
            int reach=(int)Math.ceil(radius*1.5+2);
            int xmin=Math.max(chunkX,(int)Math.floor(layerX-reach));
            int xmax=Math.min(chunkX+15,(int)Math.ceil(layerX+reach));
            int zmin=Math.max(chunkZ,(int)Math.floor(layerZ-reach));
            int zmax=Math.min(chunkZ+15,(int)Math.ceil(layerZ+reach));
            int y=(int)Math.round(cy+dy);
            if(y<=SkyIslandShape.MIN_Y || y>=level.getMaxBuildHeight()-1)continue;
            for(int x=xmin;x<=xmax;x++)for(int z=zmin;z<=zmax;z++) {
                double dx=x-layerX,dz=z-layerZ;
                double u=dx*cosine+dz*sine;
                double v=-dx*sine+dz*cosine;
                // A slim tip even at the highest and lowest block layers.
                if(Math.max(Math.abs(u),Math.abs(v))>Math.max(.78,width))continue;
                pos.set(x,y,z);
                if(!level.ensureCanWrite(pos)||!level.getBlockState(pos).isAir())continue;
                // Mostly packed ice, with rare blue-ice facets like the old spikes.
                long h=SkyTerrainPatterns.mix(key,x,y,z,0x671b559L);
                BlockState material=(h&31L)==0 ? BLUE:PACKED;
                if(level.setBlock(pos,material,Block.UPDATE_CLIENTS))placed++;
            }
        }
        return placed;
    }

    /**
     * 2-8 crystalline needles in one urchin fan, angled along the actual
     * top/cliff/underside normal. Every arm has a solid cristalizedice heart
     * underneath a one-block-thick translucent *normal ice* shell.
     * Placement is chunk-local and limited to air; no cross-chunk reads.
     */
    private static int urchin(WorldGenLevel level,int chunkX,int chunkZ,
                              BlockPos.MutableBlockPos pos,SkyGlacialCrystalSites.Urchin site) {
        int count=0;
        double nx=site.nx(),ny=site.ny(),nz=site.nz();
        // Build an orthonormal frame in the plane of the native surface.
        double tx,tz,ty=0;
        double h=Math.hypot(nx,nz);
        if(h<.15) {tx=1;tz=0;}
        else {tx=nz/h;tz=-nx/h;}
        double ux=ny*tz, uy=nz*tx-nx*tz, uz=-ny*tx;
        // Core-first on ALL arms, shell-second on ALL arms. Without this
        // ordering, early shells would occupy almost every neighboring voxel
        // before later backbone slices could place their crystalline hearts.
        for(int pass=0;pass<2;pass++)for(int j=0;j<site.arms();j++) {
            long key=SkyTerrainPatterns.mix(site.key(),j,0,0,0x711c39L);
            double phi=2*Math.PI*SkyTerrainPatterns.unit(key);
            double cs=Math.cos(phi),sn=Math.sin(phi);
            double spread=j==0 ? .12 : .25+.60*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(key,1,0,0,0x711c40L));
            double vx=nx+spread*(tx*cs+ux*sn);
            double vy=ny+spread*(ty*cs+uy*sn);
            double vz=nz+spread*(tz*cs+uz*sn);
            double inv=1.0/Math.sqrt(vx*vx+vy*vy+vz*vz);
            vx*=inv;vy*=inv;vz*=inv;
            double lateral=j==0 ? 0 : .5+1.6*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(key,2,0,0,0x711c41L));
            double bx=site.x()-nx*1.7+lateral*(tx*cs+ux*sn);
            double by=site.y()-ny*1.7+lateral*(ty*cs+uy*sn);
            double bz=site.z()-nz*1.7+lateral*(tz*cs+uz*sn);
            int length=j==0 ? 15+(int)(9*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(key,3,0,0,0x711c42L)))
                    : 8+(int)(12*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(key,3,0,0,0x711c42L)));
            double baseRadius=j==0 ? 3.0 : 2.2+.9*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(key,4,0,0,0x711c43L));
            for(int step=0;step<=length;step++) {
                double t=step/(double)length;
                double outer=Math.max(.38,baseRadius*Math.pow(1-t,1.10));
                double inner=Math.max(0,outer-1.05);
                double cx=bx+vx*step, cy=by+vy*step, cz=bz+vz*step;
                int reach=(int)Math.ceil(outer+.6);
                int minX=Math.max(chunkX,(int)Math.floor(cx-reach));
                int maxX=Math.min(chunkX+15,(int)Math.ceil(cx+reach));
                int minZ=Math.max(chunkZ,(int)Math.floor(cz-reach));
                int maxZ=Math.min(chunkZ+15,(int)Math.ceil(cz+reach));
                if(minX>maxX || minZ>maxZ)continue;
                for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                    double dx=x-cx,dz=z-cz;
                    double horizontal=dx*dx+dz*dz;
                    if(horizontal>outer*outer)continue;
                    int y0=Math.max(SkyIslandShape.MIN_Y+1,(int)Math.floor(cy-reach));
                    int y1=Math.min(SkyIslandShape.MAX_Y-1,(int)Math.ceil(cy+reach));
                    for(int y=y0;y<=y1;y++) {
                        double dist=horizontal+(y-cy)*(y-cy);
                        if(dist>outer*outer)continue;
                        boolean isCore=dist<=inner*inner;
                        if((pass==0)!=isCore)continue;
                        // The crystalline core stays behind translucent ice.
                        // Existing terrain, lakes, trees and structures win.
                        BlockState material=pass==0 ? CORE:ICE;
                        pos.set(x,y,z);
                        if(level.ensureCanWrite(pos) && level.getBlockState(pos).isAir()
                                && level.setBlock(pos,material,Block.UPDATE_CLIENTS))count++;
                    }
                }
            }
        }
        return count;
    }

    private static int arch(WorldGenLevel level,int chunkX,int chunkZ,
                            BlockPos.MutableBlockPos pos,SkyGlacialCrystalSites.Crystal c) {
        int count=0;
        double angle=2*Math.PI*SkyTerrainPatterns.unit(c.key());
        double cos=Math.cos(angle),sin=Math.sin(angle);
        double width=7+c.radius()*1.25;
        for(int step=-(int)width;step<=(int)width;step++) {
            double u=step/width;
            double yCenter=c.y()+Math.round(1+c.height()*.70*Math.sqrt(Math.max(0,1-u*u)));
            double cx=c.x()+cos*step, cz=c.z()+sin*step;
            int thick=Math.abs(step)<width*.8 ? 2:3;
            for(int lateral=-thick;lateral<=thick;lateral++)
                for(int dy=-1;dy<=1;dy++) {
                    int x=(int)Math.round(cx-sin*lateral),z=(int)Math.round(cz+cos*lateral);
                    if(x<chunkX||x>=chunkX+16||z<chunkZ||z>=chunkZ+16)continue;
                    if(put(level,pos,x,(int)yCenter+dy,z,c.key()^0x416bcL))count++;
                }
        }
        return count;
    }

    private static boolean put(WorldGenLevel level,BlockPos.MutableBlockPos pos,
                               int x,int y,int z,long salt) {
        if(y<=SkyIslandShape.MIN_Y || y>=level.getMaxBuildHeight()-1)return false;
        pos.set(x,y,z);
        // Ice is placed only into empty air. Existing terrain, snow, trees,
        // structures and water are never replaced by this pass.
        if(!level.ensureCanWrite(pos) || !level.getBlockState(pos).isAir())return false;
        long hash=SkyTerrainPatterns.mix(salt,x,y,z,0x671b558L);
        BlockState ice=(hash&15)==0 ? BLUE : (hash&31)==1 ? ICE : PACKED;
        return level.setBlock(pos,ice,Block.UPDATE_CLIENTS);
    }
}
