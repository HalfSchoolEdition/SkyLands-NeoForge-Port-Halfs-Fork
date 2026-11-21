package net.deepcanion.skylands.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.registries.Registries;

import net.deepcanion.skylands.entity.IgneoRoboFelineEntity;

public class IgneoRoboFelinePlayerCollidesWithThisEntityProcedure {
	public static void execute(LevelAccessor world, Entity entity, Entity sourceentity) {
		// Contact damage and its accompanying attack animation must not be
		// triggered by Creative or Spectator players.
		if (entity == null || !(sourceentity instanceof Player player) || entity.level().isClientSide()
				|| player.isCreative() || player.isSpectator())
			return;
		if (entity instanceof IgneoRoboFelineEntity) {
			((IgneoRoboFelineEntity) entity).setAnimation("attack");
		}
		sourceentity.hurt(new DamageSource(world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC)), 3);
	}
}
