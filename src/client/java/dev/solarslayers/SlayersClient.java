package dev.solarslayers;

import com.mojang.blaze3d.platform.InputConstants;
import dev.solarslayers.compat.Compat;
import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.gui.ConfigScreen;
import dev.solarslayers.hud.SlayerHud;
import dev.solarslayers.slayer.ChatListener;
import dev.solarslayers.slayer.EntityScanner;
import dev.solarslayers.slayer.QuestWarning;
import dev.solarslayers.slayer.ShopTracker;
import dev.solarslayers.slayer.SidebarState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SlayersClient implements ClientModInitializer {
    public static final String MOD_ID = "solarslayers";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category CATEGORY = Compat.keyCategory(MOD_ID, "main");
    private static KeyMapping openKey;
    private static boolean openPending;
    private static final List<long[]> TIMERS = new ArrayList<>();      // {ticksLeft}
    private static final List<Runnable> TIMER_TASKS = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        SlayersConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.solarslayers.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            for (String name : new String[] {"slayer+", "slayerplus"}) {
                dispatcher.register(ClientCommandManager.literal(name).executes(c -> {
                    openPending = true; // opened next tick, after the chat screen has closed
                    return 1;
                }));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(SlayersClient::tick);
        ClientReceiveMessageEvents.GAME.register(ChatListener::onGame);
        AttackEntityCallback.EVENT.register(QuestWarning::onAttack);
        Compat.registerHud(MOD_ID, "hud", SlayerHud::render);

        LOG.info("Solar Slayers loaded");
        System.out.println("[SolarSlayers] loaded: keybind registered, commands /slayer+ /slayerplus"); // Lunar only logs stdout
    }

    private static void tick(Minecraft mc) {
        while (openKey.consumeClick()) openPending = true;
        if (openPending && mc.player != null) {
            openPending = false;
            mc.setScreen(new ConfigScreen(mc.screen));
        }

        if (mc.player != null && mc.level != null) {
            SidebarState.INSTANCE.update(mc);
            EntityScanner.INSTANCE.update(mc);
            ShopTracker.tick(mc);
        } else {
            EntityScanner.INSTANCE.update(mc);
        }

        Iterator<long[]> it = TIMERS.iterator();
        Iterator<Runnable> tasks = TIMER_TASKS.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            long[] t = it.next();
            Runnable r = tasks.next();
            if (--t[0] <= 0) {
                it.remove();
                tasks.remove();
                due.add(r);
            }
        }
        due.forEach(Runnable::run);
    }

    /** Runs {@code task} on the client thread after {@code ticks} ticks. */
    public static void schedule(int ticks, Runnable task) {
        Minecraft.getInstance().execute(() -> {
            TIMERS.add(new long[] {ticks});
            TIMER_TASKS.add(task);
        });
    }

    public static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Solar Slayers").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }
}
