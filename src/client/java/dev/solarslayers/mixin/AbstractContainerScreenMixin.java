package dev.solarslayers.mixin;

import dev.solarslayers.slayer.ShopTracker;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swallows a right-click on an Upgrade Shop item (which would craft it) and toggles tracking instead. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void solarslayers$track(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (ShopTracker.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)) ci.cancel();
    }
}
