package dev.bloom.client.core.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HudTest {
    @Test
    public void anchorsRoundTripOffsets() {
        for (Anchor a : Anchor.values()) {
            float left = a.left(10, 50, 400);
            assertEquals(10, a.offsetX(left, 50, 400), 1e-4);
        }
        assertEquals(Anchor.BOTTOM_RIGHT, Anchor.nearest(390, 290, 400, 300));
        assertEquals(Anchor.CENTER, Anchor.nearest(200, 150, 400, 300));
        // Right-anchored widget keeps its distance from the right edge on resize.
        float off = Anchor.TOP_RIGHT.offsetX(340, 50, 400); // 10px gap
        assertEquals(1000 - 60, Anchor.TOP_RIGHT.left(off, 50, 1000), 1e-4);
    }

    @Test
    public void snappingToEdgesCentresAndOtherWidgets() {
        HudSnap.Result r = HudSnap.snap(new HudSnap.Rect(3.5f, 50, 40, 10), List.of(), 400, 300, 0);
        assertEquals(HudSnap.EDGE_MARGIN, r.x(), 1e-4);
        assertEquals(1, r.guides().size());
        r = HudSnap.snap(new HudSnap.Rect(178, 100, 40, 10), List.of(), 400, 300, 0); // centre at 198 → 200
        assertEquals(180, r.x(), 1e-4);
        r = HudSnap.snap(new HudSnap.Rect(100, 62, 40, 10), List.of(new HudSnap.Rect(100, 40, 40, 20)), 400, 300, 0);
        assertEquals(60, r.y(), 1e-4);
        r = HudSnap.snap(new HudSnap.Rect(113, 117, 40, 10), List.of(), 400, 300, 10);
        assertEquals(110, r.x(), 1e-4);
        assertEquals(120, r.y(), 1e-4);
        r = HudSnap.snap(new HudSnap.Rect(390, -20, 40, 10), List.of(), 400, 300, 0);
        assertEquals(360, r.x(), 1e-4);
        assertEquals(0, r.y(), 1e-4);
    }

    @Test
    public void layoutsSaveLoadAndSurviveBadData() {
        HudLayouts a = new HudLayouts(null);
        HudLayout l = a.create("Speedrun", a.active());
        WidgetState fps = l.get(HudWidgetType.FPS);
        fps.anchor = Anchor.BOTTOM_LEFT;
        fps.setOffset(12, -30);
        fps.setScale(7f);
        fps.textColor = 0xFF00FF;
        fps.setOption("colorCoded", true);
        a.setActive(l.id());
        JsonObject saved = a.save().getAsJsonObject();

        HudLayouts b = new HudLayouts(null);
        b.load(saved, false);
        assertEquals("speedrun", b.active().id());
        WidgetState f2 = b.active().get(HudWidgetType.FPS);
        assertEquals(Anchor.BOTTOM_LEFT, f2.anchor);
        assertEquals(-30, f2.y, 0);
        assertEquals(WidgetState.MAX_SCALE, f2.scale, 0);
        assertEquals(0xFF00FF, f2.textColor);
        assertTrue(f2.option("colorCoded"));
        assertTrue(f2.option("label"));

        HudLayouts c = new HudLayouts(null);
        c.load(JsonParser.parseString("{\"layouts\":[{\"id\":\"../x\"},{\"id\":\"ok\",\"widgets\":{\"fps\":{\"x\":\"NaN\",\"anchor\":\"nowhere\",\"scale\":-3},\"bogus\":{}}}]}"), false);
        assertEquals(1, c.all().size());
        assertEquals("ok", c.active().id());
        assertEquals(HudWidgetType.FPS.defaultAnchor(), c.active().get(HudWidgetType.FPS).anchor);
        assertEquals(WidgetState.MIN_SCALE, c.active().get(HudWidgetType.FPS).scale, 0);
    }

    @Test
    public void lastLayoutCannotBeDeletedAndResetRestoresPreset() {
        HudLayouts a = new HudLayouts(null);
        assertTrue(a.delete("default"));
        assertFalse(a.delete("pvp"));
        a.get("pvp").get(HudWidgetType.KEYSTROKES).visible = false;
        a.reset("pvp");
        assertTrue(a.get("pvp").get(HudWidgetType.KEYSTROKES).visible);
    }
}
