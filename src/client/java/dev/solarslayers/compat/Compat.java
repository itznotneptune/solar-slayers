package dev.solarslayers.compat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Minecraft 1.21.11: resource ids are {@code Identifier}. */
public final class Compat {
    private Compat() {}

    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static KeyMapping.Category keyCategory(String namespace, String path) {
        return KeyMapping.Category.register(Identifier.fromNamespaceAndPath(namespace, path));
    }

    public static void registerHud(String namespace, String path, HudElement element) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(namespace, path), element);
    }
}
