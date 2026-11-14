package dev.solarslayers.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Locale;

/** Popup colour picker: saturation/brightness square, hue strip, presets and a hex field. */
final class ColorPicker {
    static final int W = 148, H = 128;
    private static final int SQ = 100, SQ_H = 70, HUE_W = 12;
    private static final int[] PRESETS = {
            0xFFFF3B3B, 0xFFFFA62B, 0xFFFFE14D, 0xFF5BE37D, 0xFF3FD6E8, 0xFF4D8BFF, 0xFFB46CFF, 0xFFFF6EC7
    };

    private final Option.Color option;
    private final int x, y;
    private float h, s, v;
    private int dragging; // 0 none, 1 square, 2 hue
    private EditBox hex;
    private boolean updatingHex;

    ColorPicker(Option.Color option, int x, int y) {
        this.option = option;
        this.x = x;
        this.y = y;
        float[] hsv = Draw.toHsv(option.get.getAsInt());
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
    }

    void attach(ConfigScreen screen) {
        hex = new EditBox(screen.font(), x + 28, y + H - 20, 64, 12, Component.literal("Hex"));
        hex.setMaxLength(7);
        hex.setBordered(false);
        hex.setTextColor(Draw.TEXT);
        setHexText();
        hex.setResponder(text -> {
            if (updatingHex) return;
            String t = text.startsWith("#") ? text.substring(1) : text;
            if (t.length() == 6) {
                try {
                    int rgb = Integer.parseInt(t, 16);
                    float[] hsv = Draw.toHsv(rgb);
                    h = hsv[0];
                    s = hsv[1];
                    v = hsv[2];
                    option.set.accept(0xFF000000 | rgb);
                    screen.changed();
                } catch (NumberFormatException ignored) {
                    // keep typing
                }
            }
        });
        screen.addPickerBox(hex);
    }

    void detach(ConfigScreen screen) {
        if (hex != null) screen.removePickerBox(hex);
    }

    boolean contains(double mx, double my) {
        return Draw.in(mx, my, x, y, W, H);
    }

    void render(GuiGraphicsExtractor g, Font font, int mx, int my, float delta) {
        Draw.panel(g, x - 1, y - 1, x + W + 1, y + H + 1, 0xFF000000, 0xFF000000);
        Draw.panel(g, x, y, x + W, y + H, 0xFF17181F, Draw.BORDER);
        int sx = x + 8, sy = y + 8;

        // saturation (x) / brightness (y) square
        for (int i = 0; i < SQ; i++) {
            int top = Draw.hsv(h, i / (float) (SQ - 1), 1f);
            g.fillGradient(sx + i, sy, sx + i + 1, sy + SQ_H, top, 0xFF000000);
        }
        int cx = sx + Math.round(s * (SQ - 1)), cy = sy + Math.round((1 - v) * (SQ_H - 1));
        Draw.outline(g, cx - 2, cy - 2, 5, 5, 0xFFFFFFFF);

        // hue strip
        int hx = sx + SQ + 8;
        for (int j = 0; j < SQ_H; j++) {
            g.fill(hx, sy + j, hx + HUE_W, sy + j + 1, Draw.hsv(j / (float) SQ_H, 1f, 1f));
        }
        int hy = sy + Math.round(h * (SQ_H - 1));
        g.fill(hx - 2, hy - 1, hx + HUE_W + 2, hy + 1, 0xFFFFFFFF);

        // presets
        int py = sy + SQ_H + 6;
        for (int i = 0; i < PRESETS.length; i++) {
            int px = sx + i * 16;
            boolean hover = Draw.in(mx, my, px, py, 14, 10);
            Draw.panel(g, px, py, px + 14, py + 10, PRESETS[i], hover ? 0xFFFFFFFF : 0xFF3A3C4A);
        }

        // preview + hex
        int ry = y + H - 22;
        Draw.panel(g, sx, ry, sx + 16, ry + 14, current(), 0xFF5A5D70);
        Draw.panel(g, x + 26, ry, x + 26 + 70, ry + 14, 0xFF0D0E12, hex.isFocused() ? Draw.ACCENT_DIM : Draw.BORDER);
        hex.extractRenderState(g, mx, my, delta);
        g.text(font, "Done", x + W - 8 - font.width("Done"), ry + 3,
                Draw.in(mx, my, x + W - 40, ry, 34, 14) ? Draw.GOLD : Draw.MUTED, false);
    }

    private int current() {
        return Draw.hsv(h, s, v);
    }

    boolean mouseClicked(ConfigScreen screen, MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        int sx = x + 8, sy = y + 8;
        if (Draw.in(mx, my, sx, sy, SQ, SQ_H)) {
            dragging = 1;
            pick(screen, mx, my);
            return true;
        }
        if (Draw.in(mx, my, sx + SQ + 6, sy, HUE_W + 4, SQ_H)) {
            dragging = 2;
            pick(screen, mx, my);
            return true;
        }
        int py = sy + SQ_H + 6;
        for (int i = 0; i < PRESETS.length; i++) {
            if (Draw.in(mx, my, sx + i * 16, py, 14, 10)) {
                float[] hsv = Draw.toHsv(PRESETS[i]);
                h = hsv[0];
                s = hsv[1];
                v = hsv[2];
                apply(screen);
                return true;
            }
        }
        int ry = y + H - 22;
        if (Draw.in(mx, my, x + W - 40, ry, 34, 14)) {
            screen.closePicker();
            return true;
        }
        if (Draw.in(mx, my, x + 26, ry, 70, 14)) {
            screen.setFocused(hex);
            hex.setFocused(true);
            hex.mouseClicked(e, dbl);
            return true;
        }
        return true;
    }

    boolean mouseDragged(MouseButtonEvent e) {
        if (dragging != 0) pick(null, e.x(), e.y());
        return true;
    }

    void mouseReleased() {
        dragging = 0;
    }

    boolean keyPressed(ConfigScreen screen, KeyEvent e) {
        if (e.isConfirmation()) {
            screen.closePicker();
            return true;
        }
        return false;
    }

    private ConfigScreen lastScreen;

    private void pick(ConfigScreen screen, double mx, double my) {
        if (screen != null) lastScreen = screen;
        int sx = x + 8, sy = y + 8;
        if (dragging == 1) {
            s = clamp((float) (mx - sx) / (SQ - 1));
            v = 1 - clamp((float) (my - sy) / (SQ_H - 1));
        } else if (dragging == 2) {
            h = clamp((float) (my - sy) / (SQ_H - 1));
        }
        apply(lastScreen);
    }

    private void apply(ConfigScreen screen) {
        option.set.accept(current());
        setHexText();
        if (screen != null) screen.changed();
    }

    private void setHexText() {
        if (hex == null) return;
        updatingHex = true;
        hex.setValue(String.format(Locale.ROOT, "#%06X", current() & 0xFFFFFF));
        updatingHex = false;
    }

    private static float clamp(float f) {
        return Math.max(0, Math.min(1, f));
    }
}
