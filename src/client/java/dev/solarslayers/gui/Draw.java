package dev.solarslayers.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared look: dark translucent panels, a warm "solar" accent, soft rounded corners. */
public final class Draw {
    public static final int ACCENT = 0xFFF2A541;
    public static final int ACCENT_DIM = 0xFF9C6A2A;
    public static final int PANEL = 0xEE121318;
    public static final int SIDEBAR = 0xF0181920;
    public static final int CARD = 0xFF1D1E26;
    public static final int CARD_HOVER = 0xFF252731;
    public static final int BORDER = 0xFF2E3040;
    // Text colours are vanilla colour codes only: server resource packs recolour other values in their text shaders.
    public static final int TEXT = 0xFFFFFFFF;   // &f
    public static final int MUTED = 0xFFAAAAAA;  // &7
    public static final int FAINT = 0xFF555555;  // &8
    public static final int GOOD = 0xFF55FF55;   // &a
    public static final int BAD = 0xFFFF5555;    // &c
    public static final int GOLD = 0xFFFFAA00;   // &6, text accent
    public static final int PURPLE = 0xFFFF55FF; // &d

    private Draw() {}

    /** A filled rectangle with 1px-cut corners, which reads as rounded at GUI scale. */
    public static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 3 || y2 - y1 < 3) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        g.fill(x1 + 1, y1, x2 - 1, y2, color);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** 1px rectangle outline (GuiGraphicsExtractor.renderOutline is not in every 1.21.x). */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Rounded panel with a 1px border. */
    public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int fill, int border) {
        round(g, x1, y1, x2, y2, border);
        round(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** Pill-shaped bar (progress bars, toggle tracks). */
    public static void pill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int h = y2 - y1;
        if (h <= 4) {
            round(g, x1, y1, x2, y2, color);
            return;
        }
        g.fill(x1 + 2, y1, x2 - 2, y2, color);
        g.fill(x1 + 1, y1 + 1, x1 + 2, y2 - 1, color);
        g.fill(x2 - 2, y1 + 1, x2 - 1, y2 - 1, color);
        g.fill(x1, y1 + 2, x1 + 1, y2 - 2, color);
        g.fill(x2 - 1, y1 + 2, x2, y2 - 2, color);
    }

    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double frac, int color) {
        pill(g, x, y, x + w, y + h, 0xFF2A2C38);
        double f = Double.isNaN(frac) ? 0 : Math.max(0, Math.min(1, frac));
        int fw = (int) Math.round(w * f);
        if (fw >= 2) pill(g, x, y, x + fw, y + h, color);
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static int lerpColor(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int hsv(float h, float s, float v) {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF);
    }

    public static float[] toHsv(int argb) {
        return java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
    }
}
