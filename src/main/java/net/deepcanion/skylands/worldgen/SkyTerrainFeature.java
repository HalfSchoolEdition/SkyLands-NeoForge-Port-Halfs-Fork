package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Chunk-local placement of deterministic, volumetric floating islands.
 * Island geometry is reconstructed from seed/grid positions independently by
 * each chunk. No writes or chunk-loading biome queries are made in neighboring chunks.
 *
 * Runs once for Overworld-host biomes at TOP_LAYER_MODIFICATION, after
 * vanilla and modded ground vegetation has already been placed.  Island
 * foliage is generated immediately afterward by SkyTerrainFoliage.
 * Each island is owned
 * by the sky biome at its deterministic anchor, not by the biome of every
 * individual block: this prevents hard clipping at sky-biome borders.
 * NoiseBiome lookups do not access or generate neighboring chunks.
 * Ordinary overworld terrain is never replaced.
 */
public final class SkyTerrainFeature extends Feature<SkyTerrainThemeConfiguration> {
    public SkyTerrainFeature() { super(SkyTerrainThemeConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<SkyTerrainThemeConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!Level.OVERWORLD.equals(level.getLevel().dimension())) return false;

        // Surface cave settings are synchronized at COMMON config load; no
        // per-chunk config reads or extra worldgen pass are necessary.
        // One placed feature is attached to Overworld-host biomes. The theme at
        // each site's centre selects a single palette for that entire island.
        // Never gate individual block columns on their local sky biome: the old
        // check made vertical, chunk-sized cliff cutoffs at biome transitions.
        long seed = level.getSeed();
        int chunkX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int chunkZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        int gridX = Math.floorDiv(chunkX, SkyIslandShape.GRID);
        int gridZ = Math.floorDiv(chunkZ, SkyIslandShape.GRID);
        var source = level.getLevel().getChunkSource();
        BiomeSource biomeSource = source.getGenerator().getBiomeSource();
        Climate.Sampler sampler = source.randomState().sampler();

        List<ThemedIsland> candidateIslands = new ArrayList<>();
        // Expanded candidates for spike geometry only: horns can span several
        // chunks, but their world-seeded positions must be identical everywhere.
        List<SkyIslandShape.Island> nearbyLavicIslands = new ArrayList<>();
        List<SkyTerrainLavicChains.Chain> nearbyLavicChains = new ArrayList<>();
        List<SkyTerrainAridMegaArches.Arch> nearbyAridMegaArches = new ArrayList<>();
        // Basalt formations can protrude slightly beyond Golden cliff edges.
        List<SkyIslandShape.Island> nearbyGoldenIslands = new ArrayList<>();
        List<SkyIslandShape.Island> nearbyAridIslands = new ArrayList<>();
        List<SkyIslandShape.Island> nearbyGlaciatedIslands = new ArrayList<>();
        for (int gx = gridX - 2; gx <= gridX + 2; gx++) {
            for (int gz = gridZ - 2; gz <= gridZ + 2; gz++) {
                if (!SkyIslandShape.hasSite(seed, gx, gz)) continue;
                int sx = SkyIslandShape.siteX(seed, gx, gz);
                int sz = SkyIslandShape.siteZ(seed, gx, gz);
                // Reject clearly distant sites before sampling biome/noise.
                if (sx < chunkX - 260 || sx > chunkX + 275
                        || sz < chunkZ - 260 || sz > chunkZ + 275) continue;
                Holder<Biome> anchor = biomeSource.getNoiseBiome(
                        QuartPos.fromBlock(sx), QuartPos.fromBlock(350),
                        QuartPos.fromBlock(sz), sampler);
                String theme = themeOf(anchor);
                if (theme == null) continue;
                // Most nearby sites never touch this chunk. Only resolve the
                // palette (and its cached block states) if one actually does.
                SkyTerrainPalette palette = null;
                List<SkyIslandShape.Island> siteIslands = SkyIslandShape.sites(seed, gx, gz, theme);
                if ("lavic".equals(theme)) nearbyLavicChains.addAll(
                        SkyTerrainLavicChains.fromSite(seed, gx, gz, siteIslands, (neighborGX,neighborGZ) -> {
                            int neighborX=SkyIslandShape.siteX(seed,neighborGX,neighborGZ);
                            int neighborZ=SkyIslandShape.siteZ(seed,neighborGX,neighborGZ);
                            Holder<Biome> neighborBiome=biomeSource.getNoiseBiome(
                                    QuartPos.fromBlock(neighborX),QuartPos.fromBlock(350),
                                    QuartPos.fromBlock(neighborZ),sampler);
                            if(!"lavic".equals(themeOf(neighborBiome)))return List.of();
                            return SkyIslandShape.sites(seed,neighborGX,neighborGZ,"lavic");
                        }));
                if ("arid".equals(theme)) nearbyAridMegaArches.addAll(
                        SkyTerrainAridMegaArches.fromSite(seed,gx,gz,siteIslands));
                for (SkyIslandShape.Island island : siteIslands) {
                    if ("golden".equals(theme) && island.intersects(
                            chunkX - SkyTerrainBasaltGeometry.MARGIN,
                            chunkZ - SkyTerrainBasaltGeometry.MARGIN,
                            chunkX + 15 + SkyTerrainBasaltGeometry.MARGIN,
                            chunkZ + 15 + SkyTerrainBasaltGeometry.MARGIN))
                        nearbyGoldenIslands.add(island);
                    if ("arid".equals(theme) && island.intersects(
                            chunkX - 34, chunkZ - 34, chunkX + 49, chunkZ + 49))
                        nearbyAridIslands.add(island);
                    if ("glaciated".equals(theme) && island.intersects(
                            chunkX - 34, chunkZ - 34, chunkX + 49, chunkZ + 49))
                        nearbyGlaciatedIslands.add(island);
                    if ("lavic".equals(theme) && island.intersects(
                            chunkX - 96, chunkZ - 96, chunkX + 111, chunkZ + 111))
                        nearbyLavicIslands.add(island);
                    if (island.intersects(chunkX, chunkZ, chunkX + 15, chunkZ + 15)) {
                        if (palette == null) palette = SkyTerrainPalette.forTheme(theme);
                        candidateIslands.add(new ThemedIsland(island, palette));
                    }
                }
            }
        }
        if (candidateIslands.isEmpty()) {
            // Both crystal bridges and Lavic spikes may extend into air-only chunks.
            int spikes = SkyTerrainLavicSpikes.decorate(level,seed,chunkX,chunkZ,nearbyLavicIslands);
            int basalt = SkyTerrainBasaltColumns.decorate(level,seed,chunkX,chunkZ,nearbyGoldenIslands);
            int rocks = SkyTerrainAridFormations.decorate(level,seed,chunkX,chunkZ,nearbyAridIslands);
            int megaArches = SkyTerrainAridMegaArches.decorate(level,seed,chunkX,chunkZ,nearbyAridMegaArches);
            int chains = SkyTerrainLavicChains.decorate(level,chunkX,chunkZ,nearbyLavicChains);
            int ice = SkyTerrainGlacialCrystals.decorate(level,seed,chunkX,chunkZ,nearbyGlaciatedIslands);
            if (basalt > 0 || ice > 0) SkyTerrainFoliage.decorate(level, context.chunkGenerator(), seed, chunkX, chunkZ);
            SkyTerrainCrystalVines.decorate(level,seed,chunkX,chunkZ);
            return spikes + basalt + rocks + megaArches + chains + ice > 0;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        SkyTerrainPalette[] materialAt = new SkyTerrainPalette[SkyIslandShape.MAX_Y - SkyIslandShape.MIN_Y + 2];
        BlockState cherryMoss = BuiltInRegistries.BLOCK.get(SkyLands.id("cherry_mossy_skyrock")).defaultBlockState();
        BlockState goldenMoss = BuiltInRegistries.BLOCK.get(SkyLands.id("golden_mossy_skyrock")).defaultBlockState();
        BlockState swampMoss = BuiltInRegistries.BLOCK.get(SkyLands.id("swamp_mossy_skyrock")).defaultBlockState();
        BlockState glaciatedMoss = BuiltInRegistries.BLOCK.get(SkyLands.id("glaciated_mossy_skyrock")).defaultBlockState();
        BlockState aridMoss = BuiltInRegistries.BLOCK.get(SkyLands.id("mossy_sky_sandstone")).defaultBlockState();
        BlockState aridGrass = BuiltInRegistries.BLOCK.get(SkyLands.id("arid_grass_block")).defaultBlockState();
        boolean[] lowerSwampBand = new boolean[materialAt.length];
        int changed = 0;
        for (int x = chunkX; x < chunkX + 16; x++) {
            for (int z = chunkZ; z < chunkZ + 16; z++) {
                List<ThemedIsland> column = null;
                int low = SkyIslandShape.MAX_Y, high = SkyIslandShape.MIN_Y;
                for (ThemedIsland themed : candidateIslands) {
                    if (!themed.island.touches(x, z)) continue;
                    if (column == null) column = new ArrayList<>(4);
                    column.add(new ThemedIsland(themed.island, themed.palette,
                            themed.island.column(x, z)));
                    low = Math.min(low, themed.island.minY());
                    high = Math.max(high, themed.island.maxY());
                }
                if (column == null) continue;
                // One material decision per world X/Z column, not per Y voxel.
                // No registered biome changes, chunk edges, or feature RNG.
                boolean aridColumn = false;
                boolean lavicColumn = false;
                boolean swampColumn = false;
                for (ThemedIsland themed : column) {
                    if (themed.palette.isArid()) aridColumn = true;
                    if (themed.palette.isLavic()) lavicColumn = true;
                    if (themed.palette.isSwamp()) swampColumn = true;
                }
                SkyTerrainSubregions.AridSample aridRegion = aridColumn
                        ? SkyTerrainSubregions.arid(seed, x, z) : null;
                double coverNoise = aridRegion == null ? 0.0
                        : SkyTerrainPatterns.noise2(seed, x, z, 41, 0x57a3d198L) - .5;
                boolean duneSand = aridRegion != null
                        && aridRegion.dunes() > .46 + .19 * coverNoise;
                boolean bareHighland = aridRegion != null && !duneSand
                        && aridRegion.rockyHighlands() > .48 + .18 * coverNoise;
                // The Highlands' lateral strata warp is constant down each
                // vertical column: never recompute it for every rock voxel.
                double aridStrataWarp = aridRegion != null && aridRegion.rockyHighlands() > .18
                        ? SkyAridTerracottaPalette.warp(seed,x,z) : 0.0;
                double highlandSurfacePatch = aridRegion != null && aridRegion.rockyHighlands() > .18
                        ? SkyTerrainPatterns.noise2(seed,x,z,21,0x72a830L) : 0.0;
                SkyTerrainSubregions.SwampSample swampRegion = swampColumn
                        ? SkyTerrainSubregions.swamp(seed,x,z) : null;
                double swampPatch = swampRegion != null && (swampRegion.mire() > .25
                        || swampRegion.thickets() > .35)
                        ? SkyTerrainPatterns.noise2(seed,x,z,18,0x643c22L) : 0.0;
                SkyTerrainLavicHydrology.Sample moltenSurface = lavicColumn
                        ? SkyTerrainLavicHydrology.sample(seed, x, z,
                            SkyTerrainSubregions.lavic(seed, x, z))
                        : SkyTerrainLavicHydrology.NONE;
                low = Math.max(low, SkyIslandShape.MIN_Y);
                high = Math.min(high, SkyIslandShape.MAX_Y);
                if (low > high) continue;

                // Compose overlapping landmasses before selecting surface blocks.
                // Columns can contain disconnected shelves and cave openings.
                for (int y = low; y <= high; y++) {
                    SkyTerrainPalette owner = null;
                    for (ThemedIsland island : column) {
                        if (island.column.solid(y)) { owner = island.palette; break; }
                    }
                    materialAt[y - SkyIslandShape.MIN_Y] = owner;
                }

                // Measure separate solid shelves; apply the diorite lower band to
                // BOTH Swamp and Glaciated islands, never across cave voids.
                java.util.Arrays.fill(lowerSwampBand, false);
                for (int y = low; y <= high;) {
                    if (materialAt[y - SkyIslandShape.MIN_Y] == null) { y++; continue; }
                    int bottom = y;
                    while (y <= high && materialAt[y - SkyIslandShape.MIN_Y] != null) y++;
                    int top = y - 1;
                    int cut = bottom + (int)((top-bottom)*.48);
                    for (int yy=bottom; yy<=cut; yy++) {
                        lowerSwampBand[yy - SkyIslandShape.MIN_Y] = true;
                    }
                }

                // Count unobstructed vertical air above each top surface. This
                // distinguishes exterior ledges from shallow, enclosed cave floors.
                int airAbove = high + 16;
                int soilRemaining = 0;
                for (int y = high; y >= low; y--) {
                    int index = y - SkyIslandShape.MIN_Y;
                    SkyTerrainPalette owner = materialAt[index];
                    if (owner == null) {
                        airAbove++;
                        soilRemaining = 0;
                        continue;
                    }
                    boolean exposed = airAbove >= 9;
                    BlockState material;
                    if (exposed) {
                        if (owner.isArid() && duneSand) {
                            // Real, pale sand rather than legacy sky_sand,
                            // whose texture is identical to Arid grass.
                            // Never create unsupported falling-sand overhangs:
                            // retain solid Sky Sandstone under every sand bed.
                            int support = 0;
                            while (support < 5 && y - support - 1 >= low
                                    && materialAt[index - support - 1] == owner) support++;
                            if (support > 0) {
                                material = Blocks.SAND.defaultBlockState();
                                soilRemaining = Math.min(support - 1,
                                        2 + (int)(2.0 * aridRegion.dunes()));
                            } else {
                                material = owner.core();
                                soilRemaining = 0;
                            }
                        } else if (owner.isArid() && bareHighland) {
                            // Patchy steppe tops let plants grow between exposed
                            // terracotta beds. Sand never hangs above empty sky.
                            boolean supported=index>1 && materialAt[index-1]==owner
                                    && materialAt[index-2]==owner;
                            if(highlandSurfacePatch > .72) material=aridGrass;
                            else if(supported && highlandSurfacePatch > .49)
                                material=Blocks.RED_SAND.defaultBlockState();
                            else if(supported && highlandSurfacePatch > .34)
                                material=Blocks.SAND.defaultBlockState();
                            else if(highlandSurfacePatch > .24)material=aridMoss;
                            else if(highlandSurfacePatch > .14)material=owner.core();
                            else material=SkyAridTerracottaPalette.stone(y,aridStrataWarp);
                            soilRemaining=material.is(aridGrass.getBlock()) ? 2 : 0;
                        } else {
                            material = owner.top();
                            soilRemaining = 2;
                        }
                    } else if (soilRemaining > 0) {
                        material = owner.isArid() && duneSand
                                ? Blocks.SAND.defaultBlockState() : owner.soil();
                        soilRemaining--;
                    } else {
                        material = owner.core();
                    }
                    airAbove = 0;
                    // The wetter and overgrown Swamp districts get a deliberate
                    // irregular mud/moss mosaic, never per-voxel patch noise.
                    if (exposed && owner.isSwamp() && swampRegion != null
                            && material.is(owner.top().getBlock())) {
                        double blend = swampRegion.mire()*.65 + swampRegion.thickets()*.20;
                        double roll = SkyTerrainPatterns.unit(sample(seed,x,y,z,0x643c23L));
                        if (swampPatch + .20*roll > .93 - .22*blend)
                            material = swampRegion.mire() > swampRegion.thickets()
                                    ? Blocks.MUD.defaultBlockState() : swampMoss;
                    }
                    // Basin floors stay solid, and shoreline materials form
                    // continuous natural-looking patches, not random speckles.
                    for (ThemedIsland themed : column) {
                        SkyIslandShape.LakeSample ls=themed.column.lake();
                        if(ls==null || y!=ls.top())continue;
                        if(ls.water()) {
                            switch(ls.theme()) {
                                case "swamp" -> material=SkyTerrainPatterns.wetlandClay(seed,x,z)
                                        ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState();
                                case "golden", "cherry" -> {
                                    if(SkyTerrainPatterns.patch(seed,x,z,26,0x5f230L)>.22)
                                        material=Blocks.SAND.defaultBlockState();
                                    else material=owner.core();
                                }
                                default -> material=owner.core(); // No ice on the lake floor.
                            }
                        } else if(ls.theme().equals("swamp") && ls.blend()<.54
                                && SkyTerrainPatterns.patch(seed,x,z,15,0x45b912L)>.13) {
                            // Muddy, irregular shore strips give frogs dry
                            // spawnable ground specifically beside wetlands.
                            material=Blocks.MUD.defaultBlockState();
                        } else if((ls.theme().equals("golden")||ls.theme().equals("cherry"))
                                && ls.blend()<.43 && SkyTerrainPatterns.patch(seed,x,z,26,0x5f230L)>.18) {
                            material=Blocks.SAND.defaultBlockState();
                        }
                        break;
                    }
                    // Underground Lavic chamber floors are stone, not grass.
                    for(ThemedIsland themed:column) {
                        SkyIslandShape.CaveSample cave=themed.column.cave();
                        if(cave!=null && cave.inside() && y==cave.floorY()) {
                            material=owner.core();
                            break;
                        }
                    }
                    // Surface streams flow through Molten Rifts, touching and
                    // occasionally spilling from their floating cliff edges.
                    // Replace only visible Lavic grass caps, never lake floors,
                    // cave roofs, other themes or unsupported overhangs.
                    if (exposed && owner.isLavic() && material.is(owner.top().getBlock())
                            && index > 1 && materialAt[index-1] == owner
                            && materialAt[index-2] == owner) {
                        if (moltenSurface.molten()) {
                            material = net.deepcanion.skylands.registry.ModBlocks.MOLTEN_METAL
                                    .get().defaultBlockState();
                            soilRemaining = 0;
                        } else if (moltenSurface.magma()) {
                            material = Blocks.MAGMA_BLOCK.defaultBlockState();
                            soilRemaining = 0;
                        }
                    }
                    // Natural stone remains the dominant material. Moss is
                    // discontinuous 3D noise, rather than the entire rock mass.
                    // The Swamp's lower half is diorite (never its grass cap).
                    if (!exposed && material.is(owner.core().getBlock())) {
                        if ((owner.isSwamp() || owner.isGlaciated()) && lowerSwampBand[index]) {
                            material = Blocks.DIORITE.defaultBlockState();
                        } else if (owner.isCherry() && SkyTerrainPatterns.noise3(seed,x,y,z,11,0x723aL) > .66
                                && (sample(seed,x,y,z,0x723a91L) & 7L) == 0L) {
                            material = cherryMoss;
                        } else if (owner.isSwamp() && SkyTerrainPatterns.noise3(seed,x,y,z,11,0x8bc1L) > .66
                                && (sample(seed,x,y,z,0x8bc191L) & 3L) == 0L) {
                            // Only SIDE/INTERIOR moss is thinned. The scattered
                            // moss poking through Swamp grass is unchanged.
                            material = swampMoss;
                        } else if (owner.isGolden() && SkyTerrainPatterns.noise3(seed,x,y,z,12,0x619fL) > .65
                                && (sample(seed,x,y,z,0x619f91L) & 7L) == 0L) {
                            material = goldenMoss;
                        } else if (owner.isGlaciated() && SkyTerrainPatterns.noise3(seed,x,y,z,12,0x6cf19L) > .65
                                && (sample(seed,x,y,z,0x6cf198L) & 7L) == 0L) {
                            material = glaciatedMoss;
                        } else if (owner.isArid() && SkyTerrainPatterns.noise3(seed,x,y,z,13,0x72a81L) > .68
                                && (sample(seed,x,y,z,0x72a819L) & 7L) == 0L) {
                            material = aridMoss;
                        }
                    }
                    // Mesa core + exposed sides are colored terracotta rather
                    // than endless sandstone. Subregion edges blend through
                    // existing Sky Sandstone; strata stay aligned between
                    // naturally generated cliffs, hoodoos and giant arches.
                    if (owner.isArid() && aridRegion != null
                            && aridRegion.rockyHighlands() > .18
                            && material.is(owner.core().getBlock())) {
                        double transition = aridRegion.rockyHighlands();
                        double shape = SkyTerrainPatterns.noise2(seed,x,z,31,0x72a832L);
                        if (shape < .14 + .78 * transition)
                            material = SkyAridTerracottaPalette.stone(y,aridStrataWarp);
                    }
                    // Rare Pale Celestium veins embedded inside Lavic SkyMetal.
                    // This does not alter other palettes or exposed grass caps.
                    if (material.is(net.deepcanion.skylands.registry.ModBlocks.LAVIC_SKYMETAL.get())
                            && (sample(seed, x, y, z, 0x6fa1b8d2L) & 2047L) == 0L) {
                        material = owner.ore();
                    }
                    // Very rare Pearlecent Celestium in Glaciated Skyrock.
                    // Never replace the diorite underside, soil, or moss flecks.
                    if (owner.isGlaciated() && material.is(owner.core().getBlock())
                            && SkyTerrainPatterns.noise3(seed,x,y,z,5,0x42cafaL) > .56
                            && (sample(seed,x,y,z,0x42cafafaL) & 511L) == 0L) {
                        material = owner.ore();
                    }
                    // Rare exposed crystal ore embedded directly in the lower
                    // surface rock (not the topsoil or inside empty caves).
                    if (owner.hasUndersideCrystals() && y > low
                            && materialAt[index - 1] == null && !exposed
                            && (sample(seed, x, y, z, 0x39f4) & 127L) == 0L) {
                        material = owner.ore();
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir() && level.setBlock(pos, material, Block.UPDATE_CLIENTS)) {
                        changed++;
                        if (material.is(net.deepcanion.skylands.registry.ModBlocks.MOLTEN_METAL.get())
                                && (sample(seed,x,y,z,0x5cf719L) & 15L)==0L)
                            level.scheduleTick(pos,level.getFluidState(pos).getType(),2);
                    }
                }

                // Fill the entire irregular lake bowl, rather than one liquid
                // tile above another ice tile. Only Glaciated lakes receive a
                // single ice surface. Every column stays chunk-local.
                for (ThemedIsland themed : column) {
                    SkyIslandShape.LakeSample lake = themed.column.lake();
                    if (lake == null || !lake.water()) continue;
                    // Two differently themed islands can overlap in X/Z. An
                    // ice cap from one island must never freeze a lake carved
                    // into the other theme's actual, visible terrain.
                    if (lake.frozen()) {
                        int floorIndex = lake.top() - SkyIslandShape.MIN_Y;
                        if (floorIndex < 0 || floorIndex >= materialAt.length
                                || materialAt[floorIndex] != themed.palette) continue;
                    }
                    int waterY=lake.waterY();
                    if(waterY>=level.getMaxBuildHeight()-2)continue;
                    BlockState fluid=lake.theme().equals("lavic")
                            ? net.deepcanion.skylands.registry.ModBlocks.MOLTEN_METAL.get().defaultBlockState()
                            : Blocks.WATER.defaultBlockState();
                    int highest=lake.frozen()?waterY-1:waterY;
                    for(int fy=lake.top()+1;fy<=highest;fy++) {
                        pos.set(x,fy,z);
                        if(level.getBlockState(pos).isAir()
                                && level.setBlock(pos,fluid,Block.UPDATE_CLIENTS)) {
                            changed++;
                            // Worldgen uses client-only block updates. Explicitly
                            // awaken outlet fluids so a new channel spills over
                            // the cliff without relying on neighboring chunks
                            // loading or a later random fluid update.
                            if (lake.outlet()) {
                                level.scheduleTick(pos, level.getFluidState(pos).getType(), 1);
                            }
                        }
                    }
                    if(lake.frozen()) {
                        pos.set(x,waterY,z);
                        if(level.getBlockState(pos).isAir()) {
                            long texture=sample(seed,x,waterY,z,0x41307fL);
                            BlockState cap=(texture & 15L)==0L
                                    ? Blocks.PACKED_ICE.defaultBlockState()
                                    : Blocks.ICE.defaultBlockState();
                            if(level.setBlock(pos,cap,Block.UPDATE_CLIENTS))changed++;
                        }
                    }
                }

                // Large cave pools share the same seeded shape across every
                // chunk. The surrounding basin and exit are part of the solid
                // terrain density, preventing molten metal from draining.
                for(ThemedIsland themed:column) {
                    SkyIslandShape.CaveSample cave=themed.column.cave();
                    if(cave==null || !cave.inside())continue;
                    // All molten blocks share one level surface; deeper
                    // irregular basins fill downward to their local rock floor.
                    for(int fy=cave.floorY()+1;fy<=cave.liquidY();fy++) {
                        pos.set(x,fy,z);
                        if(level.getBlockState(pos).isAir() && level.setBlock(pos,
                                net.deepcanion.skylands.registry.ModBlocks.MOLTEN_METAL.get().defaultBlockState(),
                                Block.UPDATE_CLIENTS))changed++;
                    }
                    break;
                }

                // Single-block, ceiling-attached Celestium ornaments on exposed
                // undersides, similar to grass/foliage on the upper surfaces.
                // A selected rock face places exactly ONE block underneath it;
                // never extend a spike into a multi-block dripstone-like chain.
                // The per-position seed hash makes decoration repeatable without
                // relying on feature random order or loading neighboring chunks.
                for (int y = low + 1; y <= high; y++) {
                    int index = y - SkyIslandShape.MIN_Y;
                    SkyTerrainPalette owner = materialAt[index];
                    if (owner == null || !owner.hasUndersideCrystals()
                            || materialAt[index - 1] != null) continue;
                    long choice = sample(seed, x, y, z, 0x4a82);
                    if ((choice & 7L) != 0L) continue; // ~12.5% of exposed underside faces
                    pos.set(x, y - 1, z);
                    if (!level.getBlockState(pos).isAir()) continue;
                    BlockState spike = owner.spike().setValue(
                            net.deepcanion.skylands.block.AttachedBlock.FACING,
                            Direction.from2DDataValue((int)((choice >>> 20) & 3L)));
                    if (level.setBlock(pos, spike, Block.UPDATE_CLIENTS)) changed++;
                }
            }
        }
        // Seed-aligned polygonal basalt colonnades merge with Golden cliff
        // rock before vegetation. Both rocks and protrusions are chunk-local.
        changed += SkyTerrainBasaltColumns.decorate(level,seed,chunkX,chunkZ,nearbyGoldenIslands);
        // Procedural spikes are built from island geometry and rendered only
        // inside this chunk. Place them before vegetation so roots integrate.
        changed += SkyTerrainLavicSpikes.decorate(level,seed,chunkX,chunkZ,nearbyLavicIslands);
        changed += SkyTerrainAridFormations.decorate(level,seed,chunkX,chunkZ,nearbyAridIslands);
        changed += SkyTerrainAridMegaArches.decorate(level,seed,chunkX,chunkZ,nearbyAridMegaArches);
        changed += SkyTerrainGlacialCrystals.decorate(level,seed,chunkX,chunkZ,nearbyGlaciatedIslands);
        changed += SkyTerrainLavicVolcanoes.decorate(level,seed,chunkX,chunkZ,nearbyLavicIslands);
        changed += SkyTerrainLavicChains.decorate(level,chunkX,chunkZ,nearbyLavicChains);
        // Ground foliage belongs to the normal Overworld vegetal-decoration
        // stage, which is already finished.  Only now decorate our own terrain:
        // never re-run vanilla/modded features or overwrite their trees below.
        if (changed > 0) {
            SkyTerrainFoliage.decorate(level, context.chunkGenerator(), seed, chunkX, chunkZ);
        }
        // Crystal and mangrove-leaf bridges may span entirely empty chunks.
        SkyTerrainCrystalVines.decorate(level,seed,chunkX,chunkZ);
        return changed > 0;
    }

    private record ThemedIsland(SkyIslandShape.Island island,
                                SkyTerrainPalette palette,
                                SkyIslandShape.Column column) {
        private ThemedIsland(SkyIslandShape.Island island, SkyTerrainPalette palette) {
            this(island, palette, null);
        }
    }

    /** Cheap deterministic per-column hash, independent of feature random order. */
    private static long sample(long seed, int x, int y, int z, long salt) {
        long h = seed ^ salt ^ (long)x * 0x9e3779b97f4a7c15L
                ^ (long)y * 0xbf58476d1ce4e5b9L ^ (long)z * 0x94d049bb133111ebL;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }

    private static final String[] THEMES = {
            "golden", "glaciated", "arid", "lavic", "swamp", "cherry"
    };
    private static final String[] BIOME_IDS = {
            "floating_golden_islands", "floating_glaciated_islands", "floating_arid_islands",
            "floating_lavic_islands", "floating_swamp_islands", "floating_cherry_grove_islands"
    };
    private static final List<ResourceKey<Biome>> KEYS = createKeys();

    private static List<ResourceKey<Biome>> createKeys() {
        ArrayList<ResourceKey<Biome>> keys = new ArrayList<>();
        for (String id : BIOME_IDS) {
            keys.add(ResourceKey.create(Registries.BIOME, SkyLands.id(id)));
        }
        return List.copyOf(keys);
    }
    static String themeOf(Holder<Biome> biome) {
        for (int i = 0; i < KEYS.size(); i++) {
            if (biome.is(KEYS.get(i))) return THEMES[i];
        }
        return null;
    }
}
