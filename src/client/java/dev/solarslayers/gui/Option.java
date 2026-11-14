package dev.solarslayers.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** One row in the config screen: a title, a short description and a control on the right. */
public abstract class Option {
    public final String title;
    public final String description;

    protected Option(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public abstract int controlWidth();

    /** Draws the control, right-aligned inside the card, vertically centred at {@code cy}. */
    public abstract void renderControl(GuiGraphics g, Font font, int right, int cy, int mx, int my, float delta);

    /** @return true when handled. */
    public boolean click(ConfigScreen screen, double mx, double my, int right, int cy, int button) {
        return false;
    }

    public void drag(double mx, int right) {}

    public void release() {}

    public boolean matches(String q) {
        return title.toLowerCase().contains(q) || description.toLowerCase().contains(q);
    }

    List<FormattedCharSequence> wrap(Font font, int width) {
        return font.split(Component.literal(description), Math.max(40, width));
    }

    // ---- kinds ----

    public static final class Toggle extends Option {
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;
        private float anim = -1;

        public Toggle(String title, String description, Supplier<Boolean> get, Consumer<Boolean> set) {
            super(title, description);
            this.get = get;
            this.set = set;
        }

        @Override public int controlWidth() { return 24; }

        @Override
        public void renderControl(GuiGraphics g, Font font, int right, int cy, int mx, int my, float delta) {
            boolean on = get.get();
            float target = on ? 1 : 0;
            if (anim < 0) anim = target;
            anim += (target - anim) * Math.min(1, 0.35f * Math.max(1, delta));
            int x = right - 24, y = cy - 6;
            Draw.pill(g, x, y, x + 24, y + 12, Draw.lerpColor(0xFF3A3C4A, Draw.ACCENT, anim));
            int kx = x + 2 + Math.round(12 * anim);
            Draw.pill(g, kx, y + 2, kx + 8, y + 10, 0xFFFFFFFF);
        }

        @Override
        public boolean click(ConfigScreen screen, double mx, double my, int right, int cy, int button) {
            set.accept(!get.get());
            screen.changed();
            return true;
        }
    }

    public static final class Color extends Option {
        final IntSupplier get;
        final IntConsumer set;

        public Color(String title, String description, IntSupplier get, IntConsumer set) {
            super(title, description);
            this.get = get;
            this.set = set;
        }

        @Override public int controlWidth() { return 36; }

        @Override
        public void renderControl(GuiGraphics g, Font font, int right, int cy, int mx, int my, float delta) {
            int x = right - 36, y = cy - 7;
            boolean hover = Draw.in(mx, my, x, y, 36, 14);
            Draw.panel(g, x, y, x + 36, y + 14, 0xFF000000 | get.getAsInt(), hover ? 0xFFFFFFFF : 0xFF5A5D70);
        }

        @Override
        public boolean click(ConfigScreen screen, double mx, double my, int right, int cy, int button) {
            screen.openPicker(this, right - 36, cy + 9);
            return true;
        }
    }

    public static final class Slider extends Option {
        private final IntSupplier get;
        private final IntConsumer set;
        private final int min, max;
        private final String unit;
        private ConfigScreen dragging;

        public Slider(String title, String description, int min, int max, String unit, IntSupplier get, IntConsumer set) {
            super(title, description);
            this.min = min;
            this.max = max;
            this.unit = unit;
            this.get = get;
            this.set = set;
        }

        @Override public int controlWidth() { return 110; }

        @Override
        public void renderControl(GuiGraphics g, Font font, int right, int cy, int mx, int my, float delta) {
            int v = get.getAsInt();
            String label = v + unit;
            g.drawString(font, label, right - font.width(label), cy - 4, Draw.TEXT, false);
            int x = right - 110, w = 80;
            double f = (v - min) / (double) (max - min);
            Draw.bar(g, x, cy - 2, w, 4, f, Draw.ACCENT);
            int kx = x + (int) Math.round(f * (w - 8));
            Draw.pill(g, kx, cy - 5, kx + 8, cy + 5, 0xFFFFFFFF);
        }

        @Override
        public boolean click(ConfigScreen screen, double mx, double my, int right, int cy, int button) {
            dragging = screen;
            drag(mx, right);
            return true;
        }

        @Override
        public void drag(double mx, int right) {
            if (dragging == null) return;
            int x = right - 110, w = 80;
            double f = Math.max(0, Math.min(1, (mx - x - 4) / (w - 8)));
            int v = (int) Math.round(min + f * (max - min));
            if (v != get.getAsInt()) {
                set.accept(v);
                dragging.changed();
            }
        }

        @Override
        public void release() {
            dragging = null;
        }
    }

    public static final class Button extends Option {
        private final String label;
        private final Consumer<ConfigScreen> action;

        public Button(String title, String description, String label, Consumer<ConfigScreen> action) {
            super(title, description);
            this.label = label;
            this.action = action;
        }

        @Override public int controlWidth() { return 80; }

        @Override
        public void renderControl(GuiGraphics g, Font font, int right, int cy, int mx, int my, float delta) {
            int w = Math.max(60, font.width(label) + 16), x = right - w, y = cy - 8;
            boolean hover = Draw.in(mx, my, x, y, w, 16);
            Draw.panel(g, x, y, x + w, y + 16, hover ? 0xFF33354A : 0xFF2A2C3A, hover ? Draw.ACCENT : 0xFF44475C);
            g.drawCenteredString(font, label, x + w / 2, cy - 4, Draw.TEXT);
        }

        @Override
        public boolean click(ConfigScreen screen, double mx, double my, int right, int cy, int button) {
            action.accept(screen);
            return true;
        }
    }

    /** Text only, no control. */
    public static final class Info extends Option {
        public Info(String title, String description) {
            super(title, description);
        }

        @Override public int controlWidth() { return 0; }

        @Override
        public void renderControl(GuiGraphics g, Font font, int right, int cy, int mx, int my, float delta) {}
    }
}
