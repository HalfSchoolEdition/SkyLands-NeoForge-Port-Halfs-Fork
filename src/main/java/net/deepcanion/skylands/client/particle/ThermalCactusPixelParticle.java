package net.deepcanion.skylands.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Cheap, softly drifting, fullbright 1-pixel-style thermal motes around active cacti.
 * Two colors share the same sprite and renderer; no block entity or dynamic light.
 */
public final class ThermalCactusPixelParticle extends TextureSheetParticle {
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, boolean warm) {
        return (type, world, x, y, z, vx, vy, vz) ->
                new ThermalCactusPixelParticle(world, x, y, z, vx, vy, vz, sprites, warm);
    }

    private ThermalCactusPixelParticle(ClientLevel world, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites, boolean warm) {
        super(world, x, y, z);
        // Small square sprite, approximately a pixel-sized glint in the landscape.
        quadSize = 0.0325F + random.nextFloat() * 0.0175F;
        setSize(0.02F, 0.02F);
        lifetime = 30 + random.nextInt(22);
        friction = 0.98F;
        gravity = 0F;
        hasPhysics = false;
        xd = vx;
        yd = vy + 0.002D;
        zd = vz;
        rCol = warm ? 1.0F : 0.22F;
        gCol = warm ? 0.65F : 0.83F;
        bCol = warm ? 0.19F : 1.0F;
        alpha = 0F;
        pickSprite(sprites);
    }

    @Override public int getLightColor(float partialTick) {
        return 0xF000F0; // Fullbright in both sunlight and complete darkness.
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override public void tick() {
        super.tick();
        alpha = 0.92F * Math.min(Mth.clamp(age / 5.0F, 0F, 1F),
                Mth.clamp((lifetime - age) / 12.0F, 0F, 1F));
    }
}
