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
 * {@code    » +25 Slayer XP • +79 Essence • +16 Moon Fang}.
 */
public final class ChatListener {
    private static final Pattern MINIBOSS = Pattern.compile("MINIBOSS! An? (.+?) spawned");
    private static final Pattern ESSENCE_GAIN = Pattern.compile("\\+([\\d,]+) Essence");

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

        if (text.contains("SLAYER BOSS SLAIN!")) {
            if (cfg.slainChatLink) {
                // A few ticks later, so the link lands under the server's kill summary.
                SlayersClient.schedule(5, ChatListener::sendMenuLink);
            }
            return;
        }

        if (text.startsWith("»") && text.contains("Slayer XP")) {
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
        mc.gui.hud.getChat().addClientSystemMessage(SlayersClient.prefix().append(link));
    }
}
