package dev.solarslayers.slayer;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.mixin.TextDisplayAccessor;
import dev.solarslayers.util.Txt;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
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
 * T3 pups (minions) {@code Moonfang 250/250❤}, unlike the area mobs {@code Feral Moonfang} / {@code Savage Moonfang};
 * Solar Flare minions {@code Solar Flare Minion 2.5k/2.5k❤}. Name matching lives in {@link MobNames}.
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
        List<LivingEntity> blazeMinions = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Display.TextDisplay td) {
                Component text = ((TextDisplayAccessor) td).solarslayers$getText();
                if (text != null && Txt.norm(text.getString()).contains(me)) myLabels.add(td.position());
                continue;
            }
            if (!(e instanceof LivingEntity le) || !le.hasCustomName() || le.getCustomName() == null) continue;
            String raw = le.getCustomName().getString();
            String name = stripWarded(Txt.clean(raw));
            if (MobNames.isBoss(raw)) {
                bosses.add(le);
            } else if (MobNames.isBlazeMinion(raw)) {
                blazeMinions.add(le);
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
            for (LivingEntity b : bosses) bossNames.add(MobNames.base(b.getCustomName().getString()));
            for (LivingEntity le : others) {
                if (!MobNames.isMinion(MobNames.base(le.getCustomName().getString()), bossNames, cfg.minionNames)) continue;
                if (nearMyBoss(le, cfg)) highlights.put(le.getId(), cfg.minionColor);
            }
        }
        if (cfg.blazeMinionHighlight) {
            for (LivingEntity le : blazeMinions) {
                if (nearMyBoss(le, cfg)) highlights.put(le.getId(), cfg.blazeMinionColor);
            }
        }
        // normal Solar slayer mobs (e.g. "Solar Blaze 5k/5k❤"): blazes and zombified piglins with a health nametag,
        // in the slayer world only; anything already coloured as a minion keeps that colour
        if (cfg.solarMobHighlight && SidebarState.INSTANCE.inSlayer) {
            for (LivingEntity le : others) {
                if (highlights.containsKey(le.getId())) continue;
                if (le.getType() != EntityTypes.BLAZE && le.getType() != EntityTypes.ZOMBIFIED_PIGLIN) continue;
                if (Txt.clean(le.getCustomName().getString()).contains("❤")) highlights.put(le.getId(), cfg.solarMobColor);
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

    /** With "Only My Boss" on, a minion counts only near the player's own boss. */
    private boolean nearMyBoss(LivingEntity le, SlayersConfig cfg) {
        return !cfg.onlyMyBoss || (myBoss != null && le.distanceToSqr(myBoss) <= MINION_RANGE_SQ);
    }

    private static boolean isMiniboss(String name, SlayersConfig cfg) {
        for (String m : cfg.minibossNames) {
            if (name.startsWith(m)) return true;
        }
        return false;
    }
}
