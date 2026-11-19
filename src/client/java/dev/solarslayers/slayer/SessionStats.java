package dev.solarslayers.slayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session stats and the RNG drop counter, since the game started or the last reset.
 *
 * <ul>
 *   <li>Mob kills: the sidebar's quest XP ("Boss in: X/Y XP") going up counts as one kill.</li>
 *   <li>Boss kills: {@code NICE! SLAYER BOSS SLAIN!}.</li>
 *   <li>Drops: {@code DROP! (item) (luck)}, also {@code RARE DROP!}, {@code VERY RARE DROP!} and {@code CRAZY RARE DROP!}.
 *       A drop of gen slots ({@code DROP! (10 Gen Slots) (luck)}) adds to the gen slot total.</li>
 *   <li>Active time: each gap between two kills counts up to {@link #IDLE_CAP_MS}, so the clock stops three minutes
 *       after the last kill and starts again with the next one.</li>
 * </ul>
 *
 * Times are passed in by the caller, so the logic can be checked without a game.
 */
public final class SessionStats {
    public static final SessionStats INSTANCE = new SessionStats();

    /** Time without a kill after which the active clock stops. */
    public static final long IDLE_CAP_MS = 3 * 60_000L;

    public enum Rarity {
        DROP("Drop"), RARE("Rare"), VERY_RARE("Very Rare"), CRAZY_RARE("Crazy Rare");

        public final String label;

        Rarity(String label) {
            this.label = label;
        }
    }

    /** One drop line: rarity, item as shown (without brackets), luck as shown (null when absent). */
    public record Drop(Rarity rarity, String item, String luck) {}

    /** A dropped item's running tally; {@code best} is the highest rarity it has dropped at. */
    public static final class ItemCount {
        public final String name;
        public int count;
        public Rarity best;

        ItemCount(String name, Rarity rarity) {
            this.name = name;
            this.best = rarity;
        }
    }

    private static final Pattern DROP = Pattern.compile("^\\W*(CRAZY RARE |VERY RARE |RARE )?DROP!\\s*(.+)$");
    private static final Pattern GEN_SLOTS = Pattern.compile(
            "^\\+?([\\d,]+)\\s*x?\\s*gen\\s*slots?$|^gen\\s*slots?\\s*x\\s*([\\d,]+)$|^gen\\s*slots?$");
    private static final Pattern LEADING_COUNT = Pattern.compile("^\\+?[\\d,]+\\s*x?\\s+|\\s+x\\s*[\\d,]+$");

    private int mobKills, bossKills;
    private long genSlots;
    private final EnumMap<Rarity, Integer> byRarity = new EnumMap<>(Rarity.class);
    private final Map<String, ItemCount> items = new LinkedHashMap<>();
    private Drop last;

    private long activeMs;
    private long lastKill = -1;

    // sidebar XP seen on the previous tick
    private String xpQuest;
    private double xpLast = Double.NaN;

    private SessionStats() {}

    /** A fresh instance, for checks. */
    static SessionStats create() {
        return new SessionStats();
    }

    // ---- parsing ----

    /** Parses a cleaned chat line as a drop, or returns null when it isn't one. */
    public static Drop parseDrop(String text) {
        if (text == null) return null;
        Matcher m = DROP.matcher(text.trim());
        if (!m.matches()) return null;
        Rarity rarity = switch (m.group(1) == null ? "" : m.group(1).trim()) {
            case "RARE" -> Rarity.RARE;
            case "VERY RARE" -> Rarity.VERY_RARE;
            case "CRAZY RARE" -> Rarity.CRAZY_RARE;
            default -> Rarity.DROP;
        };
        String rest = m.group(2).trim();
        String luck = null;
        // the last bracketed group is the luck, as long as something comes before it
        if (rest.endsWith(")")) {
            int open = matchingOpen(rest, rest.length() - 1);
            if (open > 0 && !rest.substring(0, open).isBlank()) {
                luck = rest.substring(open + 1, rest.length() - 1).trim();
                rest = rest.substring(0, open).trim();
            }
        }
        if (rest.startsWith("(") && rest.endsWith(")") && matchingOpen(rest, rest.length() - 1) == 0) {
            rest = rest.substring(1, rest.length() - 1).trim();
        }
        if (rest.isEmpty()) return null;
        return new Drop(rarity, rest, luck == null || luck.isEmpty() ? null : luck);
    }

    private static int matchingOpen(String s, int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') depth++;
            else if (c == '(' && --depth == 0) return i;
        }
        return -1;
    }

    /** Gen slots in a dropped item ("10 Gen Slots" → 10, "Gen Slot" → 1), or 0 when it isn't gen slots. */
    public static long genSlots(String item) {
        Matcher m = GEN_SLOTS.matcher(item.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return 0;
        String n = m.group(1) != null ? m.group(1) : m.group(2);
        if (n == null) return 1;
        try {
            return Long.parseLong(n.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The item name without its amount ("10 Gen Slots" → "Gen Slots", "Moon Fang x3" → "Moon Fang"). */
    static String itemName(String item) {
        String n = LEADING_COUNT.matcher(item.trim()).replaceAll("").trim();
        return n.isEmpty() ? item.trim() : n;
    }

    // ---- events ----

    public void onMobKill(long now) {
        mobKills++;
        touch(now);
    }

    public void onBossKill(long now) {
        bossKills++;
        touch(now);
    }

    public void onDrop(Drop d) {
        byRarity.merge(d.rarity(), 1, Integer::sum);
        genSlots += genSlots(d.item());
        String name = itemName(d.item());
        ItemCount c = items.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ItemCount(name, d.rarity()));
        c.count++;
        if (d.rarity().ordinal() > c.best.ordinal()) c.best = d.rarity();
        last = d;
    }

    /** Called every tick with the sidebar's quest and its XP; a rise in XP on the same quest is a mob kill. */
    public void onSidebar(String quest, double xpHave, long now) {
        if (quest != null && quest.equals(xpQuest) && !Double.isNaN(xpHave) && !Double.isNaN(xpLast) && xpHave > xpLast) {
            onMobKill(now);
        }
        xpQuest = quest;
        xpLast = xpHave;
    }

    private void touch(long now) {
        if (lastKill >= 0) activeMs += Math.min(Math.max(0, now - lastKill), IDLE_CAP_MS);
        lastKill = now;
    }

    public void reset() {
        mobKills = bossKills = 0;
        genSlots = 0;
        byRarity.clear();
        items.clear();
        last = null;
        activeMs = 0;
        lastKill = -1;
    }

    // ---- read side ----

    public int mobKills() { return mobKills; }
    public int bossKills() { return bossKills; }
    public long genSlots() { return genSlots; }
    public int drops(Rarity r) { return byRarity.getOrDefault(r, 0); }
    public Drop last() { return last; }

    public int totalDrops() {
        int n = 0;
        for (int v : byRarity.values()) n += v;
        return n;
    }

    /** Active time, including the time since the last kill up to the cap. */
    public long activeMs(long now) {
        if (lastKill < 0) return 0;
        return activeMs + Math.min(Math.max(0, now - lastKill), IDLE_CAP_MS);
    }

    /** True when the clock has stopped because there was no kill for {@link #IDLE_CAP_MS}. */
    public boolean paused(long now) {
        return lastKill < 0 || now - lastKill >= IDLE_CAP_MS;
    }

    /** Dropped items, rarest first, then most dropped. */
    public List<ItemCount> items() {
        List<ItemCount> out = new ArrayList<>(items.values());
        out.sort((a, b) -> a.best != b.best ? b.best.ordinal() - a.best.ordinal() : b.count - a.count);
        return out;
    }
}
