package dev.bloom.client.core.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * A named set of values for the profile-scoped settings (skybox, effects, colours, HUD style,
 * quality). Built-in profiles start from a definition in code; editing one saves an override
 * file that "Reset profile" removes again.
 */
public final class Profile {
    private final String id;
    private String name;
    private final String description;
    private final boolean builtIn;
    /** Only profile-scoped keys. Missing keys mean "default". */
    private final JsonObject values;
    /** HUD layout to switch to with this profile (null = keep the current layout). */
    private String hudLayout;
    /** Built-in profile whose values were changed by the player. */
    private boolean modified;

    public Profile(String id, String name, String description, boolean builtIn, JsonObject values, String hudLayout) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.builtIn = builtIn;
        this.values = values == null ? new JsonObject() : values;
        this.hudLayout = hudLayout;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public String description() {
        return description;
    }

    public boolean builtIn() {
        return builtIn;
    }

    public boolean modified() {
        return modified;
    }

    void setModified(boolean modified) {
        this.modified = modified;
    }

    public JsonObject values() {
        return values;
    }

    public String hudLayout() {
        return hudLayout;
    }

    void setHudLayout(String hudLayout) {
        this.hudLayout = hudLayout;
    }

    public JsonObject copyValues() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonElement> e : values.entrySet()) o.add(e.getKey(), e.getValue().deepCopy());
        return o;
    }
}
