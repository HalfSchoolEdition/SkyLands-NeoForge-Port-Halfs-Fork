package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chunk-local, dense hanging mangrove-leaf tendrils and separately grouped
 * mangrove-root curtains on Swamp islands. Each vertical strand is face-linked
 * as it bends; decoration is never clipped against a neighboring chunk's state.
 */
final class SkyTerrainSwampVines {
    private SkyTerrainSwampVines() { }

    static void decorate(WorldGenLevel level, long seed, int chunkX, int chunkZ) {
        RandomSource random=RandomSource.create(SkyTerrainPatterns.mix(
                seed,chunkX,0,chunkZ,0x1be93af2L));
        // Previously each chunk had only a 2/3 chance of a leaf tendril;
        // 2-3 attempts produce roughly 3-4x as many, depending on underside availability.
        int tendrils=2+random.nextInt(2);
        for(int a=0;a<tendrils;a++) {
            int x=chunkX+5+random.nextInt(6),z=chunkZ+5+random.nextInt(6);
            BlockPos origin=underside(level,x,z);
            if(origin!=null)leavesTendril(level,random,origin,chunkX,chunkZ);
        }
        // A few dense root clusters with different hanging lengths replace
        // sparse isolated, uniformly sized strands.
        int clusters=1+random.nextInt(2);
        for(int a=0;a<clusters;a++) {
            int x=chunkX+5+random.nextInt(6),z=chunkZ+5+random.nextInt(6);
            BlockPos origin=underside(level,x,z);
            if(origin!=null)rootCurtain(level,random,origin,chunkX,chunkZ);
        }
    }

    private static BlockPos underside(WorldGenLevel level,int x,int z) {
        boolean seenSwamp=false;
        int lastSurfaceY=-1;
        for(int y=SkyIslandShape.MAX_Y-1;y>=SkyIslandShape.MIN_Y+6;y--) {
            BlockPos pos=new BlockPos(x,y,z);
            BlockState state=level.getBlockState(pos);
            if(state.is(ModBlocks.SWAMP_GRASS_BLOCK.get())) {
                seenSwamp=true;
                lastSurfaceY=y;
            }
            if(seenSwamp && lastSurfaceY-y<95
                    && (state.is(Blocks.DIORITE)||state.is(ModBlocks.SKYROCK.get())
                    ||state.is(ModBlocks.SWAMP_MOSSY_SKYROCK.get()))
                    && level.getBlockState(pos.below()).isAir())return pos.below();
            if(seenSwamp && lastSurfaceY-y>95)return null;
        }
        return null;
    }

    private static void leavesTendril(WorldGenLevel level,RandomSource rng,
                                      BlockPos anchor,int chunkX,int chunkZ) {
        BlockState leaf=Blocks.MANGROVE_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT,true);
        double phase=rng.nextDouble()*Math.PI*2;
        double direction=rng.nextDouble()*Math.PI*2;
        int length=9+rng.nextInt(15); // gentler than the occasional former 27-block spike
        BlockPos previous=anchor;
        for(int i=0;i<length;i++) {
            double t=i/(double)Math.max(1,length-1);
            // Low frequency undulation; keep the strand within a few blocks
            // of its attachment and avoid winding into thin stretched threads.
            double curve=1.65*Math.sin(t*Math.PI*2+phase)-1.65*Math.sin(phase)
                    +.75*Math.sin(t*Math.PI*3.1);
            int x=anchor.getX()+(int)Math.round(curve*Math.cos(direction));
            int z=anchor.getZ()+(int)Math.round(curve*Math.sin(direction));
            // Keep a locally generated strand inside the safe 16x16 area:
            // no clipped/reappearing sections at neighboring chunk edges.
            x=Math.max(chunkX+2,Math.min(chunkX+13,x));
            z=Math.max(chunkZ+2,Math.min(chunkZ+13,z));
            BlockPos target=new BlockPos(x,anchor.getY()-i,z);
            link(level,previous,target,leaf,chunkX,chunkZ);
            previous=target;
            // Small, sporadic leaf bulges give the vine body without making
            // all of it a solid thick rope.
            if(i>2 && rng.nextInt(4)==0) {
                putAirOnly(level,target.offset(rng.nextInt(3)-1,0,rng.nextInt(3)-1),
                        leaf,chunkX,chunkZ);
            }
        }
    }

    /** Join two adjacent samples through faces instead of diagonal contacts. */
    private static void link(WorldGenLevel level,BlockPos from,BlockPos to,
                             BlockState block,int chunkX,int chunkZ) {
        int x=from.getX(),y=from.getY(),z=from.getZ();
        putAirOnly(level,from,block,chunkX,chunkZ);
        while(x!=to.getX()||y!=to.getY()||z!=to.getZ()) {
            // Go down before moving sideways so the run remains visibly hung.
            if(y!=to.getY())y+=Integer.signum(to.getY()-y);
            else if(x!=to.getX())x+=Integer.signum(to.getX()-x);
            else z+=Integer.signum(to.getZ()-z);
            putAirOnly(level,new BlockPos(x,y,z),block,chunkX,chunkZ);
        }
    }

    private static void rootCurtain(WorldGenLevel level,RandomSource rng,
                                    BlockPos anchor,int chunkX,int chunkZ) {
        BlockState root=Blocks.MANGROVE_ROOTS.defaultBlockState();
        int branches=5+rng.nextInt(5); // clustered bouquets of 5-9 roots
        // Several attachment points under the same island mass rather than
        // identical threads originating from exactly one voxel.
        for(int branch=0;branch<branches;branch++) {
            double angle=(2*Math.PI*branch)/branches+(rng.nextDouble()-.5)*.6;
            int dx=(int)Math.round(Math.cos(angle)*(0.8+rng.nextDouble()*1.9));
            int dz=(int)Math.round(Math.sin(angle)*(0.8+rng.nextDouble()*1.9));
            BlockPos start=new BlockPos(
                    Math.max(chunkX+2,Math.min(chunkX+13,anchor.getX()+dx)),
                    anchor.getY(),
                    Math.max(chunkZ+2,Math.min(chunkZ+13,anchor.getZ()+dz)));
            // Start near the rock face or a previously added root; avoid
            // detached roots on the far edge of an air pocket.
            boolean supported=!level.getBlockState(start.above()).isAir()
                    ||level.getBlockState(start).is(root.getBlock());
            if(!supported)continue;
            int length=5+rng.nextInt(17); // visibly uneven 5-21 block lengths
            double spread=.15+.22*rng.nextDouble();
            BlockPos previous=start;
            for(int i=0;i<length;i++) {
                int sideways=(int)Math.round(Math.min(i,10)*spread);
                int x=Math.max(chunkX+2,Math.min(chunkX+13,
                        start.getX()+(int)Math.round(Math.cos(angle)*sideways)));
                int z=Math.max(chunkZ+2,Math.min(chunkZ+13,
                        start.getZ()+(int)Math.round(Math.sin(angle)*sideways)));
                BlockPos target=new BlockPos(x,start.getY()-i,z);
                // Do not jump across missing blocks when a strand bends.
                link(level,previous,target,root,chunkX,chunkZ);
                previous=target;
            }
        }
    }

    private static boolean putAirOnly(WorldGenLevel level,BlockPos p,BlockState target,
                                      int chunkX,int chunkZ) {
        if(p.getY()<=SkyIslandShape.MIN_Y || p.getY()>=level.getMaxBuildHeight()-1
                ||p.getX()<chunkX+1 || p.getX()>chunkX+14
                ||p.getZ()<chunkZ+1 || p.getZ()>chunkZ+14 || !level.ensureCanWrite(p)
                ||!level.getBlockState(p).isAir())return false;
        return level.setBlock(p,target,Block.UPDATE_CLIENTS);
    }
}
