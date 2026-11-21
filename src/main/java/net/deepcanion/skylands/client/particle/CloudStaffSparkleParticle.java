package net.deepcanion.skylands.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Small fullbright glints: created only by the local player's Cloud Staff reveal. */
@OnlyIn(Dist.CLIENT)
public final class CloudStaffSparkleParticle extends TextureSheetParticle {
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, world, x, y, z, dx, dy, dz) ->
                new CloudStaffSparkleParticle(world, x, y, z, dx, dy, dz, sprites);
    }

    private CloudStaffSparkleParticle(ClientLevel world, double x, double y, double z,
                                     double dx, double dy, double dz, SpriteSet sprites) {
        super(world, x, y, z);
        this.quadSize = 0.035F + this.random.nextFloat() * 0.0225F;
        this.setSize(0.02F, 0.02F);
        this.lifetime = 19 + this.random.nextInt(14);
        this.gravity = 0F;
        this.hasPhysics = false;
        this.friction = 0.97F;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        float hue = this.random.nextFloat();
        this.setColor(0.70F + 0.23F * hue, 0.89F + 0.10F * hue, 1.0F);
        this.alpha = 0F;
        this.pickSprite(sprites);
    }

    @Override public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override public void tick() {
        super.tick();
        float fadeIn = Mth.clamp(this.age / 5.0F, 0F, 1F);
        float fadeOut = Mth.clamp((this.lifetime - this.age) / 9.0F, 0F, 1F);
        this.alpha = Math.min(fadeIn, fadeOut) * 0.95F;
    }
}
