package net.deepcanion.skylands.mixin;

import java.util.Collections;
import java.util.WeakHashMap;
import java.util.Map;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.deepcanion.skylands.lighting.SkyLandsSkyLight;
import net.deepcanion.skylands.lighting.SkyLightChunkState;
import net.deepcanion.skylands.lighting.SplitSkyLightDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
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

    /**
     * A per-physical-chunk state replaces the previous 8,192-column LRU.
     * That LRU could evict source-removal boundaries while the corresponding
     * chunks were still actively lit. Weak keys retain the state as long as
     * Minecraft retains each chunk, without retaining unloaded chunks here.
     *
     * A Mixin's field initializer is NOT reliably copied to its target
     * constructor: initialize lazily, as the previous 0%-load fix required.
     */
    @Unique private volatile Map<LightChunk, SkyLightChunkState> skylands$trackedChunks;

    @Unique
    private Map<LightChunk, SkyLightChunkState> skylands$chunkStates() {
        Map<LightChunk, SkyLightChunkState> states = skylands$trackedChunks;
        if (states == null) {
            synchronized (this) {
                states = skylands$trackedChunks;
                if (states == null) {
                    states = Collections.synchronizedMap(new WeakHashMap<>());
                    skylands$trackedChunks = states;
                }
            }
        }
        return states;
    }

    @Unique
    private SkyLightChunkState skylands$stateFor(LightChunk chunk) {
        Map<LightChunk, SkyLightChunkState> states = skylands$chunkStates();
        synchronized (states) {
            return states.computeIfAbsent(chunk, ignored -> new SkyLightChunkState());
        }
    }

    /**
     * Vanilla's sky-source reconciliation assumes every visited section has a
     * DataLayer. Our experimental lower sky band is deliberately sparse: air
     * sections can have no light storage. When a column is updated, vanilla's
     * removeSourcesBelow() may read one of those absent layers and throw an
     * NPE in LayerLightSectionStorage.getStoredLevel().
     *
     * Return dark (zero) for absent layers, which is the natural stopping
     * value when removing a run of direct sky sources. Do not allocate storage,
     * and keep the unmodified vanilla behavior when split lighting is off.
     */
    // The concrete bytecode dispatches through SkyLightSectionStorage, not
    // LayerLightSectionStorage (its superclass). A superclass owner in the
    // INVOKE target causes a critical 0/1 injection failure on world load.
    @WrapOperation(
            method = "removeSourcesBelow",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;getStoredLevel(J)I"),
            require = 1)
    private int skylands$readSkySourceSafely(SkyLightSectionStorage storage,
            long packedPos, Operation<Integer> original) {
        if (skylands$guardMissingSkySection(storage, packedPos, "source-read")) return 0;
        return original.call(storage, packedPos);
    }

    /**
     * The corresponding vanilla source-removal and source-addition code must
     * not write into a section whose DataLayer has been unloaded in the
     * meantime. This is deliberately scoped to the two sky-source methods,
     * not the whole lighting engine or other mods' storage.
     */
    @WrapOperation(
            method = {"removeSourcesBelow", "addSourcesAbove"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;setStoredLevel(JI)V"),
            require = 1)
    private void skylands$writeSkySourceSafely(SkyLightSectionStorage storage,
            long packedPos, int value, Operation<Void> original) {
        if (skylands$guardMissingSkySection(storage, packedPos, "source-write")) return;
        original.call(storage, packedPos, value);
    }

    /**
     * When a virtual sky source decreases, vanilla can traverse neighbouring
     * sections whose light DataLayer has not been allocated (or has already
     * been unloaded). The older guard above only covered source reconciliation
     * in removeSourcesBelow(), not queued propagation, which is why a client
     * could still crash in SkyLightEngine.propagateDecrease().
     *
     * For split lighting in the Overworld, treat those absent light sections
     * as dark rather than dereferencing a null DataLayer. All real/stored
     * sections still use vanilla lighting, and the guard does nothing if the
     * experiment is disabled.
     */
    @WrapOperation(
            method = "propagateDecrease",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;getStoredLevel(J)I"),
            require = 1)
    private int skylands$readDecreaseSafely(SkyLightSectionStorage storage,
            long packedPos, Operation<Integer> original) {
        if (skylands$guardMissingSkySection(storage, packedPos, "decrease-read")) return 0;
        return original.call(storage, packedPos);
    }

    // Some propagation paths also write/read neighbours during an increase.
    // These are optional injection sites: the exact count depends on the
    // Minecraft 1.21.1 method's compiled call sites.
    @WrapOperation(
            method = "propagateIncrease",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;getStoredLevel(J)I"),
            require = 0)
    private int skylands$readIncreaseSafely(SkyLightSectionStorage storage,
            long packedPos, Operation<Integer> original) {
        if (skylands$guardMissingSkySection(storage, packedPos, "increase-read")) return 0;
        return original.call(storage, packedPos);
    }

    @WrapOperation(
            method = {"propagateDecrease", "propagateIncrease"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;setStoredLevel(JI)V"),
            require = 0)
    private void skylands$writePropagationSafely(SkyLightSectionStorage storage,
            long packedPos, int value, Operation<Void> original) {
        if (skylands$guardMissingSkySection(storage, packedPos, "propagation-write")) return;
        original.call(storage, packedPos, value);
    }

    @Unique
    private boolean skylands$guardMissingSkySection(LayerLightSectionStorage<?> storage,
            long packedPos, String operation) {
        if (!SkyLandsSkyLight.splitMode()) return false;
        // For the common case, do not examine the world or log anything.
        LightStorageAccess access = (LightStorageAccess) storage;
        if (access.skylands$hasLightSection(SectionPos.blockToSection(packedPos))) return false;
        if (!skylands$overworld(access.skylands$getChunkSource())) return false;
        SplitSkyLightDiagnostics.skippedAbsentSection(operation, packedPos);
        return true;
    }

    /** Drop chunk state when vanilla disables lighting for a still-live chunk. */
    @Inject(method = "setLightEnabled", at = @At("TAIL"))
    private void skylands$forgetDisabledChunk(ChunkPos chunkPos, boolean enabled, CallbackInfo ci) {
        if (enabled || !SkyLandsSkyLight.splitMode()) return;
        LightChunkGetter getter = ((LightEngineAccess) this).skylands$getChunkSource();
        LightChunk chunk = getter.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (chunk != null) skylands$chunkStates().remove(chunk);
        // If the chunk is no longer accessible, the weak-key entry will be
        // reclaimed once Minecraft releases its last reference to that chunk.
    }

    @Inject(method = "propagateLightSources", at = @At("TAIL"))
    private void skylands$seedBelowIslands(ChunkPos chunkPos, CallbackInfo ci) {
        if (!SkyLandsSkyLight.splitMode()) return;
        LightEngineAccess access = (LightEngineAccess) this;
        LightChunkGetter getter = access.skylands$getChunkSource();
        if (!skylands$overworld(getter)) return;
        LightChunk chunk = getter.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (chunk == null || !skylands$hasSkyIsland(chunk)) return;

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
        if (chunk == null || !skylands$hasSkyIsland(chunk)) return;
        if (chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15) <= SKYLANDS$TOP_Y) return;
        skylands$refreshColumn(access, chunk, x, z, true);
    }

    @Unique private static boolean skylands$overworld(LightChunkGetter getter) {
        // Do not override lighting in the Nether, End or other dimensions.
        return !(getter.getLevel() instanceof Level level)
                || (level.dimension() == Level.OVERWORLD && level.dimensionType().hasSkyLight());
    }

    @Unique private boolean skylands$hasSkyIsland(LightChunk chunk) {
        // Only positive results are cached. A negative during proto-chunk
        // initialization must never prevent detection after generation.
        if (skylands$chunkStates().containsKey(chunk)) return true;
        if (!(chunk instanceof ChunkAccess access)) return false;
        int startSection = Math.max(0, access.getSectionIndex(SKYLANDS$MARKER_Y));
        for (int section = startSection; section < access.getSectionsCount(); section++) {
            if (access.getSection(section).maybeHas(state -> state.is(SkyLandsSkyLight.HIGH_ISLAND_MARKERS))) {
                skylands$stateFor(chunk);
                return true;
            }
        }
        return false;
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
        SkyLightChunkState chunkState = skylands$stateFor(chunk);
        int lastBottom = chunkState.getBottom(x, z);
        Integer oldBottom = lastBottom == SkyLightChunkState.UNTRACKED ? null : lastBottom;
        int directBottom = SKYLANDS$TOP_Y + 1;


        for (int y = SKYLANDS$TOP_Y; y >= minY; y--) {
            pos.setY(y);
            BlockState state = chunk.getBlockState(pos);
            if (state.getLightBlock(chunk, pos) != 0) {
                directlyExposed = false;

            }
            if (!directlyExposed && (oldBottom == null || y < oldBottom)) break;
            long packed = pos.asLong();
            // Missing air sections must not receive a virtual light write or
            // queued source. They have no DataLayer and are lit lazily by
            // SplitSkyLightSectionStorageMixin on the read side.
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
        // Retain only a known virtual source boundary: do not claim ownership
        // of naturally bright vanilla columns which we never modified.
        // No LRU eviction can lose this boundary while the chunk is loaded.
        if (directBottom <= SKYLANDS$TOP_Y && (oldBottom != null || newlyLit)) {
            chunkState.setBottom(x, z, directBottom);
        } else if (allowRemoval && oldBottom != null) {
            chunkState.setBottom(x, z, SkyLightChunkState.UNTRACKED);
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
