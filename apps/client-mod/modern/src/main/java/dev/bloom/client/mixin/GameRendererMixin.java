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

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
//#if MC >= 26.3
    private void bloom$frame(CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("frame");
//#else
    private void bloom$frame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("frame");
//#endif
        if (BloomClient.ready()) BloomClient.get().onFrame();
    }

    /** After the world (and hand) are drawn, before the HUD: MOTION's shader effects. */
    @Inject(method = "renderLevel", at = @At("TAIL"))
//#if MC >= 26.3
    private void bloom$postEffects(CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("postEffects");
        DeltaTracker deltaTracker = net.minecraft.client.Minecraft.getInstance().getDeltaTracker();
//#else
    private void bloom$postEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("postEffects");
//#endif
        if (!BloomClient.ready()) return;
        try {
            PostEffects.render(deltaTracker.getGameTimeDeltaPartialTick(true));
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("shader effects", e);
        }
    }

//#if MC < 26
    /** Zoom: narrow the world's field of view (not the hand's: that call passes useFovSetting = false). */
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void motion$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        dev.bloom.client.Hooks.fired("zoomFov");
        if (!useFovSetting) return;
        double m = Zoom.fovMultiplier();
        if (m < 0.999) cir.setReturnValue((float) (cir.getReturnValue() * m));
    }
//#endif
}
