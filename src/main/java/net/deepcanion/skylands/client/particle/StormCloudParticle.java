package net.deepcanion.skylands.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Cloud-smoke visuals (same two sprites/size as Smoke Flower), with a stable vortex orbit. */
public final class StormCloudParticle extends TextureSheetParticle {
    private final double centerX, centerZ;
    private final double orbitY, orbitRadius;
    private final double startAngle, angularSpeed;
    private final double bobPhase;

    // The three speed arguments encode the vortex's center, not initial velocity.
    private StormCloudParticle(ClientLevel level, double x, double y, double z,
            double centerX, double centerY, double centerZ, SpriteSet sprites) {
        super(level, x, y, z);
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.orbitY = y;
        this.orbitRadius = Math.min(10.0, Math.hypot(x - centerX, z - centerZ));
        this.startAngle = Math.atan2(z - centerZ, x - centerX);
        this.angularSpeed = 0.065 + random.nextDouble() * 0.06;
        this.bobPhase = random.nextDouble() * Math.PI * 2.0;
        // Match both Smoke Flower cloud-particle classes' exact geometry/scale.
        setSize(4F, 4F);
        quadSize *= 8F;
        // Reduced lifetime compensates for the 4x emission rate near multiple storms.
        lifetime = 75 + random.nextInt(35);
        gravity = 0F;
        hasPhysics = false;
        pickSprite(sprites);
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        double angle = startAngle + age * angularSpeed;
        // Slight turbulence adds motion without drifting outside the ~10-block radius.
        double r = Math.min(10.0, orbitRadius + 0.20 * Math.sin(age * 0.12 + bobPhase));
        double yOffset = Math.sin(age * 0.07 + bobPhase) * 0.35;
        setPos(centerX + Math.cos(angle) * r,
                orbitY + yOffset,
                centerZ + Math.sin(angle) * r);
        // Gentle end fade; otherwise retain the original cloud-smoke look.
        if (age > lifetime - 16) alpha = Math.max(0F, (lifetime - age) / 16F);
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, cx, cy, cz) ->
                new StormCloudParticle(level, x, y, z, cx, cy, cz, sprites);
    }
}
