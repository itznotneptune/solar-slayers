package dev.solarslayers.slayer;

import dev.solarslayers.SlayersClient;
import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.config.SlayersConfig.Requirement;
import dev.solarslayers.config.SlayersConfig.TrackedItem;
import dev.solarslayers.util.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upgrade Shop tracking. Recorded lore (2026-11-14), after "Requires:":
 * {@code  ✔ Moonfang Alpha Level 2}, {@code  ✔ 1,000 Essence}, {@code  ✘ 256 Moon Fang}; the balance item reads
 * {@code  | Essence: 1,831}. Right-click on a craftable item would craft it, so that click is swallowed here and never
 * reaches the server.
 */
public final class ShopTracker {
    private static final Pattern LEVEL = Pattern.compile("^(.+?) Level (\\d+)$");
    private static final Pattern ESSENCE = Pattern.compile("^([\\d,]+) Essence$");
    private static final Pattern ITEM = Pattern.compile("^([\\d,]+) (.+)$");
    private static final Pattern BALANCE = Pattern.compile("Essence: ([\\d,]+)");
    private static final Pattern NAME_TAG = Pattern.compile("\\s*\\[[^\\]]*\\]\\s*$");

    private ShopTracker() {}

    /**
     * A slayer shop screen. Since the 2026-11-15 menu update the items sit in "Sword Upgrades", "Armor Upgrades" and
     * "Throwables" under the main "Upgrade Shop"; every one of them shows the Slayer Essence balance item, so that is
     * what identifies a shop screen, whatever it is titled.
     */
    public static boolean isShop(AbstractContainerScreen<?> screen) {
        String title = Txt.norm(screen.getTitle().getString());
        if (title.contains("upgrade")) return true;
        for (Slot s : screen.getMenu().slots) {
            if (s.container instanceof Inventory) continue;
            for (String l : lore(s.getItem())) {
                if (BALANCE.matcher(l).find()) return true;
            }
        }
        return false;
    }

    /** @return true to cancel the click (it was a tracking toggle). */
    public static boolean onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int button, ClickType type) {
        if (!SlayersConfig.get().shopTracker || slot == null || button != 1 || type != ClickType.PICKUP) return false;
        if (!isShop(screen) || slot.container instanceof Inventory) return false;
        ItemStack stack = slot.getItem();
        List<String> lore = lore(stack);
        if (lore.stream().noneMatch(l -> l.equals("Requires:"))) return false;
        toggle(stack, lore);
        return true;
    }

    /**
     * While a shop screen is open: reads the exact essence balance, and refreshes tracked items' requirements from the
     * live lore (so the ✔/✘ and any requirement the server changes stay current).
     */
    public static void tick(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !isShop(cs)) return;
        SlayersConfig cfg = SlayersConfig.get();
        boolean changed = false;
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory) continue;
            List<String> lore = lore(s.getItem());
            for (String l : lore) {
                Matcher m = BALANCE.matcher(l);
                if (m.find()) {
                    double v = Txt.num(m.group(1));
                    if (v != cfg.essence) {
                        cfg.essence = v;
                        changed = true;
                    }
                }
            }
            if (cfg.tracked.isEmpty() || lore.stream().noneMatch(l -> l.equals("Requires:"))) continue;
            String name = itemName(s.getItem());
            for (TrackedItem t : cfg.tracked) {
                if (!t.name.equals(name)) continue;
                List<Requirement> fresh = parse(lore);
                if (!same(fresh, t.requirements)) {
                    t.requirements = fresh;
                    changed = true;
                }
            }
        }
        if (changed) SlayersConfig.save();
    }

    private static boolean same(List<Requirement> a, List<Requirement> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Requirement x = a.get(i), y = b.get(i);
            if (x.kind != y.kind || x.need != y.need || x.metInShop != y.metInShop || !x.target.equals(y.target)) return false;
        }
        return true;
    }

    public static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack.isEmpty()) return out;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return out;
        for (Component c : lore.lines()) out.add(Txt.clean(c.getString()));
        return out;
    }

    public static String itemName(ItemStack stack) {
        return NAME_TAG.matcher(Txt.clean(stack.getHoverName().getString())).replaceAll("").trim();
    }

    private static void toggle(ItemStack stack, List<String> lore) {
        SlayersConfig cfg = SlayersConfig.get();
        String name = itemName(stack);
        Minecraft mc = Minecraft.getInstance();
        boolean removed = cfg.tracked.removeIf(t -> t.name.equals(name));
        if (removed) {
            mc.gui.getChat().addMessage(SlayersClient.prefix()
                    .append(Component.literal("Stopped tracking ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW)));
        } else {
            TrackedItem t = new TrackedItem();
            t.name = name;
            t.requirements = parse(lore);
            cfg.tracked.add(t);
            mc.gui.getChat().addMessage(SlayersClient.prefix()
                    .append(Component.literal("Now tracking ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (right-click again to stop)").withStyle(ChatFormatting.DARK_GRAY)));
        }
        SlayersConfig.save();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, removed ? 0.8f : 1.2f));
    }

    static List<Requirement> parse(List<String> lore) {
        List<Requirement> out = new ArrayList<>();
        boolean in = false;
        for (String line : lore) {
            if (line.equals("Requires:")) { in = true; continue; }
            if (!in) continue;
            if (line.isBlank()) break;
            String t = line.trim();
            Requirement r = new Requirement();
            r.metInShop = t.startsWith("✔");
            t = t.replaceFirst("^[✔✘]\\s*", "");
            Matcher m;
            if ((m = LEVEL.matcher(t)).matches()) {
                r.kind = Requirement.Kind.LEVEL;
                r.target = m.group(1).trim();
                r.need = Long.parseLong(m.group(2));
            } else if ((m = ESSENCE.matcher(t)).matches()) {
                r.kind = Requirement.Kind.ESSENCE;
                r.target = "Essence";
                r.need = Long.parseLong(m.group(1).replace(",", ""));
            } else if ((m = ITEM.matcher(t)).matches()) {
                r.kind = Requirement.Kind.ITEM;
                r.target = m.group(2).trim();
                r.need = Long.parseLong(m.group(1).replace(",", ""));
            } else if (!t.isEmpty()) {
                // No count: one specific item to own, e.g. the previous tier ("Moonhide Helmet") or a boss drop ("Star Core").
                r.kind = Requirement.Kind.OWN;
                r.target = t;
                r.need = 1;
            } else {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    /** Current amount toward a requirement, or -1 when unknown. */
    public static double have(Requirement r, Minecraft mc) {
        SlayersConfig cfg = SlayersConfig.get();
        return switch (r.kind) {
            case LEVEL -> {
                Integer lvl = cfg.slayerLevels.get(Txt.norm(r.target));
                yield lvl == null ? -1 : lvl;
            }
            case ESSENCE -> essence();
            case ITEM, OWN -> count(mc, r.target);
            case OTHER -> -1;
        };
    }

    /** Best known essence: the exact shop value, unless the sidebar proves it stale. */
    public static double essence() {
        SlayersConfig cfg = SlayersConfig.get();
        SidebarState sb = SidebarState.INSTANCE;
        double side = sb.essence();
        if (!Double.isNaN(side)) {
            double tol = Txt.roundingTolerance(sb.essenceText);
            if (Double.isNaN(cfg.essence) || Math.abs(cfg.essence - side) > tol) return side;
        }
        return Double.isNaN(cfg.essence) ? -1 : cfg.essence;
    }

    private static int count(Minecraft mc, String name) {
        if (mc.player == null) return -1;
        Inventory inv = mc.player.getInventory();
        int n = 0;
        // whole inventory including worn armour and offhand: the previous armour tier is often being worn
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && itemName(s).equalsIgnoreCase(name)) n += s.getCount();
        }
        return n;
    }

    public static boolean met(Requirement r, double have) {
        if (have < 0) return r.metInShop;
        // an owned item may be in a vault rather than the inventory, so trust the shop's ✔ for those
        if (r.kind == Requirement.Kind.OWN) return have >= 1 || r.metInShop;
        return have >= r.need;
    }
}
