package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.deepcanion.skylands.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Repeat the entire original placement chain, so added attempts sample fresh positions/heights. */
public final class IslandDensityPlacement extends PlacementModifier {
    public static final MapCodec<IslandDensityPlacement> CODEC = IslandDensity.Size.CODEC.fieldOf("density_class")
            .xmap(IslandDensityPlacement::new, value -> value.size);
    private final IslandDensity.Size size;
    public IslandDensityPlacement(IslandDensity.Size size) { this.size = size; }
    @Override public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return IntStream.range(0, IslandDensity.featureCopies(size)).mapToObj(ignored -> pos);
    }
    @Override public PlacementModifierType<?> type() { return ModWorldgen.DENSITY_REPEAT.get(); }
}
