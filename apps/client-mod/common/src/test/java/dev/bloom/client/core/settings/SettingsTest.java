package dev.bloom.client.core.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SettingsTest {
    @Before
    public void reset() {
        dev.bloom.client.core.sky.SkyboxCatalog.setCustom(java.util.List.of());
        BloomSettings.resetAll();
    }

    @Test
    public void everyCategoryHasSettingsAndKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (Setting s : BloomSettings.all()) assertTrue("dup " + s.key(), keys.add(s.key()));
        for (Category c : Category.values()) {
            assertFalse("empty category " + c, BloomSettings.in(c).isEmpty());
        }
        assertTrue(BloomSettings.all().size() >= 90);
    }

    @Test
    public void everySettingHasLabelAndDescription() {
        for (Setting s : BloomSettings.all()) {
            assertFalse(s.key(), s.label().isBlank());
            assertTrue(s.key() + " needs a real description", s.description().length() > 10);
            assertTrue(s.key() + " default", s.isDefault());
        }
    }

    @Test
    public void jsonRoundTripForEveryType() {
        for (Setting s : BloomSettings.all()) {
            assertTrue(s.key(), s.accepts(s.toJson()));
            assertTrue(s.key(), s.fromJson(s.toJson()));
            assertTrue(s.key(), s.isDefault());
        }
    }

    @Test
    public void numbersClampSnapAndRejectGarbage() {
        NumberSetting n = BloomSettings.PETAL_DENSITY;
        n.set(5f);
        assertEquals(1f, n.value(), 0f);
        n.set(0.333f);
        assertEquals(0.35f, n.value(), 1e-6);
        n.set(Float.NaN);
        assertEquals(0.35f, n.value(), 1e-6);
        assertFalse(n.fromJson(new JsonPrimitive("lots")));
        assertEquals("35%", n.displayValue());
        assertEquals("24 blocks", BloomSettings.PETAL_RADIUS.displayValue());
    }

    @Test
    public void colorsParseShortAndLongHex() {
        assertEquals(Integer.valueOf(0xFFAABB), ColorSetting.parseHex("#FAB".replace("FAB", "FAB")) == null ? null : ColorSetting.parseHex("#FFAABB"));
        assertEquals(Integer.valueOf(0xFFAABB), ColorSetting.parseHex("#fab"));
        assertNull(ColorSetting.parseHex("#12345"));
        assertNull(ColorSetting.parseHex("red"));
        BloomSettings.PETAL_COLOR_A.set(0x123456);
        assertEquals("#123456", BloomSettings.PETAL_COLOR_A.toJson().getAsString());
        assertEquals(0x12 / 255f, BloomSettings.PETAL_COLOR_A.red(), 1e-6);
    }

    @Test
    public void choicesRejectUnknownIds() {
        assertFalse(BloomSettings.THEME.fromJson(new JsonPrimitive("neon")));
        assertTrue(BloomSettings.THEME.is("crimson"));
        BloomSettings.THEME.cycle(1);
        assertTrue(BloomSettings.THEME.is("blood_moon")); // the Halloween themes follow Classic BLOOD
        BloomSettings.THEME.cycle(-2);
        assertTrue(BloomSettings.THEME.is("mono"));
    }

    @Test
    public void multiChoiceIgnoresUnknownIds() {
        JsonArray a = new JsonArray();
        a.add("forest");
        a.add("moon_base");
        assertTrue(BloomSettings.PETAL_BIOMES.fromJson(a));
        assertEquals(Set.of("forest"), BloomSettings.PETAL_BIOMES.selected());
        BloomSettings.PETAL_BIOMES.toggle("plains");
        assertTrue(BloomSettings.PETAL_BIOMES.contains("plains"));
    }

    @Test
    public void skyboxDropdownIncludesVanillaAndBuiltIns() {
        assertEquals("none", BloomSettings.SKY_DAY.options().get(0).id());
        assertEquals(1 + 33 + 25, BloomSettings.SKY_DAY.options().size()); // vanilla + Fantasy pack + cloudy
        assertTrue(BloomSettings.SKY_DAY.is("fs_013_sunset"));
        assertTrue(BloomSettings.SKY_NIGHT.is("fs_013_night"));
        assertTrue(BloomSettings.SKY_DAY.isValid("fs_classic_night_3"));
    }

    @Test
    public void qualityPresetsDriveLimitsAndManualEditsSwitchToCustom() {
        BloomSettings.QUALITY.set("high");
        assertEquals(3500, BloomSettings.MAX_PARTICLES.intValue());
        assertEquals(56, BloomSettings.EFFECT_DISTANCE.intValue());
        BloomSettings.MAX_PARTICLES.set(2000);
        assertTrue(BloomSettings.QUALITY.is("custom"));
        assertEquals(2000, BloomSettings.MAX_PARTICLES.intValue());
        BloomSettings.QUALITY.set("low");
        assertEquals(500, BloomSettings.MAX_PARTICLES.intValue());
    }

    @Test
    public void searchMatchesLabelsKeywordsAndCategories() {
        assertTrue(BloomSettings.search("sakura").contains(BloomSettings.PETALS));
        assertTrue(BloomSettings.search("petal density").contains(BloomSettings.PETAL_DENSITY));
        assertTrue(BloomSettings.search("iris").contains(BloomSettings.SKY_WITH_SHADERS));
        assertTrue(BloomSettings.search("zzzz").isEmpty());
        assertTrue(BloomSettings.in(Category.SKYBOX).contains(BloomSettings.HAZE));
    }

    @Test
    public void dependentSettingsFollowTheirToggle() {
        assertTrue(BloomSettings.PETAL_DENSITY.isActive());
        BloomSettings.PETALS.set(false);
        assertFalse(BloomSettings.PETAL_DENSITY.isActive());
        BloomSettings.SKY_DAY.setAvailability(() -> "Shader pack active");
        assertFalse(BloomSettings.SKY_DAY.isActive());
        BloomSettings.SKY_DAY.setAvailability(null);
    }
}
