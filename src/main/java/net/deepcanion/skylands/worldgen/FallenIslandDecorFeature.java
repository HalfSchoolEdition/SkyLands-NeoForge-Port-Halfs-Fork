package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.SkyLands;
import net.deepcanion.skylands.config.GameplayConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Native trees, shrubs and foliage on *exposed* Fallen Island surfaces. */
public final class FallenIslandDecorFeature extends Feature<NoneFeatureConfiguration> {
    private static final String[] THEMES = {"golden", "glaciated", "arid", "lavic", "swamp", "cherry"};
    private static final SkyTerrainPalette[] PALETTES = makePalettes();

    private static SkyTerrainPalette[] makePalettes() {
        SkyTerrainPalette[] palettes = new SkyTerrainPalette[THEMES.length];
        for (int i = 0; i < THEMES.length; i++) palettes[i] = SkyTerrainPalette.forTheme(THEMES[i]);
        return palettes;
    }

    public FallenIslandDecorFeature() { super(NoneFeatureConfiguration.CODEC); }

    /** Secondary pass for larger handcrafted jigsaw Fallen Islands. Small
     * procedural fragments decorate themselves directly after placing rock. */
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!Level.OVERWORLD.equals(level.getLevel().dimension())
                || !GameplayConfig.GENERATE_FALLEN_ISLANDS.get()) return false;
        RandomSource random = context.random();
        int chunkX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int chunkZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        // Palette preflight: an ordinary Overworld chunk with no SkyLands
        // grass pays only a few cheap section-palette checks, not dozens of
        // vertical block scans. This also detects sparse jigsaw grass patches.
        boolean nativeTopPresent = false;
        for (LevelChunkSection section : level.getChunk(chunkX >> 4, chunkZ >> 4).getSections()) {
            if (section.maybeHas(state -> {
                for (SkyTerrainPalette palette : PALETTES)
                    if (palette.isTop(state)) return true;
                return false;
            })) { nativeTopPresent = true; break; }
        }
        if (!nativeTopPresent) return false;

        List<Surface> candidates = new ArrayList<>();
        // Full search only on a confirmed native-rock chunk. It is essential
        // for handcrafted Golden templates with as few as ten grass cells.
        for (int x = chunkX; x < chunkX + 16; x++) for (int z = chunkZ; z < chunkZ + 16; z++) {
            Surface site = surface(level, PALETTES, x, z);
            if (site != null) candidates.add(site);
        }
        // Jigsaw islands may contain structure-authored vegetation already.
        // Do not replace it, and never create vanilla lookalike oak/spruce trees.
        boolean tree = random.nextFloat() < .67 && placeOneTree(level,
                context.chunkGenerator(), random, candidates, chunkX, chunkZ);
        int plants = scatterPlants(level, random, candidates, 0.50f);
        scatterGlacialSnow(level, random, candidates);
        return tree || plants > 0;
    }

    /** Always called by a successful procedural fragment: no sampling phase
     * can miss its relatively small, handcrafted upper rock shelf. */
    static void decorateFragment(WorldGenLevel level, ChunkGenerator generator,
                                 RandomSource random, String theme, List<BlockPos> shelves,
                                 int cx, int cz) {
        if (shelves.isEmpty()) return;
        int index = SkyTerrainVegetation.themeIndex(theme);
        List<Surface> sites = new ArrayList<>(shelves.size());
        SkyTerrainPalette palette = SkyTerrainPalette.forTheme(theme);
        for (BlockPos at : shelves) {
            if (level.getBlockState(at.below()).is(palette.top().getBlock()))
                sites.add(new Surface(at, index));
        }
        if (sites.isEmpty()) return;
        // Try native trees on reasonably broad shelves, rarely leave a patch
        // completely bare. More than one can look crowded on a small fragment.
        if (random.nextFloat() < .76f && sites.size() >= 45) {
            List<Surface> central = new ArrayList<>();
            for (Surface site : sites)
                if (Math.abs(site.at().getX() - cx) <= 4 &&
                        Math.abs(site.at().getZ() - cz) <= 4) central.add(site);
            if (!central.isEmpty()) {
                int chunkX = Math.floorDiv(cx, 16) * 16;
                int chunkZ = Math.floorDiv(cz, 16) * 16;
                placeOneTree(level, generator, random, central, chunkX, chunkZ);
            }
        }
        if (index == 1 && random.nextInt(3) == 0)
            frostBerryFragmentCluster(level, random, sites);
        scatterPlants(level, random, sites, .48f);
        scatterGlacialSnow(level, random, sites);
    }

    private static void frostBerryFragmentCluster(WorldGenLevel level, RandomSource random,
                                                  List<Surface> sites) {
        Surface center=sites.get(random.nextInt(sites.size()));
        int desired=1+random.nextInt(6), placed=0;
        for (int attempt=0;attempt<desired*9 && placed<desired;attempt++) {
            Surface site=sites.get(random.nextInt(sites.size()));
            if (Math.abs(site.at().getX()-center.at().getX())>4
                    || Math.abs(site.at().getZ()-center.at().getZ())>4)continue;
            BlockPos at=site.at();
            if (!level.ensureCanWrite(at) || !level.getBlockState(at).isAir())continue;
            BlockState bush=(random.nextBoolean()
                    ? net.deepcanion.skylands.registry.ModBlocks.FROST_BERRY_BUSH.get()
                    : net.deepcanion.skylands.registry.ModBlocks.LARGE_FROST_BERRY_BUSH.get()).defaultBlockState();
            if(bush.canSurvive(level,at) && level.setBlock(at,bush,Block.UPDATE_CLIENTS))placed++;
        }
    }

    private static void scatterGlacialSnow(WorldGenLevel level, RandomSource random,
                                           List<Surface> sites) {
        for (Surface site : sites) {
            if (site.themeIndex() != 1 || random.nextFloat() >= .56f) continue;
            BlockPos at = site.at();
            if (!level.ensureCanWrite(at) || !level.getBlockState(at).isAir()) continue;
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (snow.canSurvive(level, at)) level.setBlock(at, snow, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean placeOneTree(WorldGenLevel level, ChunkGenerator generator,
                                        RandomSource random, List<Surface> candidates,
                                        int chunkX, int chunkZ) {
        if (candidates.isEmpty()) return false;
        for (int tries = 0; tries < Math.min(18, candidates.size()); tries++) {
            Surface s = candidates.get(random.nextInt(candidates.size()));
            BlockPos at = s.at();
            if (!level.ensureCanWrite(at) || !level.getBlockState(at).isAir()) continue;
            if (s.themeIndex() == 2) {
                // Arid uses its own configured SkyLands cactus tree, exactly
                // as it does in the sky biome, rather than vanilla acacia.
                if (SkyTerrainCacti.place(level, random, at)) return true;
            } else if (SkyTerrainTrees.placeFallen(level, random, s.themeIndex(), at,
                    chunkX, chunkZ)) return true;
        }
        return false;
    }

    private static int scatterPlants(WorldGenLevel level, RandomSource random,
                                     List<Surface> sites, float chance) {
        int placed = 0;
        for (Surface site : sites) {
            if (random.nextFloat() >= chance) continue;
            BlockPos at = site.at();
            if (!level.ensureCanWrite(at) || !level.getBlockState(at).isAir()) continue;
            BlockState plant = SkyTerrainVegetation.select(site.themeIndex(), random);
            if (!plant.canSurvive(level, at)) continue;
            if (plant.getBlock() instanceof DoublePlantBlock) {
                if (!level.ensureCanWrite(at.above()) || !level.getBlockState(at.above()).isAir()) continue;
                DoublePlantBlock.placeAt(level, plant, at, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                placed++;
            } else if (level.setBlock(at, plant, Block.UPDATE_CLIENTS)) placed++;
        }
        return placed;
    }

    private static Surface surface(WorldGenLevel level, SkyTerrainPalette[] palettes, int x, int z) {
        int y = SmallFallenIslandFeature.groundSurface(level, x, z);
        if (y > 235 || y < level.getMinBuildHeight() + 2) return null;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        // Plants can occupy the upper position and some templates have a
        // non-native grass/dirt layer immediately above their core substrate.
        for (int dy = 1; dy <= 3; dy++) {
            probe.set(x, y - dy, z);
            BlockState ground = level.getBlockState(probe);
            for (int theme = 0; theme < palettes.length; theme++) {
                if (palettes[theme].isTop(ground)) {
                    BlockPos at = probe.above();
                    return level.getBlockState(at).isAir() ? new Surface(at, theme) : null;
                }
            }
        }
        return null;
    }

    private record Surface(BlockPos at, int themeIndex) { }
}
