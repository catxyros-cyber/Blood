package dev.bloom.client.mixin;

import dev.bloom.client.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies the independent camera angles while the held free-look key is down. */
@Mixin(Camera.class)
public abstract class CameraFreeLookMixin {
    // 26.1 split Camera#setup; the entity's view angles are read in alignWithEntity.
//#if MC >= 26
    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
//#else
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
//#endif
    private float motion$freeLookYaw(Entity entity, float partialTick) {
        return FreeLook.viewYaw(entity, partialTick);
    }

//#if MC >= 26
    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
//#else
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
//#endif
    private float motion$freeLookPitch(Entity entity, float partialTick) {
        return FreeLook.viewPitch(entity, partialTick);
    }

//#if MC >= 26
    /** Zoom: 26.1 moved the world's field of view into the camera (the hand's is calculateHudFov). */
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void motion$zoom(float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        double m = dev.bloom.client.Zoom.fovMultiplier();
        if (m < 0.999) cir.setReturnValue((float) (cir.getReturnValue() * m));
    }
//#endif
}
