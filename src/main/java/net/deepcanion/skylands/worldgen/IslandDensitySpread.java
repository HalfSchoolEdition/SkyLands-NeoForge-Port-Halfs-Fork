package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.deepcanion.skylands.registry.ModWorldgen;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** A random-spread placement, compatible with /locate, with more candidates above 100%. */
public final class IslandDensitySpread extends RandomSpreadStructurePlacement {
    /** Compose the three flat spread fields as one product; DFU products stop at eight arguments. */
    private record SpreadSettings(int spacing, int separation, RandomSpreadType spreadType) {
        private static final MapCodec<SpreadSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, 4096).fieldOf("spacing").forGetter(SpreadSettings::spacing),
                Codec.intRange(0, 4095).fieldOf("separation").forGetter(SpreadSettings::separation),
                RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(SpreadSettings::spreadType)
        ).apply(instance, SpreadSettings::new));
    }

    public static final MapCodec<IslandDensitySpread> CODEC = RecordCodecBuilder.<IslandDensitySpread>mapCodec(instance ->
            placementCodec(instance).and(instance.group(
                    SpreadSettings.CODEC.forGetter((IslandDensitySpread value) -> new SpreadSettings(value.originalSpacing, value.originalSeparation, value.spreadType())),
                    IslandDensity.Size.CODEC.fieldOf("density_class").forGetter(value -> value.size)
            )).apply(instance, (offset, method, frequency, salt, exclusion, spread, size) ->
                    new IslandDensitySpread(offset, method, frequency, salt, exclusion, spread.spacing(), spread.separation(), spread.spreadType(), size))
    ).validate(value -> value.originalSpacing > value.originalSeparation ? DataResult.success(value)
            : DataResult.error(() -> "Island structure spacing must be greater than separation"));

    private final int originalSpacing, originalSeparation;
    private final IslandDensity.Size size;
    public IslandDensitySpread(Vec3i offset, FrequencyReductionMethod method, float frequency, int salt,
                               Optional<ExclusionZone> exclusion, int spacing, int separation, RandomSpreadType spread, IslandDensity.Size size) {
        super(offset, method, frequency, salt, exclusion, spacing, separation, spread);
        this.originalSpacing = spacing;
        this.originalSeparation = separation;
        this.size = size;
    }
    @Override public int spacing() {
        double factor = Math.max(1.0, IslandDensity.maxPercent(size) / 100.0);
        return Math.max(1, (int) Math.floor(originalSpacing / Math.sqrt(factor)));
    }
    @Override public int separation() {
        return Math.min(spacing() - 1, (int) Math.floor((double) originalSeparation * spacing() / originalSpacing));
    }
    public double opportunityFactor() { return Math.pow((double) originalSpacing / spacing(), 2); }
    @Override public ChunkPos getPotentialStructureChunk(long seed, int x, int z) {
        if (spacing() == originalSpacing) return super.getPotentialStructureChunk(seed, x, z);
        return new RandomSpreadStructurePlacement(spacing(), separation(), spreadType(), salt()).getPotentialStructureChunk(seed, x, z);
    }
    @Override public StructurePlacementType<?> type() { return ModWorldgen.DENSITY_SPREAD.get(); }
}
