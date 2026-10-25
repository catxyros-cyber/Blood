package dev.bloom.client.game;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players wearing a 3D avatar: their normal model, armour and cape are hidden in the render state
 * (marked invisible, so Minecraft skips the body but still runs the layers), and AvatarLayer draws
 * the avatar instead. Purely visual; nothing about the player's hitbox or the server changes.
 */
public final class AvatarStatePatch {
    /** Render-state entity id → avatar model id, for this frame's players. */
    private static final Map<Integer, String> ACTIVE = new ConcurrentHashMap<>();

    private AvatarStatePatch() {
    }

    public static void apply(Avatar entity, AvatarRenderState state) {
        String model = null;
        try {
            if (!entity.isInvisible() && !entity.isSpectator() && !state.isSpectator) model = CapeManager.avatarFor(entity.getUUID());
            // Test hook (CI smoke test): show this avatar on the local player; the test can clear it.
            String forced = System.getProperty("motion.smoke.avatar");
            if (model == null && forced != null && !forced.isEmpty() && entity == net.minecraft.client.Minecraft.getInstance().player) model = forced;
        } catch (RuntimeException ignored) {
            // Never break player rendering over a cosmetic.
        }
        if (model == null) {
            ACTIVE.remove(state.id);
            return;
        }
        ACTIVE.put(state.id, model);
        state.isInvisible = true;
        state.headEquipment = ItemStack.EMPTY;
        state.chestEquipment = ItemStack.EMPTY;
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
        state.showCape = false;
    }

    /** The avatar to draw for this render state, or null. */
    public static String modelFor(AvatarRenderState state) {
        return ACTIVE.get(state.id);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
