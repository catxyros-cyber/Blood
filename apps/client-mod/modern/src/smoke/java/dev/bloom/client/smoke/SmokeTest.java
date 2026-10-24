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

            // Applies every mixin to its target now (most targets are already loaded).
            MixinEnvironment.getCurrentEnvironment().audit();
            log("mixin audit done");
            log("render errors: " + BloomClient.renderErrorSummary());
        }
        log("done");
    }
}
