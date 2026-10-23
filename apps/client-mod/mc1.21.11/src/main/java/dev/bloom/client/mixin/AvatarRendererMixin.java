package dev.bloom.client.mixin;

import dev.bloom.client.game.CapeMotion;
import dev.bloom.client.game.SkinStatePatch;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth cape motion and ensure the custom cape reaches the final render state. */
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

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void bloom$capeOnState(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        SkinStatePatch.apply(entity.getUUID(), state);
    }
}
