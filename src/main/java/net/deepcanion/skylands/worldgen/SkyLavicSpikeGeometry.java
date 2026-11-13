package net.deepcanion.skylands.worldgen;

import java.util.ArrayList;
import java.util.List;

/** Pure, deterministic spike backbone and radius sampling for procedural Lavic spires. */
public final class SkyLavicSpikeGeometry {
    private SkyLavicSpikeGeometry() { }

    public record Vec3(double x, double y, double z) {
        Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
        Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
        double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
        Vec3 normalize() {
            double len = length();
            return len < 1.0e-8 ? new Vec3(0, 1, 0) : new Vec3(x / len, y / len, z / len);
        }
    }

    public record Profile(Vec3 axis, Vec3 bendA, Vec3 bendB, double length,
                          double baseRadius, double tipRadius,
                          double bendAmpA, double bendAmpB, boolean blunt,
                          double skyPull, double skyCurveStart, double skyDrift) {
        public Vec3 point(double t) {
            double s = t * length;
            double arc = Math.sin(t * Math.PI);
            double twist = Math.sin(t * Math.PI * 2.0 + 0.6);
            double skyT = t <= skyCurveStart ? 0.0 : (t - skyCurveStart) / Math.max(0.08, 1.0 - skyCurveStart);
            skyT = Math.max(0.0, Math.min(1.0, skyT));
            double lift = skyPull * length * skyT * skyT * (0.55 + 0.45 * t);
            double drift = skyDrift * length * skyT * skyT;
            return axis.scale(s)
                    .add(bendA.scale(bendAmpA * arc + drift * 0.18))
                    .add(bendB.scale(bendAmpB * twist * (0.25 + 0.75 * t)))
                    .add(new Vec3(0.0, lift, 0.0));
        }
        public double radius(double t) {
            if (blunt) {
                double r = baseRadius + (tipRadius - baseRadius) * Math.pow(t, 0.72);
                return t > 0.92 ? Math.max(tipRadius * 0.92, r * 0.94) : r;
            }
            double tapered = Math.pow(t, 0.92);
            return baseRadius + (tipRadius - baseRadius) * tapered;
        }
        public int extent() {
            return (int)Math.ceil(length + Math.max(baseRadius, tipRadius)
                    + Math.abs(bendAmpA) + Math.abs(bendAmpB) + 3.0);
        }
        public Vec3 tip() { return point(1.0); }
    }

    public static Profile create(long seed, double nx, double ny, double nz,
                                 boolean underside, boolean side, boolean companion) {
        Vec3 axis = new Vec3(nx, ny, nz).normalize();
        Vec3 ref = Math.abs(axis.y) < 0.82 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 bendA = cross(axis, ref).normalize();
        Vec3 bendB = cross(axis, bendA).normalize();
        double length = companion
                ? 5.0 + unit(seed ^ 0x11L) * 8.5
                : 8.0 + unit(seed ^ 0x21L) * 15.0;
        if (underside) length *= 1.12 + unit(seed ^ 0x22L) * 0.20;
        if (side) length *= 1.04 + unit(seed ^ 0x23L) * 0.17;
        boolean blunt = unit(seed ^ 0x31L) < (companion ? 0.26 : 0.38);
        double baseRadius = companion
                ? 1.15 + unit(seed ^ 0x41L) * 1.55
                : 1.7 + unit(seed ^ 0x42L) * 2.55;
        double tipRadius = blunt
                ? 0.9 + unit(seed ^ 0x43L) * 0.95
                : 0.25 + unit(seed ^ 0x44L) * 0.55;
        double bendAmpA = (underside ? 1.5 : 1.0) + unit(seed ^ 0x51L) * (companion ? 0.7 : 1.6);
        double bendAmpB = 0.35 + unit(seed ^ 0x52L) * (companion ? 0.8 : 1.45);
        if (side && axis.y < -0.18) bendAmpA *= 1.08;
        double skyPull;
        double skyCurveStart;
        if (underside) {
            skyPull = 0.54 + unit(seed ^ 0x61L) * (companion ? 0.14 : 0.20);
            skyCurveStart = 0.34 + unit(seed ^ 0x62L) * 0.18;
        } else if (side) {
            skyPull = 0.38 + unit(seed ^ 0x63L) * (companion ? 0.16 : 0.24);
            skyCurveStart = 0.24 + unit(seed ^ 0x64L) * 0.16;
        } else {
            skyPull = 0.10 + unit(seed ^ 0x65L) * (companion ? 0.10 : 0.16);
            skyCurveStart = 0.42 + unit(seed ^ 0x66L) * 0.16;
        }
        double skyDrift = (unit(seed ^ 0x67L) - 0.5) * (companion ? 0.08 : 0.16);
        return new Profile(axis, bendA, bendB, length, baseRadius, tipRadius,
                bendAmpA, bendAmpB, blunt, skyPull, skyCurveStart, skyDrift);
    }

    /**
     * Rare landmark claws share the exact same curve as ordinary claws but
     * grow longer and heavier. Scaling the original immutable profile keeps
     * cross-chunk reconstruction deterministic and avoids extra site searches.
     */
    public static Profile giant(Profile original, double lengthScale, double radiusScale) {
        return new Profile(original.axis(), original.bendA(), original.bendB(),
                original.length() * lengthScale,
                original.baseRadius() * radiusScale,
                original.tipRadius() * radiusScale,
                original.bendAmpA() * lengthScale,
                original.bendAmpB() * lengthScale,
                original.blunt(), original.skyPull(), original.skyCurveStart(), original.skyDrift());
    }

    public static List<Vec3> backbone(Profile profile) {
        double step = 0.68;
        int count = Math.max(3, (int)Math.ceil(profile.length() / step));
        ArrayList<Vec3> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            double t = i / (double)count;
            points.add(profile.point(t));
        }
        return points;
    }

    public static boolean solid(Profile profile, int x, int y, int z) {
        double best = Double.POSITIVE_INFINITY;
        double bestRadius = 0.0;
        List<Vec3> points = backbone(profile);
        for (int i = 0; i < points.size(); i++) {
            double t = i / (double)Math.max(1, points.size() - 1);
            Vec3 p = points.get(i);
            double dx = x - p.x, dy = y - p.y, dz = z - p.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < best) {
                best = d2;
                bestRadius = profile.radius(t);
            }
        }
        return best <= bestRadius * bestRadius;
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }

    private static double unit(long hash) {
        return ((hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdL >>> 11) * 0x1.0p-53;
    }
}
