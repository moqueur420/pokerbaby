package io.pokerbaby.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class HandMetaTest {
    @Test
    @DisplayName("П-8, П-9, Н-1: caller metadata is immutable and value-based")
    void preservesCallerMetadata() {
        List<String> players = new ArrayList<>(List.of("human", "bot"));
        Instant time = Instant.parse("2026-11-12T12:00:00Z");
        HandMeta meta = new HandMeta("m1-h1", players, Optional.of(time));
        players.clear();
        assertEquals("m1-h1", meta.handId());
        assertEquals("human", meta.playerId(0));
        assertEquals("bot", meta.playerId(1));
        assertEquals(Optional.of(time), meta.startedAt());
        assertEquals(meta, new HandMeta("m1-h1", List.of("human", "bot"), Optional.of(time)));
        assertThrows(UnsupportedOperationException.class, () -> meta.playerIds().clear());
        assertTrue(new HandMeta("m1-h1", List.of("human", "bot")).startedAt().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> meta.playerId(-1));
        assertThrows(IllegalArgumentException.class, () -> meta.playerId(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("П-8: identifiers must not be blank")
    void rejectsBlankIds(String blank) {
        assertThrows(IllegalArgumentException.class, () -> new HandMeta(blank, List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> new HandMeta("h1", List.of(blank, "b")));
    }

    @Test
    @DisplayName("П-8: missing, repeated, or wrong-size player metadata is rejected")
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new HandMeta("h", List.of("a", "a")));
        assertThrows(IllegalArgumentException.class, () -> new HandMeta("h", List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> new HandMeta("h", List.of("a", "b", "c")));
        assertThrows(NullPointerException.class, () -> new HandMeta(null, List.of("a", "b")));
        assertThrows(NullPointerException.class, () -> new HandMeta("h", null));
        assertThrows(NullPointerException.class, () -> new HandMeta("h", Arrays.asList("a", null)));
        assertThrows(NullPointerException.class, () -> new HandMeta("h", List.of("a", "b"), null));
    }
}
