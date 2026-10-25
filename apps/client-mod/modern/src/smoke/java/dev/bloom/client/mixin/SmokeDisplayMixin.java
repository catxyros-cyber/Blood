//#if MC >= 26.3
package dev.bloom.client.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * CI smoke test only (never in a release jar): the virtual display on CI (Xvfb + Mesa) offers no
 * sRGB-capable framebuffers, so 26.3's SDL window can't be created. Real GPUs have them. This drops
 * the sRGB request (SDL_GL_FRAMEBUFFER_SRGB_CAPABLE, the fifth attribute set) under the test.
 */
@Mixin(value = GlBackend.class, remap = false)
public abstract class SmokeDisplayMixin {
    @ModifyArg(method = "createWindow", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z", ordinal = 4), index = 1)
    private int motion$noSrgbOnCi(int value) {
        return 0;
    }
}
//#endif
