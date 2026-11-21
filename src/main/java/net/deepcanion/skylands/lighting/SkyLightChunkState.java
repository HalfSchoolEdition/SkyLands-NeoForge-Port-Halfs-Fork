package net.deepcanion.skylands.lighting;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Per-loaded-chunk virtual-source bookkeeping, independent of block light.
 * The owner keeps this as a value in a WeakHashMap keyed by the LightChunk:
 * neither the map nor this value retains the chunk after it unloads.
 */
public final class SkyLightChunkState {
    public static final int UNTRACKED = Integer.MIN_VALUE;
    private final AtomicIntegerArray columnBottoms = new AtomicIntegerArray(16 * 16);

    public SkyLightChunkState() {
        for (int i = 0; i < 256; i++) columnBottoms.set(i, UNTRACKED);
    }

    private static int index(int x, int z) {
        return ((z & 15) << 4) | (x & 15);
    }

    public int getBottom(int x, int z) {
        return columnBottoms.get(index(x, z));
    }

    public void setBottom(int x, int z, int bottom) {
        columnBottoms.set(index(x, z), bottom);
    }
}
