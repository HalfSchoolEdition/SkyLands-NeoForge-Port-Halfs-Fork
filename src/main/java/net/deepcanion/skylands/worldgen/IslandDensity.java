package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.Codec;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

/** Stable thinning without consuming the generator's random stream. */
public final class IslandDensity {
    private static volatile int[] activeDensity;
    public static void starting(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        activeDensity = new int[] {atStart(GameplayConfig.SKY_SMALL), atStart(GameplayConfig.SKY_MEDIUM), atStart(GameplayConfig.SKY_LARGE),
                atStart(GameplayConfig.OUTSIDE_SMALL), atStart(GameplayConfig.OUTSIDE_MEDIUM), atStart(GameplayConfig.OUTSIDE_LARGE)};
    }
    private static int atStart(net.neoforged.neoforge.common.ModConfigSpec.IntValue value) {
        value.clearCache();
        return value.get();
    }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { activeDensity = null; }
    public enum Size implements StringRepresentable {
        NONE, SMALL, MEDIUM, LARGE;
        public static final Codec<Size> CODEC = StringRepresentable.fromEnum(Size::values);
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static boolean isSkyBiome(Holder<Biome> biome) {
        return biome.is(SkyBiomes.GOLDEN) || biome.is(SkyBiomes.GLACIATED) || biome.is(SkyBiomes.LAVIC)
                || biome.is(SkyBiomes.ARID) || biome.is(SkyBiomes.CHERRY) || biome.is(SkyBiomes.SWAMP);
    }

    public static int percent(Size size, boolean skyBiome) {
        int[] snapshot = activeDensity;
        if (size != Size.NONE && snapshot != null) return snapshot[(skyBiome ? 0 : 3) + size.ordinal() - 1];
        return switch (size) {
            case NONE -> 100;
            case SMALL -> (skyBiome ? GameplayConfig.SKY_SMALL : GameplayConfig.OUTSIDE_SMALL).get();
            case MEDIUM -> (skyBiome ? GameplayConfig.SKY_MEDIUM : GameplayConfig.OUTSIDE_MEDIUM).get();
            case LARGE -> (skyBiome ? GameplayConfig.SKY_LARGE : GameplayConfig.OUTSIDE_LARGE).get();
        };
    }

    public static boolean keep(Size size, Holder<Biome> biome, long worldSeed, int x, int y, int z, ResourceLocation id) {
        return keepAtFactor(size, biome, worldSeed, x, y, z, id, 1.0);
    }

    public static int maxPercent(Size size) { return Math.max(percent(size, true), percent(size, false)); }
    public static int featureCopies(Size size) { return Math.max(1, (maxPercent(size) + 99) / 100); }

    public static boolean keepAtFactor(Size size, Holder<Biome> biome, long worldSeed, int x, int y, int z, ResourceLocation id, double opportunityFactor) {
        int percent = percent(size, isSkyBiome(biome));
        double acceptance = percent / (100.0 * opportunityFactor);
        if (acceptance >= 1.0) return true;
        if (percent <= 0) return false;
        return sample(worldSeed, x, y, z, id) < acceptance;
    }

    /** Stable acceptance hash, separate from the original placement random stream. */
    static double sample(long seed, int x, int y, int z, ResourceLocation id) {
        long hash = mix(seed ^ id.toString().hashCode());
        hash = mix(hash ^ ((long) x * 0x632BE59BD9B4E019L));
        hash = mix(hash ^ ((long) y * 0x9E3779B97F4A7C15L));
        hash = mix(hash ^ ((long) z * 0xC6BC279692B5CC83L));
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private IslandDensity() { }
}
