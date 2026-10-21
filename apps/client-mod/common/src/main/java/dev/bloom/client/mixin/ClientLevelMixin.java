package dev.bloom.client.mixin;

import dev.bloom.client.render.FogTweaks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void bloom$tintSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 v = cir.getReturnValue();
        Vec3 tinted = FogTweaks.tintSky(v);
        if (tinted != v) cir.setReturnValue(tinted);
    }
}
