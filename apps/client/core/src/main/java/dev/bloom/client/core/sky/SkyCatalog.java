package dev.bloom.client.core.sky;

import dev.bloom.client.core.settings.ChoiceSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The skies a player can choose from: BLOOD's built-ins, "vanilla", and imported custom skies. */
public final class SkyCatalog {
    public static final String VANILLA = "vanilla";
    public static final String CUSTOM_PREFIX = "custom/";

    public record Entry(String id, String label, String description) {}

    public static final List<Entry> BUILT_IN = List.of(
            new Entry("cherry_blossom", "Cherry Blossom Sky", "Soft pink daylight with a warm, glowing horizon."),
            new Entry("pink_sunset", "Pink Sunset", "A low golden-pink sunset with layered clouds."),
            new Entry("sakura_night", "Sakura Night", "Deep plum night with a rosy horizon and stars."),
            new Entry("starry_night", "Starry Night", "Clear dark-blue night packed with stars."),
            new Entry("galaxy", "Galaxy", "A bright galactic band with nebula colour."),
            new Entry("fantasy_clouds", "Fantasy Clouds", "Big, fluffy pastel clouds on a bright sky."),
            new Entry("aurora", "Aurora", "Green and violet aurora curtains over a night sky."),
            new Entry("dreamy_pastel", "Dreamy Pastel", "A gentle lilac-to-peach gradient."),
            new Entry("clear_blue", "Clear Blue Sky", "A crisp, cloudless blue sky."));

    private static final List<Entry> custom = new CopyOnWriteArrayList<>();

    private SkyCatalog() {}

    public static boolean isBuiltIn(String id) {
        for (Entry e : BUILT_IN) if (e.id().equals(id)) return true;
        return false;
    }

    public static Entry find(String id) {
        for (Entry e : BUILT_IN) if (e.id().equals(id)) return e;
        for (Entry e : custom) if (e.id().equals(id)) return e;
        return null;
    }

    /** Replace the list of imported skies (called after scanning the skies folder). */
    public static void setCustom(List<Entry> entries) {
        custom.clear();
        custom.addAll(entries);
    }

    public static List<Entry> custom() {
        return List.copyOf(custom);
    }

    public static List<ChoiceSetting.Option> options() {
        List<ChoiceSetting.Option> out = new ArrayList<>();
        out.add(new ChoiceSetting.Option(VANILLA, "Vanilla Minecraft sky"));
        for (Entry e : BUILT_IN) out.add(new ChoiceSetting.Option(e.id(), e.label()));
        for (Entry e : custom) out.add(new ChoiceSetting.Option(e.id(), e.label() + " (custom)"));
        return out;
    }
}
