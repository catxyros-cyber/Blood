package dev.bloom.client.mixin;

import dev.bloom.client.game.CapeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MOTION tags: "[OWNER] name" above the player's head. Only on this client; servers never see it. */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void bloom$tag(CallbackInfoReturnable<Component> cir) {
        dev.bloom.client.Hooks.fired("displayName");
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide()) return;
        cir.setReturnValue(CapeManager.tagged(self.getUUID(), cir.getReturnValue()));
    }
}
