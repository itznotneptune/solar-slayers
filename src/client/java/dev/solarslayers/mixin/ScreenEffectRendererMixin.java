package dev.solarslayers.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.solarslayers.config.SlayersConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low fire: moves the burning overlay down the screen by {@link SlayersConfig#fireOverlay}, or hides it at 0%.
 * The method is {@code renderFire} up to 1.21.11 and {@code submitFire} in 26.3; both take the pose stack first.
 */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
    /** How far the flames drop at 1%: half the overlay's height, which leaves only their tips showing. */
    private static final float MAX_DROP = 0.5f;

    @Inject(method = {"renderFire", "submitFire"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void solarslayers$lowerFire(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        int pct = SlayersConfig.get().fireOverlay;
        if (pct >= 100) return;
        if (pct <= 0) {
            ci.cancel();
            return;
        }
        pose.translate(0, -drop(pct), 0);
    }

    @Inject(method = {"renderFire", "submitFire"}, at = @At("RETURN"), require = 0)
    private static void solarslayers$restoreFire(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        int pct = SlayersConfig.get().fireOverlay;
        if (pct > 0 && pct < 100) pose.translate(0, drop(pct), 0);
    }

    private static float drop(int pct) {
        return MAX_DROP * (100 - pct) / 100f;
    }
}
