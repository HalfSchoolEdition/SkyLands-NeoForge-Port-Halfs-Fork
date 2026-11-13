package net.deepcanion.skylands.worldgen;

import java.util.Optional;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.deepcanion.skylands.worldgen.SkyBiomes;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Data-driven island placement with explicit world-height and writable-region guards. */
public final class IslandTemplateFeature extends Feature<TemplateFeatureConfiguration> {
    public IslandTemplateFeature() { super(TemplateFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<TemplateFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        // SkyLands template features belong exclusively to the Overworld.
        // Biome modifiers alone cannot protect against accidental cross-dimension registration.
        if (!Level.OVERWORLD.equals(level.getLevel().dimension())) return false;
        TemplateFeatureConfiguration config = context.config();
        if (config.structure().getPath().startsWith("fallen_isle_")
                && !net.deepcanion.skylands.config.GameplayConfig.GENERATE_FALLEN_ISLANDS.get()) return false;
        RandomSource random = context.random();
        BlockPos candidate = context.origin();
        if (config.densityClass() != IslandDensity.Size.NONE && !IslandDensity.keepAtFactor(config.densityClass(), level.getBiome(candidate), level.getSeed(),
                candidate.getX(), candidate.getY(), candidate.getZ(), config.structure(), IslandDensity.featureCopies(config.densityClass()))) return false;
        Optional<StructureTemplate> loaded = level.getLevel().getServer().getStructureManager().get(config.structure());
        if (loaded.isEmpty()) return false;
        StructureTemplate template = loaded.get();
        if (template.getSize().getX() == 0 || template.getSize().getY() == 0 || template.getSize().getZ() == 0) return false;
        Rotation rotation = config.randomRotation() ? Rotation.getRandom(random) : Rotation.NONE;
        Mirror mirror = config.randomMirror() ? Mirror.values()[random.nextInt(Mirror.values().length)] : Mirror.NONE;
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror)
                .setRandom(random).setIgnoreEntities(true)
                .addProcessor(new BlockIgnoreProcessor(config.ignoredBlocks().stream().map(Holder::value).toList()))
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        // Sky-only NBT island FEATURES used to inherit narrow, low absolute
        // height ranges from their placed-feature JSONs. Their main surface is
        // independently sampled here; non-sky biome structures stay unchanged.
        String skyTheme = null;
        var candidateBiome=level.getBiome(candidate);
        if(candidateBiome.is(SkyBiomes.SWAMP)) skyTheme="swamp";
        else if(candidateBiome.is(SkyBiomes.GLACIATED)) skyTheme="glaciated";
        else if(candidateBiome.is(SkyBiomes.GOLDEN)) skyTheme="golden";
        else if(candidateBiome.is(SkyBiomes.ARID)) skyTheme="arid";
        else if(candidateBiome.is(SkyBiomes.LAVIC)) skyTheme="lavic";
        else if(candidateBiome.is(SkyBiomes.CHERRY)) skyTheme="cherry";
        String structureName=config.structure().getPath();
        // Only standalone island templates, not caves, spikes, launchers or ruins.
        boolean smallIsland=structureName.startsWith("isle_")
                || structureName.startsWith("cherry_isle_")
                || structureName.startsWith("desert_isle_")
                || structureName.startsWith("lava_isle_")
                || structureName.startsWith("sstructure");
        int placementY = skyTheme!=null && smallIsland
                ? SkyTemplateIslandHeights.chooseY(level.getSeed(),skyTheme,structureName,
                        candidate.getX(),candidate.getZ(),candidate.getY(),
                        template.getSize().getY(),config.offset().getY(),
                        level.getMinBuildHeight(),level.getMaxBuildHeight())
                : candidate.getY();
        BlockPos origin = new BlockPos(candidate.getX(), placementY, candidate.getZ()).offset(config.offset());
        String templateName = config.structure().getPath();
        boolean surfaceLauncher = switch (templateName) {
            case "launcher_01_complete", "ice_launcher_part0", "lavic_launcher_part0", "arid_launcher_part0" -> true;
            default -> false;
        };
        BlockPos placement;
        BoundingBox bounds;
        if (surfaceLauncher) {
            // The Golden launcher's tall template is off-centre: its actual base
            // is at (5, 0, 6), not the centre of the upper landing island.
            // All three other launchers use a 7x7 ground-level template.
            boolean golden = templateName.equals("launcher_01_complete");
            int radius = golden ? 2 : 3;
            int safeSurface = launcherSurfaceHeight(level, origin.getX(), origin.getZ(), radius);
            if (safeSurface == Integer.MIN_VALUE) return false;
            BlockPos localBaseCentre = golden ? new BlockPos(5, 0, 6) : new BlockPos(3, 0, 3);
            BlockPos rotatedBaseCentre = StructureTemplate.calculateRelativePosition(settings, localBaseCentre);
            placement = new BlockPos(origin.getX() - rotatedBaseCentre.getX(),
                    safeSurface + config.offset().getY(), origin.getZ() - rotatedBaseCentre.getZ());
            bounds = template.getBoundingBox(settings, placement);
        } else {
            bounds = template.getBoundingBox(settings, origin);
            placement = origin.offset(origin.getX() - (bounds.minX() + bounds.maxX()) / 2,
                    0, origin.getZ() - (bounds.minZ() + bounds.maxZ()) / 2);
            bounds = template.getBoundingBox(settings, placement);
        }
        if (bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight()) return false;
        if (!surfaceLauncher && bounds.maxY() >= SkyIslandShape.MIN_Y
                && SkyTerrainStructureGuard.wouldIntersect(level.getSeed(),
                        level.getLevel().getChunkSource().getGenerator().getBiomeSource(),
                        level.getLevel().getChunkSource().randomState().sampler(),
                        bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ())) return false;
        if (!level.ensureCanWrite(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                || !level.ensureCanWrite(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))
                || !level.ensureCanWrite(new BlockPos(bounds.minX(), bounds.minY(), bounds.maxZ()))
                || !level.ensureCanWrite(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.minZ()))) return false;
        return template.placeInWorld(level, placement, placement, settings, random, Block.UPDATE_CLIENTS);
    }

    /**
     * Require dry, solid and reasonably level natural ground under the entire
     * launcher base. Vanilla surface-height sampling at one corner can otherwise
     * bury the off-centre launcher in a hillside or place it underwater.
     *
     * Plants and snow layers on top of ground are ignored. Tree canopies and
     * trunks are rejected so the launcher isn't generated on top of foliage.
     * The two-block slope tolerance avoids terrain-clipping platforms.
     */
    private static int launcherSurfaceHeight(WorldGenLevel level, int centerX, int centerZ, int radius) {
        int highest = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int groundY = Integer.MIN_VALUE;
                // Surface heightmaps can include short grass, flowers or snow.
                for (int below = 1; below <= 4; below++) {
                    BlockPos groundPos = new BlockPos(x, top - below, z);
                    BlockState ground = level.getBlockState(groundPos);
                    if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)
                            || ground.is(BlockTags.LOGS)) return Integer.MIN_VALUE;
                    if (ground.isSolidRender(level, groundPos)) {
                        groundY = groundPos.getY() + 1;
                        break;
                    }
                }
                if (groundY == Integer.MIN_VALUE) return Integer.MIN_VALUE;
                highest = Math.max(highest, groundY);
                lowest = Math.min(lowest, groundY);
                if (highest - lowest > 2) return Integer.MIN_VALUE;
            }
        }
        return highest;
    }
}
