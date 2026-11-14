package dev.solarslayers.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.solarslayers.render.Highlight;
import dev.solarslayers.slayer.EntityScanner;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whole-body highlight: highlighted entities are drawn full-bright with the model tinted to the chosen colour. The tint is
 * applied where {@code submit} reads {@code getModelTint}, so renderers that override it (wolves do) are covered too.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void solarslayers$tag(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        Integer color = EntityScanner.INSTANCE.colorFor(entity.getId());
        ((FabricRenderState) state).setData(Highlight.COLOR, color);
        if (color != null) state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
    }

    @ModifyExpressionValue(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
    private int solarslayers$tint(int original, @Local(argsOnly = true) LivingEntityRenderState state) {
        Integer color = ((FabricRenderState) state).getData(Highlight.COLOR);
        return color == null ? original : (0xFF000000 | color);
    }
}
