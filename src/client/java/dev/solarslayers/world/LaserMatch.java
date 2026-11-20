package dev.solarslayers.world;

/**
 * Recognises the Prism Laser's server particles from their parameters alone (no Minecraft classes, so it can be
 * checked outside the game). The numbers come from the 2026-11-21 recording: every laser packet is dust with
 * {@code maxSpeed} 0 and a rainbow colour whose lowest channel is exactly 0.2 and highest exactly 1.0, and each layer
 * has its own dust scale. Nothing else in a 402k-packet capture matched these.
 */
public final class LaserMatch {
    public enum Type { DUST, END_ROD, EXPLOSION_EMITTER, OTHER }

    public enum Kind {
        /** Not the laser. */
        NONE,
        /** A core station on the beam's centre line (scale 2.64); used to rebuild the beam. */
        CORE,
        /** Hidden: helix (1.54), travelling rings (1.76), end spray and impact ring (1.98). */
        BEAM,
        /** Hidden, and proves a laser was fired this tick: impact burst, muzzle ring, the laser's end_rods. */
        SIGNATURE,
        /** A plain explosion_emitter; hidden only when a laser signature arrives with it. */
        EMITTER
    }

    private LaserMatch() {}

    public static Kind classify(Type type, float scale, float r, float g, float b, int count,
                                float dx, float dy, float dz, float speed) {
        switch (type) {
            case DUST -> {
                if (!near(speed, 0)) return Kind.NONE;
                boolean spreadNone = near(dx, 0) && near(dy, 0) && near(dz, 0);
                if (rainbow(r, g, b)) {
                    if (near(scale, 2.64f) && count == 2 && near(dx, 0.06f) && near(dy, 0.06f) && near(dz, 0.06f)) return Kind.CORE;
                    if ((near(scale, 1.54f) || near(scale, 1.76f) || near(scale, 1.98f)) && count == 1 && spreadNone) return Kind.BEAM;
                    if (near(scale, 2.2f) && count == 2 && near(dx, 1.2f) && near(dy, 1.2f) && near(dz, 1.2f)) return Kind.SIGNATURE;
                    return Kind.NONE;
                }
                // muzzle ring: fixed pink, scale 1.2, round the shooter's feet
                if (near(scale, 1.2f) && count == 1 && spreadNone && near(r, 1f) && near(g, 0.4f) && near(b, 0.8784f)) return Kind.SIGNATURE;
                return Kind.NONE;
            }
            case END_ROD -> {
                if (count == 35 && near(dx, 0.2f) && near(dy, 0.2f) && near(dz, 0.2f) && near(speed, 0.25f)) return Kind.SIGNATURE;
                if (count == 8 && near(dx, 0.3f) && near(dy, 0.6f) && near(dz, 0.3f) && near(speed, 0.02f)) return Kind.SIGNATURE;
                if (count == 92 && near(dx, 0.5f) && near(dy, 0.5f) && near(dz, 0.5f) && near(speed, 0.45f)) return Kind.SIGNATURE;
                return Kind.NONE;
            }
            case EXPLOSION_EMITTER -> {
                return count <= 1 && near(dx, 0) && near(dy, 0) && near(dz, 0) && near(speed, 0) ? Kind.EMITTER : Kind.NONE;
            }
            default -> {
                return Kind.NONE;
            }
        }
    }

    /** Saturated rainbow as the laser sends it: lowest channel 0.2, highest 1.0 (colours are quantised to /255). */
    static boolean rainbow(float r, float g, float b) {
        float min = Math.min(r, Math.min(g, b));
        float max = Math.max(r, Math.max(g, b));
        return Math.abs(min - 0.2f) <= 0.004f && max >= 0.996f;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 0.006f;
    }

    /** Opaque RGB for a hue in degrees at the given saturation, full brightness. */
    public static int hsv(double hueDeg, double sat) {
        double h = ((hueDeg % 360) + 360) % 360 / 60;
        int i = (int) h;
        double f = h - i;
        double p = 1 - sat, q = 1 - sat * f, t = 1 - sat * (1 - f);
        double r, g, b;
        switch (i) {
            case 0 -> { r = 1; g = t; b = p; }
            case 1 -> { r = q; g = 1; b = p; }
            case 2 -> { r = p; g = 1; b = t; }
            case 3 -> { r = p; g = q; b = 1; }
            case 4 -> { r = t; g = p; b = 1; }
            default -> { r = 1; g = p; b = q; }
        }
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }
}
