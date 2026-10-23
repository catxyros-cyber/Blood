package dev.bloom.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** MOTION title screen: restrained entry splash followed by a custom main menu. */
public final class MotionTitleScreen extends Screen {
    private static final long WALK_IN_MS = 1_250L;
    private static final int BACKGROUND_WIDTH = 3840;
    private static final int BACKGROUND_HEIGHT = 2160;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            "bloomclient", "textures/gui/motion_title_bg.png");
    private static final String[] MENU_ITEMS = {"Singleplayer", "Multiplayer", "Minecraft Settings", "MOTION Client", "Quit Game"};

    private boolean entered;
    private boolean entering;
    private long openedAt = System.currentTimeMillis();
    private long enterStartedAt;

    public MotionTitleScreen() { super(Component.literal("MOTION Client")); }

    @Override
    protected void init() {
        clearWidgets();
    }

    private void enterMenu() {
        if (entering || entered) return;
        entering = true;
        enterStartedAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float walkIn = entering ? Math.min(1f, (now - enterStartedAt) / (float) WALK_IN_MS) : 0f;
        if (entering && walkIn >= 1f) {
            entering = false;
            entered = true;
        }

        drawMotionBackdrop(g, now - openedAt);
        if (entered || entering) drawMainPanel(g, mouseX, mouseY, walkIn, now);
        else drawSplash(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Quiet center splash, matching the reference's single logo and invitation. */
    private void drawSplash(GuiGraphics g) {
        int cx = Math.round(width * 0.34f);
        int cy = Math.round(height * 0.47f);
        drawLogoMark(g, cx, cy - 38, 34, 0xFFF2F4F2);
        centerText(g, "MOTION Client", cx, cy + 18, 0xFFF1F2F1);
        centerText(g, "Press to enter", cx, cy + 48, 0xFFB8BCBF);
        g.fill(cx - 33, cy + 66, cx + 33, cy + 67, 0x6655D995);
    }

    private void drawMainPanel(GuiGraphics g, int mouseX, int mouseY, float walkIn, long now) {
        int menuX = Math.max(38, Math.round(width * 0.20f));
        int logoY = height / 2 - 140;
        int menuY = height / 2 - 36;
        float reveal = entering ? easeOutCubic(walkIn) : 1f;

        // A slim, translucent backing keeps the menu readable without covering the background.
        int panelX = menuX - 22;
        int panelW = Math.min(270, width / 3);
        int panelY = logoY - 14;
        int panelH = Math.min(height - panelY - 22, 330);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0x24101418, 0x52101317);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x9955D995);
        g.fill(panelX + 8, panelY + 1, panelX + panelW - 8, panelY + 2, 0x3355D995);

        drawLogoMark(g, menuX + 86, logoY + 27, 24, 0xFFEEF1EF);
        centerText(g, "MOTION Client", menuX + 86, logoY + 63, 0xFFF2F3F2);
        centerText(g, "JAVA EDITION", menuX + 86, logoY + 80, 0xFF9FA6A2);

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int rowY = menuY + i * 34;
            int alpha = Math.round(255 * reveal);
            int rowX = menuX - 6;
            int rowW = 184;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY - 3 && mouseY < rowY + 24;
            if (hovered) {
                g.fillGradient(rowX, rowY - 3, rowX + rowW, rowY + 24, (alpha / 3 << 24) | 0x26332C, (alpha / 5 << 24) | 0x121915);
                g.fill(rowX, rowY - 3, rowX + 2, rowY + 24, (alpha << 24) | 0x55D995);
            }
            int color = hovered ? 0xFFF2F4F2 : (alpha << 24) | (i == 0 ? 0xDCE3DF : 0xB8C0BB);
            g.drawString(font, MENU_ITEMS[i], menuX + 10, rowY + 4, color, false);
        }

        float t = easeOutCubic(reveal);
        float targetX = width * 0.79f;
        float x = entering ? width + 100f + (targetX - width - 100f) * t : targetX;
        float scale = Math.max(1.85f, Math.min(3.0f, height / 330f));
        float stride = entering ? (1f - walkIn) * (float) Math.sin(now * 0.016f) : (float) Math.sin(now * 0.0015f) * 0.025f;
        float y = height * 0.22f + (entering ? (1f - t) * 36f : 0f);
        drawCharacter(g, x, y, scale, stride);
        g.drawString(font, "READY WHEN YOU ARE", Math.round(x - 62), Math.round(y + 159 * scale), 0x99E2E7E4, false);
    }

    /** Full-resolution charcoal and satin-curtain artwork; small stars drift over it. */
    private void drawMotionBackdrop(GuiGraphics g, long time) {
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0f, 0f, width, height,
                BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, 0xFFFFFFFF);
        for (int i = 0; i < 7; i++) {
            float phase = time * (0.00012f + (i % 3) * 0.000025f);
            int x = Math.round(width * (0.79f + i * 0.027f + (float) Math.sin(phase + i) * 0.009f));
            int y = Math.floorMod(i * 91 + (int) (time * (0.012f + (i % 3) * 0.004f)), Math.max(1, height + 42)) - 21;
            int radius = 4 + i % 4;
            drawStar(g, x, y, radius, i % 2 == 0 ? 0xA8E4E9E6 : 0x78D0D7D2);
        }
    }

    private void drawLogoMark(GuiGraphics g, int cx, int cy, int r, int color) {
        float[][] pts = {{0,-1},{.24f,-.24f},{1f,0},{.24f,.24f},{0,1},{-.24f,.24f},{-1f,0},{-.24f,-.24f}};
        // Crossed angular loops form a sharp, clean mark at the same size as the wordmark.
        for (int i = 0; i < pts.length; i++) {
            int a = i, b = (i + 1) % pts.length;
            drawLine(g, cx + pts[a][0] * r, cy + pts[a][1] * r, cx + pts[b][0] * r, cy + pts[b][1] * r, color, 2);
        }
        drawLine(g, cx - r * .66f, cy - r * .66f, cx + r * .66f, cy + r * .66f, color, 2);
        drawLine(g, cx + r * .66f, cy - r * .66f, cx - r * .66f, cy + r * .66f, color, 2);
    }

    private void drawStar(GuiGraphics g, int cx, int cy, int r, int color) {
        float[][] pts = new float[8][2];
        for (int i = 0; i < 8; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 4;
            float rr = i % 2 == 0 ? r : r * .34f;
            pts[i][0] = cx + (float) Math.cos(a) * rr;
            pts[i][1] = cy + (float) Math.sin(a) * rr;
        }
        for (int i = 0; i < 8; i++) drawLine(g, pts[i][0], pts[i][1], pts[(i + 1) % 8][0], pts[(i + 1) % 8][1], color, 1);
    }

    private void drawLine(GuiGraphics g, float x1, float y1, float x2, float y2, int color, int thickness) {
        float dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        int half = Math.max(0, thickness - 1) / 2;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x1 + dx * t), y = Math.round(y1 + dy * t);
            g.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    private void centerText(GuiGraphics g, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /** A softly shaded, rounded avatar with a restrained walk cycle. */
    private void drawCharacter(GuiGraphics g, float centerX, float top, float scale, float stride) {
        float bob = Math.abs(stride) * 2.2f;
        int x = Math.round(centerX), y = Math.round(top + bob);
        int shadow = Math.round(45 * scale);
        g.fill(x - shadow, y + Math.round(157 * scale), x + shadow, y + Math.round(160 * scale), 0x50323E36);

        drawLimb(g, x - Math.round(10 * scale), y + Math.round(96 * scale), scale, stride * 18f, 0xFF182039, 0xFF101729);
        drawLimb(g, x + Math.round(10 * scale), y + Math.round(96 * scale), scale, -stride * 18f, 0xFF202A48, 0xFF101729);
        drawArm(g, x - Math.round(25 * scale), y + Math.round(46 * scale), scale, -stride * 14f);
        drawArm(g, x + Math.round(25 * scale), y + Math.round(46 * scale), scale, stride * 14f);

        int torsoW = Math.round(48 * scale), torsoH = Math.round(58 * scale);
        int torsoX = x - torsoW / 2, torsoY = y + Math.round(43 * scale);
        fillSoftRect(g, torsoX, torsoY, torsoW, torsoH, Math.round(10 * scale), 0xFF293558, 0xFF121A30);
        fillSoftRect(g, x - Math.max(1, Math.round(scale)), torsoY + Math.round(7 * scale), Math.max(2, Math.round(2 * scale)),
                torsoH - Math.round(14 * scale), Math.max(1, Math.round(scale)), 0x6655D995, 0x0055D995);

        int headW = Math.round(38 * scale), headH = Math.round(39 * scale);
        int headX = x - headW / 2, headY = y + Math.round(2 * scale);
        fillSoftRect(g, headX, headY, headW, headH, Math.round(11 * scale), 0xFF252832, 0xFF171A22);
        fillSoftRect(g, headX - Math.round(scale), headY, headW + Math.round(2 * scale), Math.round(14 * scale), Math.round(10 * scale), 0xFF252C28, 0xFF141916);
        fillSoftRect(g, headX + Math.round(2 * scale), headY + Math.round(8 * scale), Math.round(5 * scale), Math.round(20 * scale), Math.round(2 * scale), 0xFF12151B, 0xFF12151B);
        fillSoftRect(g, headX + headW - Math.round(7 * scale), headY + Math.round(8 * scale), Math.round(5 * scale), Math.round(20 * scale), Math.round(2 * scale), 0xFF12151B, 0xFF12151B);
        int maskW = Math.round(27 * scale), maskH = Math.round(11 * scale);
        fillSoftRect(g, x - maskW / 2, headY + Math.round(21 * scale), maskW, maskH, Math.round(3 * scale), 0xFFEDECE6, 0xFFC9CBC8);
        int eyeW = Math.max(2, Math.round(3 * scale)), eyeH = Math.max(1, Math.round(scale));
        fillSoftRect(g, x - Math.round(8 * scale), headY + Math.round(24 * scale), eyeW, eyeH, eyeH, 0xFF252832, 0xFF252832);
        fillSoftRect(g, x + Math.round(5 * scale), headY + Math.round(24 * scale), eyeW, eyeH, eyeH, 0xFF252832, 0xFF252832);
        fillSoftRect(g, x - Math.round(4 * scale), headY + Math.round(29 * scale), Math.round(8 * scale), Math.max(1, Math.round(scale)), Math.max(1, Math.round(scale)), 0xFF42454A, 0xFF42454A);
    }

    private void fillSoftRect(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            float inset = 0f;
            if (r > 0 && row < r) inset = r - (float) Math.sqrt(Math.max(0, r * r - (r - row) * (r - row)));
            else if (r > 0 && row >= h - r) {
                int fromBottom = h - 1 - row;
                inset = r - (float) Math.sqrt(Math.max(0, r * r - (r - fromBottom) * (r - fromBottom)));
            }
            int left = (int) Math.ceil(inset), right = (int) Math.floor(w - inset);
            if (right <= left) continue;
            float t = h <= 1 ? 0f : row / (float) (h - 1);
            g.fill(x + left, y + row, x + right, y + row + 1, mixColor(top, bottom, t));
        }
    }

    private int mixColor(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private void drawLimb(GuiGraphics g, int pivotX, int pivotY, float scale, float angle, int front, int side) {
        g.pose().pushMatrix();
        g.pose().translate((float) pivotX, (float) pivotY);
        g.pose().rotate((float) Math.toRadians(angle));
        int w = Math.round(21 * scale), h = Math.round(54 * scale);
        fillSoftRect(g, -w / 2, 0, w, h, Math.round(7 * scale), front, side);
        fillSoftRect(g, -w / 2 - 1, h - Math.round(8 * scale), w + 2, Math.round(9 * scale), Math.round(5 * scale), 0xFFE9E8E1, 0xFFBFC4C3);
        g.pose().popMatrix();
    }

    private void drawArm(GuiGraphics g, int pivotX, int pivotY, float scale, float angle) {
        g.pose().pushMatrix();
        g.pose().translate((float) pivotX, (float) pivotY);
        g.pose().rotate((float) Math.toRadians(angle));
        int w = Math.round(17 * scale), h = Math.round(50 * scale), cuff = Math.round(9 * scale);
        fillSoftRect(g, -w / 2, 0, w, h, Math.round(7 * scale), 0xFF242B4B, 0xFF151B32);
        fillSoftRect(g, -w / 2, h - cuff, w, cuff + 1, Math.round(5 * scale), 0xFFE9E8E1, 0xFFBFC4C3);
        g.pose().popMatrix();
    }

    private static float easeOutCubic(float value) {
        float inverse = 1f - value;
        return 1f - inverse * inverse * inverse;
    }

    private int hoveredMenuItem(double mouseX, double mouseY) {
        int menuX = Math.max(38, Math.round(width * 0.20f));
        int menuY = height / 2 - 36;
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int y = menuY + i * 34;
            if (mouseX >= menuX - 6 && mouseX < menuX + 178 && mouseY >= y - 3 && mouseY < y + 24) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_1) return super.mouseClicked(event, doubleClick);
        if (!entered) {
            enterMenu();
            return true;
        }
        int item = hoveredMenuItem(event.x(), event.y());
        if (item < 0) return false;
        switch (item) {
            case 0 -> Minecraft.getInstance().setScreen(new SelectWorldScreen(this));
            case 1 -> Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this));
            case 2 -> Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options));
            case 3 -> Minecraft.getInstance().setScreen(new BloomMenuScreen(this));
            case 4 -> Minecraft.getInstance().stop();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!entered && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE)) {
            enterMenu();
            return true;
        }
        if (entered) {
            int key = event.key();
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) {
                activateMenuItem(key - GLFW.GLFW_KEY_1);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void activateMenuItem(int item) {
        switch (item) {
            case 0 -> Minecraft.getInstance().setScreen(new SelectWorldScreen(this));
            case 1 -> Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this));
            case 2 -> Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options));
            case 3 -> Minecraft.getInstance().setScreen(new BloomMenuScreen(this));
            case 4 -> Minecraft.getInstance().stop();
        }
    }

    @Override
    public void onClose() {
        // Like Minecraft's title screen, Escape does not close the game.
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
