package dev.bloom.client.fabric;

import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.platform.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;

/** Fabric (and Quilt, through its Fabric compatibility) entry point. */
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
        HudRenderCallback.EVENT.register((graphics, delta) -> BloomClient.get().renderHud(graphics, delta));
        WorldRenderEvents.LAST.register(context -> BloomClient.get().renderWorld(context.positionMatrix(), context.projectionMatrix(),
                context.camera(), context.tickCounter().getGameTimeDeltaPartialTick(false)));
    }
}
