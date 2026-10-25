package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.game.HitEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit effects: notices when you attack something. It only watches; the attack itself is untouched. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), require = 0)
    private void motion$hitEffects(Player player, Entity target, CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("attack");
        if (!BloomClient.ready()) return;
        try {
            HitEffects.onAttack(Minecraft.getInstance(), target);
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("hit effects", e);
        }
    }
}
