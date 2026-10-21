package dev.bloom.client.neoforge;

import dev.bloom.client.BloomClient;
import dev.bloom.client.BloomKeys;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.platform.Platform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

/** NeoForge entry point (client only). */
@Mod(value = BloomClient.MOD_ID, dist = Dist.CLIENT)
public final class BloomNeoForge {
    public BloomNeoForge(IEventBus modBus, ModContainer container) {
        BloomClient.init(new Platform() {
            @Override
            public String loaderName() {
                return "NeoForge";
            }

            @Override
            public boolean isModLoaded(String modId) {
                return ModList.get().isLoaded(modId);
            }

            @Override
            public Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }

            @Override
            public String modVersion() {
                return container.getModInfo().getVersion().toString();
            }
        });
        modBus.addListener(BloomNeoForge::registerKeys);
        modBus.addListener(BloomNeoForge::registerLayers);
        NeoForge.EVENT_BUS.addListener(BloomNeoForge::onTick);
        NeoForge.EVENT_BUS.addListener(BloomNeoForge::onRenderStage);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new BloomMenuScreen(parent));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : BloomKeys.ALL) event.register(key);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "hud"),
                (graphics, delta) -> BloomClient.get().renderHud(graphics, delta));
    }

    private static void onTick(ClientTickEvent.Post event) {
        BloomClient.get().tick(Minecraft.getInstance());
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        BloomClient.get().renderWorld(event.getModelViewMatrix(), event.getProjectionMatrix(), event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
