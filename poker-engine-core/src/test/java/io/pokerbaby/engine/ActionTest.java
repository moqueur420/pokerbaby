package io.pokerbaby.engine;

import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ActionTest {
    @Test
    @DisplayName("П-5: action factories preserve exact total-to amounts")
    void actionFactories() {
        assertEquals(new Action(ActionType.FOLD, OptionalLong.empty()), Action.fold());
        assertEquals(new Action(ActionType.CHECK, OptionalLong.empty()), Action.check());
        assertEquals(new Action(ActionType.CALL, OptionalLong.empty()), Action.call());
        assertEquals(new Action(ActionType.BET, OptionalLong.of(100)), Action.betTo(100));
        assertEquals(new Action(ActionType.RAISE, OptionalLong.of(250)), Action.raiseTo(250));
        assertEquals(250, Action.raiseTo(250).to().orElseThrow());
        assertEquals(ActionType.RAISE, Action.raiseTo(250).type());
        // Upper game limits belong to legalActions/apply, not to the action data type.
        assertDoesNotThrow(() -> Action.raiseTo(Long.MAX_VALUE));
    }

    @ParameterizedTest
    @EnumSource(ActionType.class)
    @DisplayName("П-5: amount presence follows action type")
    void rejectsMismatchedAmounts(ActionType type) {
        if (type == ActionType.BET || type == ActionType.RAISE) {
            assertThrows(IllegalArgumentException.class, () -> new Action(type, OptionalLong.empty()));
            assertThrows(IllegalArgumentException.class, () -> new Action(type, OptionalLong.of(0)));
            assertThrows(IllegalArgumentException.class, () -> new Action(type, OptionalLong.of(-1)));
        } else {
            for (long amount : List.of(-1L, 0L, 100L)) {
                assertThrows(IllegalArgumentException.class, () -> new Action(type, OptionalLong.of(amount)));
            }
        }
    }

    @Test
    @DisplayName("П-5: null action components are rejected")
    void nullComponents() {
        assertThrows(NullPointerException.class, () -> new Action(null, OptionalLong.empty()));
        assertThrows(NullPointerException.class, () -> new Action(ActionType.CALL, null));
    }

    @ParameterizedTest
    @EnumSource(IllegalActionException.Code.class)
    @DisplayName("Д-3: errors carry a stable machine-readable code")
    void codedFailures(IllegalActionException.Code code) {
        IllegalActionException failure = new IllegalActionException(code, "Action rejected");
        assertEquals(code, failure.code());
        assertEquals("Action rejected", failure.getMessage());
    }

    @Test
    @DisplayName("Д-3: error code and message cannot be null")
    void invalidFailures() {
        assertThrows(NullPointerException.class, () -> new IllegalActionException(null, "message"));
        assertThrows(NullPointerException.class,
                () -> new IllegalActionException(IllegalActionException.Code.HAND_COMPLETE, null));
    }
}
