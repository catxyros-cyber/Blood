package dev.bloom.client.core.cape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which MOTION cape each player wears, as told by the MOTION server (/v1/cape-owners). Decides who
 * needs looking up (new players, or answers older than {@link #TTL_MS}), batches at most
 * {@link #BATCH} players per request and parses the reply. Pure Java (no Minecraft), unit tested.
 */
public final class CapeDirectory {
    public static final long TTL_MS = 5 * 60_000;
    public static final int BATCH = 100;
    /** After a failed request, wait this long before asking again (server down, offline). */
    public static final long RETRY_MS = 60_000;

    private record Known(String capeId, Tag tag, String wingsId, Aura aura, long at) {
    }

    /** A MOTION aura: a colour and a particle style ("glow", "embers", "sparkles", "orbit"). */
    public record Aura(int rgb, String style) {
    }

    /** Accepted aura styles; anything else is ignored. */
    public static final java.util.Set<String> AURA_STYLES = java.util.Set.of("glow", "embers", "sparkles", "orbit");

    private final Map<String, Known> known = new HashMap<>();
    private final Map<String, Long> pending = new HashMap<>();
    private long blockedUntil;

    /** Normalised Minecraft UUID (32 lowercase hex) or null. */
    public static String normalize(String uuid) {
        if (uuid == null) return null;
        String u = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return u.matches("[0-9a-f]{32}") ? u : null;
    }

    /** A MOTION tag like [OWNER]: text (letters, digits, a few symbols) and an RGB colour. */
    public record Tag(String text, int rgb) {
    }

    /** The player's tag, or null. */
    public synchronized Tag tagOf(String uuid) {
        Known k = known.get(normalize(uuid));
        return k == null ? null : k.tag();
    }

    /** Wings accessory id for a player, or null. */
    public synchronized String wingsOf(String uuid) {
        Known k = known.get(normalize(uuid));
        return k == null ? null : k.wingsId();
    }

    /** The player's aura, or null. */
    public synchronized Aura auraOf(String uuid) {
        Known k = known.get(normalize(uuid));
        return k == null ? null : k.aura();
    }

    /** Cape id for a player, or null (no cape, or not known yet). */
    public synchronized String capeOf(String uuid) {
        Known k = known.get(normalize(uuid));
        return k == null ? null : k.capeId();
    }

    /** Players (from {@code present}) to ask about now, at most {@link #BATCH}; marks them pending. */
    public synchronized List<String> due(Collection<String> present, long now) {
        List<String> out = new ArrayList<>();
        if (now < blockedUntil) return out;
        for (String raw : present) {
            String u = normalize(raw);
            if (u == null) continue;
            Known k = known.get(u);
            Long p = pending.get(u);
            if (p != null && now - p < 30_000) continue;
            if (k == null || now - k.at() > TTL_MS) {
                out.add(u);
                pending.put(u, now);
                if (out.size() >= BATCH) break;
            }
        }
        return out;
    }

    /** Applies a reply {"capes": {uuid: capeId}} for the players that were asked. */
    public synchronized void answer(List<String> asked, String json, long now) {
        Map<String, String> capes = new HashMap<>();
        Map<String, Tag> tags = new HashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonObject() && root.getAsJsonObject().get("tags") instanceof JsonObject t) {
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                String u = normalize(e.getKey());
                if (u == null || !(e.getValue() instanceof JsonObject o)) continue;
                JsonElement text = o.get("text"), color = o.get("color");
                if (text == null || !text.isJsonPrimitive() || color == null || !color.isJsonPrimitive()) continue;
                String tx = text.getAsString(), c = color.getAsString();
                if (!tx.matches("[A-Za-z0-9 _+!*#&.\\-]{1,16}") || !c.matches("#[0-9a-fA-F]{6}")) continue;
                tags.put(u, new Tag(tx, Integer.parseInt(c.substring(1), 16)));
            }
        }
        if (root.isJsonObject() && root.getAsJsonObject().get("capes") instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String u = normalize(e.getKey());
                if (u != null && e.getValue().isJsonPrimitive()) {
                    String id = e.getValue().getAsString();
                    if (id.matches("[0-9a-f-]{36}")) capes.put(u, id);
                }
            }
        }
        Map<String, String> wings = new HashMap<>();
        if (root.isJsonObject() && root.getAsJsonObject().get("wings") instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String u = normalize(e.getKey());
                if (u != null && e.getValue().isJsonPrimitive()) {
                    String id = e.getValue().getAsString();
                    if (id.matches("[0-9a-f-]{36}")) wings.put(u, id);
                }
            }
        }
        Map<String, Aura> auras = new HashMap<>();
        if (root.isJsonObject() && root.getAsJsonObject().get("auras") instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String u = normalize(e.getKey());
                if (u == null || !(e.getValue() instanceof JsonObject a)) continue;
                JsonElement color = a.get("color"), style = a.get("style");
                if (color == null || !color.isJsonPrimitive() || style == null || !style.isJsonPrimitive()) continue;
                String c = color.getAsString(), st = style.getAsString();
                if (!c.matches("#[0-9a-fA-F]{6}") || !AURA_STYLES.contains(st)) continue;
                auras.put(u, new Aura(Integer.parseInt(c.substring(1), 16), st));
            }
        }
        for (String u : asked) {
            pending.remove(u);
            known.put(u, new Known(capes.get(u), tags.get(u), wings.get(u), auras.get(u), now));
        }
    }

    /** The request failed: forget the pending marks and back off. */
    public synchronized void failed(List<String> asked, long now) {
        for (String u : asked) pending.remove(u);
        blockedUntil = now + RETRY_MS;
    }

    /** Your own cape changed (or a forced refresh): ask again on the next round. */
    public synchronized void invalidate(String uuid) {
        known.remove(normalize(uuid));
    }

    public synchronized void clear() {
        known.clear();
        pending.clear();
        blockedUntil = 0;
    }

    /** Drop entries of players not seen for a while, so the map can't grow forever. */
    public synchronized void trim(long now) {
        known.values().removeIf(k -> now - k.at() > TTL_MS * 3);
    }
}
