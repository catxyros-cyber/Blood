package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.render.PostEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bloom$frame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (BloomClient.ready()) BloomClient.get().onFrame();
    }

    /** After the world (and hand) are drawn, before the HUD: BLOOD's shader effects. */
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void bloom$postEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!BloomClient.ready()) return;
        try {
            PostEffects.render(deltaTracker.getGameTimeDeltaPartialTick(true));
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("shader effects", e);
        }
    }
}
