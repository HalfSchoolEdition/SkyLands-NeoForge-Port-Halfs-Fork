package net.deepcanion.skylands.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access only the vanilla sky engine's lighting storage/queues, not block light. */
@Mixin(LightEngine.class)
public interface LightEngineAccess {
    @Accessor("storage")
    LayerLightSectionStorage<?> skylands$getStorage();

    @Accessor("chunkSource")
    LightChunkGetter skylands$getChunkSource();

    @Invoker("enqueueIncrease")
    void skylands$enqueueIncrease(long position, long queueEntry);

    @Invoker("enqueueDecrease")
    void skylands$enqueueDecrease(long position, long queueEntry);
}
