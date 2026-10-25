package dev.bloom.client.core.settings;

/** Sections of the Right Shift menu, in display order. */
public enum Category {
    GENERAL("General", "general"),
    HUD("HUD Editor", "hud"),
    SKYBOX("Skyboxes", "skybox"),
    WEATHER("Weather & Environment", "weather"),
    PARTICLES("Particles", "particles"),
    VISUAL_EFFECTS("Visual Effects", "effects"),
    WORLD("World Appearance", "world"),
    COMBAT("Combat", "combat"),
    WAYPOINTS("Waypoints", "waypoints"),
    PERFORMANCE("Performance", "performance"),
    PROFILES("Profiles", "profiles"),
    KEYBINDS("Keybinds", "keybinds");

    private final String label;
    private final String icon;

    Category(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    /** Name of the sprite in assets/bloomclient/textures/gui/sprites/icons/. */
    public String icon() {
        return icon;
    }
}
