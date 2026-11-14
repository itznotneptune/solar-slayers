package dev.solarslayers.hud;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.config.SlayersConfig.HudPos;
import dev.solarslayers.config.SlayersConfig.Requirement;
import dev.solarslayers.config.SlayersConfig.TrackedItem;
import dev.solarslayers.gui.Draw;
import dev.solarslayers.slayer.EntityScanner;
import dev.solarslayers.slayer.ShopTracker;
import dev.solarslayers.slayer.SidebarState;
import dev.solarslayers.util.Txt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** The three HUD panels: slayer progress, pity meter and upgrade tracker. */
public final class SlayerHud {
    public enum Element {
        PROGRESS("Slayer Progress"), PITY("Pity Meter"), TRACKER("Upgrade Tracker");

        public final String label;

        Element(String label) {
            this.label = label;
        }

        public HudPos pos() {
            SlayersConfig c = SlayersConfig.get();
            return switch (this) {
                case PROGRESS -> c.progressPos;
                case PITY -> c.pityPos;
                case TRACKER -> c.trackerPos;
            };
        }

        public boolean enabled() {
            SlayersConfig c = SlayersConfig.get();
            return switch (this) {
                case PROGRESS -> c.progressHud;
                case PITY -> c.pityHud;
                case TRACKER -> c.trackerHud && c.shopTracker;
            };
        }
    }

    /** One line: text, colour, and an optional progress bar under it. */
    public record Line(String text, int color, double bar, int barColor) {
        static Line text(String t, int c) {
            return new Line(t, c, Double.NaN, 0);
        }

        static Line bar(String t, int c, double frac, int barColor) {
            return new Line(t, c, frac, barColor);
        }

        boolean hasBar() {
            return !Double.isNaN(bar);
        }
    }

    private static final int PAD = 5;
    private static final int LINE = 10;
    private static final int BAR_H = 3;
    public static final int MIN_W = 110;

    private SlayerHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SlayersConfig cfg = SlayersConfig.get();
        boolean inSlayer = SidebarState.INSTANCE.inSlayer;
        for (Element e : Element.values()) {
            if (!e.enabled()) continue;
            if (cfg.hudOnlyInSlayer && !inSlayer && e != Element.TRACKER) continue;
            List<Line> lines = lines(e, false);
            if (!lines.isEmpty()) draw(g, mc.font, e.pos(), lines);
        }
    }

    /** Draws a panel; returns its unscaled size {w, h}. */
    public static int[] draw(GuiGraphicsExtractor g, Font font, HudPos pos, List<Line> lines) {
        int[] size = measure(font, lines);
        g.pose().pushMatrix();
        g.pose().translate(pos.x, pos.y);
        g.pose().scale(pos.scale, pos.scale);
        Draw.panel(g, 0, 0, size[0], size[1], 0xC0101116, 0xA02E3040);
        g.fill(1, 2, 3, size[1] - 2, Draw.ACCENT);
        int y = PAD;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            g.text(font, l.text(), PAD + 3, y, l.color(), true);
            y += LINE;
            if (l.hasBar()) {
                Draw.bar(g, PAD + 3, y - 1, size[0] - PAD * 2 - 3, BAR_H, l.bar(), l.barColor());
                y += BAR_H + 2;
            }
            if (i == 0) y += 2; // breathing room under the title
        }
        g.pose().popMatrix();
        return size;
    }

    public static int[] measure(Font font, List<Line> lines) {
        int w = MIN_W;
        int h = PAD * 2 - 1;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            w = Math.max(w, font.width(l.text()) + PAD * 2 + 3);
            h += LINE + (l.hasBar() ? BAR_H + 2 : 0) + (i == 0 ? 2 : 0);
        }
        return new int[] {w, h};
    }

    public static List<Line> lines(Element e, boolean preview) {
        return switch (e) {
            case PROGRESS -> progress(preview);
            case PITY -> pity(preview);
            case TRACKER -> tracker(preview);
        };
    }

    private static List<Line> progress(boolean preview) {
        SidebarState s = SidebarState.INSTANCE;
        EntityScanner es = EntityScanner.INSTANCE;
        List<Line> out = new ArrayList<>();
        if (preview && !s.inSlayer) {
            out.add(Line.text("Moonfang Alpha II", Draw.GOLD));
            out.add(Line.bar("Boss in: 55/100 XP", Draw.TEXT, 0.55, Draw.ACCENT));
            out.add(Line.text("9 kills left", Draw.MUTED));
            return out;
        }
        if (!s.inSlayer) return out;
        if (s.bossAlive) {
            out.add(Line.text("☠ " + (s.quest == null ? "Slayer Boss" : s.quest), Draw.BAD));
            String hp = es.myBossHp != null && !Double.isNaN(s.bossHpMax)
                    ? es.myBossHp + "/" + Txt.fmt(s.bossHpMax) : s.bossHpText;
            double cur = es.myBossHp != null ? Txt.num(es.myBossHp) : s.bossHp;
            out.add(Line.bar("HP: " + hp + " ❤", Draw.TEXT, cur / s.bossHpMax, Draw.BAD));
            out.add(Line.text("Time left: " + s.bossTime, Draw.MUTED));
            if (es.myBossWarded) out.add(Line.text("WARDED – hit it with your weapon", Draw.PURPLE));
        } else if (s.quest != null) {
            out.add(Line.text(s.quest, Draw.GOLD));
            if (!Double.isNaN(s.xpNeed)) {
                out.add(Line.bar("Boss in: " + Txt.fmt(s.xpHave) + "/" + Txt.fmt(s.xpNeed) + " XP",
                        Draw.TEXT, s.xpHave / s.xpNeed, Draw.ACCENT));
                int perKill = Math.max(1, SlayersConfig.get().xpPerKill);
                long kills = (long) Math.ceil(Math.max(0, s.xpNeed - s.xpHave) / perKill);
                out.add(Line.text(kills + (kills == 1 ? " kill left" : " kills left"), Draw.MUTED));
            }
        } else if (s.noQuest) {
            out.add(Line.text("No active slayer quest", Draw.BAD));
            out.add(Line.text("Start one with /slayer", Draw.MUTED));
        }
        return out;
    }

    private static List<Line> pity(boolean preview) {
        SidebarState s = SidebarState.INSTANCE;
        List<Line> out = new ArrayList<>();
        if (preview && !s.inSlayer) {
            out.add(Line.text("Pity Meter", Draw.PURPLE));
            out.add(Line.text("250 Slayer Essence", Draw.TEXT));
            out.add(Line.bar("100/250 (40%)", Draw.MUTED, 0.4, 0xFFC08CFF));
            return out;
        }
        if (!s.inSlayer) return out;
        out.add(Line.text("Pity Meter", Draw.PURPLE));
        if (!s.pitySet) {
            out.add(Line.text("Not set – choose one with /pity", Draw.MUTED));
            return out;
        }
        out.add(Line.text(s.pityItem, Draw.TEXT));
        if (s.pityPct >= 0) {
            out.add(Line.bar(Txt.fmt(s.pityHave) + "/" + Txt.fmt(s.pityNeed) + " (" + s.pityPct + "%)",
                    Draw.MUTED, s.pityPct / 100.0, 0xFFC08CFF));
        }
        return out;
    }

    private static List<Line> tracker(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        List<TrackedItem> items = SlayersConfig.get().tracked;
        List<Line> out = new ArrayList<>();
        if (items.isEmpty()) {
            if (preview) {
                out.add(Line.text("Upgrade Tracker", Draw.GOLD));
                out.add(Line.text("MOONHIDE ARMOR", Draw.TEXT));
                out.add(Line.text("✔ Moonfang Alpha Lv 3", Draw.GOOD));
                out.add(Line.bar("✘ Essence 1,831/2,500", Draw.BAD, 0.73, Draw.ACCENT));
                out.add(Line.bar("✘ Moon Fang 314/512", Draw.BAD, 0.61, Draw.ACCENT));
            }
            return out;
        }
        out.add(Line.text("Upgrade Tracker", Draw.GOLD));
        for (TrackedItem t : items) {
            boolean all = true;
            List<Line> reqs = new ArrayList<>();
            for (Requirement r : t.requirements) {
                double have = ShopTracker.have(r, mc);
                boolean met = ShopTracker.met(r, have);
                all &= met;
                String mark = met ? "✔ " : "✘ ";
                int col = met ? Draw.GOOD : Draw.BAD;
                switch (r.kind) {
                    case LEVEL -> reqs.add(Line.text(mark + r.target + " Lv " + r.need
                            + (have >= 0 ? " (you: " + (int) have + ")" : ""), col));
                    case ESSENCE, ITEM -> {
                        String label = mark + r.target + " " + (have >= 0 ? Txt.fmt(Math.min(have, 1e15)) : "?") + "/" + Txt.fmt(r.need);
                        if (met || have < 0) reqs.add(Line.text(label, col));
                        else reqs.add(Line.bar(label, col, have / r.need, Draw.ACCENT));
                    }
                    case OWN, OTHER -> reqs.add(Line.text(mark + r.target, col));
                }
            }
            out.add(Line.text(t.name + (all ? "  – ready to craft!" : ""), all ? Draw.GOOD : Draw.TEXT));
            out.addAll(reqs);
        }
        return out;
    }
}
