package dev.bloom.client.mixin;

import dev.bloom.client.game.CapeManager;
import dev.bloom.client.game.SkinCompat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MOTION capes: swap the cape texture in the player's skin, so Minecraft's own cape layer (and the
 * elytra, when the player has no elytra texture) draw it. Only this client sees the change.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void bloom$cape(CallbackInfoReturnable<Object> cir) {
        dev.bloom.client.Hooks.fired("skin");
        Identifier cape = CapeManager.capeFor(((AbstractClientPlayer) (Object) this).getUUID());
        if (cape == null) return;
        Object skin = cir.getReturnValue();
        Object changed = SkinCompat.withCape(skin, cape);
        if (changed != skin) cir.setReturnValue(changed);
    }
}
