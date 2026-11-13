package net.deepcanion.skylands.worldgen;

import net.deepcanion.skylands.block.SkyCactusSpeciesBlock;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Native Solero/Lunero cactus spires. Always terminate in an animated top. */
final class SkyTerrainCacti {
    private SkyTerrainCacti() { }
    static boolean place(WorldGenLevel level, RandomSource random, BlockPos root) {
        SkyCactusSpeciesBlock species = (SkyCactusSpeciesBlock)
                (random.nextBoolean() ? ModBlocks.SOLERO_CACTUS.get() : ModBlocks.LUNERO_CACTUS.get());
        int height = 3 + random.nextInt(5);
        for (int i = 0; i < height; i++) {
            BlockPos at = root.above(i);
            if (at.getY() >= level.getMaxBuildHeight() || !level.ensureCanWrite(at)
                    || !level.getBlockState(at).isAir()) return false;
        }
        boolean open = species.opensAt(level.getLevel().getDayTime());
        // Ground check is checked before any writes, preserving terrain.
        BlockState probe = species.defaultBlockState();
        if (!probe.canSurvive(level, root)) return false;
        for (int i = 0; i < height; i++) {
            SkyCactusSpeciesBlock.Part part = i == height - 1
                    ? SkyCactusSpeciesBlock.Part.TOP
                    : random.nextInt(5) == 0 ? SkyCactusSpeciesBlock.Part.BRANCH
                    : SkyCactusSpeciesBlock.Part.TRUNK;
            BlockState state = species.defaultBlockState()
                    .setValue(SkyCactusSpeciesBlock.PART, part)
                    .setValue(SkyCactusSpeciesBlock.FACING,
                            Direction.Plane.HORIZONTAL.getRandomDirection(random))
                    .setValue(SkyCactusSpeciesBlock.OPEN, i == height - 1 && open);
            level.setBlock(root.above(i), state, Block.UPDATE_CLIENTS);
        }
        // Worldgen uses minimal update flags. The top's periodic scheduled
        // updates are established when the block gets a block update.
        level.scheduleTick(root.above(height - 1), species, 3 + random.nextInt(8));
        return true;
    }
}
