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

/** Two-frame, fullbright firefly. All movement and blinking are purely client side. */
@OnlyIn(Dist.CLIENT)
public final class FireflyParticle extends TextureSheetParticle {
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, world, x, y, z, vx, vy, vz) ->
                new FireflyParticle(world, x, y, z, vx, vy, vz, sprites);
    }

    private final SpriteSet sprites;
    private final float phaseA, phaseB, phaseC;
    private int blinkTicks;
    private boolean lit;
    private float glow;

    private FireflyParticle(ClientLevel level, double x, double y, double z,
                            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.phaseA = this.random.nextFloat() * Mth.TWO_PI;
        this.phaseB = this.random.nextFloat() * Mth.TWO_PI;
        this.phaseC = this.random.nextFloat() * Mth.TWO_PI;
        // Keep the current 3x3 lit/unlit sprite and movement, but reduce
        // its visible quad to 30% of the previous size (~0.063-0.078 blocks).
        this.quadSize = (0.21F + this.random.nextFloat() * 0.05F) * 0.30F;
        this.setSize(0.03F, 0.03F);
        this.lifetime = 140 + this.random.nextInt(100);
        this.gravity = 0F;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lit = this.random.nextBoolean();
        this.blinkTicks = 10 + this.random.nextInt(35);
        this.glow = this.lit ? 0.7F : 0F;
        this.setColor(0.95F + this.random.nextFloat() * 0.05F,
                0.95F + this.random.nextFloat() * 0.05F,
                0.85F + this.random.nextFloat() * 0.15F);
        this.setSprite(this.sprites.get(this.lit ? 1 : 0, 1));
        // Begin truly invisible; fade in instead of flashing on spawn.
        this.alpha = 0F;
    }

    private static float smoothstep(float value) {
        float t = Mth.clamp(value, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    @Override public int getLightColor(float partialTick) {
        return this.lit || this.glow > 0.1F ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (--this.blinkTicks <= 0) {
            this.lit = !this.lit;
            this.blinkTicks = this.lit ? 14 + this.random.nextInt(20) : 32 + this.random.nextInt(50);
        }
        // Layered drifting loops: long slow curves plus a short, gently
        // wandering swirl. The offsets are seeded once per particle.
        double t = this.age;
        this.xd = Math.sin(t * 0.065D + this.phaseA) * 0.013D
                + Math.sin(t * 0.19D + this.phaseB) * 0.005D;
        this.yd = Math.cos(t * 0.09D + this.phaseC) * 0.006D
                + Math.sin(t * 0.17D + this.phaseA) * 0.004D + 0.001D;
        this.zd = Math.cos(t * 0.071D + this.phaseB) * 0.013D
                + Math.sin(t * 0.21D + this.phaseC) * 0.005D;
        this.move(this.xd, this.yd, this.zd);
        this.glow = Mth.clamp(this.glow + (this.lit ? 0.09F : -0.07F), 0F, 1F);
        // Smooth both ends of the particle lifetime. The two sprites still
        // handle blinking, but the overall visibility no longer cuts off.
        float birth = smoothstep(this.age / 30F);
        float death = smoothstep((this.lifetime - this.age) / 45F);
        this.alpha = (this.lit ? this.glow : 0.18F * this.glow + 0.02F) * birth * death;
        this.setSprite(this.sprites.get((this.lit || this.glow > 0.25F) ? 1 : 0, 1));
    }
}
