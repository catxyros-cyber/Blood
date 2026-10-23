package dev.bloom.client;

import dev.bloom.client.core.Log;
import dev.bloom.client.core.compat.CompatReport;
import dev.bloom.client.core.hud.HudLayouts;
import dev.bloom.client.core.particles.AdaptiveBudget;
import dev.bloom.client.core.particles.ParticleSystem;
import dev.bloom.client.core.perf.CpsCounter;
import dev.bloom.client.core.perf.FrameStats;
import dev.bloom.client.core.perf.LowFpsAdvisor;
import dev.bloom.client.core.profile.Profile;
import dev.bloom.client.core.profile.ProfileManager;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.core.settings.SettingsStore;
import dev.bloom.client.core.sky.SkyFavorites;
import dev.bloom.client.core.sky.SkyboxCatalog;
import dev.bloom.client.core.sky.SkyboxLibrary;
import dev.bloom.client.game.CapeManager;
import dev.bloom.client.game.GameEnv;
import dev.bloom.client.game.McImageCodec;
import dev.bloom.client.game.ShaderCompat;
import dev.bloom.client.gui.BloomMenuScreen;
import dev.bloom.client.gui.BloomToast;
import dev.bloom.client.gui.HudEditorScreen;
import dev.bloom.client.hud.HudRenderer;
import dev.bloom.client.platform.Platform;
import dev.bloom.client.render.EffectsRenderer;
import dev.bloom.client.render.PostEffects;
import dev.bloom.client.render.SkyTextures;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MOTION Client's shared (loader-independent) entry point. The Fabric and NeoForge entrypoints
 * call {@link #init}, register {@link BloomKeys}, and forward ticks, HUD rendering and world
 * rendering here.
 */
public final class BloomClient {
    public static final String MOD_ID = "bloomclient";
    private static final Logger LOGGER = LoggerFactory.getLogger("MOTION Client");
    private static BloomClient instance;

    private final Platform platform;
    private final Path configDir;
    private final SettingsStore store;
    private final ProfileManager profiles;
    private final HudLayouts hudLayouts;
    private final SkyboxLibrary skyboxes;
    private final SkyFavorites favorites;
    private final ParticleSystem particles = new ParticleSystem();
    private final GameEnv env = new GameEnv();
    private final FrameStats frames = new FrameStats();
    private final CpsCounter cps = new CpsCounter();
    private final LowFpsAdvisor advisor = new LowFpsAdvisor();
    private final AdaptiveBudget budget = new AdaptiveBudget();
    private final HudRenderer hud;
    private final List<CompatReport.Note> compatNotes;

    private boolean shaderActive;
    private long lastSecond;
    private int ticks;
    private boolean hudHidden;
    private boolean hadLevel;
    private String pendingLoadProblem;

    private BloomClient(Platform platform) {
        this.platform = platform;
        Log.setSink(new Log.Sink() {
            @Override
            public void info(String msg) {
                LOGGER.info(msg);
            }

            @Override
            public void warn(String msg, Throwable t) {
                if (t == null) LOGGER.warn(msg);
                else LOGGER.warn(msg, t);
            }
        });
        this.configDir = platform.configDir().resolve(MOD_ID);
        BloomSettings.init();
        this.store = new SettingsStore(configDir.resolve("settings.json"), BloomSettings.all(), System::currentTimeMillis);
        this.hudLayouts = new HudLayouts(store);
        this.profiles = new ProfileManager(configDir.resolve("profiles"), BloomSettings.all(), store, System::currentTimeMillis);
        this.skyboxes = new SkyboxLibrary(configDir.resolve("skyboxes"), new McImageCodec(configDir.resolve(".tmp")));
        this.favorites = new SkyFavorites(store);
        store.addSection(favorites);
        this.skyboxes.scan();
        this.store.load();
        this.profiles.ready();
        this.pendingLoadProblem = store.takeLoadProblem();
        SkyboxCatalog.setCustomEnabled(BloomSettings.SKY_LOAD_CUSTOM.on());
        BloomSettings.SKY_LOAD_CUSTOM.addListener(s -> {
            SkyboxCatalog.setCustomEnabled(BloomSettings.SKY_LOAD_CUSTOM.on());
            Minecraft.getInstance().execute(SkyTextures::invalidateAll);
        });
        BloomSettings.SKY_DAY.revalidate();
        BloomSettings.SKY_NIGHT.revalidate();
        this.hud = new HudRenderer(this);
        this.compatNotes = CompatReport.evaluate(platform::isModLoaded);
        for (CompatReport.Note n : compatNotes) Log.info("Compatibility: " + n.name() + " — " + n.message());

        // Sky, fog and world effects pause while a shader pack is active (unless the player opts in).
        java.util.function.Supplier<String> shaderReason = () -> worldEffectsPaused()
                ? "Paused: a shader pack is active. Turn on \"Use with shader packs\" (Skyboxes → Compatibility) to override." : null;
        for (Setting s : BloomSettings.all()) {
            String k = s.key();
            if ((k.startsWith("sky.") && !k.equals("sky.withShaders")) || k.startsWith("world.") || k.startsWith("env.")
                    || k.startsWith("rain.") || k.startsWith("snow.") || k.startsWith("petals.") || k.startsWith("leaves.")
                    || k.startsWith("fireflies.") || k.startsWith("dust.") || k.startsWith("wind.") || k.startsWith("glow.")
                    || k.startsWith("post.")) {
                s.setAvailability(shaderReason);
            }
        }

        // Shader effects: show load/hook problems under the main toggle, and let a failed shader
        // chain be retried once the player changes one of its settings.
        BloomSettings.POST.setAvailability(() -> shaderReason.get() != null ? shaderReason.get() : PostEffects.problem());
        for (Setting s : BloomSettings.all()) {
            if (s.key().startsWith("post.") || s == BloomSettings.POST_QUALITY || s == BloomSettings.LITE_MODE) {
                s.addListener(changed -> PostEffects.retry());
            }
        }

        store.addExternalChangeListener((keys, sections) -> {
            skyboxes.scan();
            BloomSettings.SKY_DAY.revalidate();
            BloomSettings.SKY_NIGHT.revalidate();
            toast("Settings updated", "Changes from the MOTION launcher were applied.");
        });
        profiles.addListener((Profile p, ProfileManager.Reason reason) -> {
            if (reason == ProfileManager.Reason.BIOME || reason == ProfileManager.Reason.EXTERNAL) {
                toast("Visual profile", p.name());
            }
            if (p.hudLayout() != null) hudLayouts.setActive(p.hudLayout());
        });
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveNow, "MOTION settings save"));
        Log.info("MOTION Client " + platform.modVersion() + " ready on " + platform.loaderName());
    }

    public static void init(Platform platform) {
        if (instance != null) return;
        instance = new BloomClient(platform);
    }

    private static final java.util.Map<String, Integer> RENDER_ERRORS = new java.util.HashMap<>();

    /** Logs a rendering failure (rate-limited) instead of crashing the game. */
    public static void reportRenderError(String what, Throwable t) {
        int n = RENDER_ERRORS.merge(what, 1, Integer::sum);
        if (n == 1 || n % 1000 == 0) Log.warn("MOTION " + what + " rendering failed (" + n + "x)", t);
    }

    public static BloomClient get() {
        return instance;
    }

    public static boolean ready() {
        return instance != null;
    }

    // ------------------------------------------------------------------ accessors

    public Platform platform() {
        return platform;
    }

    public Path configDir() {
        return configDir;
    }

    public SettingsStore store() {
        return store;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public HudLayouts hudLayouts() {
        return hudLayouts;
    }

    public SkyboxLibrary skyboxes() {
        return skyboxes;
    }

    public SkyFavorites favorites() {
        return favorites;
    }

    public ParticleSystem particles() {
        return particles;
    }

    public FrameStats frames() {
        return frames;
    }

    public CpsCounter cps() {
        return cps;
    }

    public AdaptiveBudget budget() {
        return budget;
    }

    public HudRenderer hudRenderer() {
        return hud;
    }

    public GameEnv env() {
        return env;
    }

    public List<CompatReport.Note> compatNotes() {
        return compatNotes;
    }

    public boolean shaderActive() {
        return shaderActive;
    }

    public boolean hudHidden() {
        return hudHidden;
    }

    /** Sky, fog tweaks and world particles are paused while a shader pack runs (unless opted in). */
    public boolean worldEffectsPaused() {
        return shaderActive && !BloomSettings.SKY_WITH_SHADERS.on();
    }

    public boolean skyActive() {
        return BloomSettings.ENABLED.on() && !worldEffectsPaused();
    }

    // ------------------------------------------------------------------ events

    /** Every rendered frame (from the GameRenderer mixin). */
    public void onFrame() {
        frames.frame(System.nanoTime());
    }

    public void onMouseClick(int button) {
        if (button == 0 || button == 1) cps.click(button, System.currentTimeMillis());
    }

    /** End of every client tick. */
    public void tick(Minecraft mc) {
        ticks++;
        try {
            handleKeys(mc);
            store.tick();
            profiles.tick();
            if (pendingLoadProblem != null && mc.level != null) {
                toast("MOTION settings", pendingLoadProblem);
                pendingLoadProblem = null;
            }
            env.update(mc.level);
            if (mc.level == null) {
                if (hadLevel) CapeManager.clear();
                hadLevel = false;
            } else {
                hadLevel = true;
                CapeManager.tick(mc);
                dev.bloom.client.game.Auras.tick(mc);
            }
            if (mc.level != null && mc.gameRenderer != null) {
                Vec3 cam = mc.gameRenderer.getMainCamera().position();
                // Performance-friendly mode halves MOTION's particle budget.
                float factor = budget.factor() * (BloomSettings.LITE_MODE.on() ? 0.5f : 1f);
                if (!mc.isPaused()) particles.tick(env, cam.x, cam.y, cam.z, factor);
            } else {
                particles.clear();
            }
            long now = System.currentTimeMillis();
            if (now - lastSecond >= 1000) {
                lastSecond = now;
                everySecond(mc, now);
            }
        } catch (RuntimeException e) {
            // Never let a MOTION bug crash the game.
            if (ticks % 200 == 1) Log.warn("MOTION tick failed", e);
        }
    }

    private void everySecond(Minecraft mc, long now) {
        shaderActive = ShaderCompat.shaderPackInUse();
        int fps = frames.fps(System.nanoTime());
        budget.update(BloomSettings.ADAPTIVE.on(), fps, BloomSettings.ADAPTIVE_TARGET.intValue());
        if (mc.level != null) {
            profiles.onBiomeGroup(GameEnv.normalSky(mc.level) ? groupIdAtCamera() : null);
            if (BloomSettings.LOW_FPS_TIP.on()) {
                List<LowFpsAdvisor.Candidate> tip = advisor.update(now, fps, BloomSettings.LOW_FPS_THRESHOLD.intValue(),
                        lowFpsCandidates(), mc.screen == null && !mc.isPaused());
                if (tip != null) {
                    StringBuilder b = new StringBuilder("Try lowering: ");
                    for (int i = 0; i < tip.size(); i++) b.append(i > 0 ? ", " : "").append(tip.get(i).label());
                    b.append(" (Right Shift → Performance). Nothing was changed.");
                    toast("FPS is low", b.toString());
                }
            }
        }
    }

    private String groupIdAtCamera() {
        int g = env.groupAtCamera();
        return g < 0 ? null : BloomSettings.BIOME_GROUPS.get(g).id();
    }

    private List<LowFpsAdvisor.Candidate> lowFpsCandidates() {
        List<LowFpsAdvisor.Candidate> out = new ArrayList<>();
        if (!BloomSettings.ENABLED.on()) return out;
        if (BloomSettings.RAIN.on() && particles.count(ParticleSystem.RAIN) > 0) out.add(new LowFpsAdvisor.Candidate("rain.density", "rain density", 5));
        if (BloomSettings.SNOW.on()) out.add(new LowFpsAdvisor.Candidate("snow.density", "snow density", 4));
        if (BloomSettings.PETALS.on()) out.add(new LowFpsAdvisor.Candidate("petals.density", "petal density", 4));
        if (BloomSettings.LEAVES.on()) out.add(new LowFpsAdvisor.Candidate("leaves.density", "falling leaves", 3));
        if (!BloomSettings.QUALITY.is("low")) out.add(new LowFpsAdvisor.Candidate("perf.quality", "the quality preset", 3));
        if (BloomSettings.DUST.on() || BloomSettings.FIREFLIES.on()) out.add(new LowFpsAdvisor.Candidate("dust.enabled", "ambient particles", 2));
        if (BloomSettings.SKY_ENABLED.on()) out.add(new LowFpsAdvisor.Candidate("sky.enabled", "the custom skybox", 1));
        return out;
    }

    private void handleKeys(Minecraft mc) {
        FreeLook.tick(mc);
        while (BloomKeys.MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new BloomMenuScreen(null));
        }
        while (BloomKeys.VISUALS_MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(BloomMenuScreen.visuals(null));
        }
        while (BloomKeys.HUD_EDITOR.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new HudEditorScreen(null));
        }
        while (BloomKeys.TOGGLE_VISUALS.consumeClick()) {
            BloomSettings.ENABLED.toggle();
            toast("MOTION visuals", BloomSettings.ENABLED.on() ? "On" : "Off — plain Minecraft");
        }
        while (BloomKeys.TOGGLE_HUD.consumeClick()) {
            hudHidden = !hudHidden;
        }
        while (BloomKeys.NEXT_PROFILE.consumeClick()) {
            List<Profile> all = profiles.all();
            int i = 0;
            for (int j = 0; j < all.size(); j++) if (all.get(j).id().equals(profiles.selectedId())) i = j;
            Profile next = all.get((i + 1) % all.size());
            profiles.select(next.id());
            toast("Visual profile", next.name());
        }
    }

    public void renderHud(GuiGraphics g, DeltaTracker delta) {
        if (hudHidden) return;
        hud.render(g, delta);
    }

    public void renderWorld(Matrix4f modelView, Matrix4f projection, Camera camera, float partialTick) {
        EffectsRenderer.render(this, modelView, projection, camera, partialTick);
    }

    public void toast(String title, String message) {
        if (!BloomSettings.TOASTS.on()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getToastManager().addToast(new BloomToast(title, message));
        } catch (RuntimeException e) {
            Log.warn("Could not show notification", e);
        }
    }

    /** Saves settings and profiles immediately. */
    public void saveNow() {
        try {
            store.flush();
            profiles.flush();
        } catch (RuntimeException e) {
            Log.warn("Saving MOTION settings failed", e);
        }
    }

    /** Called when the world (or resource packs) changes: drop cached textures. */
    public void onResourcesReloaded() {
        SkyTextures.invalidateAll();
        PostEffects.onResourcesReloaded();
    }
}
