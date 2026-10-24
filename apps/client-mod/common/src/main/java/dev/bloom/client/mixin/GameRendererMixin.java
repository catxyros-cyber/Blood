package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.Zoom;
import dev.bloom.client.render.PostEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Frame timing, and MOTION's shader effects right after the world is drawn (before the HUD). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bloom$frame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (BloomClient.ready()) BloomClient.get().onFrame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void bloom$postEffects(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (!BloomClient.ready()) return;
        try {
            PostEffects.render(deltaTracker.getGameTimeDeltaPartialTick(true));
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("shader effects", e);
        }
    }

    /** Zoom: narrow the world's field of view (not the hand's: that call passes useFovSetting = false). */
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true, require = 0)
    private void motion$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (!useFovSetting) return;
        double m = Zoom.fovMultiplier();
        if (m < 0.999) cir.setReturnValue(cir.getReturnValue() * m);
    }
}
