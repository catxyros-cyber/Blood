package dev.bloom.client.gui.page;

import dev.bloom.client.BloomClient;
import dev.bloom.client.core.particles.ParticleSystem;
import dev.bloom.client.core.perf.FrameStats;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.Ui;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PerformancePage extends GenericPage {
    public PerformancePage(BloomMenuScreen screen) {
        super(screen, Category.PERFORMANCE);
    }

    @Override
    public List<Element> build() {
        List<Element> rows = new ArrayList<>();
        rows.add(new Stats());
        rows.add(new Rows.Info("info", "These numbers are measured live on your PC. While the menu is open the game is still running, so FPS may be a little lower than in play.", Theme.palette().textDim()));
        rows.addAll(super.build());
        return rows;
    }

    /** Live measurements (no estimates). */
    static final class Stats extends Element {
        private final float[] graph = new float[120];

        @Override
        public int preferredHeight(int width) {
            return 74;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            Theme.Palette p = Theme.palette();
            BloomClient bc = BloomClient.get();
            FrameStats f = bc.frames();
            long now = System.nanoTime();
            Ui.panel(g, x, y, w, h - 2, p.surface());
            int col = (w - 16) / 4;
            stat(g, x + 8, y + 6, "FPS", String.valueOf(f.fps(now)), p);
            stat(g, x + 8 + col, y + 6, "Frame time", String.format(Locale.ROOT, "%.1f ms", f.averageMs(now, 1_000_000_000L)), p);
            double low = f.onePercentLowFps(now, 10_000_000_000L);
            stat(g, x + 8 + 2 * col, y + 6, "1% low (10 s)", low > 0 ? String.format(Locale.ROOT, "%.0f FPS", low) : "measuring…", p);
            ParticleSystem ps = bc.particles();
            stat(g, x + 8 + 3 * col, y + 6, "MOTION particles", ps.count() + " / " + BloomSettings.MAX_PARTICLES.intValue(), p);
            String budget = BloomSettings.ADAPTIVE.on()
                    ? (Math.round(bc.budget().factor() * 100) + "% of limit" + (bc.budget().factor() < 0.99f ? " (FPS below target)" : ""))
                    : "adaptive off";
            Ui.text(g, "Adaptive: " + budget + (bc.shaderActive() ? " · shader pack active" : ""), x + 8, y + 30, p.textFaint());
            int n = f.recentMs(graph);
            int gx = x + 8, gy = y + 42, gw = w - 16, gh = 24;
            g.fill(gx, gy + gh, gx + gw, gy + gh + 1, Theme.alpha(p.textDim(), 0.3f));
            float bw = gw / (float) graph.length;
            for (int i = 0; i < n; i++) {
                float ms = graph[i];
                int bh = Math.max(1, Math.round(gh * Math.min(1f, ms / 50f)));
                int c = ms > 33.4f ? p.danger() : ms > 16.8f ? p.warn() : Theme.accentArgb();
                g.fill(gx + Math.round(i * bw), gy + gh - bh, gx + Math.max(Math.round(i * bw) + 1, Math.round((i + 1) * bw)), gy + gh, Theme.alpha(c, 0.85f));
            }
        }

        private static void stat(GuiGraphics g, int x, int y, String label, String value, Theme.Palette p) {
            Ui.text(g, label, x, y, p.textFaint());
            Ui.text(g, value, x, y + 11, p.text());
        }
    }
}
