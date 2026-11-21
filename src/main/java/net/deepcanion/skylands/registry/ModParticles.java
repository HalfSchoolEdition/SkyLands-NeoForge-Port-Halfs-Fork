package net.deepcanion.skylands.registry;
import net.deepcanion.skylands.SkyLands;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.*;
import net.neoforged.neoforge.registries.*;
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, SkyLands.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD_SOURCE = PARTICLES.register("cloud_source", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD = PARTICLES.register("cloud", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD_SMOKE = PARTICLES.register("cloud_smoke", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD_SMOKE_1 = PARTICLES.register("cloud_smoke_1", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMPTY = PARTICLES.register("empty", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ICE = PARTICLES.register("ice", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TEMP_FIRE_PARTICLE = PARTICLES.register("temp_fire_particle", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD_BARRIER_2 = PARTICLES.register("cloud_barrier_2", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FAST = PARTICLES.register("fast", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD_BARRIER_3 = PARTICLES.register("cloud_barrier_3", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND = PARTICLES.register("wind", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_LEAF = PARTICLES.register("fire_leaf", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREFLY = PARTICLES.register("firefly", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STORM_CLOUD = PARTICLES.register("storm_cloud", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STORM_CLOUD_1 = PARTICLES.register("storm_cloud_1", () -> new SimpleParticleType(false));
    // Same sprites as the ambient Glaciated Sky snowflake, but a distinct orbiting storm particle.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STORM_SNOWFLAKE = PARTICLES.register("storm_snowflake", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CLOUD_STAFF_SPARKLE = PARTICLES.register("cloud_staff_sparkle", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ITEM_BREAK = PARTICLES.register("item_break", () -> new SimpleParticleType(false));
    // Tiny colored fullbright pixel particles around active Solero / Lunero cacti.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CACTUS_WARM_PIXEL =
            PARTICLES.register("cactus_warm_pixel", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CACTUS_COLD_PIXEL =
            PARTICLES.register("cactus_cold_pixel", () -> new SimpleParticleType(false));
    private ModParticles() { }
}
