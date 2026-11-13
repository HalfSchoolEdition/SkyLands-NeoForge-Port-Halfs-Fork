package net.deepcanion.skylands.mixin;

import java.util.LinkedHashMap;
import java.util.Map;
import net.deepcanion.skylands.lighting.SkyLandsSkyLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TEST BUILD: A second *real sky-light* source band directly beneath the sky
 * islands, while ordinary sky lighting remains untouched above that band.
 *
 * Vanilla stores a single direct sky-exposure threshold per column. Changing
 * that threshold would light the cave interiors in the islands as well. This
 * experiment instead inserts additional SKY source entries into the normal
 * SKY engine below Y=240, never into the BLOCK light engine.
 *
 * The source is refreshed following changes in the lower section. This does
 * not yet cover every cross-chunk edge and unloaded-light update case; those
 * are explicit test targets, NOT guaranteed behavior of this build.
 */
@Mixin(SkyLightEngine.class)
public abstract class SplitSkyLightEngineMixin {
    @Unique private static final int SKYLANDS$TOP_Y = 239;
    @Unique private static final int SKYLANDS$MARKER_Y = 270;

    /** Small bounded cache per light engine. No perpetual world-wide chunk map. */
    @Unique private final Map<Long, Boolean> skylands$skyIslandChunks =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > 256;
                }
            };

    @Unique private final Map<Long, Integer> skylands$virtualColumnBottoms =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                    return size() > 8192;
                }
            };

    @Inject(method = "propagateLightSources", at = @At("TAIL"))
    private void skylands$seedBelowIslands(ChunkPos chunkPos, CallbackInfo ci) {
        if (!SkyLandsSkyLight.splitMode()) return;
        LightEngineAccess access = (LightEngineAccess) this;
        LightChunkGetter getter = access.skylands$getChunkSource();
        if (!skylands$overworld(getter)) return;
        LightChunk chunk = getter.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (chunk == null || !skylands$hasSkyIsland(chunk, chunkPos.toLong())) return;

        ChunkSkyLightSources vanillaSources = chunk.getSkyLightSources();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                if (vanillaSources.getLowestSourceY(localX, localZ) <= SKYLANDS$TOP_Y) continue;
                skylands$refreshColumn(access, chunk, chunkPos.getMinBlockX() + localX,
                        chunkPos.getMinBlockZ() + localZ, false);
            }
        }
    }

    @Inject(method = "checkNode", at = @At("TAIL"))
    private void skylands$refreshAfterBlockChange(long position, CallbackInfo ci) {
        if (!SkyLandsSkyLight.splitMode() || BlockPos.getY(position) > SKYLANDS$TOP_Y) return;
        LightEngineAccess access = (LightEngineAccess) this;
        LightChunkGetter getter = access.skylands$getChunkSource();
        if (!skylands$overworld(getter)) return;
        int x = BlockPos.getX(position);
        int z = BlockPos.getZ(position);
        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        LightChunk chunk = getter.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (chunk == null || !skylands$hasSkyIsland(chunk, chunkPos.toLong())) return;
        if (chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15) <= SKYLANDS$TOP_Y) return;
        skylands$refreshColumn(access, chunk, x, z, true);
    }

    @Unique private static boolean skylands$overworld(LightChunkGetter getter) {
        // Do not override lighting in the Nether, End or other dimensions.
        return !(getter.getLevel() instanceof Level level)
                || (level.dimension() == Level.OVERWORLD && level.dimensionType().hasSkyLight());
    }

    @Unique private boolean skylands$hasSkyIsland(LightChunk chunk, long packedChunk) {
        Boolean cached = skylands$skyIslandChunks.get(packedChunk);
        if (cached != null) return cached;
        boolean found = false;
        if (chunk instanceof ChunkAccess access) {
            int startSection = Math.max(0, access.getSectionIndex(SKYLANDS$MARKER_Y));
            for (int section = startSection; section < access.getSectionsCount(); section++) {
                if (access.getSection(section).maybeHas(state -> state.is(SkyLandsSkyLight.HIGH_ISLAND_MARKERS))) {
                    found = true;
                    break;
                }
            }
        }
        skylands$skyIslandChunks.put(packedChunk, found);
        return found;
    }

    @Unique private void skylands$refreshColumn(LightEngineAccess engine,
            LightChunk chunk, int x, int z, boolean allowRemoval) {
        LightStorageAccess storage = (LightStorageAccess) engine.skylands$getStorage();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, SKYLANDS$TOP_Y, z);
        boolean directlyExposed = true;
        boolean newlyLit = false;
        long upperChanged = 0L;
        long lowerChanged = 0L;
        int minY = chunk.getMinBuildHeight();
        long columnKey = ((long) x << 32) ^ (z & 0xffffffffL);
        Integer oldBottom = skylands$virtualColumnBottoms.get(columnKey);
        int directBottom = SKYLANDS$TOP_Y + 1;


        for (int y = SKYLANDS$TOP_Y; y >= minY; y--) {
            pos.setY(y);
            BlockState state = chunk.getBlockState(pos);
            if (state.getLightBlock(chunk, pos) != 0) {
                directlyExposed = false;

            }
            if (!directlyExposed && (oldBottom == null || y < oldBottom)) break;
            long packed = pos.asLong();
            if (!storage.skylands$hasLightSection(SectionPos.blockToSection(packed))) continue;
            int previous = storage.skylands$getStoredLevel(packed);
            if (directlyExposed) {
                directBottom = y;
                if (previous != 15) {
                    storage.skylands$setStoredLevel(packed, 15);
                    newlyLit = true;
                    if (upperChanged == 0L) upperChanged = packed;
                    lowerChanged = packed;
                }
            } else if (allowRemoval && oldBottom != null && y >= oldBottom && previous == 15) {
                // Revoke ONLY the positions our previous virtual column covered.
                // Avoid erasing arbitrary stored light in an ordinary underground cave.
                storage.skylands$setStoredLevel(packed, 0);
                engine.skylands$enqueueDecrease(packed,
                        SkyLightEngineAccess.skylands$getRemoveSkySourceEntry());
            }
        }
        if (directBottom <= SKYLANDS$TOP_Y && (oldBottom != null || newlyLit)) {
            skylands$virtualColumnBottoms.put(columnKey, directBottom);
        }
        if (newlyLit) {
            // Vanilla queue entries spread the new SKY source horizontally;
            // the direct vertical column was already filled at strength 15.
            engine.skylands$enqueueIncrease(upperChanged,
                    SkyLightEngineAccess.skylands$getAddSkySourceEntry());
            if (lowerChanged != upperChanged) {
                engine.skylands$enqueueIncrease(lowerChanged,
                        SkyLightEngineAccess.skylands$getAddSkySourceEntry());
            }
        }
    }
}
