package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Hide Minecraft's rain" when MOTION's own weather is on. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bloom$hideVanillaWeather(CallbackInfo ci) {
        BloomClient bc = BloomClient.get();
        if (bc != null && BloomSettings.ENABLED.on() && BloomSettings.RAIN_HIDE_VANILLA.on() && !bc.worldEffectsPaused()) ci.cancel();
    }
}
