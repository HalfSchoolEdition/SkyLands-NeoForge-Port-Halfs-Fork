package net.deepcanion.skylands.block;

/**
 * Sky Swamp Bush plant. Firefly particles are emitted by the client-side
 * ClientSwampFireflies controller, which scales emission to nearby bush density.
 * Keeping emission in one place avoids duplicate, camera-sampling-dependent bursts.
 */
public final class SkySwampBushBlock extends SkyDoublePlantBlock {
    public SkySwampBushBlock(Properties properties) { super(properties); }
}
