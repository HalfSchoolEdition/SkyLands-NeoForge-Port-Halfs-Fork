package net.deepcanion.skylands.mixin;

import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reuse the same sky-source queue entries as vanilla rather than forging bits. */
@Mixin(SkyLightEngine.class)
public interface SkyLightEngineAccess {
    @Accessor("ADD_SKY_SOURCE_ENTRY")
    static long skylands$getAddSkySourceEntry() {
        throw new AssertionError("Accessor not transformed");
    }

    @Accessor("REMOVE_SKY_SOURCE_ENTRY")
    static long skylands$getRemoveSkySourceEntry() {
        throw new AssertionError("Accessor not transformed");
    }
}
