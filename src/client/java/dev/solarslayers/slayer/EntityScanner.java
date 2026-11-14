package dev.solarslayers.slayer;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.mixin.TextDisplayAccessor;
import dev.solarslayers.util.Txt;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds slayer bosses and minibosses among loaded entities. Recorded naming (2026-11-14):
 * boss wolf {@code ☠ Moonfang Alpha 1.5k❤} (prefixed {@code WARDED } while warded), with a text display just above it
 * reading {@code ☠ Moonfang Alpha 1.5k❤\n03:00\nSpawned by: <player>}; miniboss {@code Moonlit Hunter 250/250❤};
 * T3 pups (minions) {@code Moonfang 250/250❤}, unlike the area mobs {@code Feral Moonfang} / {@code Savage Moonfang}.
 */
public final class EntityScanner {
    public static final EntityScanner INSTANCE = new EntityScanner();

    /** entity id → ARGB highlight colour; read from the render thread. */
    private final Map<Integer, Integer> highlights = new ConcurrentHashMap<>();

    /** The player's own boss, or null. */
    public LivingEntity myBoss;
    public boolean myBossWarded;
    public String myBossHp;      // "1.5k" from the nametag

    /** Minions count as "near my boss" within this distance (squared). */
    private static final double MINION_RANGE_SQ = 24 * 24;

    private EntityScanner() {}

    public Integer colorFor(int entityId) {
        return highlights.get(entityId);
    }

    public void update(Minecraft mc) {
        highlights.clear();
        myBoss = null;
        myBossWarded = false;
        myBossHp = null;
        if (mc.level == null || mc.player == null) return;
        SlayersConfig cfg = SlayersConfig.get();
        String me = "spawned by: " + mc.player.getName().getString().toLowerCase(Locale.ROOT);

        List<Vec3> myLabels = new ArrayList<>();
        List<LivingEntity> bosses = new ArrayList<>();
        List<LivingEntity> others = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Display.TextDisplay td) {
                Component text = ((TextDisplayAccessor) td).solarslayers$getText();
                if (text != null && Txt.norm(text.getString()).contains(me)) myLabels.add(td.position());
                continue;
            }
            if (!(e instanceof LivingEntity le) || !le.hasCustomName() || le.getCustomName() == null) continue;
            String name = stripWarded(Txt.clean(le.getCustomName().getString()));
            if (name.startsWith("☠")) {
                bosses.add(le);
            } else if (isMiniboss(name, cfg)) {
                if (cfg.minibossHighlight) highlights.put(le.getId(), cfg.minibossColor);
            } else {
                others.add(le);
            }
        }

        for (Vec3 label : myLabels) {
            LivingEntity best = null;
            double bestD = 9.0; // within 3 blocks of its label
            for (LivingEntity b : bosses) {
                double d = b.position().distanceToSqr(label);
                if (d < bestD) { bestD = d; best = b; }
            }
            if (best != null) { myBoss = best; break; }
        }
        if (myBoss != null) {
            String raw = Txt.clean(myBoss.getCustomName().getString());
            myBossWarded = raw.startsWith("WARDED");
            int heart = raw.lastIndexOf('❤');
            int space = raw.lastIndexOf(' ', Math.max(0, heart));
            if (heart > 0 && space >= 0) myBossHp = raw.substring(space + 1, heart);
        }

        if (cfg.minionHighlight && !others.isEmpty()) {
            List<String> bossNames = new ArrayList<>();
            for (LivingEntity b : bosses) bossNames.add(baseName(stripWarded(Txt.clean(b.getCustomName().getString()))));
            for (LivingEntity le : others) {
                String base = baseName(stripWarded(Txt.clean(le.getCustomName().getString())));
                if (!isMinion(base, bossNames, cfg.minionNames)) continue;
                if (cfg.onlyMyBoss && (myBoss == null || le.distanceToSqr(myBoss) > MINION_RANGE_SQ)) continue;
                highlights.put(le.getId(), cfg.minionColor);
            }
        }

        if (cfg.bossHighlight) {
            if (cfg.onlyMyBoss) {
                if (myBoss != null) highlights.put(myBoss.getId(), cfg.bossColor);
            } else {
                for (LivingEntity b : bosses) highlights.put(b.getId(), cfg.bossColor);
            }
        }
    }

    private static String stripWarded(String name) {
        return name.startsWith("WARDED ") ? name.substring(7).trim() : name;
    }

    /** {@code ☠ Moonfang Alpha 5k❤} → {@code Moonfang Alpha}; {@code Moonfang 250/250❤} → {@code Moonfang}. */
    static String baseName(String name) {
        String n = name.startsWith("☠") ? name.substring(1).trim() : name;
        int space = n.lastIndexOf(' ');
        if (n.endsWith("❤") && space > 0) n = n.substring(0, space);
        return n.trim();
    }

    /**
     * A known minion name, or a name the boss's own name starts with ("Moonfang" for the "Moonfang Alpha"), so other
     * slayers' minions are picked up without a list. Whole-name match only, so "Feral Moonfang" is never a minion.
     */
    static boolean isMinion(String base, List<String> bossNames, List<String> known) {
        if (base.isEmpty()) return false;
        for (String m : known) {
            if (base.equalsIgnoreCase(m)) return true;
        }
        String prefix = base + " ";
        for (String b : bossNames) {
            if (b.regionMatches(true, 0, prefix, 0, prefix.length())) return true;
        }
        return false;
    }

    private static boolean isMiniboss(String name, SlayersConfig cfg) {
        for (String m : cfg.minibossNames) {
            if (name.startsWith(m)) return true;
        }
        return false;
    }
}
