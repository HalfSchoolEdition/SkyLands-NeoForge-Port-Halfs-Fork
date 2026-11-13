package net.deepcanion.skylands.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Short or tall visual models, each one independently occupies one block. */
public final class FrostBerryBushBlock extends BushBlock {
    public static final MapCodec<FrostBerryBushBlock> CODEC = simpleCodec(FrostBerryBushBlock::new);
    public FrostBerryBushBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BushBlock> codec() { return CODEC; }
    @Override protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
        return soil.is(PlantSupport.SOILS) || super.mayPlaceOn(soil, level, pos);
    }
}
