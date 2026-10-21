package dev.bloom.client.core.sky;

import dev.bloom.client.core.settings.ChoiceSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Built-in BLOOD skyboxes plus any the player imported. Built-in textures live in
 * assets/bloomclient/textures/skybox/&lt;id&gt;.png (3x2 atlas, see docs/CUSTOM_SKYBOXES.md).
 */
public final class SkyboxCatalog {
    public static final String NONE = "none";
    public static final String CUSTOM_PREFIX = "custom:";

    public record Entry(String id, String name, String description, boolean builtIn, int previewTint) {
    }

    public static final List<Entry> BUILT_IN = List.of(
            new Entry("crimson_dusk", "Crimson Dusk", "A low red sun under heavy crimson cloud. BLOOD's default day sky.", true, 0xA8243A),
            new Entry("blood_moon", "Blood Moon", "A huge red moon over a near-black sky with faint stars. BLOOD's default night sky.", true, 0x3A0712),
            new Entry("cherry_blossom", "Cherry Blossom Sky", "Warm pink dusk over a cherry grove, with soft drifting clouds.", true, 0xF7A8C4),
            new Entry("pink_sunset", "Pink Sunset", "A glowing low sun melting into rose and violet.", true, 0xF59A8C),
            new Entry("sakura_night", "Sakura Night", "Deep indigo night with a faint pink horizon and a bright starfield.", true, 0x3A2A5C),
            new Entry("starry_night", "Starry Night", "Clear dark sky with dense, crisp stars.", true, 0x141A33),
            new Entry("galaxy", "Galaxy", "A colourful band of the galaxy arching overhead.", true, 0x2B1846),
            new Entry("fantasy_clouds", "Fantasy Clouds", "Towering pastel clouds in a bright, dreamy sky.", true, 0xBFD7F5),
            new Entry("aurora", "Aurora", "Green and violet aurora curtains above a dark horizon.", true, 0x1C3B3A),
            new Entry("dreamy_pastel", "Dreamy Pastel", "Soft lavender, peach and mint gradients.", true, 0xE6CFF2),
            new Entry("clear_blue", "Clear Blue Sky", "A crisp, cloudless summer sky.", true, 0x7DB7F0)
    );

    private static final List<Entry> CUSTOM = new CopyOnWriteArrayList<>();

    private SkyboxCatalog() {
    }

    /** Replaces the list of imported skyboxes (called after scanning the skyboxes folder). */
    public static void setCustom(List<Entry> entries) {
        CUSTOM.clear();
        CUSTOM.addAll(entries);
    }

    public static List<Entry> custom() {
        return Collections.unmodifiableList(CUSTOM);
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>(BUILT_IN);
        out.addAll(CUSTOM);
        return out;
    }

    public static Entry find(String id) {
        for (Entry e : BUILT_IN) if (e.id().equals(id)) return e;
        for (Entry e : CUSTOM) if (e.id().equals(id)) return e;
        return null;
    }

    /** Dropdown options: "Vanilla sky" first, then built-ins, then imported skyboxes. */
    public static List<ChoiceSetting.Option> options() {
        List<ChoiceSetting.Option> out = new ArrayList<>();
        out.add(new ChoiceSetting.Option(NONE, "Vanilla sky"));
        for (Entry e : BUILT_IN) out.add(new ChoiceSetting.Option(e.id(), e.name()));
        for (Entry e : CUSTOM) out.add(new ChoiceSetting.Option(e.id(), e.name() + " (custom)"));
        return out;
    }
}
