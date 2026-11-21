package net.deepcanion.skylands.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Reuses the vanilla snowflake sprite set, not the vanilla snowflake particle ID. */
public final class StormSnowflakeParticle extends TextureSheetParticle {
    private final double centerX, centerZ;
    private final double originY, orbitRadius;
    private final double startAngle, angularSpeed, phase;
    private final SpriteSet sprites;

    private StormSnowflakeParticle(ClientLevel level, double x, double y, double z,
            double centerX, double centerY, double centerZ, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.originY = y;
        this.orbitRadius = Math.min(10.0, Math.hypot(x - centerX, z - centerZ));
        this.startAngle = Math.atan2(z - centerZ, x - centerX);
        this.angularSpeed = 0.075 + random.nextDouble() * 0.07;
        this.phase = random.nextDouble() * Math.PI * 2.0;
        this.quadSize = 0.12F + random.nextFloat() * 0.10F;
        this.lifetime = 60 + random.nextInt(30);
        this.gravity = 0F;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        double angle = startAngle + age * angularSpeed;
        double r = Math.min(10.0, orbitRadius + 0.15 * Math.sin(age * 0.11 + phase));
        setPos(centerX + Math.cos(angle) * r,
                originY + Math.sin(age * 0.085 + phase) * 0.40,
                centerZ + Math.sin(angle) * r);
        this.setSpriteFromAge(sprites);
        if (age > lifetime - 14) alpha = Math.max(0F, (lifetime - age) / 14F);
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, cx, cy, cz) ->
                new StormSnowflakeParticle(level, x, y, z, cx, cy, cz, sprites);
    }
}
