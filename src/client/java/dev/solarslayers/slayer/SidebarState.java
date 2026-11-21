package dev.solarslayers.slayer;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.util.Txt;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the SolarSkies sidebar says about slayers. Recorded layout (2026-11-14):
 * <pre>
 *  ꜱʟᴀʏᴇʀ
 * Moonfang Alpha ᴀʀᴇᴀ III        (or ꜱʟᴀʏᴇʀ ʜᴜʙ)
 * ʟᴇᴠᴇʟ: 3 (45 ᴛᴏ ɢᴏ)
 * ᴇѕѕᴇɴᴄᴇ: 1.8k
 * Your Quest
 * Moonfang Alpha III              (or ɴᴏ ǫᴜᴇѕᴛ)
 * ʙᴏѕѕ ɪɴ: 55/150 xᴘ              (or ❤ 2.5k/5k (02:50) while the boss is alive)
 * Pity Meter
 * 250 Slayer Essence              (or ɴᴏᴛ ꜱᴇᴛ)
 * ᴍᴇᴛᴇʀ: 100/250 (40%)
 * </pre>
 */
public final class SidebarState {
    private static final String N = "([\\d.,]+[kmbt]?)";
    private static final Pattern BOSS_IN = Pattern.compile("boss in: " + N + "/" + N + " xp");
    private static final Pattern BOSS_HP = Pattern.compile("❤ " + N + "/" + N + " \\((\\d+:\\d+)\\)");
    private static final Pattern METER = Pattern.compile("meter: " + N + "/" + N + " \\((\\d+)%\\)");
    private static final Pattern LEVEL = Pattern.compile("level: (\\d+) \\(" + N + " to go\\)");
    private static final Pattern ESSENCE = Pattern.compile("essence: " + N);
    private static final Pattern AREA = Pattern.compile("(.+?) area\\b.*");

    public static final SidebarState INSTANCE = new SidebarState();

    public boolean inSlayer;
    public String area;          // "Moonfang Alpha" (display form), null in the hub
    public int level = -1;
    public boolean noQuest;
    public String quest;         // "Moonfang Alpha III", null without a quest
    public double xpHave = Double.NaN, xpNeed = Double.NaN;
    public boolean bossAlive;
    public String bossHpText;    // "2.5k/5k"
    public double bossHp = Double.NaN, bossHpMax = Double.NaN;
    public String bossTime;      // "02:50"
    public boolean pitySet;
    public String pityItem;
    public double pityHave = Double.NaN, pityNeed = Double.NaN;
    public int pityPct = -1;
    /** A sidebar line names the Grave Tyrant: world 1's slayer, shown in that world only. */
    public boolean graveTyrant;
    public String essenceText;   // as shown, e.g. "1.8k"

    private SidebarState() {}

    public void update(Minecraft mc) {
        reset();
        if (mc.level == null) return;
        List<String> lines = read(mc.level.getScoreboard());
        if (lines.isEmpty()) return;

        String pending = null;
        for (String raw : lines) {
            String clean = Txt.clean(raw);
            String n = Txt.norm(raw);
            if (n.isEmpty()) continue;
            if (n.contains("grave tyrant")) graveTyrant = true;

            if (n.equals("slayer")) { inSlayer = true; continue; }
            if (n.equals("your quest")) { pending = "quest"; continue; }
            if (n.equals("pity meter")) { pending = "pity"; continue; }

            if ("quest".equals(pending)) {
                pending = null;
                if (n.equals("no quest")) noQuest = true;
                else quest = clean;
                continue;
            }
            if ("pity".equals(pending)) {
                pending = null;
                if (n.equals("not set")) pitySet = false;
                else { pitySet = true; pityItem = clean; }
                continue;
            }

            Matcher m;
            if ((m = BOSS_IN.matcher(n)).find()) {
                xpHave = Txt.num(m.group(1));
                xpNeed = Txt.num(m.group(2));
            } else if ((m = BOSS_HP.matcher(n)).find()) {
                bossAlive = true;
                bossHpText = m.group(1) + "/" + m.group(2);
                bossHp = Txt.num(m.group(1));
                bossHpMax = Txt.num(m.group(2));
                bossTime = m.group(3);
            } else if ((m = METER.matcher(n)).find()) {
                pityHave = Txt.num(m.group(1));
                pityNeed = Txt.num(m.group(2));
                pityPct = Integer.parseInt(m.group(3));
            } else if ((m = LEVEL.matcher(n)).find()) {
                level = Integer.parseInt(m.group(1));
            } else if ((m = ESSENCE.matcher(n)).find()) {
                essenceText = m.group(1);
            } else if (inSlayer && area == null && (m = AREA.matcher(n)).matches() && !n.contains("hub")) {
                int cut = clean.toLowerCase().indexOf(" ᴀʀᴇᴀ");
                area = cut > 0 ? clean.substring(0, cut).trim() : m.group(1);
            }
        }

        if (!inSlayer) {
            reset();
            return;
        }
        if (area != null && level >= 0) {
            Integer old = SlayersConfig.get().slayerLevels.put(Txt.norm(area), level);
            if (old == null || old != level) SlayersConfig.save();
        }
        if (pitySet) forgetSavedPity();
    }

    /**
     * A pity item is set, so the XP saved while none was set has gone into it (and may be used up by a drop). Drop the
     * remembered "XP saved" amounts; the next "Not set (... XP saved)" kill summary brings the real figure back.
     */
    public static void forgetSavedPity() {
        SlayersConfig cfg = SlayersConfig.get();
        if (!cfg.pitySaved.isEmpty()) {
            cfg.pitySaved.clear();
            SlayersConfig.save();
        }
    }

    /** Key for per-slayer pity data: the current area, or "" in the hub. */
    public String pityKey() {
        return area == null ? "" : Txt.norm(area);
    }

    /** Essence from the sidebar, as a number, or NaN outside slayer areas. */
    public double essence() {
        return essenceText == null ? Double.NaN : Txt.num(essenceText);
    }

    private void reset() {
        inSlayer = false;
        graveTyrant = false;
        area = null;
        level = -1;
        noQuest = false;
        quest = null;
        xpHave = xpNeed = Double.NaN;
        bossAlive = false;
        bossHpText = null;
        bossHp = bossHpMax = Double.NaN;
        bossTime = null;
        pitySet = false;
        pityItem = null;
        pityHave = pityNeed = Double.NaN;
        pityPct = -1;
        essenceText = null;
    }

    private static List<String> read(Scoreboard sb) {
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return List.of();
        List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
        List<String> out = new ArrayList<>(entries.size());
        for (PlayerScoreEntry e : entries) {
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            Component name = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(team, e.ownerName());
            out.add(name.getString());
        }
        return out;
    }
}
