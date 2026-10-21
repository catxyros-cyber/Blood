package dev.bloom.client.mixin;

import dev.bloom.client.game.CapeManager;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BLOOD tags in the Tab player list too. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void bloom$tag(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(CapeManager.tagged(info.getProfile().getId(), cir.getReturnValue()));
    }
}
