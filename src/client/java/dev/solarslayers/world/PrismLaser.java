package dev.solarslayers.world;

import dev.solarslayers.config.SlayersConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Replaces the Prism Laser's rainbow particle storm with a quiet hollow outline. Matching packets are dropped before
 * they spawn anything ({@link LaserMatch}); the beam's centre line is rebuilt from its core stations, and four thin
 * lines plus a ring at each end are drawn round it with client-side dust. Applies to every player's laser.
 *
 * <p>The beam stays where it was fired (only its far end moves), lasts 6 ticks, and arrives as one batch per tick.
 * Lag can bunch several ticks into one, so beams are matched by geometry, never by game time.
 */
public final class PrismLaser {
    /** Radius of the outline, the same as the laser's own helix. */
    static final double RADIUS = 0.7;
    private static final int LINES = 4;
    private static final double LINE_STEP = 0.5;
    private static final int RING_POINTS = 16;
    private static final float DOT_SCALE = 0.6f;
    /** Ticks without a core station before the outline is removed; the laser sends one batch per tick. */
    private static final int LINGER = 2;
    /** Longest an outline may live, in case the end of the beam is never seen. */
    private static final int MAX_AGE = 30;
    /** Stations are 0.3 apart; anything closer than this belongs to the same beam. */
    private static final double STATION_GAP = 0.45;
    /** How far a station may sit from a known beam's centre line and still belong to it. */
    private static final double ON_LINE = 0.15;

    private static final List<Vec3> cores = new ArrayList<>();
    private static final List<Beam> beams = new ArrayList<>();
    private static final List<Deferred> emitters = new ArrayList<>();
    private static boolean signatureNow;
    private static boolean signatureBefore;
    private static int tick;

    private PrismLaser() {}

    /** Called on the client thread for each particle packet; true means drop it. */
    public static boolean onPacket(ClientboundLevelParticlesPacket p) {
        if (!SlayersConfig.get().prismLaserOutline) return false;
        // the only line that reads the packet: its getters differ between Minecraft versions
        return onParticle(p.getParticle(), p.getX(), p.getY(), p.getZ(), p.getCount(), p.getXDist(), p.getYDist(), p.getZDist(), p.getMaxSpeed());
    }

    private static boolean onParticle(ParticleOptions o, double x, double y, double z, int count,
                                      float dx, float dy, float dz, float speed) {
        LaserMatch.Type type;
        float scale = 0, r = 0, g = 0, b = 0;
        if (o instanceof DustParticleOptions d) {
            type = LaserMatch.Type.DUST;
            Vector3f c = d.getColor();
            r = c.x();
            g = c.y();
            b = c.z();
            scale = d.getScale();
        } else if (o.getType() == ParticleTypes.END_ROD) {
            type = LaserMatch.Type.END_ROD;
        } else if (o.getType() == ParticleTypes.EXPLOSION_EMITTER) {
            type = LaserMatch.Type.EXPLOSION_EMITTER;
        } else {
            return false;
        }
        LaserMatch.Kind kind = LaserMatch.classify(type, scale, r, g, b, count, dx, dy, dz, speed);
        switch (kind) {
            case CORE -> cores.add(new Vec3(x, y, z));
            case SIGNATURE -> signatureNow = true;
            case EMITTER -> emitters.add(new Deferred(o, x, y, z, tick));
            case BEAM -> { }
            case NONE -> { return false; }
        }
        return true;
    }

    public static void tick(Minecraft mc) {
        if (!SlayersConfig.get().prismLaserOutline || mc.level == null) {
            clear();
            return;
        }
        if (!cores.isEmpty()) signatureNow = true;
        trackBeams(mc);
        replayEmitters(mc);
        signatureBefore = signatureNow;
        signatureNow = false;
        tick++;
    }

    /** An explosion_emitter is the laser's impact only if a laser signature came in the same tick or next to it. */
    private static void replayEmitters(Minecraft mc) {
        boolean laser = signatureNow || signatureBefore;
        Iterator<Deferred> it = emitters.iterator();
        while (it.hasNext()) {
            Deferred e = it.next();
            if (laser) {
                it.remove();
            } else if (tick - e.tick >= 1) {
                it.remove();
                mc.particleEngine.createParticle(e.options, e.x, e.y, e.z, 0, 0, 0);
            }
        }
    }

    private static void trackBeams(Minecraft mc) {
        // stations on an outline that is already drawn extend or shorten it
        for (Beam beam : beams) beam.seenLen = -1;
        List<Vec3> loose = new ArrayList<>();
        for (Vec3 c : cores) {
            Beam owner = null;
            for (Beam beam : beams) {
                if (beam.dir != null && beam.along(c) > -0.3 && beam.offAxis(c) <= ON_LINE) {
                    owner = beam;
                    break;
                }
            }
            if (owner == null) loose.add(c);
            else {
                owner.seenLen = Math.max(owner.seenLen, owner.along(c));
                owner.lastSeen = tick;
            }
        }
        cores.clear();

        for (List<Vec3> group : cluster(loose)) {
            Vec3 a = farthest(group, group.get(0));
            Vec3 b = farthest(group, a);
            Beam known = null;
            for (Beam beam : beams) {
                if (beam.dir == null && (beam.start.distanceTo(a) < 0.25 || beam.start.distanceTo(b) < 0.25)) known = beam;
            }
            if (known != null) {
                // a beam first seen as a single station now has a direction
                Vec3 end = known.start.distanceTo(a) < 0.25 ? b : a;
                if (end.distanceTo(known.start) > 0.01) known.dir = end.subtract(known.start).normalize();
                known.seenLen = end.distanceTo(known.start);
                known.lastSeen = tick;
                continue;
            }
            // the start is the end next to a shooter: 1.1 blocks in front of their eyes, 0.3 lower
            boolean aStarts = shooterDistance(mc, a) <= shooterDistance(mc, b);
            Beam beam = new Beam(aStarts ? a : b, tick);
            Vec3 end = aStarts ? b : a;
            if (end.distanceTo(beam.start) > 0.01) beam.dir = end.subtract(beam.start).normalize();
            beam.seenLen = end.distanceTo(beam.start);
            beams.add(beam);
        }

        Iterator<Beam> it = beams.iterator();
        while (it.hasNext()) {
            Beam beam = it.next();
            if (tick - beam.lastSeen > LINGER || tick - beam.born > MAX_AGE) {
                beam.erase();
                it.remove();
            } else if (beam.seenLen >= 0 && Math.abs(beam.seenLen - beam.drawnLen) > 0.15) {
                beam.draw(mc, beam.seenLen);
            }
        }
    }

    private static double shooterDistance(Minecraft mc, Vec3 point) {
        double best = Double.MAX_VALUE;
        for (Player pl : mc.level.players()) {
            Vec3 start = pl.getEyePosition().add(pl.getViewVector(1f).scale(1.1)).add(0, -0.3, 0);
            best = Math.min(best, start.distanceTo(point));
        }
        return best;
    }

    /** Splits points into chains whose neighbours are at most one station gap apart. */
    static List<List<Vec3>> cluster(List<Vec3> points) {
        List<List<Vec3>> groups = new ArrayList<>();
        boolean[] used = new boolean[points.size()];
        for (int i = 0; i < points.size(); i++) {
            if (used[i]) continue;
            used[i] = true;
            List<Vec3> group = new ArrayList<>();
            group.add(points.get(i));
            for (int k = 0; k < group.size(); k++) {
                Vec3 q = group.get(k);
                for (int j = 0; j < points.size(); j++) {
                    if (!used[j] && points.get(j).distanceToSqr(q) <= STATION_GAP * STATION_GAP) {
                        used[j] = true;
                        group.add(points.get(j));
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private static Vec3 farthest(List<Vec3> group, Vec3 from) {
        Vec3 best = from;
        double d = -1;
        for (Vec3 v : group) {
            double e = v.distanceToSqr(from);
            if (e > d) {
                d = e;
                best = v;
            }
        }
        return best;
    }

    private static void clear() {
        for (Beam beam : beams) beam.erase();
        beams.clear();
        cores.clear();
        emitters.clear();
        signatureNow = signatureBefore = false;
    }

    private record Deferred(ParticleOptions options, double x, double y, double z, int tick) {}

    private static final class Beam {
        final Vec3 start;
        final int born;
        Vec3 dir;
        int lastSeen;
        double seenLen = -1;
        double drawnLen = -1;
        final List<Particle> dots = new ArrayList<>();

        Beam(Vec3 start, int born) {
            this.start = start;
            this.born = born;
            this.lastSeen = born;
        }

        double along(Vec3 p) {
            return p.subtract(start).dot(dir);
        }

        double offAxis(Vec3 p) {
            Vec3 rel = p.subtract(start);
            return rel.subtract(dir.scale(rel.dot(dir))).length();
        }

        void draw(Minecraft mc, double len) {
            erase();
            drawnLen = len;
            Vec3 d = dir != null ? dir : new Vec3(0, -1, 0);
            Vec3 helper = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 u = d.cross(helper).normalize();
            Vec3 v = d.cross(u).normalize();
            SlayersConfig c = SlayersConfig.get();

            for (int line = 0; line < LINES; line++) {
                double ang = 2 * Math.PI * line / LINES;
                Vec3 off = u.scale(Math.cos(ang) * RADIUS).add(v.scale(Math.sin(ang) * RADIUS));
                for (double s = 0; s <= len + 1e-6; s += LINE_STEP) dot(mc, start.add(d.scale(s)).add(off), s, c);
            }
            for (double s : new double[] {0, len}) {
                Vec3 centre = start.add(d.scale(s));
                for (int i = 0; i < RING_POINTS; i++) {
                    double ang = 2 * Math.PI * i / RING_POINTS;
                    dot(mc, centre.add(u.scale(Math.cos(ang) * RADIUS)).add(v.scale(Math.sin(ang) * RADIUS)), s, c);
                }
            }
        }

        private void dot(Minecraft mc, Vec3 at, double along, SlayersConfig c) {
            // the laser's own hue runs 17.65° per block; keep that, but still and softer
            int rgb = c.prismLaserRainbow ? LaserMatch.hsv(9.12 + 17.65 * along, 0.55) : c.prismLaserColor & 0xFFFFFF;
            Particle p = mc.particleEngine.createParticle(new DustParticleOptions(rgb, DOT_SCALE), at.x, at.y, at.z, 0, 0, 0);
            if (p == null) return;
            p.setParticleSpeed(0, 0, 0);
            p.setLifetime(MAX_AGE + LINGER + 5);
            if (p instanceof SingleQuadParticle q) {
                q.setColor((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
            }
            dots.add(p);
        }

        void erase() {
            for (Particle p : dots) p.remove();
            dots.clear();
        }
    }
}
