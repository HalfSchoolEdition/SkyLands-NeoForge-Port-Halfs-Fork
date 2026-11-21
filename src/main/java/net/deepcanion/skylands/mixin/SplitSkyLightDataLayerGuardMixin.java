package net.deepcanion.skylands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.deepcanion.skylands.lighting.SkyLandsSkyLight;
import net.deepcanion.skylands.lighting.SplitSkyLightDiagnostics;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Final missing-DataLayer defense for SKY storage. Unlike checking
 * storingLightForSection() before getStoredLevel()/setStoredLevel(), this
 * handles the actual DataLayer receiver at its use site, so a mismatch
 * between section state and allocated data cannot dereference null.
 * BLOCK light and non-Overworld lighting retain vanilla behavior.
 */
@Mixin(LayerLightSectionStorage.class)
public abstract class SplitSkyLightDataLayerGuardMixin {
    @WrapOperation(method = "getStoredLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/DataLayer;get(III)I"), require = 1)
    private int skylands$readActualLayer(DataLayer layer, int x, int y, int z,
            Operation<Integer> original, long packedPos) {
        if (layer == null && skylands$enabledInThisStorage()) {
            skylands$diagnoseMissingLayer("getStoredLevel", packedPos);
            return 0;
        }
        return original.call(layer, x, y, z);
    }

    @WrapOperation(method = "setStoredLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/DataLayer;set(IIII)V"), require = 1)
    private void skylands$writeActualLayer(DataLayer layer, int x, int y, int z, int value,
            Operation<Void> original, long packedPos, int storedLevel) {
        if (layer == null && skylands$enabledInThisStorage()) {
            skylands$diagnoseMissingLayer("setStoredLevel", packedPos);
            return;
        }
        original.call(layer, x, y, z, value);
    }

    @Unique
    private boolean skylands$enabledInThisStorage() {
        if (!((Object) this instanceof SkyLightSectionStorage) || !SkyLandsSkyLight.splitMode()) {
            return false;
        }
        LightChunkGetter getter = ((LightStorageAccess) this).skylands$getChunkSource();
        return getter != null && getter.getLevel() instanceof Level level
                && level.dimension() == Level.OVERWORLD && level.dimensionType().hasSkyLight();
    }

    @Unique
    private void skylands$diagnoseMissingLayer(String operation, long packedPos) {
        LayerLightSectionStorage<?> storage = (LayerLightSectionStorage<?>) (Object) this;
        long section = SectionPos.blockToSection(packedPos);
        boolean reportedPresent = ((LightStorageAccess) this).skylands$hasLightSection(section);
        boolean snapshotPresent = storage.getDataLayerData(section) != null;
        SplitSkyLightDiagnostics.nullDataLayer(operation, packedPos,
                reportedPresent, snapshotPresent);
    }
}
