package dev.bloom.client.core.sky;

import dev.bloom.client.core.settings.ChoiceSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Built-in BLOOD skyboxes (BLOOD's own, plus 25 CC0 "Cloudy Skyboxes" by Screaming Brain Studios)
 * plus any the player imported. Built-in textures live in
 * assets/bloomclient/textures/skybox/&lt;id&gt;.png (3x2 atlas, see docs/CUSTOM_SKYBOXES.md).
 */
public final class SkyboxCatalog {
    public static final String NONE = "none";
    public static final String CUSTOM_PREFIX = "custom:";

    public record Entry(String id, String name, String description, boolean builtIn, int previewTint) {
    }

    public static final List<Entry> BUILT_IN = List.of(
            new Entry("crimson_dusk", "Crimson Dusk", "A low red sun under heavy crimson cloud. BLOOD's default day sky.", true, 0xA8243A),
            new Entry("blood_moon", "Blood Moon", "A huge red moon over a near-black sky with faint stars. BLOOD's default night sky.", true, 0x3A0712),
            new Entry("cherry_blossom", "Cherry Blossom Sky", "Warm pink dusk over a cherry grove, with soft drifting clouds.", true, 0xF7A8C4),
            new Entry("pink_sunset", "Pink Sunset", "A glowing low sun melting into rose and violet.", true, 0xF59A8C),
            new Entry("sakura_night", "Sakura Night", "Deep indigo night with a faint pink horizon and a bright starfield.", true, 0x3A2A5C),
            new Entry("starry_night", "Starry Night", "Clear dark sky with dense, crisp stars.", true, 0x141A33),
            new Entry("galaxy", "Galaxy", "A colourful band of the galaxy arching overhead.", true, 0x2B1846),
            new Entry("fantasy_clouds", "Fantasy Clouds", "Towering pastel clouds in a bright, dreamy sky.", true, 0xBFD7F5),
            new Entry("aurora", "Aurora", "Green and violet aurora curtains above a dark horizon.", true, 0x1C3B3A),
            new Entry("dreamy_pastel", "Dreamy Pastel", "Soft lavender, peach and mint gradients.", true, 0xE6CFF2),
            new Entry("clear_blue", "Clear Blue Sky", "A crisp, cloudless summer sky.", true, 0x7DB7F0),
            // Cloudy Skyboxes by Screaming Brain Studios (CC0), converted by tools/import_sbs_skies.py.
            new Entry("sbs_silver_morning", "Silver Morning", "Soft grey cloud over a rosy horizon with a pale sun.", true, 0x88767B),
            new Entry("sbs_golden_haze", "Golden Haze", "Bright golden cloud banks lit by a warm sun.", true, 0xB8AC9A),
            new Entry("sbs_amber_dusk", "Amber Dusk", "Hazy amber clouds over a low evening sun.", true, 0x9E8D89),
            new Entry("sbs_summer_blue", "Summer Blue", "Deep summer blue with crisp white clouds.", true, 0xACD1F2),
            new Entry("sbs_fair_weather", "Fair Weather", "Light blue sky with scattered fair-weather clouds.", true, 0x9DC5ED),
            new Entry("sbs_clear_noon", "Clear Noon", "Clear blue sky with a bright noon horizon.", true, 0x8BAEE0),
            new Entry("sbs_lavender_clouds", "Lavender Clouds", "Lavender and pink clouds drifting over a soft sun.", true, 0xA59EC6),
            new Entry("sbs_soft_daylight", "Soft Daylight", "Pale, even daylight under thin cloud.", true, 0xA8AFB5),
            new Entry("sbs_storm_gold", "Storm Gold", "Dark storm clouds over a burning gold horizon.", true, 0x8F835D),
            new Entry("sbs_overcast", "Overcast", "Flat grey-green overcast. Calm and moody.", true, 0x667172),
            new Entry("sbs_violet_night", "Violet Night", "Purple night clouds under a bright moon.", true, 0x4C3A7C),
            new Entry("sbs_slate_dusk", "Slate Dusk", "Dark slate dusk with a small, distant sun.", true, 0x443E54),
            new Entry("sbs_sand_fog", "Sand Fog", "Warm, dusty fog with a hidden sun.", true, 0x978070),
            new Entry("sbs_emerald_haze", "Emerald Haze", "An eerie green haze, great for spooky worlds.", true, 0x70A57C),
            new Entry("sbs_midnight_glow", "Midnight Glow", "Near-black night with a single glowing light on the horizon.", true, 0x292735),
            new Entry("sbs_cloud_tide", "Cloud Tide", "Dramatic blue-white clouds sweeping overhead.", true, 0x8386BA),
            new Entry("sbs_plum_sunset", "Plum Sunset", "Plum and magenta clouds over a red sunset.", true, 0x825367),
            new Entry("sbs_steel_horizon", "Steel Horizon", "Steel-blue cloud layers over a bright horizon.", true, 0x626978),
            new Entry("sbs_crimson_storm", "Crimson Storm", "A red sky under heavy crimson cloud. Made for BLOOD.", true, 0x96504A),
            new Entry("sbs_whiteout", "Whiteout", "Bright white fog with a soft glowing sun.", true, 0x9193AB),
            new Entry("sbs_fire_sunset", "Fire Sunset", "Orange fire-lit clouds over a blazing sunset.", true, 0x864C26),
            new Entry("sbs_rust_sunset", "Rust Sunset", "Rust-brown clouds and a deep orange sunset.", true, 0x7D412D),
            new Entry("sbs_rose_swirl", "Rose Swirl", "Swirling pink and white clouds.", true, 0xA8808E),
            new Entry("sbs_pale_clouds", "Pale Clouds", "Pale cream clouds in soft light.", true, 0xA29592),
            new Entry("sbs_dream_mist", "Dream Mist", "Lavender-grey mist with gentle cloud.", true, 0x847E99)
    );

    private static final List<Entry> CUSTOM = new CopyOnWriteArrayList<>();

    private SkyboxCatalog() {
    }

    /** Replaces the list of imported skyboxes (called after scanning the skyboxes folder). */
    public static void setCustom(List<Entry> entries) {
        CUSTOM.clear();
        CUSTOM.addAll(entries);
    }

    public static List<Entry> custom() {
        return Collections.unmodifiableList(CUSTOM);
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>(BUILT_IN);
        out.addAll(CUSTOM);
        return out;
    }

    public static Entry find(String id) {
        for (Entry e : BUILT_IN) if (e.id().equals(id)) return e;
        for (Entry e : CUSTOM) if (e.id().equals(id)) return e;
        return null;
    }

    /** Dropdown options: "Vanilla sky" first, then built-ins, then imported skyboxes. */
    public static List<ChoiceSetting.Option> options() {
        List<ChoiceSetting.Option> out = new ArrayList<>();
        out.add(new ChoiceSetting.Option(NONE, "Vanilla sky"));
        for (Entry e : BUILT_IN) out.add(new ChoiceSetting.Option(e.id(), e.name()));
        for (Entry e : CUSTOM) out.add(new ChoiceSetting.Option(e.id(), e.name() + " (custom)"));
        return out;
    }
}
