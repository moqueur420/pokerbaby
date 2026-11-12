package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SeatChipsTest {
    @Test
    @DisplayName("П-2, П-3: current balances include zero and preserve exact long totals")
    void representsBalancesAndTheirSum() {
        SeatChips chips = SeatChips.of(0, 2_000_000_000_000L);
        assertEquals(0, chips.get(0));
        assertEquals(2_000_000_000_000L, chips.get(1));
        assertEquals(2_000_000_000_000L, chips.total());
        assertEquals(0, SeatChips.of(0, 0).total());
    }

    @Test
    @DisplayName("П-9: input copying, replacement, and value equality")
    void immutableValues() {
        List<Long> input = new ArrayList<>(List.of(10_000L, 20_000L));
        SeatChips chips = SeatChips.copyOf(input);
        input.set(0, 1L);
        assertEquals(10_000, chips.get(0));
        assertThrows(UnsupportedOperationException.class, () -> chips.values().set(0, 1L));
        SeatChips updated = chips.with(1, 0);
        assertEquals(20_000, chips.get(1));
        assertEquals(0, updated.get(1));
        assertEquals(10_000, updated.total());
        assertEquals(chips, chips);
        assertEquals(chips, SeatChips.of(10_000, 20_000));
        assertEquals(chips.hashCode(), SeatChips.of(10_000, 20_000).hashCode());
        assertNotEquals(chips, updated);
        assertNotEquals(chips, null);
        assertNotEquals(chips, chips.values());
        assertEquals("[10000, 20000]", chips.toString());
        assertThrows(IllegalArgumentException.class, () -> chips.with(0, -1));
    }

    @Test
    @DisplayName("П-2: match and hand start have different stack limits")
    void validatesContextualStartingLimits() {
        assertDoesNotThrow(() -> SeatChips.of(1, 1).validateMatchStart());
        assertDoesNotThrow(() -> SeatChips.of(1_000_000_000_000L, 1_000_000_000_000L).validateMatchStart());
        assertDoesNotThrow(() -> SeatChips.of(1, 1_999_999_999_999L).validateHandStart());
        assertThrows(IllegalArgumentException.class,
                () -> SeatChips.of(1, 1_000_000_000_001L).validateMatchStart());
        assertThrows(IllegalArgumentException.class,
                () -> SeatChips.of(1_000_000_000_001L, 1).validateMatchStart());
        for (SeatChips zeroStack : List.of(SeatChips.of(0, 100), SeatChips.of(100, 0))) {
            assertThrows(IllegalArgumentException.class, zeroStack::validateMatchStart);
            assertThrows(IllegalArgumentException.class, zeroStack::validateHandStart);
        }
    }

    @Test
    @DisplayName("П-2, П-3: resume checks stacks and contributions together")
    void validatesSupplyWhenResuming() {
        assertDoesNotThrow(() -> SeatChips.of(0, 0).validateResume(SeatChips.of(100, 100)));
        assertDoesNotThrow(() -> SeatChips.of(1, 1).validateResume(SeatChips.of(999_999_999_999L, 999_999_999_999L)));
        assertThrows(IllegalArgumentException.class,
                () -> SeatChips.of(1, 1).validateResume(SeatChips.of(1_000_000_000_000L, 1_000_000_000_000L)));
        assertThrows(NullPointerException.class, () -> SeatChips.of(1, 1).validateResume(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, Integer.MAX_VALUE})
    @DisplayName("П-4: invalid seats are rejected consistently")
    void rejectsInvalidSeat(int seat) {
        SeatChips chips = SeatChips.of(50, 100);
        assertThrows(IllegalArgumentException.class, () -> chips.get(seat));
        assertThrows(IllegalArgumentException.class, () -> chips.with(seat, 1));
    }

    @Test
    @DisplayName("П-2, П-3, П-9: invalid sizes, nulls, negatives, and enormous values are rejected")
    void rejectsInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> SeatChips.copyOf(List.of()));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.copyOf(List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.copyOf(List.of(1L, 2L, 3L)));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.of(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.of(1, -1));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.of(Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.of(1, Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> SeatChips.of(2_000_000_000_000L, 1));
        assertThrows(NullPointerException.class, () -> SeatChips.copyOf(null));
        assertThrows(NullPointerException.class, () -> SeatChips.copyOf(Arrays.asList(1L, null)));
    }
}
