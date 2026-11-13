package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** Deterministic island-top vegetation, rare decoration, and organic ground patches. */
final class SkyTerrainFoliage {
    private static final String[] THEMES = { "golden", "glaciated", "arid", "lavic", "swamp", "cherry" };
    private static final SkyTerrainPalette[] PALETTES = makePalettes();
    private static final Block[] SURFACE_BLOCKS = makeSurfaceBlocks();
    // Worldgen runs after block registration. Avoid resolving IDs in hot scans.
    private static final Block ARID_GRASS = skyBlock("arid_grass_block");
    private static final Block ARID_MOSSY_SANDSTONE = skyBlock("mossy_sky_sandstone");
    private static final Block SKYROCK = skyBlock("skyrock");
    private static final Block CHERRY_MOSSY_SKYROCK = skyBlock("cherry_mossy_skyrock");
    private static final Block CHERRY_SHORT_GRASS = skyBlock("cherry_short_grass");
    private static final Block CHERRY_GRASS = skyBlock("cherry_grass");
    private static final Block GOLDEN_MOSSY_SKYROCK = skyBlock("golden_mossy_skyrock");
    private static final Block SWAMP_MOSSY_SKYROCK = skyBlock("swamp_mossy_skyrock");
    private static final Block GLACIATED_MOSSY_SKYROCK = skyBlock("glaciated_mossy_skyrock");
    private static final Block GLACIATED_SKYROCK = skyBlock("glaciated_skyrock");
    private static final int PLANT_ATTEMPTS = 26;

    private static Block skyBlock(String path) {
        return BuiltInRegistries.BLOCK.get(SkyLands.id(path));
    }

    private SkyTerrainFoliage() { }

    static void decorate(WorldGenLevel level, ChunkGenerator generator, long worldSeed,
                         int chunkX, int chunkZ) {
        RandomSource trees = RandomSource.create(SkyTerrainPatterns.mix(worldSeed,chunkX,0,chunkZ,0x3a4b7821L));
        RandomSource plants = RandomSource.create(SkyTerrainPatterns.mix(worldSeed,chunkX,0,chunkZ,0x71bf24d0L));
        RandomSource detail = RandomSource.create(SkyTerrainPatterns.mix(worldSeed,chunkX,0,chunkZ,0x5d7a034bL));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] treeCount = new int[6];
        boolean[] present = new boolean[6];

        // Large wetlands and frozen lakes now belong to the actual island
        // density mask. Do not run the old 4-block-radius postprocess ponds.
        // Mangroves are placed below, directly inside broad shallow wetlands.
        if (hasGlaciatedSurface(level,chunkX,chunkZ))
            SkyTerrainGlacialStreams.decorate(level,worldSeed,chunkX,chunkZ);
        SkyTerrainSwampGrowth.inWetlands(level,generator,worldSeed,chunkX,chunkZ);

        // Genuine giant Lavic pillars from the original mod. Check at the
        // center of a 3x3 write region so their 44x40 footprint fits; rare
        // landmark attempts happen BEFORE vegetation to avoid collisions.
        if (detail.nextInt(12) == 0) {
            Surface center = surfaceAt(level,cursor,chunkX+8,chunkZ+8,true);
            if (center != null && center.themeIndex() == 3
                    && SkyTerrainSubregions.lavic(worldSeed, center.position().getX(),
                            center.position().getZ()).forest() > .48)
                SkyTerrainStructures.decorateHuge(level,detail,center.position(),chunkX,chunkZ);
        }

        // Try small ruins, arches, stumps and pavilions BEFORE trees.
        // Their earlier 1-in-4 per-chunk gate made them overcrowd islands.
        // 1-in-13 is ~30.8% of that frequency; preserve the clearing retries
        // and separate RNG so forests and other worldgen remain unchanged.
        RandomSource structures=RandomSource.create(SkyTerrainPatterns.mix(
                worldSeed,chunkX,0,chunkZ,0x3acb1459L));
        if (structures.nextInt(13)==0) {
            // One fixed gate per chunk, NOT one coin per retry: repeated
            // clearing attempts would otherwise cancel out the halving.
            boolean cherryPavilionAllowed=SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(worldSeed,chunkX,0,chunkZ,0x4ce11L))<.5;
            // At most one decoration per chunk, but retry alternate clearings.
            for(int attempt=0;attempt<9;attempt++) {
                int x=chunkX+2+structures.nextInt(12);
                int z=chunkZ+2+structures.nextInt(12);
                Surface site=surfaceAt(level,cursor,x,z,true);
                if(site==null || (site.themeIndex()!=0 && site.themeIndex()!=5 && site.themeIndex()!=3))continue;
                if(site.themeIndex()==5 && !cherryPavilionAllowed)continue;
                if(SkyTerrainStructures.decorate(level,structures,site.themeIndex(),
                        site.position(),chunkX,chunkZ))break;
            }
        }

        // Sparse dune-only rock flecks, clustered Solero/Lunero cacti and
        // rare sealed oases. Reuses this existing single foliage pass.
        SkyTerrainAridDunes.decorate(level,worldSeed,chunkX,chunkZ);

        // Place Golden boulder outcrops before trees, so an otherwise suitable
        // rock site is not rejected by a canopy placed earlier in this pass.
        // Two independent candidates, weighted continuously by the same
        // Forest/Meadows/Plateau sampler used for vegetation and cliff columns.
        RandomSource boulders = RandomSource.create(SkyTerrainPatterns.mix(
                worldSeed, chunkX, 0, chunkZ, 0x4b72b29aL));
        for (int i = 0; i < 2; i++) {
            int x = chunkX + boulders.nextInt(16), z = chunkZ + boulders.nextInt(16);
            SkyTerrainSubregions.GoldenSample region = SkyTerrainSubregions.golden(worldSeed, x, z);
            if (boulders.nextDouble() >= SkyGoldenBoulderProfile.attemptChance(region)) continue;
            Surface site = surfaceAt(level, cursor, x, z, true);
            if (site != null && site.themeIndex() == 0) {
                present[0] = true;
                SkyTerrainBoulders.place(level, boulders, site.position(), chunkX, chunkZ);
            }
        }

        // Arid Grasslands reuse the same clustered Skyrock boulder geometry,
        // but with smaller focal rocks, fewer companions and no Golden moss.
        // The separate RNG leaves established Golden formations unchanged.
        RandomSource aridBoulders = RandomSource.create(SkyTerrainPatterns.mix(
                worldSeed, chunkX, 0, chunkZ, 0x41a71b22L));
        for (int i=0; i<1; i++) {
            int x=chunkX+aridBoulders.nextInt(16), z=chunkZ+aridBoulders.nextInt(16);
            var region=SkyTerrainSubregions.arid(worldSeed,x,z);
            if (region.grasslands() < .67 || aridBoulders.nextDouble() >= .19*region.grasslands())
                continue;
            Surface site=surfaceAt(level,cursor,x,z,true);
            if (site != null && site.themeIndex()==2
                    && level.getBlockState(site.position().below()).is(
                            ARID_GRASS)) {
                present[2]=true;
                SkyTerrainBoulders.placeArid(level,aridBoulders,site.position(),chunkX,chunkZ);
            }
        }

        // Smooth fields, sampled at each candidate instead of once per chunk.
        // Cherry and Swamp favor broad forest districts; Lavic stays savanna-like.
        // Tree collision checks still determine actual achieved density.
        for (int i = 0; i < 48; i++) {
            int x=chunkX+trees.nextInt(16),z=chunkZ+trees.nextInt(16);
            Surface surface=surfaceAt(level,cursor,x,z,true);
            if (surface == null) continue;
            int theme=surface.themeIndex();
            if(i>=35 && theme!=5)continue; // Additional attempts serve only cherry groves.
            present[theme] = true;
            SkyTerrainSubregions.GoldenSample golden = theme == 0
                    ? SkyTerrainSubregions.golden(worldSeed, x, z) : null;
            SkyTerrainSubregions.AridSample arid = theme == 2
                    ? SkyTerrainSubregions.arid(worldSeed, x, z) : null;
            SkyTerrainSubregions.LavicSample lavic = theme == 3
                    ? SkyTerrainSubregions.lavic(worldSeed, x, z) : null;
            SkyTerrainSubregions.GlaciatedSample glaciated = theme == 1
                    ? SkyTerrainSubregions.glaciated(worldSeed,x,z) : null;
            SkyTerrainSubregions.CherrySample cherry = theme == 5
                    ? SkyTerrainSubregions.cherry(worldSeed,x,z) : null;
            SkyTerrainSubregions.SwampSample swamp = theme == 4
                    ? SkyTerrainSubregions.swamp(worldSeed,x,z) : null;
            double forest = theme == 0 ? 0.0 : forestiness(worldSeed, x, z, 112);
            boolean dense = forest > switch (theme) {
                case 1 -> .46; case 4 -> .44; case 5 -> .40; default -> .50;
            };
            int cap = switch(theme) {
                // A shared ceiling avoids starving a Forest patch merely
                // because a few Meadows trees were tried first in this chunk.
                case 0 -> 5;
                case 1 -> glaciated.plains()>.62 || glaciated.crystals()>.62
                        ? 1 : dense ? 4 : 2;
                case 2 -> 1;
                case 3 -> lavic.forest() > .54 ? 8 : lavic.barrens() > .55 ? 4 : 2;
                case 4 -> swamp.mire()>.62 ? 2 : swamp.thickets()>.42 ? 7 : dense ? 6 : 2;
                case 5 -> cherry.meadows()>.64 ? 1 : cherry.thickets()>.53 ? 6
                        : dense ? 11 : 8;
                default -> 0;
            };
            if(treeCount[theme]>=cap) continue;
            float chance=switch(theme) {
                // Forest cores grow dense, Meadows retain scattered trees,
                // while the future Plateau landform already has sparse trees.
                // Continuous weights soften all region boundaries.
                case 0 -> (float)(.20 + .60 * golden.forest() - .15 * golden.plateau());
                case 1 -> (float)((dense ? .62 : .18)*glaciated.forest()
                        +.055*glaciated.plains()+.017*glaciated.crystals());
                case 2 -> (float)(.18 * arid.grasslands()
                        + .045 * arid.dunes() + .028 * arid.rockyHighlands());
                case 3 -> (float)(.28 * lavic.plateau() + .88 * lavic.forest()
                        + .54 * lavic.barrens() + .07 * lavic.rifts());
                case 4 -> (float)((dense ? .82 : .23)*swamp.wetlands()
                        +.10*swamp.mire()+.91*swamp.thickets());
                case 5 -> (float)((dense ? .99 : .78)*cherry.grove()
                        +.055*cherry.meadows()+.51*cherry.thickets());
                default -> 0F;
            };
            if(trees.nextFloat()>=chance) continue;
            boolean placed;
            if(theme==2) {
                placed=SkyTerrainCacti.place(level, trees, surface.position());
            } else if(theme==3) {
                double leafless = .78 * lavic.plateau() + .07 * lavic.forest()
                        + .98 * lavic.barrens() + .96 * lavic.rifts();
                placed=SkyTerrainTrees.placeLavic(level,trees,surface.position(),
                        chunkX,chunkZ,leafless);
            } else {
                placed=SkyTerrainTrees.place(level,trees,theme,surface.position(),chunkX,chunkZ);
            }
            if(placed) {
                treeCount[theme]++;
                if(theme==5 && trees.nextInt(11)==0) {
                    attachCherryBeeNest(level,trees,surface.position());
                }
            }
        }

        // Decorative structures only on their own theme's upper surface.
        for (int i=0;i<2;i++) {
            Surface s=surfaceAt(level,cursor,chunkX+detail.nextInt(16),chunkZ+detail.nextInt(16),true);
            if(s!=null) {
                present[s.themeIndex()] = true;
            }
        }

        for(int i=0;i<PLANT_ATTEMPTS;i++) {
            int x=chunkX+plants.nextInt(16), z=chunkZ+plants.nextInt(16);
            Surface s=surfaceAt(level,cursor,x,z,true);
            if(s==null) continue;
            present[s.themeIndex()] = true;
            BlockPos target=s.position();
            SkyTerrainSubregions.GoldenSample golden = s.themeIndex()==0
                    ? SkyTerrainSubregions.golden(worldSeed,x,z) : null;
            SkyTerrainSubregions.AridSample arid = s.themeIndex()==2
                    ? SkyTerrainSubregions.arid(worldSeed,x,z) : null;
            SkyTerrainSubregions.LavicSample lavic = s.themeIndex()==3
                    ? SkyTerrainSubregions.lavic(worldSeed,x,z) : null;
            SkyTerrainSubregions.GlaciatedSample glaciated = s.themeIndex()==1
                    ? SkyTerrainSubregions.glaciated(worldSeed,x,z) : null;
            SkyTerrainSubregions.CherrySample cherry = s.themeIndex()==5
                    ? SkyTerrainSubregions.cherry(worldSeed,x,z) : null;
            SkyTerrainSubregions.SwampSample swamp = s.themeIndex()==4
                    ? SkyTerrainSubregions.swamp(worldSeed,x,z) : null;
            // Plateau districts stay naturally sparsely vegetated even before
            // the actual elevation/terrace geometry arrives in Stage 2.
            if(golden!=null && plants.nextDouble() > 1.0 - .68*golden.plateau())continue;
            if(arid!=null && plants.nextDouble() >
                    arid.grasslands() + .13*arid.dunes() + .52*arid.rockyHighlands())continue;
            if(lavic!=null && plants.nextDouble() >
                    .64*lavic.plateau() + .78*lavic.forest() + .09*lavic.barrens()
                            + .025*lavic.rifts())continue;
            if(glaciated!=null && plants.nextDouble() >
                    .89*glaciated.forest()+.29*glaciated.plains()+.055*glaciated.crystals())continue;
            if(cherry!=null && plants.nextDouble() >
                    .92*cherry.grove()+.95*cherry.meadows()+.75*cherry.thickets())continue;
            if(swamp!=null && plants.nextDouble() >
                    .83*swamp.wetlands()+.22*swamp.mire()+.88*swamp.thickets())continue;
            BlockState plant=golden!=null
                    ? SkyTerrainVegetation.selectGolden(plants,golden)
                    : SkyTerrainVegetation.select(s.themeIndex(),plants);
            if(!plant.canSurvive(level,target))continue;
            if(plant.getBlock() instanceof DoublePlantBlock) {
                BlockPos upper=target.above();
                if(upper.getY()>=level.getMaxBuildHeight() || !level.ensureCanWrite(upper)
                        || !level.getBlockState(upper).isAir())continue;
                DoublePlantBlock.placeAt(level,plant,target,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            } else level.setBlock(target,plant,Block.UPDATE_CLIENTS);
        }

        // Extra plants on Golden Meadows / Glaciated Frozen Plains: bounded
        // attempts, reused surface scan, same original vegetation palette.
        if (present[0] || present[1]) for (int n=0;n<18;n++) {
            int x=chunkX+plants.nextInt(16),z=chunkZ+plants.nextInt(16);
            Surface patch=surfaceAt(level,cursor,x,z,true);
            if(patch==null)continue;
            double weight=patch.themeIndex()==0
                    ? SkyTerrainSubregions.golden(worldSeed,x,z).meadows()
                    : patch.themeIndex()==1
                    ? SkyTerrainSubregions.glaciated(worldSeed,x,z).plains():0.0;
            if(weight<.25 || plants.nextDouble()>=.62*weight)continue;
            BlockState plant=patch.themeIndex()==0
                    ? SkyTerrainVegetation.selectGolden(plants,SkyTerrainSubregions.golden(worldSeed,x,z))
                    : SkyTerrainVegetation.select(1,plants);
            if(!plant.canSurvive(level,patch.position()))continue;
            if(plant.getBlock() instanceof DoublePlantBlock) {
                if(!level.ensureCanWrite(patch.position().above())
                        ||!level.getBlockState(patch.position().above()).isAir())continue;
                DoublePlantBlock.placeAt(level,plant,patch.position(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            } else level.setBlock(patch.position(),plant,Block.UPDATE_CLIENTS);
        }

        // Dedicated, small and scattered brewing-ingredient clusters. Kept
        // separate from the regular weighted grass/flower palette.
        if (present[0] || present[1] || present[3])
            temperaturePlantClusters(level, worldSeed, cursor, chunkX, chunkZ);

        // These run LAST: top block swaps must not disrupt tree/grass detection.
        if(present[5]) cherryDetails(level,detail,worldSeed,cursor,chunkX,chunkZ);
        if(present[0]) goldenDetails(level,detail,worldSeed,cursor,chunkX,chunkZ);
        if(present[2]) {
            aridDetails(level,worldSeed,cursor,chunkX,chunkZ);
            aridBadlandsVegetation(level,worldSeed,cursor,chunkX,chunkZ);
        }
        if(present[4]) swampDetails(level,worldSeed,cursor,chunkX,chunkZ);
        if(present[1]) glaciatedDetails(level,worldSeed,cursor,chunkX,chunkZ);
        if(present[1]) frozenPlainsIce(level,worldSeed,cursor,chunkX,chunkZ);
        if(present[4]) SkyTerrainSwampVines.decorate(level,worldSeed,chunkX,chunkZ);
        if(present[3]) {
            SkyTerrainMoltenPools.decorate(level,worldSeed,chunkX,chunkZ);
        }
        if(present[4]) SkyTerrainWetlandFrogs.decorate(level,worldSeed,chunkX,chunkZ);
        // Cherry lake lilies and shoreline sugar cane, after tree placement.
        SkyTerrainLakeDecorations.decorate(level,worldSeed,chunkX,chunkZ);
        if(present[5]) SkyTerrainCherryFalls.decorate(level,worldSeed,chunkX,chunkZ);
        if(present[0] || present[1] || present[5])
            hillsideSprings(level,worldSeed,cursor,chunkX,chunkZ);
        // Snow comes after tree/plant placement, including handmade canopies.
        if(present[1] || hasGlaciatedSurface(level,chunkX,chunkZ))
            SkyTerrainGlacialSnow.decorate(level,worldSeed,chunkX,chunkZ);
    }

    private static void temperaturePlantClusters(WorldGenLevel level, long seed,
                    BlockPos.MutableBlockPos cursor, int chunkX, int chunkZ) {
        RandomSource random=RandomSource.create(SkyTerrainPatterns.mix(seed,chunkX,0,chunkZ,0x63b8f44dL));
        // Golden: forest and treed meadow, not exposed plateau.
        // Glaciated: forest and occasional treed frozen plains, never crystal glaciers.
        // Lavic: leafy burnt forest and its plateau transitions, never leafless barrens/rifts.
        // Per-chunk gates keep these useful brewing plants uncommon even in suitable regions.
        for(int theme : new int[] {0,1,3}) {
            int rarity=theme==0?7:theme==1?8:15;
            if(random.nextInt(rarity)!=0)continue;
            int centerX=chunkX+4+random.nextInt(8),centerZ=chunkZ+4+random.nextInt(8);
            int desired=1+random.nextInt(theme==1?5:3);
            int placed=0;
            for(int attempt=0;attempt<desired*9 && placed<desired;attempt++) {
                int x=centerX+random.nextInt(7)-3,z=centerZ+random.nextInt(7)-3;
                if(x<chunkX||x>=chunkX+16||z<chunkZ||z>=chunkZ+16)continue;
                Surface site=surfaceAt(level,cursor,x,z,true);
                if(site==null||site.themeIndex()!=theme)continue;
                if(theme==0) {
                    var region=SkyTerrainSubregions.golden(seed,x,z);
                    if(region.plateau()>.33 || !(region.forest()>.28 || region.meadows()>.68))continue;
                } else if(theme==1) {
                    var region=SkyTerrainSubregions.glaciated(seed,x,z);
                    if(region.crystals()>.32 || !(region.forest()>.35 || region.plains()>.64))continue;
                } else {
                    var region=SkyTerrainSubregions.lavic(seed,x,z);
                    // Mirror the same leafless-selection weights used by
                    // SkyTerrainTrees.placeLavic; reject regions dominated by
                    // barren burnt trees even if near a forest boundary.
                    double leafless=.78*region.plateau()+.07*region.forest()
                            +.98*region.barrens()+.96*region.rifts();
                    if(region.barrens()>.20 || region.rifts()>.16 || leafless>.46
                            || !(region.forest()>.36 || (region.plateau()>.45 && region.forest()>.26)))continue;
                }
                BlockState plant=theme==1
                        ? (random.nextBoolean() ? net.deepcanion.skylands.registry.ModBlocks.FROST_BERRY_BUSH.get()
                                : net.deepcanion.skylands.registry.ModBlocks.LARGE_FROST_BERRY_BUSH.get()).defaultBlockState()
                        : net.deepcanion.skylands.registry.ModBlocks.HOT_RADISH_PLANT.get().defaultBlockState();
                if(!plant.canSurvive(level,site.position()))continue;
                if(level.setBlock(site.position(),plant,Block.UPDATE_CLIENTS))placed++;
            }
        }
    }

    private static boolean hasGlaciatedSurface(WorldGenLevel level,int chunkX,int chunkZ) {
        BlockPos.MutableBlockPos probe=new BlockPos.MutableBlockPos();
        for(int dx=4;dx<=12;dx+=4)for(int dz=4;dz<=12;dz+=4) {
            Surface s=surfaceAt(level,probe,chunkX+dx,chunkZ+dz,false);
            if(s!=null && s.themeIndex()==1)return true;
        }
        return false;
    }

    private static void cherryDetails(WorldGenLevel level,RandomSource random,long seed,
                                      BlockPos.MutableBlockPos cursor,int chunkX,int chunkZ) {
        BlockState moss=CHERRY_MOSSY_SKYROCK.defaultBlockState();
        // An intermixed mosaic of vanilla Grass and Cherry Grass, NOT a grid of
        // isolated solid grass patches. Large-scale variation shifts which one
        // dominates locally, while fine noise and a per-block hash keep both
        // grasses present even inside the opposite type's 'district'.
        // Independent world-coordinate sampling makes chunk seams invisible.
        for(int x=chunkX;x<chunkX+16;x++) for(int z=chunkZ;z<chunkZ+16;z++) {
            double broad=SkyTerrainPatterns.noise2(seed,x,z,86,0x342faL);
            double detail=SkyTerrainPatterns.noise2(seed,x,z,9,0x342fbL);
            var district=SkyTerrainSubregions.cherry(seed,x,z);
            double normalGrassChance=Math.max(.09,Math.min(.84,
                    (.13+.72*broad+.29*(detail-.5))*(1.0-.34*district.thickets())));
            double choice=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x234L));
            if(choice>=normalGrassChance)continue;
            Surface s=surfaceAt(level,cursor,x,z,false);
            if(s==null || s.themeIndex()!=5)continue;
            level.setBlock(s.position().below(),Blocks.GRASS_BLOCK.defaultBlockState(),Block.UPDATE_CLIENTS);
        }
        // Small moss flecks visible through surface grass, matching the Swamp
        // top-layer style. The rock-wall moss is separately thinned in the core
        // terrain feature, without weakening this surface decoration.
        for(int i=0;i<75;i++) {
            int x=chunkX+random.nextInt(16),z=chunkZ+random.nextInt(16);
            if(SkyTerrainPatterns.patch(seed,x,z,37,0x824aL)<=.28)continue;
            Surface s=cherryGround(level,cursor,x,z);
            if(s==null||!level.getBlockState(s.position()).isAir())continue;
            if(random.nextInt(5)==0)level.setBlock(s.position().below(),moss,Block.UPDATE_CLIENTS);
        }
        // Existing scattered bushes stay in groves; Blossom Thickets get
        // more substantial, low leaf-brush pockets. Meadow cores stay open.
        BlockState leaf=Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
        for(int cluster=0;cluster<3;cluster++) {
            int cx=chunkX+random.nextInt(16),cz=chunkZ+random.nextInt(16);
            var region=SkyTerrainSubregions.cherry(seed,cx,cz);
            double chance=.15*region.grove()+.82*region.thickets()+.015*region.meadows();
            if(random.nextDouble()>chance)continue;
            int remaining=region.thickets()>.5 ? 9+random.nextInt(9) : 4+random.nextInt(7);
            for(int i=0;i<remaining*6 && remaining>0;i++) {
                int x=cx+random.nextInt(13)-6,z=cz+random.nextInt(13)-6;
                if(x<chunkX||x>=chunkX+16||z<chunkZ||z>=chunkZ+16)continue;
                if(SkyTerrainSubregions.cherry(seed,x,z).meadows()>.55)continue;
                Surface s=surfaceAt(level,cursor,x,z,true);
                if(s==null||s.themeIndex()!=5)continue;
                if(level.setBlock(s.position(),leaf,Block.UPDATE_CLIENTS)) {
                    remaining--;
                    if(region.thickets()>.55 && random.nextFloat()<.28F
                            && level.getBlockState(s.position().above()).isAir())
                        level.setBlock(s.position().above(),leaf,Block.UPDATE_CLIENTS);
                }
            }
        }
        // One world-aligned pass for BOTH petals and the lush understory.
        // Surface lookups are expensive at Sky Terrain heights; never scan
        // twice for the same candidate column just to add extra meadow grass.
        for(int x=chunkX;x<chunkX+16;x++)for(int z=chunkZ;z<chunkZ+16;z++) {
            var region=SkyTerrainSubregions.cherry(seed,x,z);
            double patch=SkyTerrainPatterns.noise2(seed,x,z,36,0x642d91L);
            double petalChance=(.24+.22*(patch-.5))*region.grove()
                    +(.73+.12*(patch-.5))*region.meadows()+.18*region.thickets();
            long petalRoll=SkyTerrainPatterns.mix(seed,x,0,z,0x642d92L);
            boolean makePetals=SkyTerrainPatterns.unit(petalRoll)<petalChance;
            long plantRoll=SkyTerrainPatterns.mix(seed,x,0,z,0x642da7L);
            double plantChance=.20*region.grove()+.77*region.meadows()+.26*region.thickets();
            if(!makePetals && SkyTerrainPatterns.unit(plantRoll)>=plantChance)continue;
            Surface s=cherryGround(level,cursor,x,z);
            if(s==null)continue;
            BlockPos at=s.position();
            BlockState existing=level.getBlockState(at);
            if(makePetals && (existing.isAir()
                    ||existing.is(CHERRY_SHORT_GRASS)
                    ||existing.is(CHERRY_GRASS))) {
                double fullness=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,1,z,0x642d93L));
                int amount=fullness<.13?1:fullness<.37?2:fullness<.70?3:4;
                int facing=(int)(4*SkyTerrainPatterns.unit(
                        SkyTerrainPatterns.mix(seed,x,2,z,0x642d94L)));
                BlockState petals=Blocks.PINK_PETALS.defaultBlockState()
                        .setValue(BlockStateProperties.FLOWER_AMOUNT,amount)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.from2DDataValue(facing));
                if(petals.canSurvive(level,at))level.setBlock(at,petals,Block.UPDATE_CLIENTS);
            } else if(existing.isAir() && SkyTerrainPatterns.unit(plantRoll)<plantChance) {
                double roll=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,1,z,0x642da8L));
                BlockState plant=roll<.075 ? Blocks.RED_TULIP.defaultBlockState()
                        : roll<.15 ? Blocks.PINK_TULIP.defaultBlockState()
                        : roll<.22 ? Blocks.WHITE_TULIP.defaultBlockState()
                        : roll<.29 ? Blocks.ORANGE_TULIP.defaultBlockState()
                        : roll<.56 ? Blocks.TALL_GRASS.defaultBlockState()
                        : Blocks.SHORT_GRASS.defaultBlockState();
                if(!plant.canSurvive(level,at))continue;
                if(plant.getBlock() instanceof DoublePlantBlock) {
                    if(at.getY()+1>=level.getMaxBuildHeight()||!level.ensureCanWrite(at.above())
                            ||!level.getBlockState(at.above()).isAir())continue;
                    DoublePlantBlock.placeAt(level,plant,at,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                } else level.setBlock(at,plant,Block.UPDATE_CLIENTS);
            }
        }
        // Bamboo is concentrated into Blossom Thickets, with a little of the
        // original occasional bamboo left in normal Cherry Groves.
        for(int group=0;group<3;group++) {
            int cx=chunkX+random.nextInt(16),cz=chunkZ+random.nextInt(16);
            var region=SkyTerrainSubregions.cherry(seed,cx,cz);
            if(random.nextDouble()>.17*region.grove()+.77*region.thickets()+.015*region.meadows())continue;
            for(int n=0;n<22;n++) {
                int x=cx+random.nextInt(9)-4,z=cz+random.nextInt(9)-4;
                if(x<chunkX||x>=chunkX+16||z<chunkZ||z>=chunkZ+16)continue;
                if(SkyTerrainSubregions.cherry(seed,x,z).meadows()>.63)continue;
                Surface s=cherryGround(level,cursor,x,z);
                if(s==null||!level.getBlockState(s.position()).isAir()||random.nextFloat()>.59F)continue;
                BlockState bamboo=Blocks.BAMBOO.defaultBlockState();
                if(!bamboo.canSurvive(level,s.position()))continue;
                int height=3+random.nextInt(region.thickets()>.54 ? 9 : 6);
                for(int y=0;y<height;y++) {
                    BlockPos at=s.position().above(y);
                    if(at.getY()>=level.getMaxBuildHeight()||!level.ensureCanWrite(at)
                            ||!level.getBlockState(at).isAir())break;
                    BlockState stalk=bamboo;
                    if(y==height-1)stalk=stalk.setValue(BambooStalkBlock.LEAVES,BambooLeaves.LARGE);
                    else if(y==height-2)stalk=stalk.setValue(BambooStalkBlock.LEAVES,BambooLeaves.SMALL);
                    level.setBlock(at,stalk,Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void goldenDetails(WorldGenLevel level,RandomSource random,long seed,
                                      BlockPos.MutableBlockPos cursor,int chunkX,int chunkZ) {
        BlockState moss=GOLDEN_MOSSY_SKYROCK.defaultBlockState();
        for(int x=chunkX;x<chunkX+16;x++)for(int z=chunkZ;z<chunkZ+16;z++) {
            double patch=SkyTerrainPatterns.patch(seed,x,z,48,0x724eL);
            SkyTerrainSubregions.GoldenSample region = SkyTerrainSubregions.golden(seed,x,z);
            if(patch<=0 && region.forest()<.18)continue;
            Surface s=surfaceAt(level,cursor,x,z,false);
            if(s==null||s.themeIndex()!=0)continue;
            BlockPos ground=s.position().below();
            // Forest interiors have persistent, irregular Podzol coverage;
            // Meadows retain grass, and future Plateaus largely keep bare grass.
            // No chunk-level decision: neighbouring chunks share the same edge.
            double roll=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x526aL));
            boolean podzol=region.forest()>.20 && (patch>.05 || region.forest()>.72)
                    && roll<.18+.77*region.forest();
            if(podzol) {
                level.setBlock(ground,roll<.055?Blocks.COARSE_DIRT.defaultBlockState():Blocks.PODZOL.defaultBlockState(),Block.UPDATE_CLIENTS);
            } else if(patch>.42 && random.nextInt(7)==0 && level.getBlockState(s.position()).isAir()) {
                level.setBlock(ground,moss,Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Existing Mossy Sky Sandstone forms rare irregular Arid surface flecks. */
    private static void aridDetails(WorldGenLevel level,long seed,
                                    BlockPos.MutableBlockPos cursor,int chunkX,int chunkZ) {
        BlockState moss=ARID_MOSSY_SANDSTONE.defaultBlockState();
        for(int x=chunkX;x<chunkX+16;x++)for(int z=chunkZ;z<chunkZ+16;z++) {
            if(SkyTerrainPatterns.patch(seed,x,z,44,0x6ca51L)<.45)continue;
            if(SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x6ca521L))>.23)continue;
            Surface s=surfaceAt(level,cursor,x,z,false);
            if(s==null||s.themeIndex()!=2)continue;
            if(level.getBlockState(s.position().below()).is(PALETTES[2].top().getBlock()))
                level.setBlock(s.position().below(),moss,Block.UPDATE_CLIENTS);
        }
    }

    private static void aridBadlandsVegetation(WorldGenLevel level,long seed,
                                    BlockPos.MutableBlockPos cursor,int chunkX,int chunkZ) {
        // One sparse, bounded pass. Surface sampling stays chunk-local and no
        // plants are placed on structures or empty floating air.
        for(int i=0;i<30;i++) {
            long h=SkyTerrainPatterns.mix(seed,chunkX+i,0,chunkZ,0x72ac38L);
            int x=chunkX+(int)(SkyTerrainPatterns.unit(h)*16);
            int z=chunkZ+(int)(SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(h,0,0,0,0x41a7L))*16);
            var r=SkyTerrainSubregions.arid(seed,x,z);
            if(r.rockyHighlands()<.38)continue;
            Surface s=surfaceAt(level,cursor,x,z,true);
            if(s==null || s.themeIndex()!=2)continue;
            BlockState ground=level.getBlockState(s.position().below());
            double roll=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(h,1,0,0,0x41a8L));
            if(ground.is(Blocks.SAND)||ground.is(Blocks.RED_SAND)) {
                if(roll<.12) {
                    RandomSource rng=RandomSource.create(h);
                    SkyTerrainCacti.place(level,rng,s.position());
                } else if(roll<.72) {
                    BlockState dry=Blocks.DEAD_BUSH.defaultBlockState();
                    if(dry.canSurvive(level,s.position()))
                        level.setBlock(s.position(),dry,Block.UPDATE_CLIENTS);
                }
            } else if(ground.is(PALETTES[2].top().getBlock()) && roll<.56) {
                BlockState plant=SkyTerrainVegetation.select(2,RandomSource.create(h));
                if(plant.canSurvive(level,s.position()))
                    level.setBlock(s.position(),plant,Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void swampDetails(WorldGenLevel level,long seed,
                                     BlockPos.MutableBlockPos cursor,int chunkX,int chunkZ) {
        BlockState moss=SWAMP_MOSSY_SKYROCK.defaultBlockState();
        BlockState orchid=Blocks.BLUE_ORCHID.defaultBlockState();
        for(int x=chunkX;x<chunkX+16;x++)for(int z=chunkZ;z<chunkZ+16;z++) {
            double mossPatch=SkyTerrainPatterns.patch(seed,x,z,40,0x85c2L);
            double flowerPatch=SkyTerrainPatterns.patch(seed,x,z,27,0x5fa9eL);
            if(mossPatch<.36 && flowerPatch<.48)continue;
            Surface s=surfaceAt(level,cursor,x,z,true);
            if(s==null||s.themeIndex()!=4)continue;
            if(mossPatch>=.36 && SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x53faL))<.28)
                level.setBlock(s.position().below(),moss,Block.UPDATE_CLIENTS);
            if(flowerPatch>.48
                    && SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x5fa9fL))<.25
                    && level.getBlockState(s.position()).isAir()
                    && orchid.canSurvive(level,s.position()))
                level.setBlock(s.position(),orchid,Block.UPDATE_CLIENTS);
        }
    }

    /** Small, scattered Glaciated Mossy Skyrock patches, deliberately modeled
     * on the existing Swamp patches without altering the Swamp behavior. */
    private static void glaciatedDetails(WorldGenLevel level,long seed,
                                        BlockPos.MutableBlockPos cursor,int chunkX,int chunkZ) {
        BlockState moss=GLACIATED_MOSSY_SKYROCK.defaultBlockState();
        for(int x=chunkX;x<chunkX+16;x++)for(int z=chunkZ;z<chunkZ+16;z++) {
            if(SkyTerrainPatterns.patch(seed,x,z,42,0x9a31L)<.36)continue;
            Surface s=surfaceAt(level,cursor,x,z,true);
            if(s==null||s.themeIndex()!=1)continue;
            if(SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,x,0,z,0x629faL))<.23)
                level.setBlock(s.position().below(),moss,Block.UPDATE_CLIENTS);
        }
    }

    /** Occasional tiny packed-ice outcrops in open Frozen Plains, not giant glaciers. */
    private static void frozenPlainsIce(WorldGenLevel level,long seed,
                                       BlockPos.MutableBlockPos cursor,int cx,int cz) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,cx,0,cz,0x3170c31L));
        for(int i=0;i<5;i++) {
            int x=cx+2+rng.nextInt(12),z=cz+2+rng.nextInt(12);
            if(SkyTerrainSubregions.glaciated(seed,x,z).plains()<.62)continue;
            Surface site=surfaceAt(level,cursor,x,z,true);
            if(site==null || site.themeIndex()!=1)continue;
            BlockPos at=site.position();
            if(!level.getBlockState(at.below()).is(SURFACE_BLOCKS[1]))continue;
            int height=1+rng.nextInt(3);
            for(int h=0;h<height;h++) {
                BlockPos p=at.above(h);
                if(!level.ensureCanWrite(p)||!level.getBlockState(p).isAir())break;
                level.setBlock(p, rng.nextInt(7)==0?Blocks.BLUE_ICE.defaultBlockState()
                        :Blocks.PACKED_ICE.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Small vanilla-style cliff-face springs, one source only, low per-chunk cap.
     * Require an exposed natural rock face and strong backing; no cave flooding. */
    private static void hillsideSprings(WorldGenLevel level,long seed,
                                        BlockPos.MutableBlockPos cursor,int cx,int cz) {
        RandomSource rng=RandomSource.create(SkyTerrainPatterns.mix(seed,cx,0,cz,0x3170c32L));
        if(rng.nextInt(6)!=0)return;
        for(int attempt=0;attempt<10;attempt++) {
            int x=cx+3+rng.nextInt(10),z=cz+3+rng.nextInt(10);
            Surface top=surfaceAt(level,cursor,x,z,false);
            if(top==null)top=cherryGround(level,cursor,x,z);
            if(top==null || (top.themeIndex()!=0 && top.themeIndex()!=1
                    && top.themeIndex()!=5))continue;
            int y=top.position().getY()-3-rng.nextInt(19);
            if(y<SkyIslandShape.MIN_Y+10)continue;
            BlockPos p=new BlockPos(x,y,z);
            if(!level.ensureCanWrite(p)||!naturalSpringRock(level.getBlockState(p),top.themeIndex()))continue;
            if(!naturalSpringRock(level.getBlockState(p.above()),top.themeIndex())
                    ||!naturalSpringRock(level.getBlockState(p.below()),top.themeIndex()))continue;
            for(Direction dir:Direction.Plane.HORIZONTAL) {
                BlockPos out=p.relative(dir);
                if(!level.ensureCanWrite(out) || !level.getBlockState(out).isAir()
                        || !level.getBlockState(out.below(2)).isAir()
                        || !level.getBlockState(out.below(5)).isAir())continue;
                // Side walls stay solid except for the one naturally exposed face.
                if(!naturalSpringRock(level.getBlockState(p.relative(dir.getOpposite())),top.themeIndex())
                        ||!naturalSpringRock(level.getBlockState(p.relative(dir.getClockWise())),top.themeIndex())
                        ||!naturalSpringRock(level.getBlockState(p.relative(dir.getCounterClockWise())),top.themeIndex()))continue;
                if(level.setBlock(p,Blocks.WATER.defaultBlockState(),Block.UPDATE_CLIENTS))
                    level.scheduleTick(p,level.getFluidState(p).getType(),1);
                return;
            }
        }
    }

    private static boolean naturalSpringRock(BlockState s,int theme) {
        return switch(theme) {
            case 0,5 -> s.is(SKYROCK) || s.is(CHERRY_MOSSY_SKYROCK)
                    || s.is(GOLDEN_MOSSY_SKYROCK);
            case 1 -> s.is(GLACIATED_MOSSY_SKYROCK)
                    || s.is(GLACIATED_SKYROCK);
            default -> false;
        };
    }
    private static void attachCherryBeeNest(WorldGenLevel level,RandomSource random,BlockPos origin) {
        for(int i=0;i<32;i++) {
            BlockPos log=origin.offset(random.nextInt(9)-4,2+random.nextInt(6),random.nextInt(9)-4);
            if(!level.getBlockState(log).is(Blocks.CHERRY_LOG))continue;
            Direction face=Direction.from2DDataValue(random.nextInt(4));
            BlockPos nest=log.relative(face);
            if(!level.ensureCanWrite(nest) || !level.getBlockState(nest).isAir())continue;
            BlockState state=Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING,face);
            if(!level.setBlock(nest,state,Block.UPDATE_CLIENTS))continue;
            if(level.getBlockEntity(nest) instanceof BeehiveBlockEntity hive) {
                int count=1+random.nextInt(2);
                for(int b=0;b<count;b++)hive.storeBee(BeehiveBlockEntity.Occupant.create(random.nextInt(300)));
            }
            return;
        }
    }

    // Vanilla grass patches should also support pink petals and bamboo, but
    // are only considered when the Skyrock beneath proves we are on a sky island.
    private static Surface cherryGround(WorldGenLevel level,BlockPos.MutableBlockPos cursor,int x,int z) {
        for(int y=SkyIslandShape.MAX_Y;y>=SkyIslandShape.MIN_Y;y--) {
            cursor.set(x,y,z);
            BlockState ground=level.getBlockState(cursor);
            if(ground.is(PALETTES[5].top().getBlock()) || ground.is(Blocks.GRASS_BLOCK)) {
                if(ground.is(Blocks.GRASS_BLOCK)) {
                    // A Skyrock core below the grass identifies our converted island patch.
                    BlockPos below=cursor.below();
                    if(!level.getBlockState(below).is(SKYROCK)
                            && !level.getBlockState(below).is(CHERRY_MOSSY_SKYROCK))continue;
                }
                return new Surface(cursor.above().immutable(),5);
            }
        }
        return null;
    }

    /** Find topsoil even if trees already cover it (for ground-color patches). */
    private static Surface surfaceAt(WorldGenLevel level,BlockPos.MutableBlockPos cursor,
                                     int x,int z,boolean requireAir) {
        for(int y=SkyIslandShape.MAX_Y;y>=SkyIslandShape.MIN_Y;y--) {
            cursor.set(x,y,z);
            BlockState state=level.getBlockState(cursor);
            // Vanilla sand also appears at Golden shores. Only recognize it
            // as Arid dune ground when its supporting bed is Sky Sandstone.
            if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                    || state.is(ARID_MOSSY_SANDSTONE)
                    || state.is(PALETTES[2].core().getBlock())
                    || SkyAridTerracottaPalette.isTerracotta(state)) {
                BlockState under=level.getBlockState(cursor.below());
                if(under.is(PALETTES[2].core().getBlock())
                        || SkyAridTerracottaPalette.isTerracotta(under)
                        || under.is(ARID_MOSSY_SANDSTONE)) {
                    BlockPos above=cursor.above();
                    if(requireAir && !level.getBlockState(above).isAir())return null;
                    return new Surface(above,2);
                }
            }
            for(int i=0;i<SURFACE_BLOCKS.length;i++) {
                if(!state.is(SURFACE_BLOCKS[i]))continue;
                BlockPos above=cursor.above();
                if(requireAir && !level.getBlockState(above).isAir())return null;
                return new Surface(above,i);
            }
        }
        return null;
    }
    private static SkyTerrainPalette[] makePalettes() {
        SkyTerrainPalette[] palettes=new SkyTerrainPalette[THEMES.length];
        for(int i=0;i<THEMES.length;i++)palettes[i]=SkyTerrainPalette.forTheme(THEMES[i]);
        return palettes;
    }
    private static Block[] makeSurfaceBlocks() {
        Block[] blocks=new Block[PALETTES.length];
        for(int i=0;i<PALETTES.length;i++)blocks[i]=PALETTES[i].top().getBlock();
        return blocks;
    }
    private static double forestiness(long seed,int x,int z,int cell) {
        return SkyTerrainPatterns.noise2(seed,x,z,cell,0x3d61e37bL);
    }
    private record Surface(BlockPos position,int themeIndex) { }
}
