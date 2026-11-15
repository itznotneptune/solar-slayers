package dev.solarslayers.compat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/** Minecraft 1.21.9/1.21.10: resource ids are {@code ResourceLocation}. */
public final class Compat {
    private Compat() {}

    public static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static KeyMapping.Category keyCategory(String namespace, String path) {
        return KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static void registerHud(String namespace, String path, HudElement element) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ResourceLocation.fromNamespaceAndPath(namespace, path), element);
    }
}
