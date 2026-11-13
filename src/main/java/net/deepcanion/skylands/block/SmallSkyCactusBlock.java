package net.deepcanion.skylands.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Berry-bush-style contact damage only while moving through the low cactus. */
public final class SmallSkyCactusBlock extends SkyFlowerBlock {
    public SmallSkyCactusBlock(Holder<MobEffect> effect, int duration, Properties properties) {
        super(effect, duration, properties);
    }

    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!(entity instanceof LivingEntity)) return;
        entity.makeStuckInBlock(state, new Vec3(0.8D, 0.75D, 0.8D));
        if (!level.isClientSide() && (Math.abs(entity.getX() - entity.xOld) >= 0.003D
                || Math.abs(entity.getZ() - entity.zOld) >= 0.003D)) {
            entity.hurt(level.damageSources().sweetBerryBush(), 1.0F);
        }
    }
}
