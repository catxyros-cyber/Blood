package dev.bloom.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bloom.client.Mc;
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

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** MOTION title screen: restrained entry splash followed by a custom main menu. */
public final class MotionTitleScreen extends Screen {
    private static final long WALK_IN_MS = 1_500L;
    private static final Identifier BRAND_MARK = Identifier.fromNamespaceAndPath(
            "bloomclient", "textures/gui/sprites/logo.png");
    private static final int BRAND_MARK_SIZE = 256;
    private static final String[] MENU_ITEMS = {"Singleplayer", "Multiplayer", "Minecraft Settings", "MOTION Client", "Quit Game"};

    private boolean entered;
    private boolean entering;
    private long openedAt = System.currentTimeMillis();
    private long enterStartedAt;
    private volatile Supplier<PlayerSkin> skinLookup;
    private PlayerModel avatarModel;
    private Boolean avatarModelSlim;
    private Identifier avatarTexture;
    private boolean skinLookupStarted;
    private final float[] hover = new float[MENU_ITEMS.length];
    private long lastFrameNanos = System.nanoTime();
    private float frameDt;

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
        long nanos = System.nanoTime();
        frameDt = Math.min(0.1f, (nanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = nanos;
        long now = System.currentTimeMillis();
        float walkIn = entering ? Math.min(1f, (now - enterStartedAt) / (float) WALK_IN_MS) : 0f;
        if (entering && walkIn >= 1f) {
            entering = false;
            entered = true;
        }

        drawMotionBackdrop(g, now - openedAt);
        if (entered || entering) drawMainPanel(g, mouseX, mouseY, walkIn, now);
        else drawSplash(g, now);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Quiet center splash, matching the reference's single logo and invitation. */
    private void drawSplash(GuiGraphics g, long now) {
        int cx = Math.round(width * 0.34f);
        int cy = Math.round(height * 0.47f);
        float breathe = 1f + (float) Math.sin(now * 0.0017f) * 0.025f;
        int markSize = Math.round(48 * breathe);
        drawBrandMark(g, cx, cy - 42, markSize, 0xFFFFFFFF);
        centerText(g, "MOTION Client", cx, cy + 18, 0xFFF1F2F1);
        int promptAlpha = 174 + Math.round(58f * (0.5f + 0.5f * (float) Math.sin(now * 0.0022f)));
        centerText(g, "Press to enter", cx, cy + 48, (promptAlpha << 24) | 0x00D7DDD9);
        g.fill(cx - 34, cy + 66, cx + 34, cy + 67, 0x7755D995);
    }

    private void drawMainPanel(GuiGraphics g, int mouseX, int mouseY, float walkIn, long now) {
        int menuX = Math.max(38, Math.round(width * 0.20f));
        int logoY = height / 2 - 140;
        int menuY = height / 2 - 36;
        float reveal = entering ? easeOutCubic(walkIn) : 1f;

        // The brand mark travels from the entry splash into its menu position as the menu opens.
        float logoProgress = entered ? 1f : reveal;
        int splashX = Math.round(width * 0.34f);
        int splashY = Math.round(height * 0.47f) - 42;
        int brandX = Math.round(splashX + (menuX + 86 - splashX) * logoProgress);
        int brandY = Math.round(splashY + (logoY + 26 - splashY) * logoProgress);
        int brandSize = Math.round(48 + (32 - 48) * logoProgress);
        drawBrandMark(g, brandX, brandY, brandSize, 0xFFFFFFFF);

        int titleX = Math.round(splashX + (menuX + 86 - splashX) * logoProgress);
        int titleY = Math.round(height * 0.47f + 18 + (logoY + 63 - (height * 0.47f + 18)) * logoProgress);
        int titleAlpha = 255;
        centerText(g, "MOTION Client", titleX, titleY, (titleAlpha << 24) | 0x00F2F3F2);
        if (!entered) {
            int promptAlpha = Math.round(255 * (1f - reveal));
            int promptX = Math.round(splashX + (menuX + 86 - splashX) * reveal);
            int promptY = Math.round(height * 0.47f + 48 + (logoY + 98 - (height * 0.47f + 48)) * reveal);
            centerText(g, "Press to enter", promptX, promptY, (promptAlpha << 24) | 0x00D7DDD9);
        }
        if (entered || walkIn > 0.2f) {
            int subtitleAlpha = entered ? 255 : Math.round(255 * Math.min(1f, (walkIn - 0.2f) / 0.5f));
            centerText(g, "JAVA EDITION", menuX + 86, logoY + 80, (subtitleAlpha << 24) | 0x009FA6A2);
        }

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int rowY = menuY + i * 34;
            float rowReveal = entered ? 1f : Math.max(0f, Math.min(1f, (walkIn - i * 0.075f) / 0.78f));
            int alpha = Math.round(255 * easeOutCubic(rowReveal));
            int rowX = menuX - 10;
            int rowW = 192;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY - 4 && mouseY < rowY + 22;
            hover[i] = Anim.approach(hover[i], hovered ? 1f : 0f, 18f, frameDt);
            float hv = hover[i];
            // A quiet glass button; hover brightens it and draws the accent edge.
            Ui.panel(g, rowX, rowY - 4, rowW, 26, Theme.withAlpha(0x0E1311, Math.round(alpha * (0.55f + 0.2f * hv))));
            if (hv > 0.01f) {
                Ui.panel(g, rowX, rowY - 4, rowW, 26, Theme.withAlpha(0xFFFFFF, Math.round(alpha * 0.10f * hv)));
                Ui.panelBorder(g, rowX, rowY - 4, rowW, 26, Theme.withAlpha(Theme.accentArgb() & 0xFFFFFF, Math.round(alpha * 0.85f * hv)));
            } else {
                Ui.panelBorder(g, rowX, rowY - 4, rowW, 26, Theme.withAlpha(0xFFFFFF, Math.round(alpha * 0.08f)));
            }
            int base = i == 0 ? 0xF2F4F3 : 0xD3D8D5;
            int color = (alpha << 24) | Theme.lerp(base, 0xFFFFFF, hv) & 0xFFFFFF;
            centerText(g, MENU_ITEMS[i], menuX + 86, rowY + 4, color);
        }

        float t = easeOutCubic(reveal);
        float targetX = width * 0.79f;
        float x = entering ? width + 100f + (targetX - width - 100f) * t : targetX;
        float stride = entering ? (1f - walkIn) * (float) Math.sin(now * 0.016f) : (float) Math.sin(now * 0.0015f) * 0.025f;
        float y = height * 0.22f + (entering ? (1f - t) * 36f : 0f);
        drawUserAvatar(g, x, y, walkIn, stride, now);
    }

    /**
     * The theme's landscape at the window's own resolution (see TitleArt), shaded on the left where
     * the menu sits and towards the bottom, so text stays readable on every theme.
     */
    private void drawMotionBackdrop(GuiGraphics g, long time) {
        if (!dev.bloom.client.game.TitleArt.draw(g, width, height)) g.fill(0, 0, width, height, 0xFF0B0F0D);
        Ui.gradientBand(g, 0, 0, Math.round(width * 0.62f), height, 0xC8070908, 0x00070908, 40);
        g.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x8C000000);
    }

    private void drawBrandMark(GuiGraphics g, int cx, int cy, int size, int color) {
        int x = cx - size / 2;
        int y = cy - size / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, BRAND_MARK, x, y, 0f, 0f, size, size, BRAND_MARK_SIZE, BRAND_MARK_SIZE, BRAND_MARK_SIZE, BRAND_MARK_SIZE, color);
    }

    private void centerText(GuiGraphics g, String text, int cx, int y, int color) {
        Ui.textCentered(g, text, cx, y, color);
    }

    /** Fetch the skin for Minecraft's active account off-thread; the default skin is shown meanwhile. */
    private void startSkinLookup() {
        if (skinLookupStarted) return;
        skinLookupStarted = true;
        Minecraft minecraft = Minecraft.getInstance();
        // The real skin, straight from Mojang (a bare profile has no textures, so the lookup below
        // alone only ever gave the default skin).
        dev.bloom.client.game.ProfileSkin.fetch(minecraft.getUser().getProfileId());
        GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
        CompletableFuture.supplyAsync(() -> minecraft.getSkinManager().createLookup(profile, false))
                .thenAccept(lookup -> minecraft.execute(() -> skinLookup = lookup))
                .exceptionally(error -> null);
    }

    private void drawUserAvatar(GuiGraphics g, float centerX, float top, float walkIn, float stride, long now) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slim;
        dev.bloom.client.game.ProfileSkin.Skin own = dev.bloom.client.game.ProfileSkin.get();
        if (own != null) {
            slim = own.slim();
            avatarTexture = own.texture();
        } else {
            // Not loaded yet (or the last try failed): ask again; ProfileSkin paces its retries.
            dev.bloom.client.game.ProfileSkin.fetch(minecraft.getUser().getProfileId());
            Supplier<PlayerSkin> lookup = skinLookup;
            PlayerSkin skin = lookup == null ? null : lookup.get();
            if (skin == null || skin.body() == null) skin = DefaultPlayerSkin.get(minecraft.getUser().getProfileId());
            if (skin == null || skin.body() == null) return;
            slim = skin.model() == PlayerModelType.SLIM;
            avatarTexture = skin.body().texturePath();
        }
        if (avatarModel == null || avatarModelSlim != slim) {
            avatarModel = new PlayerModel(minecraft.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            avatarModelSlim = slim;
        }

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
//#if MC < 26.3
        state.attackArm = HumanoidArm.RIGHT;
//#endif
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
//#if MC >= 26.2
        // 26.2 draws GUI skins from a plain model; it shares the posed player model's parts.
        g.submitSkinRenderState(new net.minecraft.client.model.Model.Simple(avatarModel.root(), net.minecraft.client.renderer.rendertype.RenderTypes::entityTranslucent),
                avatarTexture, modelScale, 4f, -18f, 0.9f, x0, y0, x1, y1);
//#else
        g.submitSkinRenderState(avatarModel, avatarTexture, modelScale, 4f, -18f, 0.9f, x0, y0, x1, y1);
//#endif

        String name = minecraft.getUser().getName();
        int nameY = Math.max(12, y0 - 14);
        int nameX = Math.round(centerX - Ui.width(name) / 2f);
        Ui.text(g, name, nameX, nameY, 0xFFE8EAE9);
        g.fill(nameX, nameY + 10, nameX + Ui.width(name), nameY + 11, 0xAAE8EAE9);
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
            if (mouseX >= menuX - 10 && mouseX < menuX + 182 && mouseY >= y - 3 && mouseY < y + 24) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        if (!entered) {
            enterMenu();
            return true;
        }
        int item = hoveredMenuItem(event.x(), event.y());
        if (item < 0) return false;
        switch (item) {
            case 0 -> Mc.setScreen(new SelectWorldScreen(this));
            case 1 -> Mc.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> Mc.setScreen(new OptionsScreen(this, Minecraft.getInstance().options));
            case 3 -> Mc.setScreen(new BloomMenuScreen(this));
            case 4 -> Minecraft.getInstance().stop();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!entered && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE)) {
            enterMenu();
            return true;
        }
        if (entered) {
            int key = event.key();
            if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_5) {
                activateMenuItem(key - InputConstants.KEY_1);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void activateMenuItem(int item) {
        switch (item) {
            case 0 -> Mc.setScreen(new SelectWorldScreen(this));
            case 1 -> Mc.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> Mc.setScreen(new OptionsScreen(this, Minecraft.getInstance().options));
            case 3 -> Mc.setScreen(new BloomMenuScreen(this));
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
