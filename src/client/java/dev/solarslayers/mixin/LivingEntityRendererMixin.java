package dev.solarslayers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.solarslayers.config.SlayersConfig;
import dev.solarslayers.render.Highlight;
import dev.solarslayers.slayer.EntityScanner;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whole-body highlight. The entity is drawn as normal (full-bright), then the same model is drawn again on top in a solid,
 * semi-transparent colour ({@link Highlight#overlay()}). Unlike tinting the model, this keeps the chosen colour true on
 * any texture: light blue on an orange blaze stays light blue instead of turning green.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void solarslayers$tag(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        Integer color = EntityScanner.INSTANCE.colorFor(entity.getId());
        ((FabricRenderState) state).setData(Highlight.COLOR, color);
        if (color != null) state.lightCoords = LightTexture.FULL_BRIGHT;
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void solarslayers$overlay(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack pose, RenderType type,
                                      int light, int overlay, int tint, TextureAtlasSprite sprite, int outline,
                                      ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
        original.call(collector, model, state, pose, type, light, overlay, tint, sprite, outline, crumbling);
        // type is null when the body is invisible; draw nothing extra then
        if (type == null || !(state instanceof FabricRenderState frs)) return;
        Integer color = frs.getData(Highlight.COLOR);
        if (color == null) return;
        int alpha = Math.round(SlayersConfig.get().highlightOpacity * 2.55f);
        original.call(collector, model, state, pose, Highlight.overlay(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                (alpha << 24) | (color & 0xFFFFFF), null, 0, null);
    }
}
