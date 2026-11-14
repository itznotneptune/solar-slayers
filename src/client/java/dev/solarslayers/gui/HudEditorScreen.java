package dev.solarslayers.gui;

import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.config.SlayersConfig.HudPos;
import dev.solarslayers.hud.SlayerHud;
import dev.solarslayers.hud.SlayerHud.Element;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Drag HUD panels around; scroll over one to scale it, right-click to reset its scale. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private Element dragging;
    private double grabX, grabY;
    private boolean dirty;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD Locations"));
        this.parent = parent;
    }

    private static List<SlayerHud.Line> linesFor(Element e) {
        List<SlayerHud.Line> real = SlayerHud.lines(e, false);
        return real.isEmpty() ? SlayerHud.lines(e, true) : real;
    }

    private int[] bounds(Element e) {
        HudPos p = e.pos();
        int[] size = SlayerHud.measure(font, linesFor(e));
        return new int[] {p.x, p.y, Math.round(size[0] * p.scale), Math.round(size[1] * p.scale)};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // no blur: the point is to see the panels over the game
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x40000000);
        for (Element e : Element.values()) {
            if (!e.enabled()) continue;
            int[] b = bounds(e);
            SlayerHud.draw(g, font, e.pos(), linesFor(e));
            boolean hover = dragging == e || Draw.in(mx, my, b[0], b[1], b[2], b[3]);
            Draw.outline(g, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, hover ? Draw.ACCENT : 0x80FFFFFF);
            if (hover) {
                String tag = e.label + "  " + Math.round(e.pos().scale * 100) + "%";
                g.text(font, tag, b[0], b[1] - 11, Draw.GOLD, true);
            }
        }
        String help = "Drag to move  •  Scroll to resize  •  Right-click to reset size  •  Esc to go back";
        int w = font.width(help) + 16;
        Draw.panel(g, (width - w) / 2, height - 26, (width + w) / 2, height - 8, 0xE0121318, Draw.BORDER);
        g.centeredText(font, help, width / 2, height - 21, Draw.MUTED);
    }

    private Element at(double mx, double my) {
        Element[] all = Element.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Element e = all[i];
            if (!e.enabled()) continue;
            int[] b = bounds(e);
            if (Draw.in(mx, my, b[0], b[1], b[2], b[3])) return e;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        Element el = at(e.x(), e.y());
        if (el == null) return false;
        if (e.button() == 1) {
            el.pos().scale = 1f;
            dirty = true;
            return true;
        }
        dragging = el;
        grabX = e.x() - el.pos().x;
        grabY = e.y() - el.pos().y;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) return false;
        int[] b = bounds(dragging);
        HudPos p = dragging.pos();
        p.x = (int) Math.max(0, Math.min(width - b[2], e.x() - grabX));
        p.y = (int) Math.max(0, Math.min(height - b[3], e.y() - grabY));
        dirty = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        Element el = at(mx, my);
        if (el == null) return false;
        HudPos p = el.pos();
        p.scale = Math.max(0.5f, Math.min(2.5f, Math.round((p.scale + (float) sy * 0.1f) * 10) / 10f));
        dirty = true;
        return true;
    }

    @Override
    public void onClose() {
        if (dirty) SlayersConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
