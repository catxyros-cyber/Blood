package dev.bloom.client.mixin;

import dev.bloom.client.BloomClient;
import dev.bloom.client.gui.MotionTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replaces only Minecraft's entry title screen; every other screen remains vanilla. */
@Mixin(Minecraft.class)
public abstract class MinecraftTitleScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen motion$replaceTitleScreen(Screen next) {
        return BloomClient.ready() && next instanceof TitleScreen ? new MotionTitleScreen() : next;
    }
}
