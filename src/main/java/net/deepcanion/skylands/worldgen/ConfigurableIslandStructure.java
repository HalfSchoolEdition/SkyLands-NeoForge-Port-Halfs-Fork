package net.deepcanion.skylands.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.deepcanion.skylands.registry.ModWorldgen;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** Keeps vanilla jigsaw layout/placement and applies density only to entire starts. */
public final class ConfigurableIslandStructure extends Structure {
    public static final MapCodec<ConfigurableIslandStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            JigsawStructure.CODEC.forGetter((ConfigurableIslandStructure value) -> value.delegate),
            IslandDensity.Size.CODEC.fieldOf("density_class").forGetter(value -> value.size),
            ResourceLocation.CODEC.optionalFieldOf("density_set").forGetter(value -> value.densitySet),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("fallen_island", false).forGetter(value -> value.fallenIsland)
    ).apply(instance, ConfigurableIslandStructure::new));

    private final JigsawStructure delegate;
    private final IslandDensity.Size size;
    private final Optional<ResourceLocation> densitySet;
    private final boolean fallenIsland;

    public ConfigurableIslandStructure(JigsawStructure delegate, IslandDensity.Size size, Optional<ResourceLocation> densitySet, boolean fallenIsland) {
        super(new StructureSettings(delegate.biomes(), delegate.spawnOverrides(), delegate.step(), delegate.terrainAdaptation()));
        this.delegate = delegate;
        this.size = size;
        this.densitySet = densitySet;
        this.fallenIsland = fallenIsland;
    }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Ground-level jigsaw Fallen Islands honor the existing world-restart config.
        if (fallenIsland && !net.deepcanion.skylands.config.GameplayConfig.GENERATE_FALLEN_ISLANDS.get())
            return Optional.empty();
        // Use the actual vanilla start position/biome, including heightmap projection.
        // At 100% no extra random draws or changes to the layout are made.
        return delegate.findGenerationPoint(context).filter(stub -> {
            var pos = stub.position();
            var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), context.randomState().sampler());
            var id = context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(this);
            // Arid/Golden/Glaciated archipelagos also exist outside SkyLands.
            // Their low-altitude and sky-height variants share the original
            // spacing/salt so we do not add more islands. Select exactly one
            // variant per horizontal site, using the same sky-biome reference
            // altitude as procedural terrain generation. The ordinary biome
            // at the lower structure Y can differ from the biome at sky Y.
            if (id != null) {
                String name = id.getPath();
                boolean outside = name.endsWith("_archipelago_outside");
                String base = outside ? name.substring(0, name.length() - "_outside".length()) : name;
                if (base.equals("golden_archipelago") || base.equals("glaciated_archipelago")
                        || base.equals("arid_archipelago")) {
                    var skyBiome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                            QuartPos.fromBlock(350), QuartPos.fromBlock(pos.getZ()), context.randomState().sampler());
                    if (IslandDensity.isSkyBiome(skyBiome) == outside) return false;
                }
            }
            if (fallenIsland) {
                // Match both the existence and the THEME of actual floating
                // terrain above/near this ground structure, not the surface
                // biome tag alone. No chunk reads or forced chunk generation.
                String above = FallenIslandSkyLink.nearbyTheme(context.seed(),
                        context.biomeSource(), context.randomState().sampler(),
                        pos.getX(), pos.getZ());
                if (above == null || id == null) return false;
                String type = id.getPath();
                if (type.startsWith("fallen_lavic") && !"lavic".equals(above)) return false;
                if (type.startsWith("fallen_golden") && !"golden".equals(above)) return false;
            }
            var set = densitySet.map(key -> context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(key));
            double factor = set.filter(value -> value.placement() instanceof IslandDensitySpread)
                    .map(value -> ((IslandDensitySpread) value.placement()).opportunityFactor()).orElse(1.0);
            if (id != null && !IslandDensity.keepAtFactor(size, biome, context.seed(),
                    context.chunkPos().x, 0, context.chunkPos().z, id, factor)) return false;
            // Do not reject a small NBT island merely because the *wide* bounding
            // box of an unrelated procedural island lies nearby. The previous
            // +/-64 x/z, -35/+75 y probe falsely rejected most island clusters
            // and archipelagos placed near the same altitude as sky terrain.
            // Probe the starting island's actual rock-bearing envelope instead;
            // unrelated large structures retain the original broad protection.
            if (pos.getY() >= 265) {
                int radius = 64;
                int bottom = pos.getY() - 35;
                int top = pos.getY() + 75;
                if (id != null) {
                    String name = id.getPath();
                    if (name.equals("golden_island_cluster")) {
                        radius = 36;
                        bottom = pos.getY();
                        top = pos.getY() + 60;
                    } else if (name.equals("glaciated_island_cluster")
                            || name.equals("swampy_island_cluster")) {
                        radius = 33;
                        bottom = pos.getY() + 24;
                        top = pos.getY() + 114;
                    } else if (name.equals("golden_archipelago")
                            || name.equals("glaciated_archipelago")
                            || name.equals("arid_archipelago")
                            || name.equals("lavic_archipelago")) {
                        radius = 30;
                        bottom = pos.getY();
                        top = pos.getY() + 46;
                    }
                }
                if (SkyTerrainStructureGuard.wouldIntersect(
                        context.seed(), context.biomeSource(), context.randomState().sampler(),
                        pos.getX() - radius, bottom, pos.getZ() - radius,
                        pos.getX() + radius, top, pos.getZ() + radius)) return false;
            }
            return true;
        });
    }

    @Override public StructureType<?> type() { return ModWorldgen.DENSITY_JIGSAW.get(); }
}
