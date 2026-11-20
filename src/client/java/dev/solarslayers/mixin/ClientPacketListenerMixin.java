package dev.solarslayers.mixin;

import dev.solarslayers.world.PrismLaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the Prism Laser's particle packets so {@link PrismLaser} can draw its outline instead. */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void solarslayers$prismLaser(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        // the network thread only reschedules the packet onto the client thread; act there
        if (!Minecraft.getInstance().isSameThread()) return;
        if (PrismLaser.onPacket(packet)) ci.cancel();
    }
}
