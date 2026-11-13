package net.deepcanion.skylands.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Broad, vertically banded mesa rock shared by Arid cliffs, towers and arches.
 * Horizontal bands persist through chunk borders; low-frequency warping keeps
 * the layers from looking like mechanically perfect horizontal stripes. */
final class SkyAridTerracottaPalette {
    private static final BlockState[] BANDS = {
        Blocks.TERRACOTTA.defaultBlockState(),
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
        Blocks.BROWN_TERRACOTTA.defaultBlockState(),
        Blocks.RED_TERRACOTTA.defaultBlockState(),
        Blocks.RED_TERRACOTTA.defaultBlockState(),
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
        Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState(),
        Blocks.WHITE_TERRACOTTA.defaultBlockState(),
        Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState(),
        Blocks.BROWN_TERRACOTTA.defaultBlockState(),
        Blocks.RED_TERRACOTTA.defaultBlockState()
    };
    private SkyAridTerracottaPalette() { }

    static double warp(long seed,int x,int z) {
        return 4.0*(SkyTerrainPatterns.noise2(seed,x,z,48,0x7281bdL)-.5)
                + 2.0*(SkyTerrainPatterns.noise2(seed,x,z,19,0x7281beL)-.5);
    }
    static BlockState stone(int y,double warp) {
        // Bands are 2-4 blocks thick and share a common vertical datum.
        int band=Math.floorMod((int)Math.floor((y + warp - 286) / 3.0),BANDS.length);
        return BANDS[band];
    }
    /** Quieter broad terracotta patches on freestanding arches. The highlands
     * keep their normal colorful strata, but their bridges aren't rainbow
     * pipes with a new bright color every three blocks. */
    static BlockState archStone(long seed,int x,int y,int z) {
        return archStone(y,SkyTerrainPatterns.noise2(seed,x,z,21,0x4a719fL),
                warp(seed,x,z));
    }
    static BlockState archStone(int y,double broad,double warp) {
        double vertical=(y+warp)/9.0;
        int stripe=Math.floorMod((int)Math.floor(vertical),5);
        if(broad>.72 && stripe==0)return Blocks.RED_TERRACOTTA.defaultBlockState();
        if(broad<.24 && stripe>=3)return Blocks.BROWN_TERRACOTTA.defaultBlockState();
        return stripe==1 || stripe==4
                ? Blocks.ORANGE_TERRACOTTA.defaultBlockState()
                : Blocks.TERRACOTTA.defaultBlockState();
    }
    static boolean isTerracotta(BlockState s) {
        return s.is(Blocks.TERRACOTTA) || s.is(Blocks.ORANGE_TERRACOTTA)
                || s.is(Blocks.RED_TERRACOTTA) || s.is(Blocks.BROWN_TERRACOTTA)
                || s.is(Blocks.YELLOW_TERRACOTTA) || s.is(Blocks.WHITE_TERRACOTTA)
                || s.is(Blocks.LIGHT_GRAY_TERRACOTTA);
    }
}
