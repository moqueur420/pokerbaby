package io.pokerbaby.engine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldenHistoryTest {
    static final String HEADER = "# pokerbaby hands-v1; baseline=5f1f103; seed=20261114; hands=10000";
    private static final String SHA256 = "341e51fff3cc6555381fdbd220a8f04f9ac86d81914ea9ca3b61db4e743649a9";

    @Test
    @DisplayName("Optimization stage 0: 10000 immutable baseline histories and final stacks")
    void matchesOriginalHistoriesByteForByte() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        var resource = GoldenHistoryTest.class.getResourceAsStream("/golden/hands-v1.txt");
        assertNotNull(resource, "The original golden corpus must be present; never regenerate it to fix a failure");
        try (var input = new DigestInputStream(resource, digest);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            assertEquals(HEADER, reader.readLine());
            for (int index = 0; index < SeededHands.GOLDEN_HANDS; index++) {
                assertEquals(reader.readLine(), SeededHands.play(index).goldenLine(),
                        "Golden history mismatch: hand=" + index + ", seed=" + (SeededHands.SEED + index));
            }
            assertNull(reader.readLine(), "Unexpected extra hands in the frozen corpus");
        }
        assertEquals(SHA256, HexFormat.of().formatHex(digest.digest()),
                "The golden resource from 5f1f103 must remain byte-for-byte unchanged");
    }
}
