package dev.solarslayers.gui;

import dev.solarslayers.SlayersClient;
import dev.solarslayers.config.SlayersConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The mod's settings: categories on the left, option cards on the right, search on top. */
public final class ConfigScreen extends Screen {
    private record Category(String name, List<Option> options) {}

    /** A laid-out row: either a section heading (option == null) or an option card. */
    private record Row(String heading, Option option, int y, int h) {}

    private static int lastCategory = 0;

    private final Screen parent;
    private final List<Category> categories = new ArrayList<>();
    private int selected = lastCategory;
    private double scroll;
    private int contentHeight;
    private boolean dirty;

    private int px, py, pw, ph;
    private static final int HEADER = 30, SIDE = 112, CARD_GAP = 4;

    private EditBox search;
    private ColorPicker picker;
    private Option.Slider draggingSlider;
    private int draggingRight;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Solar Slayers"));
        this.parent = parent;
        buildOptions();
    }

    private void buildOptions() {
        SlayersConfig c = SlayersConfig.get();
        categories.add(new Category("General", List.of(
                new Option.Button("HUD Locations", "Drag the HUD panels where you want them. Scroll over a panel to resize it.",
                        "Edit HUD", s -> s.minecraft.setScreen(new HudEditorScreen(s))),
                new Option.Toggle("Only Show HUD in Slayer Areas", "Hide the progress and pity panels outside the slayer world.",
                        () -> c.hudOnlyInSlayer, v -> c.hudOnlyInSlayer = v),
                new Option.Info("Keybind", "Set a key to open this menu in Options → Controls → Key Binds, under \"Solar Slayers\". "
                        + "You can also type /slayer+ or /slayerplus."))));
        categories.add(new Category("Highlights", List.of(
                new Option.Toggle("Boss Highlight", "Colour the whole slayer boss so it stands out from its pack.",
                        () -> c.bossHighlight, v -> c.bossHighlight = v),
                new Option.Color("Boss Colour", "Colour used for the boss highlight.", () -> c.bossColor, v -> c.bossColor = v),
                new Option.Toggle("Only My Boss", "Highlight only the boss you spawned, not other players' bosses.",
                        () -> c.onlyMyBoss, v -> c.onlyMyBoss = v),
                new Option.Toggle("Miniboss Highlight", "Colour minibosses such as the Moonlit Hunter. New minibosses are learned from chat.",
                        () -> c.minibossHighlight, v -> c.minibossHighlight = v),
                new Option.Color("Miniboss Colour", "Colour used for the miniboss highlight.",
                        () -> c.minibossColor, v -> c.minibossColor = v),
                new Option.Toggle("Minion Highlight", "Colour the minions a boss calls, such as the Moonfang Alpha's pups. "
                        + "With Only My Boss on, only minions near your boss.",
                        () -> c.minionHighlight, v -> c.minionHighlight = v),
                new Option.Color("Minion Colour", "Colour used for the minion highlight.",
                        () -> c.minionColor, v -> c.minionColor = v))));
        categories.add(new Category("Slayer HUD", List.of(
                new Option.Toggle("Slayer Progress", "Boss HP and time left while it is alive; otherwise XP until the boss and kills left.",
                        () -> c.progressHud, v -> c.progressHud = v),
                new Option.Slider("XP per Kill", "Used to work out how many kills are left before the boss spawns.",
                        1, 50, " XP", () -> c.xpPerKill, v -> c.xpPerKill = v),
                new Option.Toggle("Pity Meter", "Your selected pity item and how close the meter is.",
                        () -> c.pityHud, v -> c.pityHud = v))));
        categories.add(new Category("Alerts", List.of(
                new Option.Toggle("No Quest Warning", "Show a title when you hit slayer mobs without an active slayer quest.",
                        () -> c.questWarning, v -> c.questWarning = v),
                new Option.Toggle("Warning Sound", "Play a ding with the warning.",
                        () -> c.questWarningSound, v -> c.questWarningSound = v),
                new Option.Slider("Warning Cooldown", "Minimum time between two warnings.",
                        1, 30, "s", () -> c.questWarningCooldown, v -> c.questWarningCooldown = v),
                new Option.Button("Preview", "See what the warning looks like.", "Test",
                        s -> dev.solarslayers.slayer.QuestWarning.show()))));
        categories.add(new Category("Chat", List.of(
                new Option.Toggle("Slayer Menu Link", "After a boss dies, add a clickable chat line that opens /slayer.",
                        () -> c.slainChatLink, v -> c.slainChatLink = v))));
        categories.add(new Category("Upgrade Tracker", List.of(
                new Option.Toggle("Upgrade Tracker", "Right-click an item in the Upgrade Shop to track what you still need to craft it. "
                        + "The right-click is not sent to the server, so it never crafts.",
                        () -> c.shopTracker, v -> c.shopTracker = v),
                new Option.Toggle("Tracker HUD", "Show tracked items and your progress on screen.",
                        () -> c.trackerHud, v -> c.trackerHud = v),
                new Option.Button("Tracked Items", "Stop tracking everything.", "Clear", s -> {
                    c.tracked.clear();
                    s.changed();
                }),
                new Option.Info("Essence", "Your exact Essence is read when you open the Upgrade Shop, then kept up to date from boss kills."))));
        categories.add(new Category("Coming Soon", List.of(
                new Option.Info("Auto Restart Quest", "Starts the same tier again after a boss dies. "
                        + "Waits on the server adding /slayer start."))));
        selected = Math.max(0, Math.min(selected, categories.size() - 1));
    }

    @Override
    protected void init() {
        pw = Math.min(560, width - 24);
        ph = Math.min(340, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, px + pw - 140, py + 9, 128, 12, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(Draw.TEXT);
        search.setValue(old);
        search.setResponder(s -> scroll = 0);
        addWidget(search);
        if (picker != null) picker.attach(this);
    }

    void changed() {
        dirty = true;
    }

    void addPickerBox(EditBox box) {
        addWidget(box);
        setFocused(box);
    }

    void removePickerBox(EditBox box) {
        removeWidget(box);
        if (getFocused() == box) setFocused(null);
    }

    void openPicker(Option.Color option, int x, int y) {
        if (picker != null) picker.detach(this);
        int pxx = Math.min(x, width - ColorPicker.W - 4);
        int pyy = y + ColorPicker.H > height - 4 ? y - ColorPicker.H - 22 : y;
        picker = new ColorPicker(option, pxx, pyy);
        picker.attach(this);
    }

    void closePicker() {
        if (picker != null) picker.detach(this);
        picker = null;
    }

    // ---------------------------------------------------------------- layout

    private int contentX() { return px + SIDE + 8; }
    private int contentY() { return py + HEADER + 6; }
    private int contentW() { return pw - SIDE - 16; }
    private int contentH() { return ph - HEADER - 12; }

    private List<Row> layout() {
        List<Row> rows = new ArrayList<>();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        int y = 0;
        if (q.isEmpty()) {
            for (Option o : categories.get(selected).options()) {
                int h = cardHeight(o);
                rows.add(new Row(null, o, y, h));
                y += h + CARD_GAP;
            }
        } else {
            for (Category c : categories) {
                List<Option> hits = c.options().stream().filter(o -> o.matches(q)).toList();
                if (hits.isEmpty()) continue;
                rows.add(new Row(c.name(), null, y, 14));
                y += 14;
                for (Option o : hits) {
                    int h = cardHeight(o);
                    rows.add(new Row(null, o, y, h));
                    y += h + CARD_GAP;
                }
            }
        }
        contentHeight = y;
        return rows;
    }

    private int descWidth(Option o) {
        return contentW() - 24 - (o.controlWidth() > 0 ? o.controlWidth() + 14 : 0);
    }

    private int cardHeight(Option o) {
        int lines = o.description.isEmpty() ? 0 : o.wrap(font, descWidth(o)).size();
        return Math.max(30, 20 + lines * 10 + 4);
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - contentH());
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        List<Row> rows = layout();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        boolean popup = picker != null;
        int hmx = popup ? -1 : mx, hmy = popup ? -1 : my; // no hover effects under the popup

        // frame
        Draw.panel(g, px, py, px + pw, py + ph, Draw.PANEL, Draw.BORDER);
        g.fill(px + 1, py + HEADER, px + pw - 1, py + HEADER + 1, Draw.BORDER);
        g.fill(px + SIDE, py + HEADER + 1, px + SIDE + 1, py + ph - 1, Draw.BORDER);
        g.fill(px + 1, py + HEADER + 1, px + SIDE, py + ph - 1, Draw.SIDEBAR);

        // header
        g.drawString(font, "☀ Solar Slayers", px + 12, py + 11, Draw.GOLD, true);
        String version = "v" + SlayersClient.version();
        g.drawString(font, version, px + 12 + font.width("☀ Solar Slayers") + 6, py + 11, Draw.FAINT, false);
        int sx = px + pw - 146;
        Draw.panel(g, sx, py + 6, sx + 136, py + 22, 0xFF0D0E12, search.isFocused() ? Draw.ACCENT_DIM : Draw.BORDER);
        if (search.getValue().isEmpty() && !search.isFocused()) {
            g.drawString(font, "Search…", sx + 6, py + 10, Draw.FAINT, false);
        }
        search.render(g, mx, my, delta);

        // sidebar
        String q = search.getValue().trim();
        for (int i = 0; i < categories.size(); i++) {
            int cy = py + HEADER + 8 + i * 20;
            boolean sel = i == selected && q.isEmpty();
            boolean hover = Draw.in(hmx, hmy, px + 4, cy, SIDE - 8, 18);
            if (sel) Draw.round(g, px + 4, cy, px + SIDE - 4, cy + 18, 0xFF262836);
            else if (hover) Draw.round(g, px + 4, cy, px + SIDE - 4, cy + 18, 0xFF1F2029);
            if (sel) g.fill(px + 4, cy + 3, px + 6, cy + 15, Draw.ACCENT);
            g.drawString(font, categories.get(i).name(), px + 12, cy + 5, sel ? Draw.TEXT : hover ? Draw.TEXT : Draw.MUTED, false);
        }
        g.drawString(font, "Esc to close", px + 12, py + ph - 16, Draw.FAINT, false);

        // content
        int cx = contentX(), cyTop = contentY(), cw = contentW(), ch = contentH();
        g.enableScissor(cx, cyTop, cx + cw, cyTop + ch);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, "No settings match \"" + q + "\"", cx + cw / 2, cyTop + 20, Draw.MUTED);
        }
        for (Row r : rows) {
            int y = cyTop + r.y() - (int) scroll;
            if (y + r.h() < cyTop || y > cyTop + ch) continue;
            if (r.option() == null) {
                g.drawString(font, r.heading().toUpperCase(Locale.ROOT), cx + 2, y + 3, Draw.GOLD, false);
                continue;
            }
            Option o = r.option();
            boolean hover = Draw.in(hmx, hmy, cx, y, cw, r.h()) && hmy >= cyTop && hmy < cyTop + ch;
            Draw.round(g, cx, y, cx + cw, y + r.h(), hover ? Draw.CARD_HOVER : Draw.CARD);
            g.drawString(font, o.title, cx + 10, y + 8, Draw.TEXT, false);
            int ly = y + 20;
            for (FormattedCharSequence line : o.wrap(font, descWidth(o))) {
                g.drawString(font, line, cx + 10, ly, Draw.MUTED, false);
                ly += 10;
            }
            if (o.controlWidth() > 0) o.renderControl(g, font, cx + cw - 10, y + r.h() / 2, hmx, hmy, delta);
        }
        g.disableScissor();

        // scrollbar
        if (maxScroll() > 0) {
            int trackX = px + pw - 5;
            int thumbH = Math.max(16, (int) (ch * (ch / (double) (contentHeight))));
            int thumbY = cyTop + (int) ((ch - thumbH) * (scroll / maxScroll()));
            Draw.pill(g, trackX, cyTop, trackX + 2, cyTop + ch, 0xFF22242E);
            Draw.pill(g, trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF5A5D70);
        }

        if (picker != null) {
            g.nextStratum();
            picker.render(g, font, mx, my, delta);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        if (picker != null) {
            if (picker.contains(mx, my)) return picker.mouseClicked(this, e, dbl);
            closePicker();
            return true;
        }
        if (search.isMouseOver(mx, my) || Draw.in(mx, my, px + pw - 146, py + 6, 136, 16)) {
            setFocused(search);
            search.setFocused(true);
            return search.mouseClicked(e, dbl) || true;
        }
        search.setFocused(false);
        setFocused(null);

        for (int i = 0; i < categories.size(); i++) {
            int cy = py + HEADER + 8 + i * 20;
            if (Draw.in(mx, my, px + 4, cy, SIDE - 8, 18)) {
                selected = lastCategory = i;
                search.setValue("");
                scroll = 0;
                return true;
            }
        }

        int cx = contentX(), cyTop = contentY(), cw = contentW(), ch = contentH();
        if (!Draw.in(mx, my, cx, cyTop, cw, ch)) return super.mouseClicked(e, dbl);
        for (Row r : layout()) {
            if (r.option() == null) continue;
            int y = cyTop + r.y() - (int) scroll;
            if (!Draw.in(mx, my, cx, y, cw, r.h())) continue;
            Option o = r.option();
            int right = cx + cw - 10, mid = y + r.h() / 2;
            boolean onControl = o.controlWidth() > 0 && Draw.in(mx, my, right - o.controlWidth() - 4, mid - 10, o.controlWidth() + 8, 20);
            if (o instanceof Option.Toggle || onControl) {
                boolean handled = o.click(this, mx, my, right, mid, e.button());
                if (o instanceof Option.Slider s) {
                    draggingSlider = s;
                    draggingRight = right;
                }
                return handled;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (picker != null) return picker.mouseDragged(e);
        if (draggingSlider != null) {
            draggingSlider.drag(e.x(), draggingRight);
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (picker != null) picker.mouseReleased();
        if (draggingSlider != null) {
            draggingSlider.release();
            draggingSlider = null;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (picker != null) return true;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - sy * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE && picker != null) {
            closePicker();
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_F && (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (picker != null && picker.keyPressed(this, e)) return true;
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        if (dirty) SlayersConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (dirty) SlayersConfig.save();
        dirty = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    Font font() {
        return font;
    }
}
