package dev.bloom.client.platform;

import java.nio.file.Path;

/** The few things that differ between Fabric/Quilt and NeoForge. */
public interface Platform {
    /** "Fabric", "Quilt" or "NeoForge". */
    String loaderName();

    boolean isModLoaded(String modId);

    /** The game's config folder (…/.minecraft/config or the instance's config folder). */
    Path configDir();

    String modVersion();
}
