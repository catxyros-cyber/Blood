package dev.bloom.client.mixin;

import dev.bloom.client.game.CapeManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BLOOD capes: swap the cape texture in the player's skin, so Minecraft's own cape layer (and the
 * elytra, when the player has no elytra texture) draw it. Only this client sees the change.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void bloom$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        ResourceLocation cape = CapeManager.capeFor(((AbstractClientPlayer) (Object) this).getUUID());
        if (cape == null) return;
        PlayerSkin s = cir.getReturnValue();
        if (s == null || cape.equals(s.capeTexture())) return;
        cir.setReturnValue(new PlayerSkin(s.texture(), s.textureUrl(), cape, s.elytraTexture(), s.model(), s.secure()));
    }
}
