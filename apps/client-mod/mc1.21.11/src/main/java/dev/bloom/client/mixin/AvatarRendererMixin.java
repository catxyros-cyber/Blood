package dev.bloom.client.mixin;

import dev.bloom.client.game.CapeMotion;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth cape motion: ease Minecraft's cape angles after it computes them (see CapeMotion). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractCapeState", at = @At("TAIL"))
    private void bloom$smoothCape(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        try {
            CapeMotion.apply(state);
        } catch (RuntimeException ignored) {
            // Never break player rendering over a cosmetic.
        }
    }
}
