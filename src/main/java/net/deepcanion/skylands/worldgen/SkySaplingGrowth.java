package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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
 * Handcrafted trees for player-grown SkyLands saplings.
 *
 * Normal saplings use only standard-size WorldEdit-authored NBTs. Five of the
 * same sapling in a horizontal + grow a big Golden or Glaciated tree. The
 * regular SaplingBlock tick/bonemeal path still controls growth attempts.
 * All checks run server-side, on loaded terrain, before consuming saplings.
 */
public final class SkySaplingGrowth {
    public enum Kind { GOLDEN, GLACIATED, LAVIC }

    private static final SkyTerrainTemplates.Entry[] GOLDEN_NORMAL = {
            SkyTerrainTemplates.tree("golden_tree_01", 2, 0, 2, 4),
            SkyTerrainTemplates.tree("golden_tree_02", 2, 0, 1, 4),
            SkyTerrainTemplates.tree("golden_tree_fruit_01", 3, 0, 3, 2)
    };
    private static final SkyTerrainTemplates.Entry[] GOLDEN_BIG = {
            SkyTerrainTemplates.tree("golden_tree_big_01", 6, 1, 7, 3),
            SkyTerrainTemplates.tree("golden_tree_fruit_big_01", 3, 2, 2, 1)
    };
    private static final SkyTerrainTemplates.Entry[] GLACIATED_NORMAL = {
            SkyTerrainTemplates.tree("glacial_tree_01", 2, 0, 2, 1),
            SkyTerrainTemplates.tree("glacial_tree_02", 1, 0, 3, 1),
            SkyTerrainTemplates.tree("glacial_tree_03", 7, 3, 9, 1)
    };
    private static final SkyTerrainTemplates.Entry[] GLACIATED_BIG = {
            SkyTerrainTemplates.tree("glacial_tree_big_01", 5, 2, 2, 2),
            SkyTerrainTemplates.tree("glacial_tree_big_02", 11, 3, 10, 3)
    };
    private static final SkyTerrainTemplates.Entry[] GLACIATED_GIANT = {
            SkyTerrainTemplates.tree("glacial_tree_giant_01", 16, 4, 16, 1)
    };
    private static final SkyTerrainTemplates.Entry[] LAVIC_BIG = {
            SkyTerrainTemplates.tree("lavic_tree_big_01", 12, 3, 7, 1)
    };
    private static final SkyTerrainTemplates.Entry[] LAVIC_NORMAL = {
            SkyTerrainTemplates.tree("lavic_tree_01", 1, 0, 3, 1),
            SkyTerrainTemplates.tree("lavic_tree_02", 3, 0, 3, 1),
            SkyTerrainTemplates.tree("lavic_tree_03", 2, 0, 5, 1),
            SkyTerrainTemplates.tree("lavic_tree_04", 2, 0, 3, 1)
    };

    private static final int[][] PLUS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The 3x3 square has priority over the plus shape embedded within it. */
    private static List<BlockPos> squareAt(ServerLevel level, BlockPos centre, Block sapling) {
        List<BlockPos> result = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = centre.offset(dx, 0, dz);
            if (!level.getBlockState(pos).is(sapling)) return null;
            result.add(pos.immutable());
        }
        return result;
    }

    public static boolean grow(ServerLevel level, BlockPos triggeredPos, Block sapling,
                               Kind kind, RandomSource random) {
        // A giant Glaciated tree requires all nine saplings, not just the
        // plus contained in the square. Test every possible triggering cell.
        if (kind == Kind.GLACIATED) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos centre = triggeredPos.offset(dx, 0, dz);
                List<BlockPos> square = squareAt(level, centre, sapling);
                if (square != null) return place(level, centre, sapling, square,
                        SkyTerrainTemplates.select(GLACIATED_GIANT, random), random);
            }
        }
        // Every one of the five plus saplings can trigger large growth.
        for (int[] delta : PLUS) {
            BlockPos centre = triggeredPos.offset(delta[0], 0, delta[1]);
            List<BlockPos> plus = plusAt(level, centre, sapling);
            if (plus != null) {
                SkyTerrainTemplates.Entry[] big = switch (kind) {
                    case GOLDEN -> GOLDEN_BIG;
                    case GLACIATED -> GLACIATED_BIG;
                    case LAVIC -> LAVIC_BIG;
                };
                return place(level, centre, sapling, plus,
                        SkyTerrainTemplates.select(big, random), random);
            }
        }
        SkyTerrainTemplates.Entry[] normal = switch (kind) {
            case GOLDEN -> GOLDEN_NORMAL;
            case GLACIATED -> GLACIATED_NORMAL;
            case LAVIC -> LAVIC_NORMAL;
        };
        return place(level, triggeredPos, sapling, List.of(triggeredPos),
                SkyTerrainTemplates.select(normal, random), random);
    }

    private static List<BlockPos> plusAt(ServerLevel level, BlockPos centre, Block sapling) {
        List<BlockPos> result = new ArrayList<>(5);
        for (int[] delta : PLUS) {
            BlockPos pos = centre.offset(delta[0], 0, delta[1]);
            if (!level.getBlockState(pos).is(sapling)) return null;
            result.add(pos.immutable());
        }
        return result;
    }

    private static boolean place(ServerLevel level, BlockPos groundAir, Block sapling,
                                 List<BlockPos> consumed, SkyTerrainTemplates.Entry choice,
                                 RandomSource random) {
        Optional<StructureTemplate> loaded = level.getStructureManager().get(choice.id());
        if (loaded.isEmpty()) return false;
        StructureTemplate template = loaded.get();
        Rotation rotation = Rotation.getRandom(random);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation).setMirror(Mirror.NONE).setIgnoreEntities(true)
                .setRandom(random).addProcessor(new BlockIgnoreProcessor(List.of(
                        Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.STRUCTURE_VOID)));
        BlockPos rotatedAnchor = StructureTemplate.calculateRelativePosition(settings, choice.anchor());
        BlockPos origin = groundAir.subtract(rotatedAnchor);
        BoundingBox bounds = template.getBoundingBox(settings, origin);

        // No forced chunk loads when a player bonemeals a big tree at the edge
        // of a currently loaded area. Never partially place a template.
        if (bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight()
                || !level.hasChunksAt(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                        new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) return false;

        // Check only actual template blocks; NBT templates contain no air, but
        // a whole bounding-box check would wrongly reject trees on a slope.
        // save() exposes all authored blocks and is called only for sapling growth.
        CompoundTag nbt = template.save(new CompoundTag());
        ListTag palette = nbt.getList("palette", 10);
        ListTag blocks = nbt.getList("blocks", 10);
        if (palette.isEmpty() || blocks.isEmpty()) return false;
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int index = block.getInt("state");
            if (index < 0 || index >= palette.size()) return false;
            String name = palette.getCompound(index).getString("Name");
            if (name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                    || name.equals("minecraft:void_air") || name.equals("minecraft:structure_void")) continue;
            ListTag coords = block.getList("pos", 3);
            if (coords.size() != 3) return false;
            BlockPos local = new BlockPos(coords.getInt(0), coords.getInt(1), coords.getInt(2));
            BlockPos target = origin.offset(StructureTemplate.calculateRelativePosition(settings, local));
            BlockState found = level.getBlockState(target);
            if (found.isAir() || consumed.contains(target) && found.is(sapling)) continue;
            if (target.getY() < groundAir.getY() && naturalSoil(found)) continue;
            if (found.canBeReplaced() && found.getFluidState().isEmpty()) continue;
            return false; // Logs/structures/containers are never bulldozed.
        }

        List<BlockState> originals = new ArrayList<>(consumed.size());
        for (BlockPos pos : consumed) originals.add(level.getBlockState(pos));
        for (BlockPos pos : consumed)
            level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);

        boolean success = template.placeInWorld(level, origin, origin, settings, random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (!success) {
            for (int i = 0; i < consumed.size(); i++) {
                if (level.getBlockState(consumed.get(i)).isAir())
                    level.setBlock(consumed.get(i), originals.get(i), Block.UPDATE_ALL);
            }
        }
        return success;
    }

    private static boolean naturalSoil(BlockState state) {
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK)
                || state.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(SkyLands.id("golden_mossy_skyrock")))
                || state.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(SkyLands.id("cherry_mossy_skyrock")))
                || state.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(SkyLands.id("swamp_mossy_skyrock")))) return true;
        // Includes island-specific grass, subsoil and rock in each sky biome.
        for (String theme : new String[] {"golden", "glaciated", "arid", "lavic", "swamp", "cherry"}) {
            SkyTerrainPalette palette = SkyTerrainPalette.forTheme(theme);
            if (state.is(palette.top().getBlock()) || state.is(palette.soil().getBlock())
                    || state.is(palette.core().getBlock())) return true;
        }
        return false;
    }

    private SkySaplingGrowth() { }
}
