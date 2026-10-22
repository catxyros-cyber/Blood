package dev.bloom.client.hud;

import dev.bloom.client.core.hud.HudWidgetType;
import dev.bloom.client.core.hud.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One HUD widget. Implementations measure themselves ({@link #measure}) and then draw at the
 * origin of an already translated and scaled pose. When {@link Ctx#preview} is set (HUD
 * editor), they show sample content instead of nothing (e.g. potions when you have none).
 */
public abstract class HudWidget {
    public static final int PAD = 3;

    /** Everything a widget needs for one frame. */
    public static final class Ctx {
        public Minecraft mc;
        public Font font;
        public WidgetState state;
        public boolean preview;
        public boolean shadow;
        public int text;      // ARGB
        public int accent;    // ARGB
        public long nowMs;
        public float partialTick;
    }

    public final HudWidgetType type;

    protected HudWidget(HudWidgetType type) {
        this.type = type;
    }

    /** Content size in unscaled GUI pixels (without the background padding), or null to hide this frame. */
    public abstract int[] measure(Ctx c);

    /** Draw content with its top-left at (0, 0). */
    public abstract void render(GuiGraphics g, Ctx c, int w, int h);

    protected static int text(GuiGraphics g, Ctx c, String s, int x, int y, int color) {
        return dev.bloom.client.gui.Ui.string(g, c.font, s, x, y, color, c.shadow);
    }

    protected static int text(GuiGraphics g, Ctx c, String s, int x, int y) {
        return text(g, c, s, x, y, c.text);
    }

    protected static int dim(int argb, float k) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 255) * k), gg = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    protected static int withAlpha(int argb, float alpha) {
        return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (argb & 0xFFFFFF);
    }
}
