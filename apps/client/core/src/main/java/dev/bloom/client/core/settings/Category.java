package dev.bloom.client.core.settings;

/** Right Shift menu categories, in display order. */
public enum Category implements Labeled {
    GENERAL("General", "Theme, menu behaviour and notifications"),
    HUD("HUD Editor", "Move, resize and style on-screen widgets"),
    SKY("Skyboxes", "Day and night skies, rotation and colour"),
    WEATHER("Weather & Environment", "Decorative rain, snow and wind"),
    PARTICLES("Particles", "Cherry petals, leaves, fireflies and motes"),
    EFFECTS("Visual Effects", "Effect level, motion and transitions"),
    WORLD("World Appearance", "Fog colour, clouds and vanilla weather"),
    PERFORMANCE("Performance", "Quality presets, limits and live stats"),
    PROFILES("Profiles", "Save, switch, import and export looks"),
    KEYBINDS("Keybinds", "BLOOD shortcuts and conflicts");

    private final String label;
    private final String blurb;

    Category(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }

    @Override
    public String label() {
        return label;
    }

    public String blurb() {
        return blurb;
    }
}
