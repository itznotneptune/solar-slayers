package dev.solarslayers.slayer;

import dev.solarslayers.util.Txt;

import java.util.List;
import java.util.Locale;

/**
 * Name matching for slayer mobs, kept free of Minecraft classes so it can be checked on its own.
 * Recorded nametags: boss {@code ☠ Solar Flare 14.8k❤}, its minions {@code Solar Flare Minion 2.5k/2.5k❤}, area mobs
 * {@code Solar Blaze 5k/5k❤}, {@code Cinder Wraith 19k/19k❤}; wolf boss {@code ☠ Moonfang Alpha 5k❤}, pups
 * {@code Moonfang 250/250❤}. The health part changes with damage (and may read k or M), so it is never matched.
 */
public final class MobNames {
    private static final String BLAZE_MINION = "solar flare minion";

    private MobNames() {}

    /**
     * The name without formatting, {@code WARDED } prefix, {@code ☠}, or trailing health:
     * {@code §c☠ Solar Flare 14.8k❤} → {@code Solar Flare}; {@code Solar Flare Minion 2.5k/2.5k❤} → {@code Solar Flare Minion}.
     */
    public static String base(String raw) {
        String n = Txt.clean(raw);
        if (n.startsWith("WARDED ")) n = n.substring(7).trim();
        if (n.startsWith("☠")) n = n.substring(1).trim();
        int heart = n.lastIndexOf('❤');
        if (heart >= 0) {
            // drop the health token the heart belongs to ("2.5k/2.5k❤", "14.8k❤", "❤ 2.5k")
            int space = n.lastIndexOf(' ', heart);
            n = space > 0 ? n.substring(0, space) : n.substring(0, heart);
        }
        return n.replace("❤", "").replace("☠", "").trim();
    }

    /** A boss nametag: it starts with ☠ (after formatting and the warded prefix are removed). */
    public static boolean isBoss(String raw) {
        String n = Txt.clean(raw);
        if (n.startsWith("WARDED ")) n = n.substring(7).trim();
        return n.startsWith("☠");
    }

    /** A Solar Flare (blaze slayer) minion. The boss itself, "Solar Flare", never matches. */
    public static boolean isBlazeMinion(String raw) {
        return !isBoss(raw) && base(raw).toLowerCase(Locale.ROOT).startsWith(BLAZE_MINION);
    }

    /**
     * A generic boss minion: a known name, or a name a visible boss's own name starts with ("Moonfang" for the
     * "Moonfang Alpha"). Whole-name match only, so "Feral Moonfang" is never a minion.
     */
    public static boolean isMinion(String base, List<String> bossBases, List<String> known) {
        if (base.isEmpty()) return false;
        for (String m : known) {
            if (base.equalsIgnoreCase(m)) return true;
        }
        String prefix = base + " ";
        for (String b : bossBases) {
            if (b.regionMatches(true, 0, prefix, 0, prefix.length())) return true;
        }
        return false;
    }
}
