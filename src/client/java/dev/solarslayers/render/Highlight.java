package dev.solarslayers.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class Highlight {
    /** ARGB highlight colour carried from extractRenderState to submit; null = not highlighted. */
    public static final RenderStateDataKey<Integer> COLOR = RenderStateDataKey.create(() -> "solarslayers:highlight");

    private Highlight() {}
}
