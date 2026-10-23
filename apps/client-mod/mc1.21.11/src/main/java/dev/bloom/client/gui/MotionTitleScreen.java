package dev.bloom.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
    private volatile Supplier<PlayerSkin> skinLookup;
    private PlayerModel avatarModel;
    private PlayerModelType avatarModelType;
    private Identifier avatarTexture;
    private boolean skinLookupStarted;

    public MotionTitleScreen() { super(Component.literal("MOTION Client")); }

    @Override
    protected void init() {
        clearWidgets();
        startSkinLookup();
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
        float stride = entering ? (1f - walkIn) * (float) Math.sin(now * 0.016f) : (float) Math.sin(now * 0.0015f) * 0.025f;
        float y = height * 0.22f + (entering ? (1f - t) * 36f : 0f);
        drawUserAvatar(g, x, y, walkIn, stride, now);
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

    /** Fetch the skin for Minecraft's active account off-thread; the default skin is shown meanwhile. */
    private void startSkinLookup() {
        if (skinLookupStarted) return;
        skinLookupStarted = true;
        Minecraft minecraft = Minecraft.getInstance();
        GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
        CompletableFuture.supplyAsync(() -> minecraft.getSkinManager().createLookup(profile, false))
                .thenAccept(lookup -> minecraft.execute(() -> skinLookup = lookup))
                .exceptionally(error -> null);
    }

    private void drawUserAvatar(GuiGraphics g, float centerX, float top, float walkIn, float stride, long now) {
        Minecraft minecraft = Minecraft.getInstance();
        Supplier<PlayerSkin> lookup = skinLookup;
        PlayerSkin skin = lookup == null ? null : lookup.get();
        if (skin == null || skin.body() == null) skin = DefaultPlayerSkin.get(minecraft.getUser().getProfileId());
        if (skin == null || skin.body() == null) return;

        PlayerModelType type = skin.model();
        if (avatarModel == null || avatarModelType != type) {
            boolean slim = type == PlayerModelType.SLIM;
            avatarModel = new PlayerModel(minecraft.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            avatarModelType = type;
        }
        avatarTexture = skin.body().texturePath();

        AvatarRenderState state = new AvatarRenderState();
        state.isSpectator = false;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showCape = false;
        state.pose = Pose.STANDING;
        state.attackArm = HumanoidArm.RIGHT;
        state.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        state.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        state.bodyRot = -8f;
        state.yRot = -6f;
        state.xRot = -3f;
        state.walkAnimationPos = now * 0.018f;
        state.walkAnimationSpeed = entering ? Math.max(0f, 0.72f * (1f - walkIn)) : Math.abs(stride) * 0.03f;
        state.ageInTicks = now / 50f;
        avatarModel.setupAnim(state);

        int viewHeight = Math.round(Math.min(height * 0.76f, 650f));
        int viewWidth = Math.round(viewHeight * 0.78f);
        int x0 = Math.round(centerX - viewWidth / 2f);
        int y0 = Math.round(Math.max(10f, top - height * 0.07f));
        int x1 = x0 + viewWidth;
        int y1 = Math.min(height - 12, y0 + viewHeight);
        float modelScale = (y1 - y0) / 1.9f;
        g.submitSkinRenderState(avatarModel, avatarTexture, 4f, -18f, 0.9f, x0, y0, x1, y1, modelScale);

        String name = minecraft.getUser().getName();
        int nameY = Math.max(12, y0 - 14);
        int nameX = Math.round(centerX - font.width(name) / 2f);
        g.drawString(font, name, nameX, nameY, 0xFFE8EAE9, false);
        g.fill(nameX, nameY + 10, nameX + font.width(name), nameY + 11, 0xAAE8EAE9);
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
