package dev.bloom.client.core.sky;

import dev.bloom.client.core.settings.ChoiceSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Built-in BLOOD skyboxes (33 from "Fantasy Skybox FREE" and 25 CC0 "Cloudy Skyboxes" by Screaming Brain Studios)
 * plus any the player imported. Built-in textures live in
 * assets/bloomclient/textures/skybox/&lt;id&gt;.png (3x2 atlas, see docs/CUSTOM_SKYBOXES.md).
 */
public final class SkyboxCatalog {
    public static final String NONE = "none";
    public static final String CUSTOM_PREFIX = "custom:";

    public record Entry(String id, String name, String description, boolean builtIn, int previewTint) {
    }

    public static final List<Entry> BUILT_IN = List.of(
            // Fantasy Skybox FREE (cubemaps + panoramas), converted by tools/import_fantasy_skies.py.
            new Entry("fs_classic_day_1", "Cumulus Day", "Big painted cumulus clouds in a deep blue sky.", true, 0x4C98D6),
            new Entry("fs_classic_day_2", "Bright Cumulus", "Towering white clouds in a bright, airy sky.", true, 0x80E7F2),
            new Entry("fs_classic_day_3", "Deep Blue Day", "Rich blue sky with soft clouds along the horizon.", true, 0x58A7DD),
            new Entry("fs_classic_day_4", "Teal Breeze", "Light teal sky with drifting cloud puffs.", true, 0x85D9E0),
            new Entry("fs_classic_day_5", "Drifting Clouds", "Small clouds drifting across a clear blue sky.", true, 0x64BEEB),
            new Entry("fs_classic_day_6", "Puffy Clouds", "A clear sky full of round, puffy clouds.", true, 0x5DB6E8),
            new Entry("fs_classic_night_1", "Blue Night", "A painted blue night with long, soft clouds.", true, 0x0E3970),
            new Entry("fs_classic_night_2", "Cloud Islands", "Night sky with little floating cloud islands.", true, 0x0A2F5D),
            new Entry("fs_classic_night_3", "Teal Night", "A glowing teal night horizon under dark clouds.", true, 0x0A5D7B),
            new Entry("fs_002_day", "Azure Day", "Clear azure sky with wispy clouds.", true, 0x84DDFE),
            new Entry("fs_002_night", "Azure Night", "Deep blue night with a faint glow on the horizon.", true, 0x082D76),
            new Entry("fs_002_sunrise", "Azure Sunrise", "Gold sunrise under a bright blue sky.", true, 0x99BCA1),
            new Entry("fs_002_sunset", "Azure Sunset", "Warm red sunset fading into blue.", true, 0xA97B7B),
            new Entry("fs_002_rainy", "Azure Rain", "Grey rain clouds over a calm horizon.", true, 0x486271),
            new Entry("fs_002_snowy", "Azure Snow", "Pale, cold sky for snowy days.", true, 0xA1D9F3),
            new Entry("fs_003_day", "Cloudscape Day", "Fluffy anime-style clouds in a bright summer sky.", true, 0xAFEDFE),
            new Entry("fs_003_night", "Cloudscape Night", "Moonlit clouds in a glowing blue night.", true, 0x3088B9),
            new Entry("fs_003_sunrise", "Cloudscape Sunrise", "Golden clouds lit by a blazing sunrise.", true, 0xC8A787),
            new Entry("fs_003_sunset", "Cloudscape Sunset", "Orange-gold clouds over a warm sunset.", true, 0xC79C5E),
            new Entry("fs_003_rainy", "Cloudscape Rain", "Heavy grey storm clouds.", true, 0x708692),
            new Entry("fs_003_snowy", "Cloudscape Snow", "Soft white clouds in a cold blue sky.", true, 0xAEEAFB),
            new Entry("fs_013_day", "Sunbeam Day", "Light rays breaking through blue cloud.", true, 0x8EC5DB),
            new Entry("fs_013_night", "Crimson Night", "A dark, maroon night sky. Great for BLOOD.", true, 0x231824),
            new Entry("fs_013_sunrise", "Ember Sunrise", "A fiery sun rising over dark hills.", true, 0xA58353),
            new Entry("fs_013_sunset", "Crimson Sunset", "A deep red sunset. BLOOD's default day sky.", true, 0x985047),
            new Entry("fs_013_rainy", "Dark Storm", "Near-black storm clouds.", true, 0x1D2D2E),
            new Entry("fs_013_snowy", "Whiteout Snow", "A bright white snowy sky.", true, 0xEEFCFF),
            new Entry("fs_017_day", "Wisp Day", "Clear sky with long white wisps.", true, 0x85DAED),
            new Entry("fs_017_night", "Wisp Night", "Dark teal night with faint golden clouds.", true, 0x20363F),
            new Entry("fs_017_sunrise", "Wisp Sunrise", "A soft yellow sunrise on the horizon.", true, 0xC6C8A8),
            new Entry("fs_017_sunset", "Violet Sunset", "Purple sky over a burning red horizon.", true, 0x65373B),
            new Entry("fs_017_rainy", "Wisp Rain", "Dark rain clouds rolling in.", true, 0x3A444B),
            new Entry("fs_017_snowy", "Wisp Snow", "Pale icy sky for snowy weather.", true, 0xC6F7FF),
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

    /** Skies that were removed, mapped to the closest built-in (old settings and profiles keep working). */
    private static final java.util.Map<String, String> RENAMED = java.util.Map.ofEntries(
            java.util.Map.entry("crimson_dusk", "fs_013_sunset"),
            java.util.Map.entry("blood_moon", "fs_013_night"),
            java.util.Map.entry("cherry_blossom", "fs_002_sunset"),
            java.util.Map.entry("pink_sunset", "fs_017_sunset"),
            java.util.Map.entry("sakura_night", "fs_classic_night_2"),
            java.util.Map.entry("starry_night", "fs_002_night"),
            java.util.Map.entry("galaxy", "fs_classic_night_3"),
            java.util.Map.entry("fantasy_clouds", "fs_classic_day_1"),
            java.util.Map.entry("aurora", "fs_017_night"),
            java.util.Map.entry("dreamy_pastel", "fs_classic_day_4"),
            java.util.Map.entry("clear_blue", "fs_classic_day_5"),
            java.util.Map.entry("hw_blood_moon", "fs_013_night"),
            java.util.Map.entry("hw_halloween_night", "fs_classic_night_1"),
            java.util.Map.entry("hw_pumpkin_sky", "fs_003_sunset"),
            java.util.Map.entry("hw_haunted_forest", "fs_017_night"),
            java.util.Map.entry("hw_midnight_purple", "fs_002_night"),
            java.util.Map.entry("hw_ghostfire", "fs_013_sunset"),
            java.util.Map.entry("hw_eclipse", "fs_013_rainy"),
            java.util.Map.entry("hw_blood_storm", "fs_013_rainy"),
            java.util.Map.entry("hw_monochrome_night", "fs_017_rainy"));

    public static String migrate(String id) {
        return id == null ? null : RENAMED.getOrDefault(id, id);
    }

    private static final List<Entry> CUSTOM = new CopyOnWriteArrayList<>();
    private static final java.util.Map<String, Integer> CUSTOM_FACE = new java.util.concurrent.ConcurrentHashMap<>();
    /** When off ("Load custom skyboxes"), imported skyboxes are hidden and never loaded. */
    private static volatile boolean customEnabled = true;

    /** Built-in face sizes: all built-ins are 2K (512 px faces). */
    public static int nativeFace(String id) {
        if (id.startsWith(CUSTOM_PREFIX)) return CUSTOM_FACE.getOrDefault(id, 512);
        return 512;
    }

    static void setCustomFace(String id, int face) {
        CUSTOM_FACE.put(id, face);
    }

    /**
     * Resolution label shown in the menu: the equivalent panorama width ("4K" = 4096×2048 = 1024 px
     * faces) and the true face size. Never rounded up: a 768 px face is "3K".
     */
    public static String resolutionLabel(String id) {
        int f = nativeFace(id);
        if (f < 256) return f + " px faces";
        return (f * 4 / 1024) + "K · " + f + " px faces"; // rounded down, never up
    }

    /** "halloween", "blood", "cloudy" or "custom". */
    public static String category(String id) {
        if (id.startsWith(CUSTOM_PREFIX)) return "custom";
        if (id.startsWith("hw_")) return "halloween";
        if (id.startsWith("sbs_")) return "cloudy";
        return "blood";
    }

    public static void setCustomEnabled(boolean on) {
        customEnabled = on;
    }

    public static boolean customEnabled() {
        return customEnabled;
    }

    private SkyboxCatalog() {
    }

    /** Replaces the list of imported skyboxes (called after scanning the skyboxes folder). */
    public static void setCustom(List<Entry> entries) {
        CUSTOM.clear();
        CUSTOM.addAll(entries);
    }

    public static List<Entry> custom() {
        return customEnabled ? Collections.unmodifiableList(CUSTOM) : List.of();
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>(BUILT_IN);
        out.addAll(custom());
        return out;
    }

    public static Entry find(String id) {
        for (Entry e : BUILT_IN) if (e.id().equals(id)) return e;
        if (customEnabled) for (Entry e : CUSTOM) if (e.id().equals(id)) return e;
        return null;
    }

    /** Dropdown options: "Vanilla sky" first, then built-ins, then imported skyboxes. */
    public static List<ChoiceSetting.Option> options() {
        List<ChoiceSetting.Option> out = new ArrayList<>();
        out.add(new ChoiceSetting.Option(NONE, "Vanilla sky"));
        for (Entry e : BUILT_IN) out.add(new ChoiceSetting.Option(e.id(), e.name()));
        for (Entry e : custom()) out.add(new ChoiceSetting.Option(e.id(), e.name() + " (custom)"));
        return out;
    }
}
