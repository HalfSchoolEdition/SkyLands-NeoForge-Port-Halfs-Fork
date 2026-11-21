package net.deepcanion.skylands.client;

import java.util.ArrayList;
import java.util.List;
import net.deepcanion.skylands.registry.ModBlocks;
import net.deepcanion.skylands.registry.ModItems;
import net.deepcanion.skylands.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bounded, client-only reveal sparkles. No block entities, server ticks, or persistent scanning. */
public final class ClientCloudStaff {
    private static final int REVEAL_INTERVAL_TICKS = 10;
    private static final int HORIZONTAL_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 12;
    private static final int MAX_INDICATORS = 24;
    private static final int SPARKLES_PER_BLOCK = 2;
    private static int tickCount;
    private static ClientLevel lastLevel;
    private static final int SCAN_INTERVAL_TICKS = 30;
    private static final int CACHED_SOURCES = 64;
    private static final List<BlockPos> nearbySources = new ArrayList<>();

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != lastLevel) {
            lastLevel = level;
            tickCount = 0;
            nearbySources.clear();
        }
        if (level == null || minecraft.player == null || minecraft.isPaused()) return;
        // Minecraft selection contexts check the main-hand item (as with vanilla Light Blocks).
        if (!minecraft.player.getMainHandItem().is(ModItems.CLOUD_STAFF.get())) return;
        ++tickCount;
        if (tickCount % REVEAL_INTERVAL_TICKS != 0) return;

        BlockPos origin = minecraft.player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (tickCount % SCAN_INTERVAL_TICKS == 0) {
            nearbySources.clear();
        int found = 0;
        // Scan a small fixed region only while a staff is held. Reservoir sampling
        // avoids favoring the first clouds if there are more than the particle cap.
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; ++dx) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; ++dz) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; ++dy) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)
                            || !level.getBlockState(cursor).is(ModBlocks.CLOUD_BLOCK.get())) continue;
                    ++found;
                    if (nearbySources.size() < CACHED_SOURCES) nearbySources.add(cursor.immutable());
                    else {
                        int slot = level.random.nextInt(found);
                        if (slot < CACHED_SOURCES) nearbySources.set(slot, cursor.immutable());
                    }
                }
            }
        }
        }
        // The reveal effect is local to the staff-holder. Ordinary cloud
        // puffs retain the original rate, size and appearance for everyone.
        int emitted = 0;
        for (BlockPos pos : nearbySources) {
            if (emitted >= MAX_INDICATORS) break;
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(ModBlocks.CLOUD_BLOCK.get())) continue;
            if (level.random.nextInt(3) == 0) continue;
            emitted++;
            for (int i = 0; i < SPARKLES_PER_BLOCK; i++) {
                minecraft.particleEngine.createParticle(ModParticles.CLOUD_STAFF_SPARKLE.get(),
                        pos.getX() + 0.1 + level.random.nextDouble() * 0.8,
                        pos.getY() + 0.1 + level.random.nextDouble() * 0.8,
                        pos.getZ() + 0.1 + level.random.nextDouble() * 0.8,
                        (level.random.nextDouble() - 0.5) * 0.003,
                        0.003 + level.random.nextDouble() * 0.007,
                        (level.random.nextDouble() - 0.5) * 0.003);
            }
        }
    }

    private ClientCloudStaff() { }
}
