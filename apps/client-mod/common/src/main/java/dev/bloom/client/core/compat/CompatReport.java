package dev.bloom.client.core.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Known interactions with other mods, shown on the menu's General page and logged at start.
 * BLOOD doesn't claim to work with everything: these are the cases it knows about.
 */
public final class CompatReport {
    public enum Level { OK, INFO, WARNING }

    public record Note(String name, Level level, String message) {
    }

    private record Rule(String[] ids, String name, Level level, String message) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(new String[]{"sodium", "embeddium"}, "Sodium", Level.OK,
                    "Supported. BLOOD's sky, fog and particles work alongside Sodium."),
            new Rule(new String[]{"iris", "oculus"}, "Iris shaders", Level.INFO,
                    "Shader packs draw their own sky and fog. While a shader pack is on, BLOOD pauses its skybox, sky tint and fog changes unless you enable \"Use with shader packs\". Particles and the HUD keep working."),
            new Rule(new String[]{"optifabric"}, "OptiFine (OptiFabric)", Level.WARNING,
                    "OptiFine through OptiFabric is not supported. BLOOD may not render correctly; use Sodium + Iris instead."),
            new Rule(new String[]{"fabricskyboxes", "nuit"}, "FabricSkyBoxes / Nuit", Level.WARNING,
                    "Another custom-sky mod is installed. When it has a skybox active it replaces BLOOD's. Turn one of them off to choose."),
            new Rule(new String[]{"particlerain"}, "Particle Rain", Level.WARNING,
                    "Particle Rain also adds rain and snow particles. Turn off BLOOD's rain/snow or Particle Rain's to avoid doubled weather."),
            new Rule(new String[]{"sodium-extra", "sodiumextra"}, "Sodium Extra", Level.INFO,
                    "Sodium Extra's sky, cloud and fog toggles can hide BLOOD's skybox or override its fog. Leave them on to see BLOOD's."),
            new Rule(new String[]{"distanthorizons"}, "Distant Horizons", Level.INFO,
                    "BLOOD's fog distance setting doesn't apply to Distant Horizons' far terrain."),
            new Rule(new String[]{"betterclouds"}, "Better Clouds", Level.INFO,
                    "BLOOD's \"Clouds\" toggle hides vanilla clouds only; use Better Clouds' own settings for its clouds.")
    );

    private CompatReport() {
    }

    public static List<Note> evaluate(Predicate<String> isLoaded) {
        List<Note> out = new ArrayList<>();
        for (Rule r : RULES) {
            for (String id : r.ids()) {
                if (isLoaded.test(id)) {
                    out.add(new Note(r.name(), r.level(), r.message()));
                    break;
                }
            }
        }
        return out;
    }

    public static boolean otherSkyModPresent(Predicate<String> isLoaded) {
        return isLoaded.test("fabricskyboxes") || isLoaded.test("nuit");
    }
}
