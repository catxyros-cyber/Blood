package dev.bloom.client.game;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bloom.client.core.fx.HitShapes;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** First-person swing animations (Spin / Flip / Whirl). Visual only: your hits are unchanged. */
public final class SwordAnimation {
    private SwordAnimation() {
    }

    /** Called from ItemInHandRendererMixin after Minecraft's own attack transform. */
    public static void apply(PoseStack pose, HumanoidArm arm, float swingProgress) {
        if (swingProgress <= 0f || swingProgress >= 1f) return;
        if (!BloomSettings.ENABLED.on() || BloomSettings.SWORD_ANIMATION.is("off")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = arm == mc.player.getMainArm() ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!animates(stack)) return;
        float deg = HitShapes.swingDegrees(swingProgress) * (arm == HumanoidArm.RIGHT ? 1f : -1f);
        // Pivot around the middle of the blade rather than the hand, so it twirls in place.
        pose.translate(0f, 0.2f, 0f);
        switch (BloomSettings.SWORD_ANIMATION.id()) {
            case "flip" -> pose.mulPose(Axis.XP.rotationDegrees(-deg));
            case "whirl" -> pose.mulPose(Axis.ZP.rotationDegrees(deg));
            default -> pose.mulPose(Axis.YP.rotationDegrees(-deg));
        }
        pose.translate(0f, -0.2f, 0f);
    }

    static boolean animates(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.endsWith("_sword")) return true;
        return BloomSettings.SWORD_ANIMATION_ALL.on()
                && (path.endsWith("_axe") || path.equals("mace") || path.equals("trident") || path.endsWith("_spear"));
    }
}
