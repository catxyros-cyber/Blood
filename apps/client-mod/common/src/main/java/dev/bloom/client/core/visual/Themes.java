package dev.bloom.client.core.visual;

import java.util.List;

/**
 * The menu themes: names, what they look like, the decoration drawn behind the menus and the
 * skybox each one recommends. Colours live in gui/Theme.java. Pure Java (no Minecraft classes).
 *
 * <p>Themes and skyboxes stay independent: picking a theme only <em>offers</em> its skybox; the
 * player's skybox choice is changed only when they accept.
 */
public final class Themes {
    public enum Decor { NONE, EMBERS, PUMPKINS, MIST, BATS, WISPS, WEBS, ECLIPSE, BLOOD_MOON }

    public record Info(String id, String name, String description, Decor decor, String recommendedSky, int swatchA, int swatchB) {
    }

    public static final List<Info> ALL = List.of(
            new Info("motion", "Motion (green)", "The MOTION MOTION look: deep black with electric green. Minimal and distraction-free.", Decor.NONE, "none", 0x070A08, 0x39FF88),
            new Info("crimson", "Crimson (classic)", "The original black and crimson. Minimal and distraction-free.", Decor.NONE, "none", 0x120C0E, 0xE3192F),
            new Info("blood_moon", "Blood Moon", "Almost-black with burgundy gradients, a huge blood moon and slow red embers.", Decor.BLOOD_MOON, "fs_013_night", 0x090506, 0xD8142A),
            new Info("pumpkin_night", "Pumpkin Night", "Charcoal with burnt-orange highlights, warm floating embers and little pumpkins.", Decor.PUMPKINS, "fs_classic_night_1", 0x121110, 0xF07A1A),
            new Info("haunted_forest", "Haunted Forest", "Dark forest green and charcoal with drifting mist.", Decor.MIST, "fs_017_night", 0x0A0F0D, 0x4FB07A),
            new Info("midnight_halloween", "Midnight Halloween", "Deep purple and black, a pale moon and distant bats.", Decor.BATS, "fs_002_night", 0x0C0814, 0x9B5CFF),
            new Info("ghostfire", "Ghostfire", "Black with orange-white ghostly wisps.", Decor.WISPS, "fs_013_sunset", 0x080706, 0xFF8A3A),
            new Info("spiderweb", "Spiderweb", "Monochrome black, grey and white with fine webs in the corners.", Decor.WEBS, "fs_017_rainy", 0x0B0B0B, 0xE8E8E8),
            new Info("crimson_eclipse", "Crimson Eclipse", "Deep crimson and near-black around a dark eclipsed sun.", Decor.ECLIPSE, "fs_013_rainy", 0x100607, 0xB0101F),
            new Info("obsidian", "Obsidian", "Pure black with a vivid red accent.", Decor.NONE, null, 0x060606, 0xFF1F3D),
            new Info("ash", "Ash", "Cool grey surfaces with a red accent.", Decor.NONE, null, 0x151618, 0xDC2F3C),
            new Info("ember", "Ember", "Warm brown-black with an orange-red accent.", Decor.EMBERS, null, 0x140E0B, 0xFF4A1F),
            new Info("mono", "Monochrome", "Black and white only.", Decor.NONE, null, 0x141414, 0xE6E6E6));

    private Themes() {
    }

    public static Info get(String id) {
        for (Info i : ALL) if (i.id().equals(id)) return i;
        return ALL.get(0);
    }
}
