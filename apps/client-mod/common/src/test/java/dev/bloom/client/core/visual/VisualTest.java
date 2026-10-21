package dev.bloom.client.core.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bloom.client.core.profile.BuiltInProfiles;
import dev.bloom.client.core.profile.Profile;
import dev.bloom.client.core.settings.BloomSettings;
import dev.bloom.client.core.settings.Setting;
import dev.bloom.client.core.sky.SkyboxCatalog;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class VisualTest {
    /** common/src/main/resources, found from the working directory (Gradle runs tests in the module dir). */
    private static Path resources() {
        for (Path p : List.of(Path.of("src/main/resources"), Path.of("common/src/main/resources"),
                Path.of("/home/claude/src/blood/apps/client-mod/common/src/main/resources"))) {
            if (Files.isDirectory(p)) return p;
        }
        throw new AssertionError("resources folder not found");
    }

    private static PostParams.Input input(boolean enabled, float contrast, boolean glow, String quality, boolean lite, boolean downgrade) {
        return new PostParams.Input(enabled, 0f, contrast, 1f, 0f, 0xFFFFFF, 0f, 0f, 0f, true, glow, 0.35f, 0.72f, true, 0f, 0f,
                quality, lite, downgrade);
    }

    // ------------------------------------------------------------------ smoothing

    @Test
    public void smootherSnapsFirstThenGlidesFrameRateIndependently() {
        Smoother a = new Smoother(1), b = new Smoother(1);
        long t = 0;
        assertEquals(0f, a.update(new float[]{0f}, 0.5f, t)[0], 0f);
        b.update(new float[]{0f}, 0.5f, t);
        // 0.5 s at 10 fps vs 0.5 s at 100 fps: same result.
        for (int i = 1; i <= 5; i++) a.update(new float[]{1f}, 0.5f, t + i * 100_000_000L);
        for (int i = 1; i <= 50; i++) b.update(new float[]{1f}, 0.5f, t + i * 10_000_000L);
        assertEquals(1 - Math.exp(-1), a.get(0), 1e-3);
        assertEquals(a.get(0), b.get(0), 1e-3);
        // A time constant of 0 snaps.
        assertEquals(1f, a.update(new float[]{1f}, 0f, t + 600_000_000L)[0], 0f);
        assertTrue(a.settled(new float[]{1f}));
        // A long stall (e.g. a loading screen) can't overshoot.
        Smoother c = new Smoother(1);
        c.update(new float[]{0f}, 0.1f, 0);
        assertTrue(c.update(new float[]{1f}, 0.1f, 60_000_000_000L)[0] <= 1f);
    }

    // ------------------------------------------------------------------ post-processing decisions

    @Test
    public void neutralSettingsCostNothing() {
        assertNull("off", PostParams.chain(input(false, 1.2f, true, "high", false, false)));
        assertNull("all neutral: the pass is skipped", PostParams.chain(input(true, 1f, false, "medium", false, false)));
        assertEquals(PostParams.Chain.GRADE, PostParams.chain(input(true, 1.1f, false, "medium", false, false)));
    }

    @Test
    public void qualityLiteAndAdaptiveChooseTheChain() {
        assertEquals(PostParams.Chain.GLOW_MEDIUM, PostParams.chain(input(true, 1f, true, "medium", false, false)));
        assertEquals(PostParams.Chain.GLOW_HIGH, PostParams.chain(input(true, 1f, true, "high", false, false)));
        assertNull("low quality never glows (and the rest is neutral)", PostParams.chain(input(true, 1f, true, "low", false, false)));
        assertEquals(PostParams.Chain.GRADE, PostParams.chain(input(true, 1.1f, true, "low", false, false)));
        assertEquals("performance mode drops glow", PostParams.Chain.GRADE, PostParams.chain(input(true, 1.1f, true, "high", true, false)));
        // Low FPS steps down one level.
        assertEquals(PostParams.Chain.GLOW_MEDIUM, PostParams.chain(input(true, 1f, true, "high", false, true)));
        assertNull(PostParams.chain(input(true, 1f, true, "medium", false, true)));
    }

    @Test
    public void uniformsFollowTheSettings() {
        PostParams.Input in = new PostParams.Input(true, 0.5f, 1.2f, 0.8f, -0.4f, 0xE3192F, 0.3f, 0.6f, 0.5f, false, true, 0.4f, 0.7f,
                true, 1f, 1f, "medium", false, false);
        float[] u = PostParams.uniforms(in, PostParams.chain(in), new float[PostParams.UNIFORMS.length]);
        assertEquals(0.5f, u[PostParams.EXPOSURE], 0f);
        assertEquals(0xE3 / 255f, u[PostParams.TINT_R], 1e-6);
        assertEquals(0x19 / 255f, u[PostParams.TINT_G], 1e-6);
        assertEquals(0.4f, u[PostParams.GLOW_STRENGTH], 0f);
        assertEquals(1f, u[PostParams.MOOD], 1e-6);
        assertEquals(0f, u[PostParams.SOFT_HIGHLIGHTS], 0f);
        // Without the glow chain, the glow uniform is 0 (the grade shader then never samples the bloom texture).
        PostParams.uniforms(in, PostParams.Chain.GRADE, u);
        assertEquals(0f, u[PostParams.GLOW_STRENGTH], 0f);
        // Performance mode drops sharpening.
        PostParams.Input lite = new PostParams.Input(true, 0f, 1f, 1f, 0f, 0, 0f, 0f, 0.8f, true, false, 0f, 0.7f, false, 0f, 0f,
                "medium", true, false);
        assertEquals(0f, PostParams.uniforms(lite, PostParams.chain(lite), u)[PostParams.SHARPEN], 0f);
        assertNull("lite + only sharpen = nothing to do", PostParams.chain(lite));
    }

    // ------------------------------------------------------------------ shader files

    private static JsonObject json(Path p) throws Exception {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    public void everyChainUsesProgramsThatExistAndDeclareTheirUniforms() throws Exception {
        Path res = resources();
        Path programs = res.resolve("assets/minecraft/shaders/program");
        for (PostParams.Chain c : PostParams.Chain.values()) {
            JsonObject chain = json(res.resolve("assets/bloomclient/shaders/post/" + c.file + ".json"));
            Set<String> targets = new HashSet<>(Set.of("minecraft:main"));
            for (JsonElement t : chain.getAsJsonArray("targets")) {
                targets.add(t.isJsonPrimitive() ? t.getAsString() : t.getAsJsonObject().get("name").getAsString());
            }
            for (JsonElement pe : chain.getAsJsonArray("passes")) {
                JsonObject pass = pe.getAsJsonObject();
                String name = pass.get("name").getAsString();
                assertTrue(c + ": unknown target", targets.contains(pass.get("intarget").getAsString()));
                assertTrue(c + ": unknown target", targets.contains(pass.get("outtarget").getAsString()));
                JsonObject prog = json(programs.resolve(name + ".json"));
                Set<String> declared = new HashSet<>();
                for (JsonElement u : prog.getAsJsonArray("uniforms")) declared.add(u.getAsJsonObject().get("name").getAsString());
                assertTrue(declared.containsAll(Set.of("ProjMat", "InSize", "OutSize")));
                if (pass.has("uniforms")) {
                    for (JsonElement u : pass.getAsJsonArray("uniforms")) {
                        assertTrue(c + "/" + name + " sets an undeclared uniform", declared.contains(u.getAsJsonObject().get("name").getAsString()));
                    }
                }
                Set<String> samplers = new HashSet<>();
                for (JsonElement sm : prog.getAsJsonArray("samplers")) samplers.add(sm.getAsJsonObject().get("name").getAsString());
                if (pass.has("auxtargets")) {
                    for (JsonElement a : pass.getAsJsonArray("auxtargets")) {
                        JsonObject ao = a.getAsJsonObject();
                        assertTrue(samplers.contains(ao.get("name").getAsString()));
                        assertTrue(targets.contains(ao.get("id").getAsString()));
                    }
                }
                String vsh = Files.readString(programs.resolve(prog.get("vertex").getAsString() + ".vsh"));
                String fsh = Files.readString(programs.resolve(prog.get("fragment").getAsString() + ".fsh"));
                assertTrue(vsh.startsWith("#version 150"));
                assertTrue(fsh.startsWith("#version 150"));
                // Every uniform the fragment shader reads must be declared (else it stays at 0).
                Matcher m = Pattern.compile("uniform\\s+(float|vec2|vec3|vec4|mat4)\\s+(\\w+)").matcher(fsh);
                while (m.find()) assertTrue(name + " doesn't declare " + m.group(2), declared.contains(m.group(2)));
                for (String sm : samplers) assertTrue(fsh.contains("uniform sampler2D " + sm));
            }
        }
        // Everything BLOOD sets each frame exists in the grade program.
        JsonObject grade = json(programs.resolve("bloodclient_grade_glow.json"));
        Set<String> declared = new HashSet<>();
        for (JsonElement u : grade.getAsJsonArray("uniforms")) declared.add(u.getAsJsonObject().get("name").getAsString());
        for (String u : PostParams.UNIFORMS) {
            if (!u.equals("GlowThreshold")) assertTrue("grade lacks " + u, declared.contains(u));
        }
        JsonArray brightU = json(programs.resolve("bloodclient_bright.json")).getAsJsonArray("uniforms");
        assertTrue(brightU.toString().contains("GlowThreshold"));
    }

    // ------------------------------------------------------------------ catalog & presets

    @Test
    public void everyBuiltInSkyboxHasItsFiles() {
        Path tex = resources().resolve("assets/bloomclient/textures");
        int sbs = 0;
        for (SkyboxCatalog.Entry e : SkyboxCatalog.BUILT_IN) {
            assertTrue(e.id(), Files.isRegularFile(tex.resolve("skybox/" + e.id() + ".png")));
            assertTrue(e.id(), Files.isRegularFile(tex.resolve("skybox/" + e.id() + ".png.mcmeta")));
            assertTrue(e.id(), Files.isRegularFile(tex.resolve("gui/sky_preview/" + e.id() + ".png")));
            if (e.id().startsWith("sbs_")) sbs++;
        }
        assertEquals("all 25 supplied skies are bundled", 25, sbs);
    }

    @Test
    public void presetsOnlyUseRealProfileSettings() {
        BloomSettings.init();
        Map<String, Profile> byId = new java.util.HashMap<>();
        for (Profile p : BuiltInProfiles.create()) byId.put(p.id(), p);
        for (String id : List.of("blood_moon", "cinematic", "competitive", "performance", "vanilla")) {
            Profile p = byId.get(id);
            assertNotNull("preset " + id, p);
            for (Map.Entry<String, JsonElement> v : p.values().entrySet()) {
                Setting s = BloomSettings.get(v.getKey());
                assertNotNull(id + ": unknown setting " + v.getKey(), s);
                assertTrue(id + ": " + v.getKey() + " isn't part of presets", s.profileScoped());
                assertTrue(id + ": bad value for " + v.getKey(), s.accepts(v.getValue()));
            }
        }
        assertEquals("Blood Red", byId.get("blood_moon").name());
        assertEquals("Clean PvP", byId.get("competitive").name());
        // Vanilla really turns BLOOD's world visuals off.
        JsonObject van = byId.get("vanilla").values();
        for (String k : List.of("post.enabled", "sky.enabled", "world.fogEnabled", "petals.enabled", "rain.enabled", "outline.enabled")) {
            assertFalse(k, van.get(k).getAsBoolean());
        }
        // Skies referenced by presets exist.
        for (Profile p : byId.values()) {
            for (String k : List.of("sky.day", "sky.night")) {
                if (p.values().has(k)) assertNotNull(p.id() + " " + k, SkyboxCatalog.find(p.values().get(k).getAsString()));
            }
        }
    }
}
