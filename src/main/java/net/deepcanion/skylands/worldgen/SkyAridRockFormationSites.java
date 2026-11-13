package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * World-seeded standalone Arid Highlands geology. No Minecraft block/world
 * access: every chunk reconstructs the same hoodoos, layered buttes and arches
 * from their owning island geometry, including over chunk boundaries.
 */
final class SkyAridRockFormationSites {
    static final int HOODOO = 0, BUTTE = 1, ARCH = 2;

    // Left/right offsets matter only for miniature arches. They represent the
    // actual ground at each pier, not an assumed level surface at the midpoint.
    record Form(int x, int y, int z, int radius, int height, int kind,
                double angle, long seed, int leftFootOffset, int rightFootOffset) {
        Form(int x, int y, int z, int radius, int height, int kind,
             double angle, long seed) {
            this(x,y,z,radius,height,kind,angle,seed,0,0);
        }
        int reach() { return radius + (kind==ARCH ? 6 : 4); }
        boolean intersects(int chunkX, int chunkZ) {
            int r=reach();
            return x+r>=chunkX && x-r<=chunkX+15 && z+r>=chunkZ && z-r<=chunkZ+15;
        }
        int minY() { return kind==ARCH
                ? y + Math.min(0,Math.min(leftFootOffset,rightFootOffset))-3 : y-2; }
        int maxY() { return y + height + Math.max(0,Math.max(leftFootOffset,rightFootOffset))+2; }

        /** Distorted rock profiles with recognizable shelves and voids. */
        boolean solid(int wx, int wy, int wz) {
            double dx=wx-x, dz=wz-z;
            double ripple=.22 * Math.sin(wx*.64+wz*.37+(seed&255L)*.035)
                    + .13 * Math.sin(wx*.25-wz*.41+(seed&511L)*.011);
            if (kind == ARCH) return archSolid(wx,wy,wz,dx,dz,ripple);
            double t=(wy-y)/(double)height;
            if (t < -.10 || t > 1.08) return false;
            if (kind == HOODOO) {
                // Angular, wind-eroded hoodoo, not a symmetric mushroom:
                // slabs shift laterally by several blocks between strata.
                double offsetX=1.7*Math.sin(angle+Math.floor(t*4)*1.25)
                        +1.15*Math.sin(t*6.0+angle);
                double offsetZ=1.7*Math.cos(angle+Math.floor(t*4)*1.05)
                        +.8*Math.cos(t*5.0-angle);
                double rr=radius*(t<.28 ? 1.04 : t<.49 ? .72
                        : t<.69 ? .69 : t<.87 ? 1.16 : 1.22);
                if(t>.95)rr*=Math.max(.55,1-(t-.95)*6.0);
                return Math.hypot(dx-offsetX,dz-offsetZ) <= rr+ripple;
            }
            // Multi-tier cylindrical stack with distinct horizontal ledges,
            // inset necks and a projecting cap. Nearby buttes often pair.
            double rr=t<.30 ? .94 : t<.48 ? .71 : t<.66 ? .88
                    : t<.83 ? .61 : 1.02;
            if (t>.93) rr*=Math.max(.33,1-(t-.93)*7.2);
            int tier=t<.30?0:t<.48?1:t<.66?2:t<.83?3:4;
            double driftX=(tier-2)*.83 + 1.2*Math.sin(angle+tier*2.3);
            double driftZ=(tier-2)*-.51 + 1.1*Math.cos(angle+tier*1.5);
            return Math.hypot(dx-driftX,dz-driftZ)<=radius*rr+ripple;
        }

        /** The ordinary arch is eroded rock, not a pipe abruptly sliced at u=1.07.
         * Each foot is embedded at its own ground elevation, and the outer piers
         * flare into their base instead of terminating in vertical flat walls. */
        private boolean archSolid(int wx,int wy,int wz,double dx,double dz,double ripple) {
            double along=dx*Math.cos(angle)+dz*Math.sin(angle);
            double cross=-dx*Math.sin(angle)+dz*Math.cos(angle);
            double u=along/radius;
            double absU=Math.abs(u);
            // Ease each pier toward its independently sampled local surface.
            // The center span retains its original height regardless of slopes.
            double groundShift=(u<0 ? leftFootOffset : rightFootOffset)
                    * smoothstep(.22,.92,absU);
            double t=(wy-y-groundShift)/height;
            if(t<-.23 || t>1.08)return false;

            // The two sides erode differently. Offset the opening and outer
            // silhouette slightly over height; the noise is world-coordinate
            // based so neighboring chunks reconstruct identical shapes.
            double phase=(seed&1023L)*.0097;
            double bend=.035*Math.sin(t*4.8+phase)
                    +.022*Math.sin(t*8.2+angle*1.9);
            double leftSplay=.14+.045*Math.sin(phase+1.1);
            double rightSplay=.19+.045*Math.cos(phase-1.4);
            double foot=1.0-smoothstep(.04,.64,t);
            double profileNoise=.033*Math.sin(wx*.27+wz*.21+wy*.19+phase)
                    +.022*Math.sin(wy*.39+cross*.46-phase);
            double outer=1.015+(u<0?leftSplay:rightSplay)*foot+profileNoise;
            if(Math.abs(u-bend)>outer)return false;

            double shoulder=smoothstep(.57,.88,t);
            double width=2.65+1.60*foot+.55*shoulder
                    +.43*Math.sin(along*.31+phase)+ripple*.5;
            if(Math.abs(cross + .38*Math.sin(along*.22+phase))>width)return false;

            // A high, slightly lopsided opening. The inner foot flares down to
            // rock level near each anchor, and the OUTER roof falls away toward
            // the tips instead of leaving full-height, abruptly cut end walls.
            double openingU=Math.min(1.0,Math.abs((u-bend*.6)/.96));
            double opening=Math.sqrt(Math.max(0,1-openingU*openingU));
            double underside=-.15+.78*opening
                    -.30*smoothstep(.72,.94,absU)
                    +.018*Math.sin(along*.43+phase);
            double outerSlope=smoothstep(.87,1.26,absU);
            double roof=.90-.47*outerSlope+.023*u
                    +.023*Math.sin(wx*.18+wz*.27+phase)+.012*ripple;
            return t>=underside && t<=roof;
        }
    }

    private static final Map<Long,List<Form>> CACHE=new LinkedHashMap<>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,List<Form>> first) {
            return size()>320;
        }
    };
    private SkyAridRockFormationSites() { }

    static List<Form> forIsland(long worldSeed, SkyIslandShape.Island island) {
        long key=SkyTerrainPatterns.mix(worldSeed,(int)Math.round(island.centerX()),
                (int)Math.round(island.centerY()),(int)Math.round(island.centerZ()),0x41a7f011L);
        synchronized(CACHE) {
            List<Form> cached=CACHE.get(key);
            if(cached!=null)return cached;
        }
        List<Form> forms=build(worldSeed,island,key);
        synchronized(CACHE) {
            List<Form> cached=CACHE.get(key);
            if(cached!=null)return cached;
            CACHE.put(key,forms);
        }
        return forms;
    }

    private static List<Form> build(long worldSeed, SkyIslandShape.Island island, long key) {
        boolean primary=island.primaryRadius()>=32;
        int attempts=primary?23:7;
        ArrayList<Form> list=new ArrayList<>();
        int centerX=(int)Math.round(island.centerX());
        int centerZ=(int)Math.round(island.centerZ());
        for(int i=0;i<attempts;i++) {
            long salt=SkyTerrainPatterns.mix(key,i,0,0,0x23c91L);
            double a=2*Math.PI*SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(salt,1,0,0,1));
            double distance=island.primaryRadius() * (.12+.64*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(salt,2,0,0,1)));
            int x=centerX+(int)Math.round(Math.cos(a)*distance);
            int z=centerZ+(int)Math.round(Math.sin(a)*distance);
            var region=SkyTerrainSubregions.arid(worldSeed,x,z);
            if(region.rockyHighlands()<.62)continue;
            // Upper surface must be part of the actual island, not a floating
            // Y guess that would put rock pillars above a cavern or void.
            var column=island.column(x,z);
            int surface=-1;
            for(int y=island.maxY();y>Math.max(SkyIslandShape.MIN_Y+8,
                    (int)island.centerY()-12);y--) {
                if(column.solid(y)) {surface=y;break;}
            }
            if(surface<0 || surface>SkyIslandShape.MAX_Y-35)continue;
            double roll=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(salt,3,0,0,1));
            int kind=roll<.52?HOODOO:roll<.91?BUTTE:ARCH;
            int radius=(kind==HOODOO?3:kind==BUTTE?5:8)
                    +(int)(SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(salt,4,0,0,1))
                    *(kind==HOODOO?3:kind==BUTTE?4:4));
            int height=(kind==HOODOO?12:kind==BUTTE?14:12)
                    +(int)(SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(salt,5,0,0,1))
                    *(kind==HOODOO?11:kind==BUTTE?14:8));
            double facing=2*Math.PI*SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(salt,6,0,0,1));
            if(surface+height+2>SkyIslandShape.MAX_Y)continue;
            int leftFootOffset=0, rightFootOffset=0;
            if(kind==ARCH) {
                // Measure BOTH actual pier surfaces. A central summit is not
                // necessarily the ground level of an arch near a mesa slope.
                int leftX=x-(int)Math.round(Math.cos(facing)*radius*.90);
                int leftZ=z-(int)Math.round(Math.sin(facing)*radius*.90);
                int rightX=x+(int)Math.round(Math.cos(facing)*radius*.90);
                int rightZ=z+(int)Math.round(Math.sin(facing)*radius*.90);
                int leftY=surfaceNear(island,leftX,leftZ,surface);
                int rightY=surfaceNear(island,rightX,rightZ,surface);
                if(leftY<0 || rightY<0) continue;
                leftFootOffset=leftY-surface;
                rightFootOffset=rightY-surface;
                if(surface+height+Math.max(leftFootOffset,rightFootOffset)+2
                        >SkyIslandShape.MAX_Y)continue;
            }
            // No identical overlapping monuments. Nearby formations can form
            // loose clusters without creating one giant square rock block.
            boolean tooClose=false;
            for(Form old:list) {
                double separation=Math.hypot(x-old.x(),z-old.z());
                if(separation<(radius+old.radius())*.68) {tooClose=true;break;}
            }
            if(tooClose)continue;
            list.add(new Form(x,surface,z,radius,height,kind,facing,salt,
                    leftFootOffset,rightFootOffset));
            // Some layered buttes grow together as neighboring, uneven-height
            // eroded stone cylinders rather than solitary towers.
            if(kind==BUTTE && SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(
                    salt,7,0,0,1))<.36) {
                double partnerAngle=facing+1.1;
                double gap=radius*1.38;
                int px=x+(int)Math.round(Math.cos(partnerAngle)*gap);
                int pz=z+(int)Math.round(Math.sin(partnerAngle)*gap);
                if(SkyTerrainSubregions.arid(worldSeed,px,pz).rockyHighlands()>.64) {
                    var partnerColumn=island.column(px,pz);
                    int py=-1;
                    for(int yy=Math.min(island.maxY(),SkyIslandShape.MAX_Y-30);
                            yy>surface-10;yy--) {
                        if(partnerColumn.solid(yy)){py=yy;break;}
                    }
                    if(py>=surface-9 && py+height<=SkyIslandShape.MAX_Y-2) {
                        int pr=Math.max(3,(int)Math.round(radius*.68));
                        int ph=Math.max(10,(int)Math.round(height*.78));
                        list.add(new Form(px,py,pz,pr,ph,BUTTE,facing+.6,
                                SkyTerrainPatterns.mix(salt,8,0,0,0x4711L)));
                    }
                }
            }
        }
        return List.copyOf(list);
    }

    /** Reject unsupported/clipped arches before entering the chunk placement pass. */
    private static int surfaceNear(SkyIslandShape.Island island,int x,int z,int referenceY) {
        var column=island.column(x,z);
        int top=Math.min(island.maxY(),referenceY+9);
        int bottom=Math.max(SkyIslandShape.MIN_Y,referenceY-9);
        for(int yy=top;yy>=bottom;yy--) {
            if(column.solid(yy))return yy;
        }
        return -1;
    }

    private static double smoothstep(double low,double high,double v) {
        double t=Math.max(0,Math.min(1,(v-low)/(high-low)));
        return t*t*(3-2*t);
    }
}
