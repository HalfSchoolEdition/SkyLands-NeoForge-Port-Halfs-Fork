package net.deepcanion.skylands.registry;

import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.worldgen.IslandTemplateFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, SkyLands.MOD_ID);
    public static final DeferredHolder<Feature<?>, IslandTemplateFeature> STRUCTURE_FEATURE = FEATURES.register("structure_feature", IslandTemplateFeature::new);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?>> PROCESSOR_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, SkyLands.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?>,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<net.deepcanion.skylands.worldgen.FallenIslandProcessor>>
            FALLEN_DETAILS = PROCESSOR_TYPES.register("fallen_details", () -> () -> net.deepcanion.skylands.worldgen.FallenIslandProcessor.CODEC);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, SkyLands.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.structure.StructureType<?>, net.minecraft.world.level.levelgen.structure.StructureType<net.deepcanion.skylands.worldgen.ConfigurableIslandStructure>> DENSITY_JIGSAW = STRUCTURE_TYPES.register("density_jigsaw", () -> () -> net.deepcanion.skylands.worldgen.ConfigurableIslandStructure.CODEC);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENT_TYPES = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, SkyLands.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>, net.minecraft.world.level.levelgen.placement.PlacementModifierType<net.deepcanion.skylands.worldgen.IslandDensityPlacement>> DENSITY_REPEAT = PLACEMENT_TYPES.register("density_repeat", () -> () -> net.deepcanion.skylands.worldgen.IslandDensityPlacement.CODEC);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, SkyLands.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<?>, net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<net.deepcanion.skylands.worldgen.IslandDensitySpread>> DENSITY_SPREAD = STRUCTURE_PLACEMENT_TYPES.register("density_spread", () -> () -> net.deepcanion.skylands.worldgen.IslandDensitySpread.CODEC);
    /** Deterministic chunk-local sky terrain, reconstructing islands across chunk borders. */
    public static final DeferredHolder<Feature<?>, net.deepcanion.skylands.worldgen.SkyTerrainFeature> SKY_TERRAIN = FEATURES.register("sky_terrain", net.deepcanion.skylands.worldgen.SkyTerrainFeature::new);
    /** Small spiked Fallen Islands, using the same six theme palettes. */
    public static final DeferredHolder<Feature<?>, net.deepcanion.skylands.worldgen.SmallFallenIslandFeature> SMALL_FALLEN =
            FEATURES.register("small_fallen_island", net.deepcanion.skylands.worldgen.SmallFallenIslandFeature::new);
    /** Sparse surface vegetation and trees exclusively on exposed Fallen Island soil. */
    public static final DeferredHolder<Feature<?>, net.deepcanion.skylands.worldgen.FallenIslandDecorFeature> FALLEN_DECOR =
            FEATURES.register("fallen_island_decor", net.deepcanion.skylands.worldgen.FallenIslandDecorFeature::new);
    /** Light decorative vegetation restricted to actual island surfaces. */
    public static final DeferredHolder<Feature<?>, net.deepcanion.skylands.worldgen.SkyTerrainDecorationFeature> SKY_TERRAIN_DECORATION = FEATURES.register("sky_terrain_decoration", net.deepcanion.skylands.worldgen.SkyTerrainDecorationFeature::new);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>, net.minecraft.world.level.levelgen.placement.PlacementModifierType<net.deepcanion.skylands.worldgen.SkySurfacePlacement>> SKY_SURFACE = PLACEMENT_TYPES.register("sky_surface", () -> () -> net.deepcanion.skylands.worldgen.SkySurfacePlacement.CODEC);
    private ModWorldgen() { }
}
