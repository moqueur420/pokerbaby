package io.pokerbaby.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GameConfigTest {
    @Test
    @DisplayName("П-1, П-2, В-4: default and boundary blinds")
    void validConfigurations() {
        assertEquals(new GameConfig(50, 100), GameConfig.DEFAULT);
        assertEquals(50, GameConfig.DEFAULT.smallBlind());
        assertEquals(100, GameConfig.DEFAULT.bigBlind());
        assertDoesNotThrow(() -> new GameConfig(1, 1));
        assertDoesNotThrow(() -> new GameConfig(1_000_000_000_000L, 1_000_000_000_000L));
        assertEquals(10_000, SeatChips.DEFAULT_STACK);
    }

    @ParameterizedTest
    @CsvSource({"0,100", "-1,100", "50,49", "1,0", "1,1000000000001", "9223372036854775807,9223372036854775807"})
    @DisplayName("П-1, П-2: invalid blind configurations fail immediately")
    void invalidConfigurations(long small, long big) {
        assertThrows(IllegalArgumentException.class, () -> new GameConfig(small, big));
    }
}
