package net.deepcanion.skylands.worldgen;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Small handcrafted features on SkyLands terrain. Never force-load chunks.
 * The author's WorldEdit //copy feet define the ground-level origin; root
 * blocks at negative relative Y can be embedded in natural island stone.
 */
final class SkyTerrainTemplates {
    record Entry(ResourceLocation id, BlockPos anchor, int weight) { }

    static Entry tree(String name, int x, int y, int z, int weight) {
        return new Entry(SkyLands.id("tree/" + name), new BlockPos(x, y, z), weight);
    }
    static Entry decoration(String name, int x, int y, int z, int weight) {
        return new Entry(SkyLands.id("decoration/" + name), new BlockPos(x, y, z), weight);
    }
    static Entry existing(String name, int x, int y, int z, int weight) {
        return new Entry(SkyLands.id(name), new BlockPos(x, y, z), weight);
    }

    static Entry select(Entry[] choices, RandomSource random) {
        int total = 0;
        for (Entry choice : choices) total += choice.weight();
        int pick = random.nextInt(total);
        for (Entry choice : choices) {
            pick -= choice.weight();
            if (pick < 0) return choice;
        }
        return choices[choices.length - 1];
    }

    static boolean place(WorldGenLevel level, RandomSource random, Entry entry,
                         BlockPos groundAir, int chunkX, int chunkZ) {
        // Give large handcrafted trees limited tolerance for the uneven
        // island ground beneath their wide canopy; still protect all builds.
        int slope = entry.id().getPath().startsWith("decoration/") ? 2
                : entry.id().getPath().startsWith("tree/")
                && (entry.id().getPath().contains("_big_")
                    || entry.id().getPath().contains("_giant_")) ? 3 : 0;
        return placeWithEmbed(level, random, entry, groundAir, chunkX, chunkZ, 0, slope, false);
    }

    static boolean placeEmbedded(WorldGenLevel level, RandomSource random, Entry entry,
                                 BlockPos groundAir, int chunkX, int chunkZ, int depth) {
        return placeWithEmbed(level, random, entry, groundAir, chunkX, chunkZ, depth, 0, false);
    }

    static boolean placeHugeSpire(WorldGenLevel level, RandomSource random, Entry entry,
                                  BlockPos groundAir, int chunkX, int chunkZ) {
        // For a 44-block landmark, sink its base five blocks into the terrain. The old six-block
        // slope tolerance rejected almost every giant template: a 44-block
        // footprint naturally intersects tall island ridges. Allow *native*
        // rock/soil throughout its bounding volume while still rejecting
        // non-native structures, logs, and existing builds.
        return placeWithEmbed(level, random, entry, groundAir, chunkX, chunkZ, 5, 52, false);
    }

    /** Reuse the real SkyLands tree templates at Overworld elevations. The
     * regular template path intentionally forbids Y < 270 for sky terrain.
     * Fallen Island surfaces need a separate, equally guarded ground path. */
    static boolean placeGroundedTree(WorldGenLevel level, RandomSource random, Entry entry,
                                     BlockPos groundAir, int chunkX, int chunkZ) {
        if (!entry.id().getPath().startsWith("tree/")) return false;
        return placeWithEmbed(level, random, entry, groundAir, chunkX, chunkZ, 0, 0, true);
    }

    private static boolean placeWithEmbed(WorldGenLevel level, RandomSource random, Entry entry,
                                          BlockPos groundAir, int chunkX, int chunkZ,
                                          int depth, int naturalSlopeAllowance, boolean grounded) {
        Optional<StructureTemplate> loaded = level.getLevel().getServer()
                .getStructureManager().get(entry.id());
        if (loaded.isEmpty()) return false;
        StructureTemplate template = loaded.get();
        Rotation rotation = Rotation.getRandom(random);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation).setMirror(Mirror.NONE).setIgnoreEntities(true)
                .setRandom(random)
                .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.AIR, Blocks.CAVE_AIR,
                        Blocks.VOID_AIR, Blocks.STRUCTURE_VOID)));
        BlockPos rotatedAnchor = StructureTemplate.calculateRelativePosition(settings, entry.anchor());
        BlockPos origin = groundAir.below(depth).subtract(rotatedAnchor);
        BoundingBox box = template.getBoundingBox(settings, origin);

        // FEATURES writes are limited to a 3x3 region, not just the originating
        // chunk. Check every corner BEFORE placing so large trees don't truncate.
        // Templates wider than this region are rejected, never half-generated.
        if (box.minY() < (grounded ? level.getMinBuildHeight() : SkyIslandShape.MIN_Y)
                || box.maxY() >= level.getMaxBuildHeight()
                || box.minX() < chunkX - 16 || box.maxX() >= chunkX + 32
                || box.minZ() < chunkZ - 16 || box.maxZ() >= chunkZ + 32) return false;
        for (int x : new int[] {box.minX(), box.maxX()}) {
            for (int z : new int[] {box.minZ(), box.maxZ()}) {
                if (!level.ensureCanWrite(new BlockPos(x, box.minY(), z))
                        || !level.ensureCanWrite(new BlockPos(x, box.maxY(), z))) return false;
            }
        }

        // Conservative footprint guard: never paste over another structure,
        // trees, or a natural rock wall above the root. Only BELOW the user's
        // selected ground surface can authored roots/foundations displace native
        // island blocks. Non-air terrain and players' builds otherwise protected.
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    check.set(x, y, z);
                    BlockState found = level.getBlockState(check);
                    if (found.isAir()) continue;
                    // Neighboring canopies can overlap in a dense forest; tree
                    // trunks and all non-leaf structures remain protected.
                    if (entry.id().getPath().startsWith("tree/") && found.is(BlockTags.LEAVES)) continue;
                    if (y < groundAir.getY() + naturalSlopeAllowance && naturalGround(found)) continue;
                    return false;
                }
            }
        }
        boolean placed = template.placeInWorld(level, origin, origin, settings, random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // These are direct feature templates, not jigsaw starts. Native beard_thin
        // does not run on them, and vanilla noise stops at Y=319 in SkyLands.
        // Extend the island's own palette in a short irregular foundation instead.
        String path = entry.id().getPath();
        if (placed && (path.contains("structure_pavillon") || path.contains("structure_arch")
                || path.contains("treestump") || path.contains("tree_burned_fallen"))) {
            blendSkyFoundation(level, entry, box, groundAir);
        }
        return placed;
    }

    /** A bounded beard_thin-style support underneath handcrafted sky decorations. */
    private static void blendSkyFoundation(WorldGenLevel level, Entry entry, BoundingBox box,
                                           BlockPos groundAir) {
        String path = entry.id().getPath();
        String theme = path.contains("cherry_") ? "cherry"
                : path.contains("lavic_") ? "lavic" : "golden";
        SkyTerrainPalette palette = SkyTerrainPalette.forTheme(theme);
        double midX = (box.minX() + box.maxX()) * .5;
        double midZ = (box.minZ() + box.maxZ()) * .5;
        double halfX = Math.max(2.5, (box.maxX() - box.minX() + 1) * .5);
        double halfZ = Math.max(2.5, (box.maxZ() - box.minZ() + 1) * .5);
        long seed = level.getSeed();
        // The lower template cells may be roots, not the actual standing floor.
        // Start below the lowest occupied cell, never float a bare grass floor.
        int foundationY = Math.min(box.minY(), groundAir.getY());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                double nx = (x - midX) / halfX, nz = (z - midZ) / halfZ;
                double d = Math.sqrt(nx * nx + nz * nz);
                double warp = (SkyTerrainPatterns.noise2(seed, x, z, 5, 0x52baeL) - .5) * .24;
                if (d > .94 + warp) continue;
                int drop = Math.max(1, (int)Math.round(2 + 5 * (1.0 - d)
                        + 2 * (SkyTerrainPatterns.noise2(seed, x, z, 4, 0x52bafL) - .5)));
                // Under the author's feet-level surface only. Never build long
                // rock pillars down to the actual Overworld below the islands.
                for (int dy = 1; dy <= drop; dy++) {
                    int y = foundationY - dy;
                    if (y < SkyIslandShape.MIN_Y || y >= level.getMaxBuildHeight()) break;
                    pos.set(x, y, z);
                    if (!level.ensureCanWrite(pos)) break;
                    BlockState old = level.getBlockState(pos);
                    if (!old.isAir()) {
                        // A new solid support can cover an original exposed grass
                        // surface. Convert that buried topsoil immediately so an
                        // underground grass texture is not left visible at edges.
                        if (isGrassSurface(old) && !level.getBlockState(pos.above()).isAir())
                            level.setBlock(pos, palette.core(), Block.UPDATE_CLIENTS);
                        break;
                    }
                    // These are internal supports, never exposed lawn blocks.
                    level.setBlock(pos, palette.core(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Covering rock/structure blocks can leave a pre-existing visible grass
        // texture trapped immediately beneath their base. Do not alter any exposed
        // outside surfaces or player-built structures farther away.
        for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int y = Math.max(SkyIslandShape.MIN_Y, foundationY - 9);
                     y <= Math.min(box.maxY(), groundAir.getY() + 1); y++) {
                pos.set(x, y, z);
                if (!level.ensureCanWrite(pos)) continue;
                BlockState surface = level.getBlockState(pos);
                if (!isGrassSurface(surface)) continue;
                BlockPos above = pos.above();
                BlockState cover = level.getBlockState(above);
                if (cover.isSolidRender(level, above))
                    level.setBlock(pos, palette.core(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static boolean isGrassSurface(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) return true;
        return state.is(net.deepcanion.skylands.registry.ModBlocks.GOLDEN_GRASS_BLOCK.get())
                || state.is(net.deepcanion.skylands.registry.ModBlocks.GLACIATED_GRASS_BLOCK.get())
                || state.is(net.deepcanion.skylands.registry.ModBlocks.LAVIC_SKYMETAL_GRASS.get())
                || state.is(net.deepcanion.skylands.registry.ModBlocks.CHERRY_GRASS_BLOCK.get())
                || state.is(net.deepcanion.skylands.registry.ModBlocks.SWAMP_GRASS_BLOCK.get());
    }

    private static final Set<Block> NATURAL_GROUND = naturalGroundBlocks();
    private static Set<Block> naturalGroundBlocks() {
        Set<Block> result = new HashSet<>();
        result.add(Blocks.GRASS_BLOCK);
        result.add(Blocks.DIRT);
        result.add(Blocks.DIORITE);
        result.add(Blocks.PODZOL);
        result.add(Blocks.COARSE_DIRT);
        for (String moss : new String[] {"golden_mossy_skyrock", "cherry_mossy_skyrock", "swamp_mossy_skyrock"})
            result.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(SkyLands.id(moss)));
        for (String theme : new String[] {"golden", "glaciated", "arid", "lavic", "swamp", "cherry"}) {
            SkyTerrainPalette palette = SkyTerrainPalette.forTheme(theme);
            result.add(palette.top().getBlock());
            result.add(palette.soil().getBlock());
            result.add(palette.core().getBlock());
            // Natural ore veins are generated before the huge spire pass.
            // Rejecting them across a 44x40 footprint made giant pillars
            // almost impossible to place even on otherwise safe terrain.
            result.add(palette.ore().getBlock());
        }
        return Set.copyOf(result);
    }
    private static boolean naturalGround(BlockState state) {
        return NATURAL_GROUND.contains(state.getBlock());
    }

    private SkyTerrainTemplates() { }
}
