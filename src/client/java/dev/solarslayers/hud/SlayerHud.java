package dev.solarslayers.hud;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.config.SlayersConfig.HudPos;
import dev.solarslayers.config.SlayersConfig.Requirement;
import dev.solarslayers.config.SlayersConfig.TrackedItem;
import dev.solarslayers.gui.Draw;
import dev.solarslayers.slayer.EntityScanner;
import dev.solarslayers.slayer.SessionStats;
import dev.solarslayers.slayer.ShopTracker;
import dev.solarslayers.slayer.SidebarState;
import dev.solarslayers.slayer.SlayerTimer;
import dev.solarslayers.util.Txt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** The HUD panels: slayer progress, pity meter, upgrade tracker, slayer timer, RNG drops and session stats. */
public final class SlayerHud {
    public enum Element {
        PROGRESS("Slayer Progress"), PITY("Pity Meter"), TRACKER("Upgrade Tracker"), TIMER("Slayer Timer"),
        DROPS("RNG Drops"), STATS("Session Stats");

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
                case TIMER -> c.timerPos;
                case DROPS -> c.dropsPos;
                case STATS -> c.statsPos;
            };
        }

        public boolean enabled() {
            SlayersConfig c = SlayersConfig.get();
            return switch (this) {
                case PROGRESS -> c.progressHud;
                case PITY -> c.pityHud;
                case TRACKER -> c.trackerHud && c.shopTracker;
                case TIMER -> c.timerHud;
                case DROPS -> c.dropsHud;
                case STATS -> c.statsHud;
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

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
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
    public static int[] draw(GuiGraphics g, Font font, HudPos pos, List<Line> lines) {
        int[] size = measure(font, lines);
        g.pose().pushMatrix();
        g.pose().translate(pos.x, pos.y);
        g.pose().scale(pos.scale, pos.scale);
        Draw.panel(g, 0, 0, size[0], size[1], 0xC0101116, 0xA02E3040);
        g.fill(1, 2, 3, size[1] - 2, Draw.ACCENT);
        int y = PAD;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            g.drawString(font, l.text(), PAD + 3, y, l.color(), true);
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
            case TIMER -> timer(preview);
            case DROPS -> drops(preview);
            case STATS -> stats(preview);
        };
    }

    private static final int MAX_DROP_ITEMS = 6;

    static int rarityColor(SessionStats.Rarity r) {
        return switch (r) {
            case DROP -> Draw.TEXT;
            case RARE -> 0xFF5599FF;
            case VERY_RARE -> Draw.PURPLE;
            case CRAZY_RARE -> Draw.GOLD;
            case PITY -> 0xFFC08CFF;
        };
    }

    private static List<Line> drops(boolean preview) {
        SessionStats st = SessionStats.INSTANCE;
        List<Line> out = new ArrayList<>();
        if (st.totalDrops() == 0) {
            if (preview) {
                out.add(Line.text("RNG Drops", Draw.GOLD));
                out.add(Line.text("Drop 14 · Rare 3 · Very Rare 1 · Crazy 0", Draw.MUTED));
                out.add(Line.text("Moon Fang Talisman ×1", rarityColor(SessionStats.Rarity.VERY_RARE)));
                out.add(Line.text("Gen Slots ×12", Draw.TEXT));
                out.add(Line.text("Last: Rare Moonhide (+50% Luck)", Draw.MUTED));
            }
            return out;
        }
        out.add(Line.text("RNG Drops", Draw.GOLD));
        out.add(Line.text("Drop " + st.drops(SessionStats.Rarity.DROP)
                + " · Rare " + st.drops(SessionStats.Rarity.RARE)
                + " · Very Rare " + st.drops(SessionStats.Rarity.VERY_RARE)
                + " · Crazy " + st.drops(SessionStats.Rarity.CRAZY_RARE)
                + (st.drops(SessionStats.Rarity.PITY) > 0 ? " · Pity " + st.drops(SessionStats.Rarity.PITY) : ""), Draw.MUTED));
        List<SessionStats.ItemCount> items = st.items();
        for (int i = 0; i < Math.min(MAX_DROP_ITEMS, items.size()); i++) {
            SessionStats.ItemCount it = items.get(i);
            out.add(Line.text(it.name + " ×" + it.count, rarityColor(it.best)));
        }
        if (items.size() > MAX_DROP_ITEMS) out.add(Line.text("+" + (items.size() - MAX_DROP_ITEMS) + " more", Draw.MUTED));
        SessionStats.Drop last = st.last();
        if (last != null) {
            out.add(Line.text("Last: " + (last.rarity() == SessionStats.Rarity.DROP ? ""
                    : last.rarity() == SessionStats.Rarity.PITY ? "Pity payout: " : last.rarity().label + " ")
                    + last.item() + (last.luck() == null ? "" : " (" + last.luck() + ")"), Draw.MUTED));
        }
        return out;
    }

    private static List<Line> stats(boolean preview) {
        SessionStats st = SessionStats.INSTANCE;
        long now = System.currentTimeMillis();
        List<Line> out = new ArrayList<>();
        boolean empty = st.mobKills() == 0 && st.bossKills() == 0 && st.genSlots() == 0;
        if (empty && !preview) return out;
        out.add(Line.text("Session Stats", Draw.GOLD));
        if (empty) {
            out.add(Line.text("Active: 42:10", Draw.TEXT));
            out.add(Line.text("Mob kills: 318 · Bosses: 9", Draw.GOOD));
            out.add(Line.text("Gen slots: +120", Draw.TEXT));
            return out;
        }
        boolean paused = st.paused(now);
        out.add(Line.text("Active: " + SlayerTimer.clock(st.activeMs(now)) + (paused ? " (paused)" : ""),
                paused ? Draw.MUTED : Draw.TEXT));
        out.add(Line.text("Mob kills: " + st.mobKills() + " · Bosses: " + st.bossKills(), Draw.GOOD));
        out.add(Line.text("Gen slots: +" + st.genSlots(), Draw.TEXT));
        return out;
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
            out.add(Line.bar(SlayersConfig.get().pityShowBosses ? "6 bosses left" : "100/250 (40%)",
                    Draw.MUTED, 0.4, 0xFFC08CFF));
            return out;
        }
        if (!s.inSlayer) return out;
        out.add(Line.text("Pity Meter", Draw.PURPLE));
        if (!s.pitySet) {
            out.add(Line.text("Not set – choose one with /pity", Draw.MUTED));
            // the sidebar does not show the stored XP; the kill summary does ("Pity Meter - Not set (66k XP saved)")
            Double saved = SlayersConfig.get().pitySaved.get(s.pityKey());
            out.add(saved != null ? Line.text(Txt.fmt(saved) + " XP saved", Draw.GOLD)
                    : Line.text("XP saved: shown after your next kill", Draw.MUTED));
            return out;
        }
        out.add(Line.text(s.pityItem, Draw.TEXT));
        if (s.pityPct >= 0) {
            String label = Txt.fmt(s.pityHave) + "/" + Txt.fmt(s.pityNeed) + " (" + s.pityPct + "%)";
            if (SlayersConfig.get().pityShowBosses) {
                // the meter fills by the boss's Slayer XP (recorded: +25 Slayer XP took it from 100/250 to 125/250);
                // until a kill has shown that amount, the numbers stay
                long left = bossesLeft(s.pityHave, s.pityNeed, SlayersConfig.get().bossXp.get(s.pityKey()));
                if (left == 0) label = "Meter full";
                else if (left > 0) label = left + (left == 1 ? " boss left" : " bosses left");
            }
            out.add(Line.bar(label, Draw.MUTED, s.pityPct / 100.0, 0xFFC08CFF));
        }
        return out;
    }

    /** Boss kills until the pity meter is full, or -1 when unknown. 0 means it is already full. */
    static long bossesLeft(double have, double need, Double perBoss) {
        if (perBoss == null || perBoss <= 0 || Double.isNaN(have) || Double.isNaN(need)) return -1;
        return (long) Math.ceil(Math.max(0, need - have) / perBoss);
    }

    private static List<Line> timer(boolean preview) {
        SlayerTimer t = SlayerTimer.INSTANCE;
        long now = System.currentTimeMillis();
        List<Line> out = new ArrayList<>();
        if (preview && t.kills() == 0 && !t.inQuest()) {
            out.add(Line.text("Slayer Timer", Draw.GOLD));
            out.add(Line.text("Boss fight: 0:24", Draw.TEXT));
            out.add(Line.text("Last: 2:05 (1:40 spawn + 0:25 kill)", Draw.MUTED));
            out.add(Line.text("Bosses: 6 · 14.2/hr", Draw.GOOD));
            out.add(Line.text("Average 3:10 · Best 2:05", Draw.MUTED));
            return out;
        }
        if (!t.inQuest() && t.kills() == 0 && t.lastKillMs() < 0) return out;
        out.add(Line.text("Slayer Timer", Draw.GOLD));
        if (t.bossAlive()) {
            out.add(Line.text("Boss fight: " + SlayerTimer.clock(t.fightElapsed(now)), Draw.TEXT));
            if (t.spawnTook() >= 0) out.add(Line.text("Spawned in " + SlayerTimer.clock(t.spawnTook()), Draw.MUTED));
        } else if (t.inQuest()) {
            out.add(Line.text("Spawning boss: " + SlayerTimer.clock(t.questElapsed(now)), Draw.TEXT));
        }
        if (t.lastTotalMs() >= 0) {
            out.add(Line.text("Last: " + SlayerTimer.clock(t.lastTotalMs()) + " (" + SlayerTimer.clock(t.lastSpawnMs())
                    + " spawn + " + SlayerTimer.clock(t.lastKillMs()) + " kill)", Draw.MUTED));
        } else if (t.lastKillMs() >= 0) {
            out.add(Line.text("Last kill: " + SlayerTimer.clock(t.lastKillMs()), Draw.MUTED));
        }
        if (t.kills() > 0) {
            double rate = t.perHour();
            out.add(Line.text("Bosses: " + t.kills() + (Double.isNaN(rate) ? "" : String.format(" · %.1f/hr", rate)), Draw.GOOD));
            if (t.averageMs() >= 0) {
                out.add(Line.text("Average " + SlayerTimer.clock(t.averageMs()) + " · Best " + SlayerTimer.clock(t.bestMs()), Draw.MUTED));
            }
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
