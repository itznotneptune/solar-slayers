package dev.solarslayers.render;

import dev.solarslayers.SlayersClient;
import dev.solarslayers.compat.Compat;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class Highlight {
    /** ARGB highlight colour carried from extractRenderState to submit; null = not highlighted. */
    public static final RenderStateDataKey<Integer> COLOR = RenderStateDataKey.create(() -> "solarslayers:highlight");

    private static RenderType overlay;

    private Highlight() {}

    /** A plain white texture drawn translucent over the model, so the vertex colour comes out exactly as chosen. */
    public static RenderType overlay() {
        if (overlay == null) overlay = RenderTypes.entityTranslucent(Compat.id(SlayersClient.MOD_ID, "textures/misc/white.png"));
        return overlay;
    }
}
