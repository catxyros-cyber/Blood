package dev.bloom.client.core.avatar;

import org.junit.Test;

import static org.junit.Assert.*;

public class AvatarModelsTest {
    @Test
    public void builtInsExistAndAreAboutPlayerSized() {
        assertEquals(java.util.Set.of("sahur", "tralalero", "bombardiro", "ballerina"), AvatarModels.ids());
        for (String id : AvatarModels.ids()) {
            AvatarModel m = AvatarModels.get(id);
            assertTrue(id, m.quadCount() > 40);
            float[] q = m.pose(AvatarModel.Angles.REST);
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxX = 0;
            for (int i = 0; i < q.length; i += 16) {
                for (int v = 0; v < 4; v++) {
                    minY = Math.min(minY, q[i + v * 3 + 1]);
                    maxY = Math.max(maxY, q[i + v * 3 + 1]);
                    maxX = Math.max(maxX, Math.abs(q[i + v * 3]));
                }
                float nx = q[i + 12], ny = q[i + 13], nz = q[i + 14];
                assertEquals(id + " unit normal", 1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-3);
                assertEquals(id + " opaque", 0xFF, Float.floatToRawIntBits(q[i + 15]) >>> 24);
            }
            // Feet on the ground (model space y = 1.5), head about a player's height above.
            assertEquals(id + " feet", 1.5f, maxY, 0.01f);
            float height = (maxY - minY) * 16;
            assertTrue(id + " height " + height, height > 26 && height < 36);
            assertTrue(id + " width", maxX < 1.1f);
        }
        assertNull(AvatarModels.get("nope"));
        assertNull(AvatarModels.get(null));
    }

    @Test
    public void walkingSwingsLegsInOppositionAndRestIsStill() {
        AvatarModel.Angles a = AvatarModel.Angles.walking(1.0f, 1f, 0, 0, 0, 0);
        assertEquals(-a.rightLeg(), a.leftLeg(), 1e-5);
        assertTrue(Math.abs(a.rightLeg()) > 0.1f);
        AvatarModel.Angles still = AvatarModel.Angles.walking(1.0f, 0f, 0, 0, 0, 0);
        assertEquals(0, still.rightLeg(), 1e-6);
        assertEquals(0, still.bob(), 1e-6);
        // Attacking raises the right arm well forward.
        AvatarModel.Angles hit = AvatarModel.Angles.walking(0, 0, 0, 0, 0.5f, 0);
        assertTrue(hit.rightArm() < -1f);
    }

    @Test
    public void headYawTurnsOnlyTheHead() {
        AvatarModel m = AvatarModels.get("tralalero");
        float[] rest = m.pose(AvatarModel.Angles.REST);
        float[] turned = m.pose(new AvatarModel.Angles(0, (float) Math.toRadians(60), 0, 0, 0, 0, 0));
        int changed = 0;
        for (int i = 0; i < rest.length; i++) if (Math.abs(rest[i] - turned[i]) > 1e-4) changed++;
        assertTrue(changed > 0);
        assertTrue("legs and body stay put", changed < rest.length / 2);
    }
}
