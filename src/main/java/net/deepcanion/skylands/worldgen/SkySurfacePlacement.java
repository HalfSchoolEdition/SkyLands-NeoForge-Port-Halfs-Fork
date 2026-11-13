package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.deepcanion.skylands.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

/** Finds the highest custom sky-soil surface, never ordinary Overworld ground. */
public final class SkySurfacePlacement extends PlacementModifier {
    public static final MapCodec<SkySurfacePlacement> CODEC = MapCodec.unit(new SkySurfacePlacement());
    private static final TagKey<net.minecraft.world.level.block.Block> SURFACES=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("skylands","procedural_island_surface"));
    @Override public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        for(int y=489;y>=300;y--) {
            BlockPos at=new BlockPos(pos.getX(),y,pos.getZ());
            if(context.getLevel().getBlockState(at).is(SURFACES) && context.getLevel().getBlockState(at.above()).isAir())
                return Stream.of(at.above());
        }
        return Stream.empty();
    }
    @Override public PlacementModifierType<?> type(){ return ModWorldgen.SKY_SURFACE.get(); }
}
