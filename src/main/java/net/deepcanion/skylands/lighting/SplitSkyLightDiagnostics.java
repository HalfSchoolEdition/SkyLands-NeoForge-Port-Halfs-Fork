package net.deepcanion.skylands.lighting;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.slf4j.Logger;

/**
 * Low-noise, in-game evidence for intermittent split-skylight storage faults.
 * Logging is deliberately restricted to anomalous/missing storage accesses;
 * normal lighting reads and writes do not allocate, synchronize or log.
 *
 * Separate counters and power-of-two sampling preserve the first occurrence
 * of each problem without filling latest.log during long play sessions.
 */
public final class SplitSkyLightDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentMap<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();

    private SplitSkyLightDiagnostics() { }

    /** A vanilla engine pass reached a sparse section; often harmless. */
    public static void skippedAbsentSection(String operation, long packedPos) {
        emit("guard/" + operation, packedPos, false, false, false);
    }

    /** A DataLayer was *actually null at the Java get/set invocation*. */
    public static void nullDataLayer(String operation, long packedPos,
            boolean sectionReportedPresent, boolean layerReportedPresent) {
        emit("NULL_DATALAYER/" + operation, packedPos,
                sectionReportedPresent, layerReportedPresent, true);
    }

    private static void emit(String kind, long packedPos, boolean sectionReportedPresent,
            boolean layerReportedPresent, boolean unexpected) {
        long count = COUNTS.computeIfAbsent(kind, ignored -> new AtomicLong()).incrementAndGet();
        // Log the first four hits and subsequently only on powers of two.
        if (count > 4 && (count & (count - 1)) != 0) return;

        long section = SectionPos.blockToSection(packedPos);
        String message = "[SkyLands/SplitSkyLight] " + kind + " count=" + count
                + " block=(" + BlockPos.getX(packedPos) + "," + BlockPos.getY(packedPos)
                + "," + BlockPos.getZ(packedPos) + ") section=("
                + SectionPos.x(section) + "," + SectionPos.y(section) + ","
                + SectionPos.z(section) + ") thread=" + Thread.currentThread().getName();

        if (unexpected) {
            // The storage status is a second, diagnostic snapshot, not a lock
            // or a guarantee about what the earlier vanilla read observed.
            StringBuilder trace = new StringBuilder();
            int included = 0;
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                String owner = frame.getClassName();
                if (owner.startsWith("java.lang.Thread")
                        || owner.equals(SplitSkyLightDiagnostics.class.getName())) continue;
                if (included++ == 8) break;
                if (!trace.isEmpty()) trace.append(" <- ");
                trace.append(frame.getClassName()).append('.').append(frame.getMethodName());
            }
            LOGGER.warn("{} sectionReportedPresent={} layerSnapshotPresent={} stack={}",
                    message, sectionReportedPresent, layerReportedPresent, trace);
        } else {
            LOGGER.info("{} (sparse sky-air section; sampled)", message);
        }
    }
}
