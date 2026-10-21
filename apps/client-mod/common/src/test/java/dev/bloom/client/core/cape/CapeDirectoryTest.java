package dev.bloom.client.core.cape;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CapeDirectoryTest {
    private static final String A = "0f8e1b2c-3d4e-5f60-7182-93a4b5c6d7e8", A32 = "0f8e1b2c3d4e5f60718293a4b5c6d7e8";
    private static final String B = "11111111222233334444555555555555";
    private static final String CAPE = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    public void asksOnceAnswersAndExpires() {
        CapeDirectory d = new CapeDirectory();
        List<String> due = d.due(List.of(A, B, "not-a-uuid"), 0);
        assertEquals(List.of(A32, B), due);
        assertTrue("pending players aren't asked twice", d.due(List.of(A, B), 1000).isEmpty());
        d.answer(due, "{\"capes\":{\"" + A32 + "\":\"" + CAPE + "\",\"" + B + "\":\"<script>\"}}", 2000);
        assertEquals(CAPE, d.capeOf(A));
        assertNull("junk ids are ignored", d.capeOf(B));
        assertTrue(d.due(List.of(A, B), 60_000).isEmpty());
        assertEquals(2, d.due(List.of(A, B), 2000 + CapeDirectory.TTL_MS + 1).size());
    }

    @Test
    public void backsOffOnFailureBatchesAndInvalidates() {
        CapeDirectory d = new CapeDirectory();
        List<String> due = d.due(List.of(A), 0);
        d.failed(due, 0);
        assertTrue(d.due(List.of(A), 1000).isEmpty());
        assertEquals(1, d.due(List.of(A), CapeDirectory.RETRY_MS + 1).size());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) many.add(String.format("%032x", i));
        assertEquals(CapeDirectory.BATCH, new CapeDirectory().due(many, 0).size());
        d.answer(List.of(A32), "{\"capes\":{}}", 0);
        assertNull(d.capeOf(A));
        d.invalidate(A);
        assertEquals(1, d.due(List.of(A), CapeDirectory.RETRY_MS + 5).size());
    }

    @Test
    public void readsTagsAndRejectsFormattingTricks() {
        CapeDirectory d = new CapeDirectory();
        List<String> due = d.due(List.of(A, B), 0);
        d.answer(due, "{\"capes\":{},\"tags\":{\"" + A32 + "\":{\"text\":\"OWNER\",\"color\":\"#e3192f\"},\"" + B + "\":{\"text\":\"\u00a7cX\",\"color\":\"#ffffff\"}}}", 0);
        assertEquals(new CapeDirectory.Tag("OWNER", 0xE3192F), d.tagOf(A));
        assertNull(d.tagOf(B));
        assertNull(d.capeOf(A));
    }
}
