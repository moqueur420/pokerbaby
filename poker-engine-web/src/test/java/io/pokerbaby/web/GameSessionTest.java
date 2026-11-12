package io.pokerbaby.web;

import java.util.List;
import java.util.Map;
import java.util.Random;

import io.pokerbaby.engine.IllegalActionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {
    private final GameSession session = new GameSession(new Random(42), new Random(7));

    @Test
    void hidesTheOpponentsCardsInBothStateAndHistory() {
        Map<String, Object> state = session.state();
        assertEquals(List.of("2s", "Jh"), state.get("holeCards"));
        assertEquals(List.of(), state.get("opponentCards"));
        assertFalse(state.containsKey("deck"));
        long privateDeals = ((List<?>) state.get("history")).stream()
                .map(item -> (Map<?, ?>) item)
                .filter(event -> event.get("type").equals("deal_hole"))
                .peek(event -> assertEquals(0, event.get("seat")))
                .count();
        assertEquals(1, privateDeals);
    }

    @Test
    void foldSettlesTheHandAndNextHandTransfersStacksAndButton() {
        Map<String, Object> settled = command("action", Map.of("type", "FOLD"));
        assertEquals(true, settled.get("complete"));
        assertEquals(List.of(9_950L, 10_050L), settled.get("stacks"));
        assertEquals(List.of(), settled.get("opponentCards"));
        Map<String, Object> next = command("next", Map.of());
        assertEquals(2L, next.get("handNumber"));
        assertEquals(1, next.get("buttonSeat"));
        assertEquals(20_000L, totalChips(next));
    }

    @Test
    void illegalAndStaleActionsDoNotChangeTheState() {
        Map<String, Object> before = session.state();
        assertThrows(IllegalActionException.class, () -> command("action", Map.of("type", "CHECK")));
        assertEquals(before, session.state());
        assertThrows(IllegalActionException.class, () -> command("action", Map.of("type", "RAISE", "to", "199")));
        assertEquals(before, session.state());
        command("action", Map.of("type", "CALL"));
        Map<String, Object> after = session.state();
        assertThrows(IllegalStateException.class, () -> session.command("action", Map.of("type", "CALL", "version", "0")));
        assertEquals(after, session.state());
    }

    @Test
    void cannotSkipAnActiveHandAndNewMatchRestoresTheStacks() {
        assertThrows(IllegalStateException.class, () -> command("next", Map.of()));
        command("action", Map.of("type", "FOLD"));
        Map<String, Object> reset = command("new", Map.of());
        assertEquals(1L, reset.get("handNumber"));
        assertEquals(0, reset.get("buttonSeat"));
        assertEquals(List.of(9_950L, 9_900L), reset.get("stacks"));
        assertEquals(150L, reset.get("pot"));
        assertEquals(2L, reset.get("version"));
    }

    @Test
    void passivePlayReachesShowdownWithPublicCardsAndCorrectSettlement() {
        Map<String, Object> state = session.state();
        for (int count = 0; !Boolean.TRUE.equals(state.get("complete")) && count < 100; count++) {
            Map<?, ?> legal = (Map<?, ?>) state.get("legal");
            state = command("action", Map.of("type", Boolean.TRUE.equals(legal.get("check")) ? "CHECK" : "CALL"));
            assertEquals(20_000L, totalChips(state));
        }
        assertEquals(true, state.get("complete"));
        assertEquals(5, ((List<?>) state.get("board")).size());
        assertEquals(List.of("4d", "Th"), state.get("opponentCards"));
        assertEquals(true, ((Map<?, ?>) state.get("result")).get("showdown"));
        assertEquals(0L, state.get("pot"));
    }

    @Test
    void repeatedAllInsFinishAMatchWithoutLosingChips() {
        Map<String, Object> state = session.state();
        for (int count = 0; !Boolean.TRUE.equals(state.get("matchOver")) && count < 500; count++) {
            if (Boolean.TRUE.equals(state.get("complete"))) {
                state = command("next", Map.of());
            } else {
                Map<?, ?> legal = (Map<?, ?>) state.get("legal");
                Map<?, ?> bet = (Map<?, ?>) legal.get("bet");
                Map<?, ?> raise = (Map<?, ?>) legal.get("raise");
                if (!raise.isEmpty() || !bet.isEmpty()) {
                    Map<?, ?> range = raise.isEmpty() ? bet : raise;
                    state = command("action", Map.of("type", raise.isEmpty() ? "BET" : "RAISE", "to", range.get("max").toString()));
                } else {
                    state = command("action", Map.of("type", Boolean.TRUE.equals(legal.get("check")) ? "CHECK" : "CALL"));
                }
            }
            assertEquals(20_000L, totalChips(state));
        }
        assertTrue(Boolean.TRUE.equals(state.get("matchOver")));
        assertEquals(true, state.get("complete"));
        assertThrows(IllegalStateException.class, () -> command("next", Map.of()));
    }

    private Map<String, Object> command(String command, Map<String, String> fields) {
        var input = new java.util.HashMap<>(fields);
        input.put("version", session.state().get("version").toString());
        return session.command(command, input);
    }

    private static long totalChips(Map<String, Object> state) {
        List<?> stacks = (List<?>) state.get("stacks");
        return (Long) stacks.get(0) + (Long) stacks.get(1) + (Long) state.get("pot");
    }
}
