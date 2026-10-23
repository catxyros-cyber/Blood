package dev.bloom.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** MOTION's title screen with a short, animated entry into the game menu. */
public final class MotionTitleScreen extends Screen {
    private static final long WALK_IN_MS = 1_250L;
    private boolean entered;
    private boolean entering;
    private long openedAt = System.currentTimeMillis();
    private long enterStartedAt;

    public MotionTitleScreen() { super(Component.literal("MOTION Client")); }

    @Override
    protected void init() {
        clearWidgets();
        if (!entered) {
            if (!entering) {
                Button enter = Button.builder(Component.literal("Enter MOTION"), button -> enterMenu())
                        .bounds(width / 2 - 100, height / 2 + 84, 200, 28).build();
                addRenderableWidget(enter);
                setInitialFocus(enter);
            }
            return;
        }

        int x = Math.max(28, Math.round(width * 0.18f));
        int y = height / 2 - 48;
        int w = 216;
        int gap = 34;
        addRenderableWidget(Button.builder(Component.literal("Singleplayer"), b ->
                Minecraft.getInstance().setScreen(new SelectWorldScreen(this))).bounds(x, y, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("Multiplayer"), b ->
                Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this))).bounds(x, y + gap, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("Minecraft Settings"), b ->
                Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options))).bounds(x, y + gap * 2, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("MOTION Client"), b ->
                Minecraft.getInstance().setScreen(new BloomMenuScreen(this))).bounds(x, y + gap * 3, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("Quit Game"), b ->
                Minecraft.getInstance().stop()).bounds(x, y + gap * 4, w, 26).build());
    }

    private void enterMenu() {
        if (entering || entered) return;
        entering = true;
        enterStartedAt = System.currentTimeMillis();
        clearWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float walkIn = entering ? Math.min(1f, (now - enterStartedAt) / (float) WALK_IN_MS) : 0f;
        if (entering && walkIn >= 1f) {
            entering = false;
            entered = true;
            init();
        }

        drawMotionBackdrop(g, now - openedAt);
        if (entered) drawMainPanel(g);
        else drawSplash(g, walkIn, now);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSplash(GuiGraphics g, float walkIn, long now) {
        int left = Math.max(36, Math.round(width * 0.14f));
        int titleY = height / 2 - 58;
        g.drawString(font, "MOTION  /  CLIENT", left, titleY - 30, 0xFF91A99A, false);
        g.drawString(font, "A cleaner way to play.", left, titleY + 60, 0xFFADB9B1, false);
        g.fill(left, titleY + 86, left + 50, titleY + 88, 0xFF55D995);

        if (entering) {
            float t = easeOutCubic(walkIn);
            float x = width + 120f + (width * 0.70f - width - 120f) * t;
            float scale = Math.max(1.55f, Math.min(2.05f, height / 390f));
            float stride = (1f - walkIn) * (float) Math.sin(now * 0.022f);
            float y = height * 0.30f;
            drawCharacter(g, x, y, scale, stride);
            g.drawString(font, "WELCOME IN", Math.round(x - 36), Math.round(y + 159 * scale), 0xFFB7C6BD, false);
        }
    }

    private void drawMainPanel(GuiGraphics g) {
        int panelRight = Math.max(330, Math.round(width * 0.47f));
        g.fillGradient(24, 24, panelRight, height - 24, 0xD90A100D, 0xE8070A08);
        g.fill(24, 24, panelRight, 25, 0xFF55D995);
        g.drawString(font, "MOTION", 44, 42, 0xFF55D995, false);
        g.drawString(font, "JAVA EDITION  /  CLIENT", 44, 60, 0xFF93A59A, false);
        g.drawString(font, "Your game. Your space.", 44, height / 2 - 76, 0xFFDCE7DF, false);

        float scale = Math.max(1.55f, Math.min(2.05f, height / 390f));
        float x = width * 0.73f;
        float y = height * 0.29f;
        drawCharacter(g, x, y, scale, 0f);
        g.drawString(font, "READY WHEN YOU ARE", Math.round(x - 62), Math.round(y + 159 * scale), 0xFF9CAFA3, false);
    }

    /** Fine, low-contrast waves and sparse ambient marks. */
    private void drawMotionBackdrop(GuiGraphics g, long time) {
        g.fillGradient(0, 0, width, height, 0xFF0A0E0C, 0xFF030504);
        int step = 4;
        for (int wave = 0; wave < 3; wave++) {
            float base = height * (0.64f + wave * 0.095f);
            float amplitude = 12f + wave * 5f;
            double phaseOffset = time * (0.00028 + wave * 0.00006) + wave * 1.7;
            int color = wave == 0 ? 0xFF111A15 : wave == 1 ? 0xFF0B100D : 0xFF060807;
            for (int x = 0; x < width; x += step) {
                int y = Math.round(base + (float) Math.sin(x * 0.012 + phaseOffset) * amplitude);
                g.fill(x, y, Math.min(width, x + step + 1), height, color);
            }
        }
        for (int i = 0; i < 9; i++) {
            int x = Math.floorMod(i * 173 + (int) (time * (0.008 + (i % 3) * 0.002)), Math.max(1, width));
            int y = Math.floorMod(i * 97 + (int) (time * (0.018 + (i % 3) * 0.004)), Math.max(1, height + 32)) - 16;
            g.drawString(font, "$", x, y, i % 3 == 0 ? 0x504FBD86 : 0x305B806A, false);
        }
        g.fill(0, 0, width, 2, 0xFF55D995);
    }

    /** Crisp Minecraft-shaped character with a restrained walk cycle during entry. */
    private void drawCharacter(GuiGraphics g, float centerX, float top, float scale, float stride) {
        float bob = Math.abs(stride) * 2.2f;
        int x = Math.round(centerX);
        int y = Math.round(top + bob);
        int shadow = Math.round(46 * scale);
        g.fill(x - shadow, y + Math.round(156 * scale), x + shadow, y + Math.round(159 * scale), 0x50323E36);

        drawLimb(g, x - Math.round(9 * scale), y + Math.round(98 * scale), scale, stride * 20f, 0xFF303A36, 0xFF1E2622);
        drawLimb(g, x + Math.round(9 * scale), y + Math.round(98 * scale), scale, -stride * 20f, 0xFF3B4540, 0xFF252D29);
        drawArm(g, x - Math.round(23 * scale), y + Math.round(46 * scale), scale, -stride * 16f);
        drawArm(g, x + Math.round(23 * scale), y + Math.round(46 * scale), scale, stride * 16f);

        int torsoW = Math.round(48 * scale);
        int torsoY = y + Math.round(43 * scale);
        g.fill(x - torsoW / 2, torsoY, x + torsoW / 2, y + Math.round(101 * scale), 0xFF23372D);
        g.fillGradient(x - torsoW / 2 + 2, torsoY + 2, x, y + Math.round(98 * scale), 0xFF31483A, 0xFF26392F);
        g.fill(x - Math.round(2 * scale), torsoY + 2, x + Math.round(2 * scale), y + Math.round(98 * scale), 0xFF55D995);

        int headW = Math.round(38 * scale);
        int headY = y + Math.round(2 * scale);
        int headH = Math.round(38 * scale);
        g.fill(x - headW / 2, headY, x + headW / 2, headY + headH, 0xFFD6B294);
        g.fillGradient(x - headW / 2, headY, x + headW / 2, headY + Math.round(12 * scale), 0xFF202522, 0xFF111613);
        g.fill(x - headW / 2, headY + Math.round(8 * scale), x - headW / 2 + Math.round(5 * scale), headY + Math.round(30 * scale), 0xFF171C19);
        g.fill(x + headW / 2 - Math.round(5 * scale), headY + Math.round(8 * scale), x + headW / 2, headY + Math.round(30 * scale), 0xFF171C19);
        g.fill(x - Math.round(9 * scale), headY + Math.round(21 * scale), x - Math.round(5 * scale), headY + Math.round(24 * scale), 0xFF27231F);
        g.fill(x + Math.round(5 * scale), headY + Math.round(21 * scale), x + Math.round(9 * scale), headY + Math.round(24 * scale), 0xFF27231F);
        g.fill(x - Math.round(5 * scale), headY + Math.round(31 * scale), x + Math.round(5 * scale), headY + Math.round(33 * scale), 0xFF9A6C53);
    }

    private void drawLimb(GuiGraphics g, int pivotX, int pivotY, float scale, float angle, int front, int side) {
        g.pose().pushMatrix();
        g.pose().translate((float) pivotX, (float) pivotY);
        g.pose().rotate((float) Math.toRadians(angle));
        int w = Math.round(20 * scale);
        int h = Math.round(54 * scale);
        g.fill(-w / 2, 0, w / 2, h, front);
        g.fill(-w / 2, 0, -w / 2 + Math.max(2, Math.round(4 * scale)), h - 3, side);
        g.fill(-w / 2 - 1, h - 4, w / 2 + 1, h, 0xFF151A17);
        g.pose().popMatrix();
    }

    private void drawArm(GuiGraphics g, int pivotX, int pivotY, float scale, float angle) {
        g.pose().pushMatrix();
        g.pose().translate((float) pivotX, (float) pivotY);
        g.pose().rotate((float) Math.toRadians(angle));
        int w = Math.round(16 * scale);
        int h = Math.round(50 * scale);
        g.fill(-w / 2, 0, w / 2, h, 0xFF293A31);
        g.fill(-w / 2, 0, -w / 2 + Math.max(2, Math.round(4 * scale)), h - 7, 0xFF3A4E42);
        g.fill(-w / 2, h - 8, w / 2, h, 0xFFD0AA8C);
        g.pose().popMatrix();
    }

    private static float easeOutCubic(float value) {
        float inverse = 1f - value;
        return 1f - inverse * inverse * inverse;
    }

    @Override
    public void onClose() {
        // Like Minecraft's title screen, Escape does not close the game.
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
