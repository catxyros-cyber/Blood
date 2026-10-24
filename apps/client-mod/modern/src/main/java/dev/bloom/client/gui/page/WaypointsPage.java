package dev.bloom.client.gui.page;

import dev.bloom.client.BloomKeys;
import dev.bloom.client.core.settings.Category;
import dev.bloom.client.core.waypoint.WaypointStore;
import dev.bloom.client.game.Waypoints;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.Theme;
import dev.bloom.client.gui.widget.Button;
import dev.bloom.client.gui.widget.Element;
import dev.bloom.client.gui.widget.Rows;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Waypoints in the world you're in, plus the waypoint settings. */
public class WaypointsPage extends GenericPage {
    public WaypointsPage(BloomMenuScreen screen) {
        super(screen, Category.WAYPOINTS);
    }

    @Override
    public List<Element> build() {
        Minecraft mc = Minecraft.getInstance();
        List<Element> rows = new ArrayList<>();
        rows.add(new Rows.Info("waypoints", "Press the Add waypoint key (" + BloomKeys.ADD_WAYPOINT.getTranslatedKeyMessage().getString()
                + ") to mark where you stand. Waypoints are saved per server and per world.", Theme.accentArgb()));
        rows.add(new KeybindsPage.KeyRow(screen, BloomKeys.ADD_WAYPOINT));
        String key = Waypoints.worldKey(mc);
        List<Button> top = new ArrayList<>();
        if (key != null) {
            top.add(new Button("Add waypoint here", "plus", Button.Style.PRIMARY, () -> {
                WaypointStore.Waypoint w = Waypoints.addHere(mc);
                if (w != null) screen.message("Waypoint added", w.name() + " at " + w.x() + ", " + w.y() + ", " + w.z());
                screen.requestRebuild();
            }));
        }
        top.add(resetPageButton(screen, category));
        rows.add(new Rows.Buttons(top));

        if (key == null) {
            rows.add(new Rows.Info("info", "Join a world or server to see and add its waypoints.", Theme.palette().textDim()));
        } else {
            List<WaypointStore.Waypoint> all = Waypoints.store().list(key);
            rows.add(new Rows.Header(all.isEmpty() ? "No waypoints in this world yet" : "Waypoints in this world (" + all.size() + ")"));
            for (WaypointStore.Waypoint w : all) {
                String dim = w.dimension().substring(w.dimension().indexOf(':') + 1).replace('_', ' ');
                String where = w.x() + ", " + w.y() + ", " + w.z() + " · " + dim;
                if (mc.player != null && w.dimension().equals(Waypoints.dimension(mc))) {
                    double d = w.distanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    where += String.format(Locale.ROOT, " · %d blocks away", Math.round(d));
                }
                rows.add(new Rows.Info(w.death() ? "warning" : "waypoints", w.name() + "  —  " + where, 0xFF000000 | w.color()));
                rows.add(new Rows.Buttons(List.of(new Button("Delete " + w.name(), "trash", Button.Style.DANGER, () -> {
                    Waypoints.remove(mc, w);
                    screen.requestRebuild();
                }))));
            }
        }
        rows.addAll(Pages.settings(category, screen));
        return rows;
    }
}
