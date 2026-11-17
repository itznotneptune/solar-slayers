package dev.solarslayers.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Text clean-up for SolarSkies' formatting: § codes, resource-pack icon prefixes and small-caps letters. */
public final class Txt {
    private Txt() {}

    private static final Pattern SECTION = Pattern.compile("§.");
    /**
     * Sprite icons the server puts in names and the sidebar, as {@code getString()} spells them: 1.21.11 adds the atlas
     * ({@code [item/clock_00@items]}), 1.21.9 and 1.21.10 don't ({@code [item/clock_00]}).
     */
    private static final Pattern ICON = Pattern.compile("\\[(?:item|block)/[^\\]\\s]*\\]\\s*");
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕꜱᴛᴜᴠᴡʏᴢ";
    private static final String PLAIN = "abcdefghijklmnopqrsstuvwyz";

    /** Removes § codes and icon prefixes; keeps the letters as the server sent them (for display). */
    public static String clean(String s) {
        if (s == null) return "";
        return ICON.matcher(SECTION.matcher(s).replaceAll("")).replaceAll("").trim();
    }

    /** {@link #clean} plus small-caps → ASCII and lower case (for matching). */
    public static String norm(String s) {
        String c = clean(s);
        StringBuilder b = new StringBuilder(c.length());
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            int k = SMALL.indexOf(ch);
            b.append(k >= 0 ? PLAIN.charAt(k) : ch);
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /** Parses "1.8k", "5k", "2,500", "1.2M" into a number; NaN when it cannot. */
    public static double num(String s) {
        if (s == null) return Double.NaN;
        String t = s.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (t.isEmpty()) return Double.NaN;
        double mul = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k') mul = 1e3;
        else if (last == 'm') mul = 1e6;
        else if (last == 'b') mul = 1e9;
        else if (last == 't') mul = 1e12;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Half of the last displayed digit's step: "1.8k" → 50, "1,831" → 0.5. */
    public static double roundingTolerance(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double mul = 1;
        if (t.endsWith("k")) mul = 1e3;
        else if (t.endsWith("m")) mul = 1e6;
        else if (t.endsWith("b")) mul = 1e9;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        int dot = t.indexOf('.');
        int decimals = dot < 0 ? 0 : t.length() - dot - 1;
        return 0.5 * mul / Math.pow(10, decimals);
    }

    public static String fmt(double v) {
        if (Double.isNaN(v)) return "?";
        if (Math.abs(v) >= 1e6) return trim(v / 1e6) + "M";
        if (Math.abs(v) >= 1e4) return trim(v / 1e3) + "k";
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
