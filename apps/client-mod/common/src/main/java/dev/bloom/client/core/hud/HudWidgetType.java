package dev.bloom.client.core.hud;

import java.util.List;
import java.util.Map;

/**
 * The HUD widgets BLOOD offers (mirrored in the launcher: apps/launcher/src/core/bloomclient/modules.ts). Each one is rendered by its own class in the game layer;
 * this describes defaults and the per-widget options shown in the HUD editor.
 */
public enum HudWidgetType {
    FPS("fps", "FPS", "Frames per second.", Anchor.TOP_LEFT, 4, 4, true,
            List.of(new Option("label", "Show \"FPS\" label", true), new Option("colorCoded", "Colour by speed", false))),
    FRAME_TIME("frametime", "Frame time", "Average frame time and the slowest recent frame, in milliseconds.", Anchor.TOP_LEFT, 4, 18, false,
            List.of(new Option("graph", "Show graph", true), new Option("worst", "Show slowest frame", true))),
    MEMORY("memory", "Memory", "Java memory in use and the maximum allocated to the game (-Xmx).", Anchor.TOP_LEFT, 4, 32, false,
            List.of(new Option("percent", "Show percentage", true), new Option("bar", "Show bar", false))),
    COORDINATES("coords", "Coordinates", "Your X, Y and Z position.", Anchor.TOP_LEFT, 4, 46, true,
            List.of(new Option("compact", "One line", false), new Option("facing", "Show facing direction", false),
                    new Option("dimension", "Show dimension", false), new Option("biome", "Show biome", false),
                    new Option("nether", "Show Nether/Overworld equivalent", false))),
    DIRECTION("direction", "Direction", "Which way you're facing.", Anchor.TOP_CENTER, 0, 4, false,
            List.of(new Option("compass", "Compass strip", true), new Option("degrees", "Show degrees", false))),
    KEYSTROKES("keystrokes", "Keystrokes", "Movement keys and mouse buttons, lit while held.", Anchor.BOTTOM_RIGHT, -4, -4, false,
            List.of(new Option("mouse", "Show mouse buttons", true), new Option("space", "Show jump bar", true), new Option("cps", "Show CPS on mouse buttons", true))),
    CPS("cps", "CPS", "Clicks per second for left and right mouse buttons.", Anchor.TOP_LEFT, 4, 74, false,
            List.of(new Option("right", "Include right clicks", true))),
    ARMOR("armor", "Armor", "Your armor and held item with durability.", Anchor.MIDDLE_RIGHT, -4, 0, false,
            List.of(new Option("durability", "Show durability", true), new Option("percent", "Durability as %", false),
                    new Option("bars", "Durability bars", false), new Option("held", "Include held item", true),
                    new Option("horizontal", "Horizontal", false))),
    POTIONS("potions", "Potion effects", "Active effects with time remaining.", Anchor.MIDDLE_LEFT, 4, 0, false,
            List.of(new Option("icons", "Show icons", true), new Option("time", "Show time left", true),
                    new Option("level", "Show level (II, III)", true), new Option("compact", "Compact", false),
                    new Option("hideBeneficial", "Hide beneficial effects", false), new Option("hideHarmful", "Hide harmful effects", false),
                    new Option("hideInfinite", "Hide infinite effects", false))),
    CLOCK("clock", "Clock & session", "Your computer's clock and how long you've been in this world.", Anchor.TOP_RIGHT, -4, 4, false,
            List.of(new Option("clock", "Show clock", true), new Option("h24", "24-hour clock", true),
                    new Option("seconds", "Show seconds", false), new Option("session", "Show session timer", true))),
    HELD("held", "Held item", "Main-hand and off-hand items with their durability.", Anchor.BOTTOM_LEFT, 4, -4, false,
            List.of(new Option("offhand", "Include off-hand", true), new Option("percent", "Durability as %", false),
                    new Option("bar", "Durability bar", true), new Option("count", "Show item counts", true))),
    STATUS("status", "Sprint & sneak", "Whether you're sprinting or sneaking, and if Minecraft's toggle is holding it.", Anchor.TOP_LEFT, 4, 88, false,
            List.of(new Option("sprint", "Sprint", true), new Option("sneak", "Sneak", true),
                    new Option("mode", "Show \"Toggled\" / \"Held\"", true), new Option("flying", "Flying", false))),
    INFO("info", "Info panel", "Ping, server address, world day and in-game time.", Anchor.TOP_RIGHT, -4, 32, false,
            List.of(new Option("ping", "Ping", true), new Option("server", "Server address", false),
                    new Option("day", "World day", true), new Option("time", "In-game time", false)));

    public record Option(String id, String label, boolean defaultValue) {
    }

    private final String id;
    private final String label;
    private final String description;
    private final Anchor defaultAnchor;
    private final int defaultX;
    private final int defaultY;
    private final boolean visibleByDefault;
    private final List<Option> options;

    HudWidgetType(String id, String label, String description, Anchor anchor, int x, int y, boolean visible, List<Option> options) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.defaultAnchor = anchor;
        this.defaultX = x;
        this.defaultY = y;
        this.visibleByDefault = visible;
        this.options = options;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Anchor defaultAnchor() {
        return defaultAnchor;
    }

    public int defaultX() {
        return defaultX;
    }

    public int defaultY() {
        return defaultY;
    }

    public boolean visibleByDefault() {
        return visibleByDefault;
    }

    public List<Option> options() {
        return options;
    }

    public boolean optionDefault(String optionId) {
        for (Option o : options) if (o.id().equals(optionId)) return o.defaultValue();
        return false;
    }

    public boolean hasOption(String optionId) {
        for (Option o : options) if (o.id().equals(optionId)) return true;
        return false;
    }

    private static final Map<String, HudWidgetType> BY_ID;

    static {
        java.util.HashMap<String, HudWidgetType> m = new java.util.HashMap<>();
        for (HudWidgetType t : values()) m.put(t.id, t);
        BY_ID = Map.copyOf(m);
    }

    public static HudWidgetType byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
