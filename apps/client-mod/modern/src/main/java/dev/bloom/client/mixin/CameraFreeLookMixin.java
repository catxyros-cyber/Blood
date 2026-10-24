package dev.bloom.client.mixin;

import dev.bloom.client.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies the independent camera angles while the held free-look key is down. */
@Mixin(Camera.class)
public abstract class CameraFreeLookMixin {
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float motion$freeLookYaw(Entity entity, float partialTick) {
        return FreeLook.viewYaw(entity, partialTick);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float motion$freeLookPitch(Entity entity, float partialTick) {
        return FreeLook.viewPitch(entity, partialTick);
    }
}
