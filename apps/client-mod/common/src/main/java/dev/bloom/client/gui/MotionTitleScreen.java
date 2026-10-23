package dev.bloom.client.gui;

import dev.bloom.client.BloomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** MOTION's two-step title screen: a branded entry splash followed by familiar game actions. */
public final class MotionTitleScreen extends Screen {
    private boolean entered;
    private long openedAt = System.currentTimeMillis();

    public MotionTitleScreen() { super(Component.literal("MOTION Client")); }

    @Override
    protected void init() {
        clearWidgets();
        if (!entered) {
            Button enter = Button.builder(Component.literal("Press to enter"), button -> enterMenu())
                    .bounds(width / 2 - 94, height / 2 + 48, 188, 24).build();
            addRenderableWidget(enter);
            setInitialFocus(enter);
            return;
        }

        int x = Math.max(24, Math.round(width * 0.20f));
        int y = height / 2 - 45;
        int w = 204;
        int gap = 30;
        addRenderableWidget(Button.builder(Component.literal("Singleplayer"), b ->
                Minecraft.getInstance().setScreen(new SelectWorldScreen(this))).bounds(x, y, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Multiplayer"), b ->
                Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this))).bounds(x, y + gap, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Minecraft Settings"), b ->
                Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options))).bounds(x, y + gap * 2, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("MOTION Client"), b ->
                Minecraft.getInstance().setScreen(new BloomMenuScreen(this))).bounds(x, y + gap * 3, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Quit Game"), b ->
                Minecraft.getInstance().stop()).bounds(x, y + gap * 4, w, 24).build());
    }

    private void enterMenu() {
        entered = true;
        init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawMotionBackdrop(g);
        if (entered) drawMainPanel(g); else drawSplash(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSplash(GuiGraphics g) {
        int cx = width / 2;
        int titleY = height / 2 - 36;
        g.drawCenteredString(font, "$", cx, titleY - 62, 0xFF42F58A);
        g.drawCenteredString(font, "MOTION CLIENT", cx, titleY, 0xFFF4FFF7);
        g.fill(cx - 54, titleY + 18, cx + 54, titleY + 19, 0xAA42F58A);
        g.drawCenteredString(font, "A better way to play.", cx, titleY + 30, 0xFF9CA9A1);
    }

    private void drawMainPanel(GuiGraphics g) {
        int panelRight = Math.max(300, Math.round(width * 0.43f));
        g.fillGradient(18, 18, panelRight, height - 18, 0xB50A0D0B, 0xE8060908);
        g.drawString(font, "MOTION", 34, 30, 0xFF42F58A, false);
        g.drawString(font, "CLIENT  /  JAVA EDITION", 34, 46, 0xFF9CA9A1, false);
        int avatarX = Math.round(width * 0.72f);
        int avatarY = Math.round(height * 0.30f);
        drawBlockAvatar(g, avatarX, avatarY, Math.max(1, Math.min(2, height / 260)));
        g.drawString(font, "READY WHEN YOU ARE", avatarX - 72, avatarY + 148, 0xFF9CA9A1, false);
    }

    /** Quiet falling dollar marks over a black, animated wave background. */
    private void drawMotionBackdrop(GuiGraphics g) {
        long time = System.currentTimeMillis() - openedAt;
        g.fillGradient(0, 0, width, height, 0xFF080B09, 0xFF010302);
        for (int wave = 0; wave < 3; wave++) {
            int base = (int) (height * (0.63 + wave * 0.105));
            int step = 10;
            for (int x = 0; x < width; x += step) {
                double phase = x * 0.018 + time * (0.00035 + wave * 0.00008) + wave * 1.8;
                int y = base + (int) (Math.sin(phase) * (14 + wave * 4));
                int color = wave == 0 ? 0xFF101713 : wave == 1 ? 0xFF080C09 : 0xFF030504;
                g.fill(x, y, Math.min(width, x + step + 1), height, color);
            }
        }
        for (int i = 0; i < 15; i++) {
            int x = Math.floorMod(i * 137 + (int) (time * (0.012 + (i % 4) * 0.002)), Math.max(1, width));
            int y = Math.floorMod(i * 79 + (int) (time * (0.028 + (i % 3) * 0.006)), Math.max(1, height + 24)) - 12;
            g.drawString(font, "$", x, y, i % 3 == 0 ? 0x6642F58A : 0x446E8B78, false);
        }
        g.fill(0, 0, width, 2, 0xFF42F58A);
    }

    /** A small blocky figure to echo Minecraft's player preview without loading a 3D scene. */
    private void drawBlockAvatar(GuiGraphics g, int x, int y, int scale) {
        int s = scale;
        // Head, shaded hair and face.
        g.fill(x - 20*s, y, x + 20*s, y + 40*s, 0xFFD6AA84);
        g.fill(x - 20*s, y, x + 20*s, y + 11*s, 0xFF111714);
        g.fill(x - 22*s, y + 8*s, x - 14*s, y + 32*s, 0xFF111714);
        g.fill(x + 14*s, y + 8*s, x + 22*s, y + 32*s, 0xFF111714);
        g.fill(x - 11*s, y + 20*s, x - 6*s, y + 24*s, 0xFF121212);
        g.fill(x + 6*s, y + 20*s, x + 11*s, y + 24*s, 0xFF121212);
        g.fill(x - 5*s, y + 31*s, x + 5*s, y + 33*s, 0xFF8A5742);
        // Jacket, sleeves and trousers.
        g.fill(x - 25*s, y + 42*s, x + 25*s, y + 98*s, 0xFF17241D);
        g.fill(x - 42*s, y + 44*s, x - 26*s, y + 94*s, 0xFF26352D);
        g.fill(x + 26*s, y + 44*s, x + 42*s, y + 94*s, 0xFF26352D);
        g.fill(x - 24*s, y + 98*s, x - 2*s, y + 148*s, 0xFF15191A);
        g.fill(x + 2*s, y + 98*s, x + 24*s, y + 148*s, 0xFF15191A);
        g.fill(x - 24*s, y + 60*s, x + 24*s, y + 63*s, 0xFF42F58A);
    }

    @Override
    public void onClose() {
        // Like Minecraft's title screen, Escape does not close the game.
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
