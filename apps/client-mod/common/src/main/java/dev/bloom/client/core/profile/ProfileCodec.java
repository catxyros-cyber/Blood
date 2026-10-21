package dev.bloom.client.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.settings.Setting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Profile export/import. Profiles are plain JSON data: names and setting values only.
 * Nothing in a profile is ever executed, and every value is validated against the
 * setting it targets before it is accepted.
 */
public final class ProfileCodec {
    public static final int FORMAT = 1;
    public static final String SHARE_PREFIX = "BLOOM1:";
    public static final int MAX_IMPORT_BYTES = 64 * 1024;
    public static final int MAX_NAME_LENGTH = 32;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record ImportResult(String name, String description, JsonObject values, String hudLayout, List<String> warnings) {
    }

    public static final class ImportException extends Exception {
        public ImportException(String message) {
            super(message);
        }
    }

    private ProfileCodec() {
    }

    public static JsonObject toJson(Profile p) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("type", "bloom-profile");
        o.addProperty("name", p.name());
        if (!p.description().isEmpty()) o.addProperty("description", p.description());
        o.add("values", p.copyValues());
        if (p.hudLayout() != null) o.addProperty("hudLayout", p.hudLayout());
        return o;
    }

    public static String toPrettyJson(Profile p) {
        return GSON.toJson(toJson(p));
    }

    /** Compact single-line code for sharing in chat ("BLOOM1:…"). */
    public static String toShareCode(Profile p) {
        byte[] json = new Gson().toJson(toJson(p)).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bos)) {
            out.write(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
    }

    /**
     * Parses a profile from JSON text or a share code.
     *
     * @param settingLookup resolves a key to its setting (null when unknown)
     */
    public static ImportResult parse(String text, Function<String, Setting> settingLookup) throws ImportException {
        if (text == null) throw new ImportException("Nothing to import.");
        String t = text.trim();
        if (t.length() > MAX_IMPORT_BYTES * 2) throw new ImportException("That's too large to be a BLOOD profile.");
        String json;
        if (t.startsWith(SHARE_PREFIX)) {
            json = decodeShareCode(t.substring(SHARE_PREFIX.length()));
        } else if (t.startsWith("{")) {
            if (t.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES) throw new ImportException("That's too large to be a BLOOD profile.");
            json = t;
        } else {
            throw new ImportException("This isn't a BLOOD profile. Paste a code starting with \"" + SHARE_PREFIX + "\" or choose a .json profile file.");
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) throw new ImportException("The profile file is not valid.");
            root = el.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ImportException("The profile file is not valid JSON.");
        }
        JsonElement type = root.get("type");
        if (type != null && !(type.isJsonPrimitive() && "bloom-profile".equals(type.getAsString()))) {
            throw new ImportException("This file is not a BLOOD profile.");
        }
        int format = 0;
        try {
            format = root.has("format") ? root.get("format").getAsInt() : 0;
        } catch (RuntimeException ignored) {
        }
        if (format < 1) throw new ImportException("This file is not a BLOOD profile.");
        List<String> warnings = new ArrayList<>();
        if (format > FORMAT) warnings.add("Made with a newer BLOOD Client; some options may be skipped.");

        String name = cleanName(str(root, "name"));
        if (name.isEmpty()) name = "Imported profile";
        String description = cleanText(str(root, "description"), 160);

        JsonElement valuesEl = root.get("values");
        if (valuesEl == null || !valuesEl.isJsonObject()) throw new ImportException("The profile has no settings.");
        JsonObject values = new JsonObject();
        int skipped = 0;
        for (Map.Entry<String, JsonElement> e : valuesEl.getAsJsonObject().entrySet()) {
            Setting s = settingLookup.apply(e.getKey());
            if (s == null || !s.profileScoped() || !s.accepts(e.getValue())) {
                skipped++;
                continue;
            }
            values.add(e.getKey(), e.getValue().deepCopy());
        }
        if (skipped > 0) warnings.add(skipped + " unknown or invalid option" + (skipped == 1 ? " was" : "s were") + " skipped.");
        if (values.size() == 0 && skipped > 0) throw new ImportException("None of the profile's options could be used.");
        String hud = str(root, "hudLayout");
        if (hud != null && !hud.matches("[a-z0-9_\\-]{1,48}")) hud = null;
        return new ImportResult(name, description, values, hud, warnings);
    }

    private static String decodeShareCode(String b64) throws ImportException {
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(b64.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new ImportException("The share code is damaged. Make sure you copied all of it.");
        }
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] data = in.readNBytes(MAX_IMPORT_BYTES + 1);
            if (data.length > MAX_IMPORT_BYTES) throw new ImportException("That's too large to be a BLOOD profile.");
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ImportException("The share code is damaged. Make sure you copied all of it.");
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** Printable characters only, single spaces, at most 32 characters. */
    public static String cleanName(String s) {
        return cleanText(s, MAX_NAME_LENGTH);
    }

    public static String cleanText(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                i++; // drop Minecraft formatting codes ("§c") entirely
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (b.length() > 0 && !space) {
                    b.append(' ');
                    space = true;
                }
            } else if (!Character.isISOControl(c) && c != '§' && Character.getType(c) != Character.FORMAT
                    && !Character.isSurrogate(c)) {
                // '§' would inject Minecraft formatting codes.
                b.append(c);
                space = false;
            }
        }
        return b.toString().trim();
    }
}
