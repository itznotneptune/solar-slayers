package dev.solarslayers.slayer;

import dev.solarslayers.SlayersClient;
import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.util.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reacts to slayer chat. Recorded messages (2026-11-14):
 * {@code MINIBOSS! A Moonlit Hunter spawned. ...}, {@code   NICE! SLAYER BOSS SLAIN!},
 * {@code    » +25 Slayer XP • +79 Essence • +16 Moon Fang}; with no pity item set (2026-11-16)
 * {@code    » Pity Meter - Not set (66k XP saved)}.
 */
public final class ChatListener {
    private static final Pattern MINIBOSS = Pattern.compile("MINIBOSS! An? (.+?) spawned");
    private static final Pattern ESSENCE_GAIN = Pattern.compile("\\+([\\d,]+) Essence");
    private static final Pattern SLAYER_XP_GAIN = Pattern.compile("\\+([\\d,]+) Slayer XP");
    static final Pattern PITY_SAVED = Pattern.compile("pity meter\\s*[-–—:]\\s*not set\\s*\\(([\\d.,]+[kmbt]?) xp saved\\)");

    private ChatListener() {}

    public static void onGame(Component message, boolean overlay) {
        if (overlay) return;
        String text = Txt.clean(message.getString());
        SlayersConfig cfg = SlayersConfig.get();

        Matcher m = MINIBOSS.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty() && !cfg.minibossNames.contains(name)) {
                cfg.minibossNames.add(name);
                SlayersConfig.save();
            }
            return;
        }

        if (text.contains("SLAYER QUEST STARTED!")) {
            SlayerTimer.INSTANCE.onQuestStart(System.currentTimeMillis());
            return;
        }
        if (text.contains("SLAYER BOSS SPAWNED!")) {
            SlayerTimer.INSTANCE.onBossSpawn(System.currentTimeMillis());
            return;
        }
        if (text.contains("SLAYER QUEST FAILED!")) {
            SlayerTimer.INSTANCE.onQuestFail(System.currentTimeMillis());
            return;
        }

        if (text.contains("SLAYER BOSS SLAIN!")) {
            SlayerTimer.INSTANCE.onBossKill(System.currentTimeMillis());
            if (cfg.slainChatLink) {
                // A few ticks later, so the link lands under the server's kill summary.
                SlayersClient.schedule(5, ChatListener::sendMenuLink);
            }
            return;
        }

        String norm = Txt.norm(text);
        Matcher p = PITY_SAVED.matcher(norm);
        if (p.find()) {
            double saved = Txt.num(p.group(1));
            if (!Double.isNaN(saved)) {
                cfg.pitySaved.put(SidebarState.INSTANCE.pityKey(), saved);
                SlayersConfig.save();
            }
            return;
        }
        if (norm.contains("pity meter") && !norm.contains("not set")) {
            // "» Pity Meter [|||…] 50% (125/250 XP) (...)": a pity item is set, so any saved figure is out of date
            SidebarState.forgetSavedPity();
            return;
        }

        if (text.startsWith("»") && text.contains("Slayer XP")) {
            Matcher x = SLAYER_XP_GAIN.matcher(text);
            if (x.find()) {
                double xp = Txt.num(x.group(1));
                if (xp > 0) {
                    cfg.bossXp.put(SidebarState.INSTANCE.pityKey(), xp);
                    SlayersConfig.save();
                }
            }
            Matcher e = ESSENCE_GAIN.matcher(text);
            if (e.find() && !Double.isNaN(cfg.essence)) {
                cfg.essence += Txt.num(e.group(1));
                SlayersConfig.save();
            }
        }
    }

    private static void sendMenuLink() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MutableComponent link = Component.literal("» Click here to open the slayer menu").withStyle(s -> s
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/slayer"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Runs /slayer").withStyle(ChatFormatting.GRAY))));
        mc.gui.getChat().addMessage(SlayersClient.prefix().append(link));
    }
}
