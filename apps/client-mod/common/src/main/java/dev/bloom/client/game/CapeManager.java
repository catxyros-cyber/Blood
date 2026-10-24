package dev.bloom.client.game;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bloom.client.BloomClient;
import dev.bloom.client.core.Log;
import dev.bloom.client.core.cape.CapeDirectory;
import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOTION capes in game. Every couple of seconds it asks the MOTION server which cape the players
 * around you wear (batched, cached for 5 minutes), downloads each cape image once, and hands it to
 * Minecraft through the player's skin (see AbstractClientPlayerMixin), so Minecraft's own cape and
 * elytra renderers draw it. Network work never runs on the game thread.
 *
 * <p>The server address comes from the launcher ({@code -Dbloodclient.api=https://…}). Without it,
 * or with "MOTION capes" off, nothing is requested.
 */
public final class CapeManager {
    private static final long MAX_PNG = 4L * 1024 * 1024;
    private static final int MAX_FRAMES = 64;
    private static final CapeDirectory DIRECTORY = new CapeDirectory();
    /** A loaded cape: one texture per animation frame (one for still capes). */
    private record Anim(ResourceLocation[] frames, int frameMs) {
        ResourceLocation now() {
            if (frames.length == 1) return frames[0];
            return frames[(int) ((System.currentTimeMillis() / frameMs) % frames.length)];
        }
    }

    private static final Map<String, Anim> TEXTURES = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final String API = apiBase();
    private static HttpClient http;
    private static long lastRound;

    private CapeManager() {
    }

    private static String apiBase() {
        String a = System.getProperty("bloodclient.api", "").trim().replaceAll("/+$", "");
        if (a.startsWith("https://") || a.startsWith("http://localhost") || a.startsWith("http://127.0.0.1")) return a;
        return null;
    }

    static {
        Log.info(API == null ? "MOTION capes: no server address from the launcher (-Dbloodclient.api), capes and tags are off" : "MOTION capes: using " + API);
    }

    public static boolean available() {
        return API != null;
    }

    private static HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        return http;
    }

    /** Cape texture for a player, or null. Cheap: called every frame for every visible player. */
    public static ResourceLocation capeFor(UUID uuid) {
        if (API == null || !BloomSettings.CAPES.on()) return null;
        String id = DIRECTORY.capeOf(uuid.toString());
        if (id == null) return null;
        Anim anim = TEXTURES.get(id);
        if (anim == null && !FAILED.contains(id) && LOADING.add(id)) download(id);
        return anim == null ? null : anim.now();
    }

    /** The player's MOTION tag (e.g. [OWNER]), or null. */
    public static CapeDirectory.Tag tagFor(UUID uuid) {
        if (API == null || !BloomSettings.TAGS.on()) return null;
        return DIRECTORY.tagOf(uuid.toString());
    }

    /** "[OWNER] name": the tag in its colour, then the name as it was. */
    public static net.minecraft.network.chat.Component tagged(UUID uuid, net.minecraft.network.chat.Component name) {
        CapeDirectory.Tag t = tagFor(uuid);
        if (t == null || name == null) return name;
        return net.minecraft.network.chat.Component.literal("[" + t.text() + "] ")
                .withStyle(s -> s.withColor(t.rgb()).withBold(true))
                .append(name.copy().withStyle(s -> s.withBold(false)));
    }

    /** From the client tick: start a lookup round every 2 seconds for players without a fresh answer. */
    public static void tick(Minecraft mc) {
        if (API == null || (!BloomSettings.CAPES.on() && !BloomSettings.TAGS.on()) || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRound < 2000) return;
        lastRound = now;
        List<String> present = new ArrayList<>();
        for (AbstractClientPlayer p : mc.level.players()) present.add(p.getUUID().toString());
        if (BloomSettings.TAGS.on() && mc.getConnection() != null) {
            for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (present.size() >= 400) break;
                present.add(info.getProfile().getId().toString());
            }
        }
        List<String> due = DIRECTORY.due(present, now);
        DIRECTORY.trim(now);
        if (due.isEmpty()) return;
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/v1/cape-owners?uuids=" + String.join(",", due)))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
        http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            long t = System.currentTimeMillis();
            if (err != null || res.statusCode() != 200) {
                Log.warn("MOTION capes: lookup failed (" + (err != null ? err : "HTTP " + res.statusCode()) + ")");
                DIRECTORY.failed(due, t);
                return;
            }
            try {
                DIRECTORY.answer(due, res.body(), t);
                Log.info("MOTION capes: looked up " + due.size() + " player(s): " + res.body());
            } catch (RuntimeException e) {
                DIRECTORY.failed(due, t);
            }
        });
    }

    private static void download(String capeId) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/v1/capes/" + capeId + ".png")).timeout(Duration.ofSeconds(15)).GET().build();
        http().sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((res, err) -> {
            if (err != null || res.statusCode() != 200 || res.body().length > MAX_PNG) {
                Log.warn("MOTION capes: couldn't download cape " + capeId + " (" + (err != null ? err : "HTTP " + res.statusCode()) + ")");
                LOADING.remove(capeId);
                if (res != null && res.statusCode() == 404) FAILED.add(capeId);
                return;
            }
            int frameMs = res.headers().firstValue("X-Cape-Frame-Ms").map(x -> {
                try {
                    return Math.max(20, Math.min(5000, Integer.parseInt(x.trim())));
                } catch (NumberFormatException e) {
                    return 100;
                }
            }).orElse(100);
            NativeImage[] frames;
            try (NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(res.body()))) {
                int w = img.getWidth(), h = img.getHeight();
                int fh = w / 2;
                int count = fh > 0 && h % fh == 0 ? h / fh : 0;
                if (w < 64 || w > 2048 || w % 2 != 0 || count < 1 || count > MAX_FRAMES) {
                    Log.warn("MOTION capes: cape " + capeId + " is " + w + "x" + h + ", not a cape layout");
                    FAILED.add(capeId);
                    LOADING.remove(capeId);
                    return;
                }
                // Animated capes are a vertical strip: cut it into one image per frame.
                frames = new NativeImage[count];
                for (int i = 0; i < count; i++) {
                    NativeImage frame = new NativeImage(w, fh, false);
                    for (int y = 0; y < fh; y++) for (int x = 0; x < w; x++) frame.setPixelRGBA(x, y, img.getPixelRGBA(x, i * fh + y));
                    frames[i] = frame;
                }
            } catch (Exception e) {
                Log.warn("MOTION cape " + capeId + " isn't a valid image", e);
                FAILED.add(capeId);
                LOADING.remove(capeId);
                return;
            }
            final NativeImage[] cut = frames;
            final int ms = frameMs;
            Minecraft.getInstance().execute(() -> {
                ResourceLocation[] ids = new ResourceLocation[cut.length];
                for (int i = 0; i < cut.length; i++) {
                    NativeImage frame = cut[i];
                    ids[i] = ResourceLocation.fromNamespaceAndPath(BloomClient.MOD_ID, "capes/" + capeId + "/" + i);
                    Minecraft.getInstance().getTextureManager().register(ids[i], new DynamicTexture(frame));
                }
                TEXTURES.put(capeId, new Anim(ids, ms));
                Log.info("MOTION capes: loaded cape " + capeId + (cut.length > 1 ? " (" + cut.length + " frames)" : ""));
                LOADING.remove(capeId);
            });
        });
    }

    /** Leaving a world, or capes turned off: forget lookups and free the textures (render thread). */
    public static void clear() {
        DIRECTORY.clear();
        for (Anim a : new java.util.ArrayList<>(TEXTURES.values())) for (ResourceLocation rl : a.frames()) Minecraft.getInstance().getTextureManager().release(rl);
        TEXTURES.clear();
        FAILED.clear();
    }

    /** Ask again about one player (e.g. after you changed your own cape in the launcher). */
    public static void refresh(UUID uuid) {
        DIRECTORY.invalidate(uuid.toString());
    }

    /** For the menu: how many capes are loaded. */
    public static int loadedCount() {
        return TEXTURES.size();
    }
}
