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
 * reading {@code ☠ Moonfang Alpha 1.5k❤\n03:00\nSpawned by: <player>}; miniboss {@code Moonlit Hunter 250/250❤}.
 */
public final class EntityScanner {
    public static final EntityScanner INSTANCE = new EntityScanner();

    /** entity id → ARGB highlight colour; read from the render thread. */
    private final Map<Integer, Integer> highlights = new ConcurrentHashMap<>();

    /** The player's own boss, or null. */
    public LivingEntity myBoss;
    public boolean myBossWarded;
    public String myBossHp;      // "1.5k" from the nametag

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
            } else if (cfg.minibossHighlight && isMiniboss(name, cfg)) {
                highlights.put(le.getId(), cfg.minibossColor);
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

    private static boolean isMiniboss(String name, SlayersConfig cfg) {
        for (String m : cfg.minibossNames) {
            if (name.startsWith(m)) return true;
        }
        return false;
    }
}
