package dev.bloom.client.fabric;

import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.platform.Platform;
import dev.bloom.client.render.OutlineRenderer;
import dev.bloom.client.render.WorldMatrices;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/** Fabric (and Quilt, through its Fabric compatibility) entry point for Minecraft 1.21.11. */
public final class BloomFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BloomClient.init(new Platform() {
            @Override
            public String loaderName() {
                return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "Quilt" : "Fabric";
            }

            @Override
            public boolean isModLoaded(String modId) {
                return FabricLoader.getInstance().isModLoaded(modId);
            }

            @Override
            public Path configDir() {
                return FabricLoader.getInstance().getConfigDir();
            }

            @Override
            public String modVersion() {
                return FabricLoader.getInstance().getModContainer(BloomClient.MOD_ID)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
            }
        });
        for (KeyMapping key : BloomKeys.ALL) KeyBindingHelper.registerKeyBinding(key);
        ClientTickEvents.END_CLIENT_TICK.register(client -> BloomClient.get().tick(client));
        // MOTION wings on every player model (players and mannequins).
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
                @SuppressWarnings("unchecked")
                var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel>) (Object) avatar;
                helper.register(new dev.bloom.client.render.WingsLayer(parent));
            }
        });
        HudRenderCallback.EVENT.register((graphics, delta) -> BloomClient.get().renderHud(graphics, delta));
        WorldRenderEvents.END_MAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            BloomClient.get().renderWorld(WorldMatrices.MODEL_VIEW, WorldMatrices.PROJECTION,
                    mc.gameRenderer.getMainCamera(), WorldMatrices.partialTick);
        });
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            if (!BloomClient.ready()) return true;
            try {
                // Returning false skips Minecraft's outline because MOTION drew its own.
                return !OutlineRenderer.render(Minecraft.getInstance().gameRenderer.getMainCamera(), WorldMatrices.MODEL_VIEW);
            } catch (RuntimeException e) {
                BloomClient.reportRenderError("block outline", e);
                return true;
            }
        });
    }
}
