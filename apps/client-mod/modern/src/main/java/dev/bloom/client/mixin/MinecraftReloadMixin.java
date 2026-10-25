package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** F3+T and resource pack changes: once the reload finishes, drop cached sky textures and shader targets. */
@Mixin(Minecraft.class)
public abstract class MinecraftReloadMixin {
    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void bloom$reloaded(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        dev.bloom.client.Hooks.fired("reload");
        CompletableFuture<Void> done = cir.getReturnValue();
        if (done == null) return;
        Minecraft mc = (Minecraft) (Object) this;
        done.thenRunAsync(() -> {
            if (BloomClient.ready()) BloomClient.get().onResourcesReloaded();
        }, mc);
    }
}
