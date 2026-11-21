package net.deepcanion.skylands.block;

import net.deepcanion.skylands.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Wild hanging fruit: one Float Grape from its block loot table with any tool,
 * including shears or Silk Touch. Fruit items can be re-hung beneath leaves. */
public final class FloatGrapePlantBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 3, 4, 12, 16, 12);

    public FloatGrapePlantBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.FLOAT_GRAPE.get());
    }
}
