package dev.bloom.client.smoke;

import dev.bloom.client.BloomClient;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.MotionTitleScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * CI smoke test (built only with -Psmoke, never shipped): starts the real game, walks through
 * MOTION's screens and a world, takes screenshots and checks every mixin applies. CI reads the
 * screenshots and the "[MOTION smoke]" lines in the log.
 */
@SuppressWarnings("UnstableApiUsage")
public class SmokeTest implements FabricClientGameTest {
    private static void log(String message) {
        System.out.println("[MOTION smoke] " + message);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        log("start");
        context.waitForScreen(MotionTitleScreen.class);
        context.waitTicks(60);
        context.takeScreenshot("01_title");
        log("title screen ok");

        context.setScreen(() -> new BloomMenuScreen(null));
        context.waitTicks(30);
        context.takeScreenshot("02_menu");
        log("menu ok");

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
//#if MC >= 26.2
            world.getConnection().waitForChunksRender();
//#elif MC >= 26
            world.getClientLevel().waitForChunksRender();
//#else
            world.getClientWorld().waitForChunksRender();
//#endif
            context.waitTicks(60);
            context.takeScreenshot("03_world");
            log("world ok");

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(40);
            context.takeScreenshot("04_avatar");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(20);
            context.takeScreenshot("05_back");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            context.setScreen(() -> new BloomMenuScreen(null));
            context.waitTicks(30);
            context.takeScreenshot("06_menu_in_world");
            context.setScreen(() -> null);
            context.waitTicks(10);

            // Exercise the hooks that only run on input: hit a pig in front of the player
            // (attack, swing, mouse button), zoom with the scroll wheel, the tab list, the pause
            // menu's background, and a resource reload.
            // A sword in hand (the swing animation applies to held items) and clouds on.
            world.getServer().runCommand("give @p minecraft:diamond_sword");
            context.runOnClient(mc -> mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY));
            world.getServer().runCommand("execute as @p at @p anchored eyes run summon minecraft:pig ^ ^-0.6 ^2 {NoAI:1b,NoGravity:1b}");
            context.waitTicks(20);
            context.getInput().pressMouse(0);
            context.waitTicks(10);
            context.takeScreenshot("07_hit");
            context.getInput().holdKey(dev.bloom.client.BloomKeys.ZOOM);
            context.waitTicks(5);
            context.getInput().scroll(1.0);
            context.waitTicks(10);
            context.takeScreenshot("08_zoom");
            context.getInput().releaseKey(dev.bloom.client.BloomKeys.ZOOM);
            context.getInput().holdKey(o -> o.keyPlayerList);
            context.waitTicks(10);
            context.takeScreenshot("09_tab");
            context.getInput().releaseKey(o -> o.keyPlayerList);
            context.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
            context.waitTicks(10);
            context.takeScreenshot("10_pause");
            context.setScreen(() -> null);
            context.runOnClient(mc -> mc.reloadResourcePacks());
            context.waitTicks(200);

            // Applies every mixin to its target now (most targets are already loaded).
            MixinEnvironment.getCurrentEnvironment().audit();
            log("mixin audit done");
            log("render errors: " + BloomClient.renderErrorSummary());
            log("hooks fired: " + dev.bloom.client.Hooks.firedSet());
            log("hooks NOT fired: " + dev.bloom.client.Hooks.notFired());
        }
        log("done");
    }
}
