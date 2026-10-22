package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Clouds" off in BLOOD's sky settings: skip Minecraft's clouds while a BLOOD skybox is shown. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bloom$hideClouds(CallbackInfo ci) {
        BloomClient bc = BloomClient.get();
        if (bc != null && bc.skyActive() && !BloomSettings.SKY_CLOUDS.on()) ci.cancel();
    }
}
