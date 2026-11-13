package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Rare bounded decorations for genuine Arid Dunes: small cactus groves,
 * low Skyrock outcrops and very occasional supported, sealed oases.
 * No additional feature pass, world/chunk lookup or neighbor-chunk writes. */
final class SkyTerrainAridDunes {
    private SkyTerrainAridDunes() { }

    static void decorate(WorldGenLevel level,long seed,int chunkX,int chunkZ) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,chunkX,0,chunkZ,0x33dd771aL));
        // Always reject non-dune chunks before any vertical surface scans.
        int cx=chunkX+8,cz=chunkZ+8;
        if(SkyTerrainSubregions.arid(seed,cx,cz).dunes()<.68)return;
        if(rng.nextInt(18)==0) oasis(level,seed,chunkX,chunkZ,rng);
        if(rng.nextInt(10)==0) cactusGroup(level,seed,chunkX,chunkZ,rng);
        if(rng.nextInt(8)==0) rockPatch(level,seed,chunkX,chunkZ,rng);
        if(rng.nextInt(11)==0) deadBushGroup(level,seed,chunkX,chunkZ,rng);
    }

    private static boolean dune(long seed,int x,int z) {
        return SkyTerrainSubregions.arid(seed,x,z).dunes()>.68;
    }
    private static boolean sand(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }
    /** Returns the natural dune sand top, not a cave shelf or a foreign beach. */
    private static int sandTop(WorldGenLevel level,long seed,int x,int z) {
        if(!dune(seed,x,z))return -1;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int y=SkyIslandShape.MAX_Y-2;y>=SkyIslandShape.MIN_Y+8;y--) {
            p.set(x,y,z);
            BlockState top=level.getBlockState(p);
            if(!sand(top) || !level.getBlockState(p.above()).isAir())continue;
            // Dunes intentionally have several sand layers over Sky Sandstone.
            // Confirm the underlying native island rock, rather than requiring
            // sandstone immediately under the top sand layer.
            for(int depth=1;depth<=6;depth++) {
                BlockState under=level.getBlockState(p.below(depth));
                if(under.is(ModBlocks.SKY_SANDSTONE.get())
                        || under.is(ModBlocks.MOSSY_SKY_SANDSTONE.get()))return y;
                if(!sand(under))break;
            }
        }
        return -1;
    }

    /** Very occasional, small and irregular patches on dune sand, never grass/rock. */
    private static void deadBushGroup(WorldGenLevel level,long seed,int chunkX,int chunkZ,RandomSource rng) {
        int centerX=chunkX+4+rng.nextInt(8), centerZ=chunkZ+4+rng.nextInt(8);
        int wanted=2+rng.nextInt(6), placed=0;
        BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
        for(int attempt=0;attempt<wanted*7 && placed<wanted;attempt++) {
            int x=centerX+rng.nextInt(9)-4,z=centerZ+rng.nextInt(9)-4;
            if(x<chunkX||x>=chunkX+16||z<chunkZ||z>=chunkZ+16)continue;
            int ground=sandTop(level,seed,x,z);
            if(ground<0)continue;
            at.set(x,ground+1,z);
            if(!level.ensureCanWrite(at)||!level.getBlockState(at).isAir())continue;
            BlockState bush=Blocks.DEAD_BUSH.defaultBlockState();
            if(bush.canSurvive(level,at) && level.setBlock(at,bush,Block.UPDATE_CLIENTS))placed++;
        }
    }

    /** Local height probe for oasis neighbors: the center already establishes
     * the dune height, so avoid scanning the entire floating-island column. */
    private static int sandTopNear(WorldGenLevel level,long seed,int x,int z,int aroundY) {
        if(!dune(seed,x,z))return -1;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int y=aroundY+2;y>=aroundY;y--) {
            p.set(x,y,z);
            if(!sand(level.getBlockState(p)) || !level.getBlockState(p.above()).isAir())continue;
            for(int depth=1;depth<=6;depth++) {
                BlockState under=level.getBlockState(p.below(depth));
                if(under.is(ModBlocks.SKY_SANDSTONE.get())
                        ||under.is(ModBlocks.MOSSY_SKY_SANDSTONE.get()))return y;
                if(!sand(under))break;
            }
        }
        return -1;
    }

    private static void cactusGroup(WorldGenLevel level,long seed,int cx,int cz,RandomSource rng) {
        int x=cx+4+rng.nextInt(8),z=cz+4+rng.nextInt(8);
        int count=1+rng.nextInt(3);
        for(int i=0;i<count;i++) {
            int px=x+(i==0?0:rng.nextInt(7)-3);
            int pz=z+(i==0?0:rng.nextInt(7)-3);
            if(px<cx+2||px>cx+13||pz<cz+2||pz>cz+13)continue;
            int y=sandTop(level,seed,px,pz);
            if(y>=0)SkyTerrainCacti.place(level,rng,new BlockPos(px,y+1,pz));
        }
    }

    private static void rockPatch(WorldGenLevel level,long seed,int cx,int cz,RandomSource rng) {
        int ox=cx+4+rng.nextInt(8),oz=cz+4+rng.nextInt(8);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        int count=3+rng.nextInt(5);
        for(int i=0;i<count;i++) {
            int x=ox+rng.nextInt(5)-2,z=oz+rng.nextInt(5)-2;
            if(x<cx+1||x>cx+14||z<cz+1||z>cz+14)continue;
            int y=sandTop(level,seed,x,z);
            if(y<0)continue;
            p.set(x,y,z);
            if(!level.ensureCanWrite(p))continue;
            level.setBlock(p,ModBlocks.SKYROCK.get().defaultBlockState(),Block.UPDATE_CLIENTS);
            // A few flat stones stand one block above the sand, never towers.
            if(rng.nextInt(5)==0 && level.getBlockState(p.above()).isAir()) {
                p.move(0,1,0);
                level.setBlock(p,ModBlocks.SKYROCK.get().defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Irregular 5-7 block wide pond, with a lush 9-11 block wide grassy margin.
     * Fully preflights the entire footprint so no rejected oasis leaves holes. */
    private static boolean oasis(WorldGenLevel level,long seed,int chunkX,int chunkZ,RandomSource rng) {
        // Keep the full footprint within the owning chunk (no cross-chunk writes).
        for (int attempt=0;attempt<3;attempt++) {
            int x=chunkX+7+rng.nextInt(2), z=chunkZ+7+rng.nextInt(2);
            if (oasisAt(level,seed,x,z,rng)) return true;
        }
        return false;
    }

    private static boolean oasisAt(WorldGenLevel level,long seed,int x,int z,RandomSource rng) {
        int y=sandTop(level,seed,x,z);
        if(y<SkyIslandShape.MIN_Y+12 || y>SkyIslandShape.MAX_Y-10)return false;
        // An asymmetric pool, about 15-28 blocks of water, surrounded by
        // a broader arid-grass oasis. Sample once per X/Z column, not voxel.
        boolean[][] water=new boolean[11][11];
        int[][] top=new int[11][11];
        int waterCount=0;
        long salt=SkyTerrainPatterns.mix(seed,x,y,z,0x33dd781aL);
        for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++) {
            double angle=Math.atan2(dz,dx);
            double broad=2.55 +.44*Math.sin(angle*3.0+(salt&255)*.024)
                    +.30*Math.cos(angle*5.0+((salt>>>8)&255)*.018);
            double radius2=dx*dx+dz*dz;
            boolean pool=radius2<=broad*broad;
            water[dx+5][dz+5]=pool;
            if(pool)waterCount++;
        }
        if(waterCount<13 || waterCount>35)return false;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++) {
            double r2=dx*dx+dz*dz;
            if(r2>27)continue;
            int sx=x+dx,sz=z+dz,sy=sandTopNear(level,seed,sx,sz,y);
            if(sy<0 || sy<y || sy>y+2)return false;
            if(water[dx+5][dz+5] && sy>y+1)return false;
            top[dx+5][dz+5]=sy;
            p.set(sx,sy,sz);
            if(!level.ensureCanWrite(p) || !sand(level.getBlockState(p)))return false;
            // Keep several supporting layers under every water source.
            if(water[dx+5][dz+5]) {
                for(int depth=1;depth<=3;depth++) {
                    p.set(sx,y-depth,sz);
                    if(!solidBed(level.getBlockState(p)))return false;
                }
            }
        }
        BlockState grass=ModBlocks.ARID_GRASS_BLOCK.get().defaultBlockState();
        for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++) {
            if(dx*dx+dz*dz>27)continue;
            int sx=x+dx,sz=z+dz,sy=top[dx+5][dz+5];
            p.set(sx,sy,sz);
            if(water[dx+5][dz+5]) {
                // Carve a one-block terrace down to the shared water level.
                if(sy>y)level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
                p.set(sx,y,sz);
                level.setBlock(p,Blocks.WATER.defaultBlockState(),Block.UPDATE_CLIENTS);
                level.scheduleTick(p,level.getFluidState(p).getType(),1);
            } else {
                level.setBlock(p,grass,Block.UPDATE_CLIENTS);
            }
        }
        // Richer vegetation, clustered around the shore rather than random
        // solitary dead bushes. Avoid placing plants into the water.
        for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++) {
            if(dx*dx+dz*dz>27 || water[dx+5][dz+5])continue;
            boolean shore=false;
            for(int nx=-1;nx<=1&&!shore;nx++)for(int nz=-1;nz<=1;nz++) {
                int ix=dx+nx+5,iz=dz+nz+5;
                if(ix>=0&&ix<11&&iz>=0&&iz<11&&water[ix][iz]){shore=true;break;}
            }
            if(rng.nextInt(shore?5:4)==0)continue;
            p.set(x+dx,top[dx+5][dz+5]+1,z+dz);
            if(!level.ensureCanWrite(p)||!level.getBlockState(p).isAir())continue;
            BlockState plant=rng.nextInt(13)==0?Blocks.DEAD_BUSH.defaultBlockState()
                    :SkyTerrainVegetation.select(2,rng);
            if(plant.canSurvive(level,p))level.setBlock(p,plant,Block.UPDATE_CLIENTS);
        }
        if(rng.nextInt(3)==0) {
            // Azalea can only grow on the grass edge; this is optional.
            int[][] spots={{4,1},{-4,1},{1,4},{-1,-4},{3,3},{-3,-3}};
            for(int start=rng.nextInt(spots.length),i=0;i<spots.length;i++) {
                int[] spot=spots[(start+i)%spots.length];
                int dx=spot[0],dz=spot[1];
                if(dx*dx+dz*dz>27 || water[dx+5][dz+5])continue;
                azalea(level,x+dx,z+dz,top[dx+5][dz+5],rng);
                break;
            }
        }
        return true;
    }

    private static boolean solidBed(BlockState state) {
        return sand(state) || state.is(ModBlocks.SKY_SANDSTONE.get())
                || state.is(ModBlocks.MOSSY_SKY_SANDSTONE.get());
    }

    private static void azalea(WorldGenLevel level,int x,int z,int groundY,RandomSource rng) {
        int trunk=3+rng.nextInt(2);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        p.set(x,groundY,z);
        if(!level.getBlockState(p).is(ModBlocks.ARID_GRASS_BLOCK.get()))return;
        // Preflight every canopy voxel to avoid clipping existing decorations.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=trunk-2;dy<=trunk+1;dy++) {
            if(dx*dx+dz*dz>5 || dy==trunk+1 && dx*dx+dz*dz>1)continue;
            p.set(x+dx,groundY+1+dy,z+dz);
            if(!level.ensureCanWrite(p)||!level.getBlockState(p).isAir())return;
        }
        for(int dy=1;dy<=trunk;dy++) {
            p.set(x,groundY+dy,z);
            level.setBlock(p,Blocks.OAK_LOG.defaultBlockState(),Block.UPDATE_CLIENTS);
        }
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=trunk-2;dy<=trunk+1;dy++) {
            int r2=dx*dx+dz*dz;
            if(r2>5 || dy==trunk+1 && r2>1 || dx==0 && dz==0 && dy<=trunk-1)continue;
            p.set(x+dx,groundY+1+dy,z+dz);
            if(!level.getBlockState(p).isAir())continue;
            BlockState leaves=(rng.nextInt(4)==0?Blocks.FLOWERING_AZALEA_LEAVES
                    :Blocks.AZALEA_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
            level.setBlock(p,leaves,Block.UPDATE_CLIENTS);
        }
    }
}
