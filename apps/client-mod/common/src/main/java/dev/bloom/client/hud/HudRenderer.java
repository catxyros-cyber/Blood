package dev.bloom.client.hud;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.hud.HudLayout;
import dev.bloom.client.core.hud.HudSnap;
import dev.bloom.client.core.hud.HudWidgetType;
import dev.bloom.client.core.hud.WidgetState;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.gui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws the active HUD layout. Every widget is drawn inside its own try/catch: if one keeps
 * failing (for example because another mod changed something it reads), only that widget is
 * switched off for the session and the player is told; the rest of the HUD keeps working.
 */
public final class HudRenderer {
    private static final int MAX_FAILURES = 3;

    private final BloomClient bloom;
    private final Map<HudWidgetType, HudWidget> widgets = new EnumMap<>(HudWidgetType.class);
    private final Map<HudWidgetType, Integer> failures = new EnumMap<>(HudWidgetType.class);
    private final HudWidget.Ctx ctx = new HudWidget.Ctx();

    public HudRenderer(BloomClient bloom) {
        this.bloom = bloom;
        for (HudWidgetType t : HudWidgetType.values()) widgets.put(t, Widgets.create(t));
    }

    public boolean broken(HudWidgetType t) {
        return failures.getOrDefault(t, 0) >= MAX_FAILURES;
    }

    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!BloomSettings.ENABLED.on() || !BloomSettings.HUD_ENABLED.on() || mc.options.hideGui) return;
        if (BloomSettings.HUD_HIDE_IN_F3.on() && mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen instanceof dev.bloom.client.gui.HudEditorScreen) return; // the editor draws its own copy
        HudLayout layout = bloom.hudLayouts().active();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        for (WidgetState s : layout.widgets()) {
            if (!s.visible || broken(s.type)) continue;
            drawWidget(g, s, false, partial, g.guiWidth(), g.guiHeight());
        }
    }

    /** Prepared context for one widget (also used by the HUD editor). */
    public HudWidget.Ctx context(WidgetState s, boolean preview, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ctx.mc = mc;
        ctx.font = mc.font;
        ctx.state = s;
        ctx.preview = preview;
        ctx.shadow = BloomSettings.HUD_SHADOW.on();
        ctx.text = 0xFF000000 | (s.textColor >= 0 ? s.textColor : BloomSettings.HUD_TEXT.rgb());
        ctx.accent = 0xFF000000 | Theme.accent();
        ctx.nowMs = System.currentTimeMillis();
        ctx.partialTick = partial;
        return ctx;
    }

    /** Screen rectangle of a widget (after scaling) or null when it has nothing to show. */
    public HudSnap.Rect bounds(WidgetState s, boolean preview, int screenW, int screenH) {
        try {
            HudWidget w = widgets.get(s.type);
            int[] size = w.measure(context(s, preview, 1f));
            if (size == null) return null;
            float bw = (size[0] + 2 * HudWidget.PAD) * s.scale, bh = (size[1] + 2 * HudWidget.PAD) * s.scale;
            float x = s.anchor.left(s.x, bw, screenW), y = s.anchor.top(s.y, bh, screenH);
            return new HudSnap.Rect(x, y, bw, bh);
        } catch (RuntimeException e) {
            fail(s.type, e);
            return null;
        }
    }

    /** Draws one widget at its layout position. Returns its bounds, or null if hidden/failed. */
    public HudSnap.Rect drawWidget(GuiGraphics g, WidgetState s, boolean preview, float partial, int screenW, int screenH) {
        HudWidget w = widgets.get(s.type);
        g.pose().pushPose();
        try {
            HudWidget.Ctx c = context(s, preview, partial);
            int[] size = w.measure(c);
            if (size == null) return null;
            int bw = size[0] + 2 * HudWidget.PAD, bh = size[1] + 2 * HudWidget.PAD;
            float sw = bw * s.scale, sh = bh * s.scale;
            float x = s.anchor.left(s.x, sw, screenW), y = s.anchor.top(s.y, sh, screenH);
            // Keep widgets on screen even after the window shrinks.
            x = Math.max(0, Math.min(screenW - sw, x));
            y = Math.max(0, Math.min(screenH - sh, y));
            g.pose().translate(x, y, 0f);
            g.pose().scale(s.scale, s.scale, 1f);
            if (BloomSettings.HUD_BACKGROUND.on()) {
                int bg = s.backgroundColor >= 0 ? s.backgroundColor : BloomSettings.HUD_BG_COLOR.rgb();
                float op = s.backgroundOpacity >= 0 ? s.backgroundOpacity : BloomSettings.HUD_BG_OPACITY.value();
                if (op > 0.01f) {
                    int argb = ((int) (op * 255) << 24) | bg;
                    g.fill(1, 0, bw - 1, bh, argb);
                    g.fill(0, 1, 1, bh - 1, argb);
                    g.fill(bw - 1, 1, bw, bh - 1, argb);
                }
            }
            g.pose().translate(HudWidget.PAD, HudWidget.PAD, 0f);
            w.render(g, c, size[0], size[1]);
            return new HudSnap.Rect(x, y, sw, sh);
        } catch (RuntimeException e) {
            fail(s.type, e);
            return null;
        } finally {
            g.pose().popPose();
        }
    }

    private void fail(HudWidgetType t, RuntimeException e) {
        int n = failures.merge(t, 1, Integer::sum);
        if (n == 1) Log.warn("HUD widget '" + t.label() + "' failed", e);
        if (n == MAX_FAILURES) {
            Log.warn("HUD widget '" + t.label() + "' turned off for this session after repeated errors");
            bloom.toast("HUD widget turned off", t.label() + " kept failing and was switched off until restart. Other widgets are fine.");
        }
    }
}
