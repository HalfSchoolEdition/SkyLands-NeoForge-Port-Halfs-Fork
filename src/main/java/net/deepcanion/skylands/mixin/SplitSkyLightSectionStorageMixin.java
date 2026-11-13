package net.deepcanion.skylands.mixin;

import java.util.concurrent.ConcurrentHashMap;
import net.deepcanion.skylands.lighting.SkyLandsSkyLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EXPERIMENT: Read-side light for *missing* all-air SKY light sections below
 * the split boundary. The physical SKY light engine, its queues and its saved
 * light data remain untouched. This is intentionally not a second light map.
 *
 * Vanilla has no nibble data in most completely empty sections between the
 * floating islands and the ground. The virtual source in
 * SplitSkyLightEngineMixin already illuminates stored ground sections but
 * cannot write brightness into sections that have no storage. This mixin
 * supplies the sky-light value lazily when such air is queried (F3, entity
 * and hand lighting), avoiding section allocations and light propagation.
 */
@Mixin(SkyLightSectionStorage.class)
public abstract class SplitSkyLightSectionStorageMixin {
    @Unique private static final int SKYLANDS$SPLIT_Y = 240;
    @Unique private static final int SKYLANDS$FADE_BLOCKS = 16;
    @Unique private static final int SKYLANDS$CACHE_LIMIT = 512;

    // Positive hits only. Never cache misses: an ungenerated chunk may later
    // acquire SkyLands terrain. Bounded to avoid growth while exploring.
    @Unique private final ConcurrentHashMap<Long, Boolean> skylands$hostChunks = new ConcurrentHashMap<>();

    @Inject(method = "getLightValue(J)I", at = @At("RETURN"), cancellable = true)
    private void skylands$fillMissingSkyAir(long packedPos, CallbackInfoReturnable<Integer> cir) {
        if (!SkyLandsSkyLight.splitMode()) return;
        int y = BlockPos.getY(packedPos);
        if (y >= SKYLANDS$SPLIT_Y) return;

        // No change to the light stored in ordinary occupied sections. This
        // also prevents unexpectedly illuminating enclosed island caves.
        LightStorageAccess storage = (LightStorageAccess) this;
        if (storage.skylands$hasLightSection(SectionPos.blockToSection(packedPos))) return;

        // One light-level step per block at the upper edge, instead of a
        // sudden 0 -> 15 entity/hand lighting change at an arbitrary height.
        // At Y<=224 the virtual air returns full skylight; above 239 none.
        int virtualLight = Math.min(15, 15 * (SKYLANDS$SPLIT_Y - y - 1)
                / (SKYLANDS$FADE_BLOCKS - 1));
        if (virtualLight <= cir.getReturnValue()) return;

        LightChunkGetter getter = storage.skylands$getChunkSource();
        if (!(getter.getLevel() instanceof Level level)
                || level.dimension() != Level.OVERWORLD
                || !level.dimensionType().hasSkyLight()) return;

        int x = BlockPos.getX(packedPos);
        int z = BlockPos.getZ(packedPos);
        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z));
        LightChunk chunk = getter.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (!(chunk instanceof ChunkAccess access)) return;
        // A live air check prevents a false positive while sections are being
        // initialized or modified; no blocks are made transparent here.
        if (!chunk.getBlockState(BlockPos.of(packedPos)).isAir()) return;
        if (chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15) <= SKYLANDS$SPLIT_Y - 1) return;

        long key = chunkPos.toLong();
        if (!skylands$hostChunks.containsKey(key)) {
            if (!skylands$containsIsland(access)) return;
            if (skylands$hostChunks.size() >= SKYLANDS$CACHE_LIMIT) {
                skylands$hostChunks.clear();
            }
            skylands$hostChunks.put(key, Boolean.TRUE);
        }
        cir.setReturnValue(virtualLight);
    }

    @Unique private static boolean skylands$containsIsland(ChunkAccess chunk) {
        int start = Math.max(0, chunk.getSectionIndex(270));
        for (int section = start; section < chunk.getSectionsCount(); section++) {
            if (chunk.getSection(section).maybeHas(s -> s.is(SkyLandsSkyLight.HIGH_ISLAND_MARKERS))) {
                return true;
            }
        }
        return false;
    }
}
