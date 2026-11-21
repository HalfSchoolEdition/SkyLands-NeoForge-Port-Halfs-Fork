package net.deepcanion.skylands.client;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.effect.SkyTemperatures;
import net.deepcanion.skylands.registry.ModBlocks;
import net.deepcanion.skylands.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Nighttime fireflies rooted at real Sky Swamp bushes, not at the camera.
 * Client-only, with bounded cached scans and proportional per-bush emission.
 */
public final class ClientSwampFireflies {
    private static final int SEARCH_RADIUS = 40;
    private static final int VERTICAL_RADIUS = 12;
    private static final int SEARCH_INTERVAL = 80;
    private static final int EMISSION_INTERVAL = 10;
    private static final int MAX_BUSHES = 96;
    // Per-bush probabilities per 10-tick pulse (one firefly/bush every ~4.5-6 seconds).
    private static final int NEAR_BUSH_CHANCE = 9;
    private static final int OUTER_BUSH_CHANCE = 12;
    private static final int MAX_EMISSIONS_PER_PULSE = 24;
    private static final int MIN_RESCAN_INTERVAL = 20;
    private static final int NEAR_RADIUS = 20;
    private static final List<BlockPos> nearbyBushes = new ArrayList<>();
    private static final List<BlockPos> outerBushes = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static BlockPos lastScanOrigin;
    private static int ticks;
    private static int lastScanTick;

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            lastLevel = level;
            lastScanOrigin = null;
            ticks = 0;
            lastScanTick = -SEARCH_INTERVAL;
            nearbyBushes.clear();
            outerBushes.clear();
        }
        if (level == null || mc.player == null || mc.isPaused()) return;
        // Sky islands can report full skylight and a false vanilla night flag even at midnight.
        // Use the actual world clock for nighttime; retain low-light daytime spawning.
        if (!SkyTemperatures.isNight(level.getDayTime())
                && level.getMaxLocalRawBrightness(mc.player.blockPosition()) > 9) return;
        ticks++;
        if (ticks % EMISSION_INTERVAL != 0) return;

        BlockPos origin = mc.player.blockPosition();
        // Avoid a costly full scan every tick when flying quickly through the biome.
        if (lastScanOrigin == null || ticks - lastScanTick >= SEARCH_INTERVAL
                || (ticks - lastScanTick >= MIN_RESCAN_INTERVAL && movedFarFromScan(origin))) {
            scanBushes(level, origin);
            lastScanOrigin = origin.immutable();
            lastScanTick = ticks;
        }
        if (nearbyBushes.isEmpty() && outerBushes.isEmpty()) return;

        // Emission scales with the number of *actual bushes*, never with empty
        // space in the search volume. One bush contributes at most one firefly
        // per pulse, while dense patches create proportionally more fireflies.
        // The hard cap limits extreme particle counts in heavily planted areas.
        int emitted = emitFromBushes(level, mc, nearbyBushes, NEAR_BUSH_CHANCE,
                MAX_EMISSIONS_PER_PULSE);
        if (emitted < MAX_EMISSIONS_PER_PULSE) {
            emitFromBushes(level, mc, outerBushes, OUTER_BUSH_CHANCE,
                    MAX_EMISSIONS_PER_PULSE - emitted);
        }
    }

    private static int emitFromBushes(ClientLevel level, Minecraft mc, List<BlockPos> bushes,
                                      int chance, int budget) {
        if (budget <= 0 || bushes.isEmpty()) return 0;
        int emitted = 0;
        // Rotate which bush gets examined first so an unusually dense area
        // doesn't systematically favor bushes at the start of the scan cache.
        int offset = level.random.nextInt(bushes.size());
        for (int i = 0; i < bushes.size() && emitted < budget; ++i) {
            BlockPos bush = bushes.get((offset + i) % bushes.size());
            if (level.random.nextInt(chance) != 0) continue;
            if (!level.hasChunkAt(bush)) continue;
            BlockState state = level.getBlockState(bush);
            if (!state.is(ModBlocks.SWAMP_BUSH.get())
                    || state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.LOWER) continue;

            // Nearby foliage can fill a candidate spawn point. Retry locally,
            // but never multiply the number of fireflies emitted by one bush.
            for (int attempt = 0; attempt < 5; ++attempt) {
                double x = bush.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 5.0D;
                double y = bush.getY() + 0.6D + level.random.nextDouble() * 3.0D;
                double z = bush.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 5.0D;
                BlockPos at = BlockPos.containing(x, y, z);
                if (level.isOutsideBuildHeight(at) || !level.hasChunkAt(at)
                        || !level.getBlockState(at).isAir()) continue;
                // Create particles client-side to avoid the short distance
                // cutoff of the regular level.addParticle path.
                if (mc.particleEngine.createParticle(ModParticles.FIREFLY.get(), x, y, z, 0, 0, 0) != null) {
                    emitted++;
                }
                break;
            }
        }
        return emitted;
    }

    private static boolean movedFarFromScan(BlockPos origin) {
        if (lastScanOrigin == null) return true;
        long dx = origin.getX() - lastScanOrigin.getX();
        long dy = origin.getY() - lastScanOrigin.getY();
        long dz = origin.getZ() - lastScanOrigin.getZ();
        return dx * dx + dy * dy + dz * dz > 100L;
    }

    private static void scanBushes(ClientLevel level, BlockPos origin) {
        nearbyBushes.clear();
        outerBushes.clear();
        // Scan loaded world blocks directly, as in the known-working firefly build.
        // A moderate radius and scan throttle avoid excessive client work.
        int nearFound = 0;
        int outerFound = 0;
        int radiusSquared = SEARCH_RADIUS * SEARCH_RADIUS;
        int nearRadiusSquared = NEAR_RADIUS * NEAR_RADIUS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; ++dx) {
            int x = origin.getX() + dx;
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; ++dz) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radiusSquared) continue;
                int z = origin.getZ() + dz;
                // Avoid loading chunks as a side effect of a client particle scan.
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; ++dy) {
                    cursor.set(x, origin.getY() + dy, z);
                    if (level.isOutsideBuildHeight(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(ModBlocks.SWAMP_BUSH.get())
                            || state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                    if (distanceSquared <= nearRadiusSquared) {
                        ++nearFound;
                        cacheBush(nearbyBushes, cursor, nearFound, level);
                    } else {
                        ++outerFound;
                        cacheBush(outerBushes, cursor, outerFound, level);
                    }
                }
            }
        }
    }

    private static void cacheBush(List<BlockPos> cache, BlockPos pos, int seen, ClientLevel level) {
        if (cache.size() < MAX_BUSHES) cache.add(pos.immutable());
        else {
            int index = level.random.nextInt(seen);
            if (index < MAX_BUSHES) cache.set(index, pos.immutable());
        }
    }

    private ClientSwampFireflies() { }
}
