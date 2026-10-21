package dev.bloom.client.core.settings;

/** Enumerated option types used by BLOOD's settings. */
public final class Opts {
    private Opts() {}

    public enum Theme implements Labeled {
        CHERRY("Cherry Blossom", 0xFFF29BC1, 0xFF8FD694),
        FOREST("Forest", 0xFF6FCF8B, 0xFFE3C77B),
        LAVENDER("Lavender", 0xFFB79CF2, 0xFFF29BC1),
        OCEAN("Ocean", 0xFF7CC4F2, 0xFF8FE3D2),
        MONO("Monochrome", 0xFFE6E6E6, 0xFF9A9A9A);

        private final String label;
        /** Main accent colour (ARGB). */
        public final int accent;
        /** Secondary accent used for highlights. */
        public final int accent2;

        Theme(String label, int accent, int accent2) {
            this.label = label;
            this.accent = accent;
            this.accent2 = accent2;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum EffectsLevel implements Labeled {
        FULL("Full", 1f),
        REDUCED("Reduced", 0.5f),
        PERFORMANCE("Performance", 0f);

        private final String label;
        /** Multiplier applied to decorative particle budgets. */
        public final float particleScale;

        EffectsLevel(String label, float particleScale) {
            this.label = label;
            this.particleScale = particleScale;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum Quality implements Labeled {
        LOW("Low", 500, 16, false),
        MEDIUM("Medium", 1400, 24, true),
        HIGH("High", 3000, 32, true),
        CUSTOM("Custom", -1, -1, true);

        private final String label;
        public final int maxParticles;
        public final int radius;
        public final boolean splashes;

        Quality(String label, int maxParticles, int radius, boolean splashes) {
            this.label = label;
            this.maxParticles = maxParticles;
            this.radius = radius;
            this.splashes = splashes;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum RainMode implements Labeled {
        OFF("Off"),
        FOLLOW_WEATHER("When it rains"),
        ALWAYS("Always");

        private final String label;

        RainMode(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum SnowMode implements Labeled {
        OFF("Off"),
        FOLLOW_WEATHER("When it snows"),
        COLD_BIOMES("In cold biomes"),
        ALWAYS("Always");

        private final String label;

        SnowMode(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum SnowIntensity implements Labeled {
        LIGHT("Light snowfall", 1f, 1f),
        HEAVY("Heavy snowfall", 2.4f, 1.35f);

        private final String label;
        public final float densityScale;
        public final float speedScale;

        SnowIntensity(String label, float densityScale, float speedScale) {
            this.label = label;
            this.densityScale = densityScale;
            this.speedScale = speedScale;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum FlakeStyle implements Labeled {
        SOFT("Soft"),
        CRYSTAL("Crystal"),
        MIXED("Mixed");

        private final String label;

        FlakeStyle(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum PetalArea implements Labeled {
        EVERYWHERE("Everywhere"),
        CHERRY_GROVES("Cherry groves only"),
        SELECTED_BIOMES("Selected biomes");

        private final String label;

        PetalArea(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum PetalShape implements Labeled {
        CLASSIC("Classic petal"),
        ROUND("Round"),
        HEART("Heart"),
        MIXED("Mixed");

        private final String label;

        PetalShape(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum FogTint implements Labeled {
        OFF("Vanilla"),
        MATCH_SKY("Match the sky"),
        CUSTOM("Custom colour");

        private final String label;

        FogTint(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum SkyResolution implements Labeled {
        LOW("256 px", 256),
        MEDIUM("512 px", 512),
        HIGH("1024 px", 1024);

        private final String label;
        public final int size;

        SkyResolution(String label, int size) {
            this.label = label;
            this.size = size;
        }

        @Override
        public String label() {
            return label;
        }
    }
}
