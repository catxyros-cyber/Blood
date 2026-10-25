package dev.bloom.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bloom.client.BloomClient;
import dev.bloom.client.game.SwordAnimation;
//#if MC < 26.3
import net.minecraft.client.renderer.ItemInHandRenderer;
//#endif
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swing animations. Pinned to the exact method; a version without it simply keeps Minecraft's swing. */
//#if MC >= 26.3
// 26.3 renamed the first-person hand renderer.
@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
//#else
@Mixin(ItemInHandRenderer.class)
//#endif
public abstract class ItemInHandRendererMixin {
    @Inject(method = "applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            at = @At("TAIL"), require = 0)
    private void motion$swingAnimation(PoseStack pose, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        dev.bloom.client.Hooks.fired("swing");
        if (!BloomClient.ready()) return;
        try {
            SwordAnimation.apply(pose, arm, swingProgress);
        } catch (RuntimeException e) {
            BloomClient.reportRenderError("swing animation", e);
        }
    }
}
