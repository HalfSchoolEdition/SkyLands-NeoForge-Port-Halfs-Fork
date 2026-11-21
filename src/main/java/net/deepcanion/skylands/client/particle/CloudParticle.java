
package net.deepcanion.skylands.client.particle;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;

import net.minecraft.util.Mth;

@OnlyIn(Dist.CLIENT)
public class CloudParticle extends TextureSheetParticle {
	public static CloudParticleProvider provider(SpriteSet spriteSet) {
		return new CloudParticleProvider(spriteSet, 1.0f);
	}

	/** Compatibility alias: identical appearance to the ordinary cloud particle. */
	public static CloudParticleProvider sourceProvider(SpriteSet spriteSet) {
		return new CloudParticleProvider(spriteSet, 1.0f);
	}

	public static class CloudParticleProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet spriteSet;
		private final float sizeMultiplier;

		public CloudParticleProvider(SpriteSet spriteSet, float sizeMultiplier) {
			this.spriteSet = spriteSet;
			this.sizeMultiplier = sizeMultiplier;
		}

		public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			CloudParticle particle = new CloudParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
			particle.quadSize *= sizeMultiplier;
			return particle;
		}
	}

	private final SpriteSet spriteSet;

	protected CloudParticle(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
		super(world, x, y, z);
		this.spriteSet = spriteSet;
		this.setSize(4f, 4f);
		this.quadSize *= 8f;
		this.lifetime = (int) Math.max(1, 360 + (this.random.nextInt(360) - 180));
		this.gravity = 0f;
		this.alpha = 0.0f;
		this.hasPhysics = false;
		this.xd = vx * 0.02;
		this.yd = vy * 0.02;
		this.zd = vz * 0.02;
		this.pickSprite(spriteSet);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		super.tick();
		this.alpha = Mth.clamp(((float)this.age / (float)this.lifetime) * 2.0f - 0.1f, 0.0f, 1.0f) * (1.0f - Mth.clamp(((float)this.age / (float)this.lifetime) * 2.0f, 0.0f, 1.0f)) * 4.0f;
	}
}
