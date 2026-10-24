package dev.bloom.client.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Your own skin for the title screen, fetched straight from Mojang's public profile service. A
 * plain profile object has no texture data, so asking Minecraft's skin manager for it only ever
 * returned the default Steve/Alex skin.
 */
public final class ProfileSkin {
    public record Skin(Identifier texture, boolean slim) {
    }

    private static volatile Skin skin;
    private static volatile boolean started;

    private ProfileSkin() {
    }

    /** The loaded skin, or null while it's loading (or if it couldn't be fetched). */
    public static Skin get() {
        return skin;
    }

    public static void fetch(UUID id) {
        if (started || id == null) return;
        started = true;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        String uuid = id.toString().replace("-", "");
        HttpRequest profile = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
                .timeout(Duration.ofSeconds(10)).GET().build();
        http.sendAsync(profile, HttpResponse.BodyHandlers.ofString()).thenCompose(res -> {
            if (res.statusCode() != 200) throw new IllegalStateException("profile HTTP " + res.statusCode());
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            String url = null;
            boolean slim = false;
            for (JsonElement p : root.getAsJsonArray("properties")) {
                JsonObject o = p.getAsJsonObject();
                if (!"textures".equals(o.get("name").getAsString())) continue;
                String decoded = new String(Base64.getDecoder().decode(o.get("value").getAsString()), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    JsonObject s = textures.getAsJsonObject("SKIN");
                    url = s.get("url").getAsString();
                    slim = s.has("metadata") && "slim".equals(s.getAsJsonObject("metadata").get("model").getAsString());
                }
            }
            if (url == null) throw new IllegalStateException("no skin on profile");
            // Mojang lists http:// texture URLs; the same files are served over https.
            String https = url.replaceFirst("^http://", "https://");
            final boolean isSlim = slim;
            return http.sendAsync(HttpRequest.newBuilder(URI.create(https)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> new Object[]{r, isSlim});
        }).whenComplete((pair, err) -> {
            if (err != null) {
                Log.warn("MOTION title screen: couldn't load your skin (" + err + ")");
                return;
            }
            @SuppressWarnings("unchecked")
            HttpResponse<byte[]> r = (HttpResponse<byte[]>) pair[0];
            boolean slim = (Boolean) pair[1];
            if (r.statusCode() != 200 || r.body().length > 512 * 1024) return;
            NativeImage img;
            try {
                img = NativeImage.read(new java.io.ByteArrayInputStream(r.body()));
            } catch (Exception e) {
                Log.warn("MOTION title screen: your skin isn't a valid image (" + e + ")");
                return;
            }
            if (img.getWidth() != 64 || img.getHeight() != 64) {
                img.close();
                return; // legacy 64x32 skins: keep the default model
            }
            final NativeImage done = img;
            Minecraft.getInstance().execute(() -> {
                Identifier tex = Identifier.fromNamespaceAndPath(BloomClient.MOD_ID, "title/skin_" + uuid);
                Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "MOTION title skin", done));
                skin = new Skin(tex, slim);
            });
        });
    }
}
