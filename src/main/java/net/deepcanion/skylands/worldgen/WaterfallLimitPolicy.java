package net.deepcanion.skylands.worldgen;

/**
 * Deterministic, position-only policy for the optional high-sky waterfall limit.
 * No world/chunk state, block entities, persistent data or background tasks.
 * Each straight vertical column keeps the same limit across ticks and reloads.
 */
public final class WaterfallLimitPolicy {
    private WaterfallLimitPolicy() { }

    /** Inclusive length range; distinct salt for the two liquid families. */
    public static int columnLimit(int x, int z, int minLength, int maxLength, int salt) {
        int lower = Math.min(minLength, maxLength);
        int upper = Math.max(minLength, maxLength);
        int hash = x * 0x9E3779B9 ^ z * 0x7F4A7C15 ^ salt;
        hash ^= hash >>> 16;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        hash *= 0xC2B2AE35;
        hash ^= hash >>> 16;
        return lower + Math.floorMod(hash, upper - lower + 1);
    }

    /**
     * If a column of {@code length} falling blocks ends at {@code bottomY},
     * its top falling block must be at or above the sky-origin threshold.
     * This deliberately does NOT require looking up a source block.
     */
    public static boolean reachesSky(int bottomY, int length, int minOriginY) {
        return (long) bottomY + length - 1 >= minOriginY;
    }
}
