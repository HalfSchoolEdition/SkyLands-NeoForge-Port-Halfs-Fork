package net.deepcanion.skylands.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Internal operations already used by the vanilla sky-light engine. */
@Mixin(LayerLightSectionStorage.class)
public interface LightStorageAccess {
    @Accessor("chunkSource")
    LightChunkGetter skylands$getChunkSource();

    @Invoker("storingLightForSection")
    boolean skylands$hasLightSection(long packedSection);

    @Invoker("getStoredLevel")
    int skylands$getStoredLevel(long packedBlock);

    @Invoker("setStoredLevel")
    void skylands$setStoredLevel(long packedBlock, int level);
}
