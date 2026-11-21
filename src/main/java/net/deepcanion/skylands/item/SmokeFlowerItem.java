package net.deepcanion.skylands.item;

import net.deepcanion.skylands.entity.SmokeFlowerProjEntity;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.deepcanion.skylands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Plant a Smoke Flower on soil; using it in air keeps its original projectile ability. */
public final class SmokeFlowerItem extends SkyThrowableItem {
    private static final TagKey<Block> PLANTING_SOILS = TagKey.create(Registries.BLOCK, SkyLands.id("smoke_flower_soils"));
    public SmokeFlowerItem() {
        super(new Item.Properties().rarity(Rarity.UNCOMMON), 60,
                (world, user) -> SmokeFlowerProjEntity.shoot(world, user, world.getRandom()));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        // Only planting on the top of a suitable surface counts as an interaction.
        // For other interactions, don't consume the item or accidentally throw it.
        if (context.getClickedFace() != Direction.UP || context.getPlayer() == null)
            return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        // Only explicit player planting is restricted. Old structure/worldgen plants
        // may survive on their pre-existing, broader support list.
        if (!level.getBlockState(clicked).is(PLANTING_SOILS)) return InteractionResult.PASS;
        BlockPos target = clicked.above();
        BlockState young = ModBlocks.SMOKE_FLOWER_PLANT_GROW_STAGE.get().defaultBlockState()
                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
        if (!level.isInWorldBounds(target) || !level.isInWorldBounds(target.above())
                || !level.hasChunkAt(target) || !level.hasChunkAt(target.above())
                || !level.getBlockState(target).canBeReplaced()
                || !level.getBlockState(target.above()).canBeReplaced()
                || !level.getFluidState(target).isEmpty()
                || !level.getFluidState(target.above()).isEmpty()
                || !young.canSurvive(level, target)) return InteractionResult.PASS;
        if (!level.mayInteract(context.getPlayer(), target)
                || !context.getPlayer().mayUseItemAt(target, Direction.UP, context.getItemInHand()))
            return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            DoublePlantBlock.placeAt(level, young, target, 3);
            level.playSound(null, target, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
