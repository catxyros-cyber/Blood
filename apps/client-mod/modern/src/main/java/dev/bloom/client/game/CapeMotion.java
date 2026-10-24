package dev.bloom.client.game;

import dev.bloom.client.core.settings.BloomSettings;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth cape motion. Minecraft sets a cape's angles straight from the player's speed every
 * frame, so it snaps and can swing almost flat (up to 150°). Here each player's cape eases
 * toward those angles over about a sixth of a second, and how far it swings back is scaled by
 * "Cape sway": standing still it hangs, walking it drifts back a little, sprinting a bit more.
 *
 * <p>Called from AvatarRenderer#extractCapeState (render thread only), for every cape drawn.
 */
public final class CapeMotion {
    /** Hard limit for how far back a cape can lean (Minecraft allows 150°). */
    private static final float MAX_LEAN = 85f;
    /** Response speed: higher follows faster. 7/s settles in ~0.4 s. */
    private static final float RATE = 7f;

    private static final class State {
        float flap, lean, lean2;
        long at;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long lastPrune;

    private CapeMotion() {
    }

    public static void apply(AvatarRenderState st) {
        if (!BloomSettings.CAPE_SMOOTH.on()) return;
        float sway = BloomSettings.CAPE_SWAY.value();
        float tLean = Math.min(st.capeLean * sway, MAX_LEAN);
        float tFlap = st.capeFlap * (0.4f + 0.6f * sway);
        float tLean2 = st.capeLean2 * sway;

        long now = System.nanoTime();
        State s = STATES.get(st.id);
        if (s == null || now - s.at > 500_000_000L) {
            // New (or not seen for a while): start where it should be, no swoop-in.
            if (s == null) STATES.put(st.id, s = new State());
            s.flap = tFlap;
            s.lean = tLean;
            s.lean2 = tLean2;
        } else {
            float dt = Math.min(0.1f, (now - s.at) / 1e9f);
            float a = 1f - (float) Math.exp(-dt * RATE);
            s.flap += (tFlap - s.flap) * a;
            s.lean += (tLean - s.lean) * a;
            s.lean2 += (tLean2 - s.lean2) * a;
        }
        s.at = now;
        st.capeFlap = s.flap;
        st.capeLean = s.lean;
        st.capeLean2 = s.lean2;

        if (now - lastPrune > 5_000_000_000L) {
            lastPrune = now;
            STATES.values().removeIf(x -> now - x.at > 10_000_000_000L);
        }
    }

    /** Leaving a world: forget everything. */
    public static void clear() {
        STATES.clear();
    }
}
