package net.deepcanion.skylands.entity;

import net.deepcanion.skylands.block.CelestiumSpikeBlock;
import net.deepcanion.skylands.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.deepcanion.skylands.block.WaterloggedAttachedBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Reverse-gravity Celestium spike. Reuses vanilla FallingBlockRenderer and its
 * blockstate spawn-packet format, but movement and impact are intentionally
 * handled here rather than by FallingBlockEntity.tick() (which always falls).
 */
public final class RisingCelestiumSpikeEntity extends FallingBlockEntity {
    private static final double UPWARD_ACCELERATION = 0.04D;
    private static final double MAX_UPWARD_SPEED = 3.0D;
    private BlockState risingBlockState;
    private double ascendedBlocks;

    public RisingCelestiumSpikeEntity(EntityType<? extends RisingCelestiumSpikeEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CelestiumSpikeBlock)) return;
        RisingCelestiumSpikeEntity entity = new RisingCelestiumSpikeEntity(
                ModEntities.RISING_CELESTIUM_SPIKE.get(), level);
        entity.risingBlockState = state;
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        entity.setStartPos(pos);
        level.addFreshEntity(entity);
    }

    @Override
    public BlockState getBlockState() {
        return risingBlockState != null ? risingBlockState : super.getBlockState();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        // The parent stores its blockstate privately. Supply our real spike state
        // so the vanilla FallingBlockRenderer sees the right texture on clients.
        return new ClientboundAddEntityPacket(this, serverEntity, Block.getId(getBlockState()));
    }

    @Override
    public void tick() {
        // Entity.baseTick() keeps fluids, portals and ordinary entity lifecycle
        // handling; FallingBlockEntity.tick() must not run (downward physics).
        this.baseTick();
        if (this.isRemoved()) return;
        if (!(getBlockState().getBlock() instanceof CelestiumSpikeBlock)) {
            this.discard();
            return;
        }
        if (getY() + getBbHeight() >= level().getMaxBuildHeight()) {
            this.discard(); // no item entity above the dimension ceiling
            return;
        }

        Vec3 velocity = getDeltaMovement();
        double risingSpeed = Math.min(MAX_UPWARD_SPEED, Math.max(0, velocity.y) + UPWARD_ACCELERATION);
        Vec3 movement = new Vec3(velocity.x, risingSpeed, velocity.z);
        setDeltaMovement(movement);
        double oldY = getY();
        this.move(MoverType.SELF, movement);
        ascendedBlocks += Math.max(0.0D, getY() - oldY);
        setDeltaMovement(getDeltaMovement().scale(0.98D));

        // Like pointed dripstone, this only hurts on *impact*, not as it
        // passes through mobs during its flight. One impact = one damage event.
        if (this.verticalCollision && risingSpeed > 0.0D && !this.verticalCollisionBelow) {
            if (!level().isClientSide) {
                // Unsupported ceiling spikes can relocate to the next solid
                // overhead support. Floor spikes retain their previous behavior:
                // impact damage and an item drop, not automatic reattachment.
                if (!tryAttachToNextCeiling()) {
                    impact();
                    this.spawnAtLocation(new ItemStack(getBlockState().getBlock()));
                }
            }
            this.discard();
            return;
        }
        if (getY() + getBbHeight() >= level().getMaxBuildHeight()) {
            this.discard();
        }
    }

    private boolean tryAttachToNextCeiling() {
        BlockState flying = getBlockState();
        if (flying.getValue(CelestiumSpikeBlock.FACE) != AttachFace.CEILING) return false;
        // On upward collision the entity's top touches the underside of the
        // collided block. Its own block space is immediately below that face.
        BlockPos target = BlockPos.containing(getX(), getBoundingBox().maxY - 0.01D, getZ());
        BlockState old = level().getBlockState(target);
        if (!old.canBeReplaced()) return false;
        BlockState placed = flying.setValue(WaterloggedAttachedBlock.WATERLOGGED,
                old.getFluidState().is(Fluids.WATER));
        if (!placed.canSurvive(level(), target)) return false;
        return level().setBlock(target, placed, Block.UPDATE_ALL);
    }

    private void impact() {
        int travelled = Math.max(0, (int)Math.ceil(ascendedBlocks - 1.0D));
        if (travelled == 0) return;
        float damage = Math.min(40.0F, travelled * 6.0F);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(0.15D, 0.20D, 0.15D),
                living -> living.isAlive() && !living.isSpectator()
                        && !(living instanceof Player player && player.isCreative()))) {
            victim.hurt(this.damageSources().fallingStalactite(this), damage);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // The parent's field is private, so explicitly overwrite its saved
        // BlockState. On reload, super.readAdditionalSaveData restores it.
        tag.put("BlockState", NbtUtils.writeBlockState(getBlockState()));
        tag.putDouble("SkyLandsAscendedBlocks", ascendedBlocks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        risingBlockState = null; // fall back to the superclass' restored state
        ascendedBlocks = Math.max(0.0D, tag.getDouble("SkyLandsAscendedBlocks"));
    }
}
