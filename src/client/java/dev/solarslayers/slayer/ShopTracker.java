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

    public static boolean isShop(AbstractContainerScreen<?> screen) {
        return Txt.norm(screen.getTitle().getString()).contains("upgrade shop");
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

    /** Reads the exact essence balance while the shop is open. */
    public static void tick(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !isShop(cs)) return;
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory) continue;
            for (String l : lore(s.getItem())) {
                Matcher m = BALANCE.matcher(l);
                if (m.find()) {
                    double v = Txt.num(m.group(1));
                    SlayersConfig cfg = SlayersConfig.get();
                    if (v != cfg.essence) {
                        cfg.essence = v;
                        SlayersConfig.save();
                    }
                    return;
                }
            }
        }
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
            } else {
                r.kind = Requirement.Kind.OTHER;
                r.target = t;
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
            case ITEM -> count(mc, r.target);
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
        int n = 0;
        for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
            if (!s.isEmpty() && itemName(s).equalsIgnoreCase(name)) n += s.getCount();
        }
        return n;
    }

    public static boolean met(Requirement r, double have) {
        if (have < 0) return r.metInShop;
        return have >= r.need;
    }
}
