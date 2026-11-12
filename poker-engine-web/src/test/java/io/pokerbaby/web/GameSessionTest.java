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

    @Test
    void replayDoesNotChangeTheGameAndHidesFutureReveals() {
        playPassively();
        Map<String, Object> before = session.state();
        Map<String, Object> replay = session.replay(1);
        assertEquals(before, session.state());
        assertEquals(true, replay.get("outcomeVerified"));
        List<?> steps = (List<?>) replay.get("steps");
        assertTrue(steps.size() > 10);
        boolean revealed = false;
        for (Object item : steps) {
            Map<?, ?> step = (Map<?, ?>) item;
            Map<?, ?> view = (Map<?, ?>) step.get("state");
            Map<?, ?> event = (Map<?, ?>) step.get("event");
            if (event.get("type") != null && event.get("type").equals("showdown") && event.get("seat").equals(1)) revealed = true;
            if (event.get("type") != null && event.get("type").equals("deal_hidden")) assertFalse(event.containsKey("cards"));
            assertEquals(revealed, !((List<?>) view.get("opponentCards")).isEmpty());
            assertFalse(view.containsKey("deck"));
            assertEquals(20_000L, totalChips(view));
            for (Object fact : (List<?>) view.get("history")) {
                Map<?, ?> recorded = (Map<?, ?>) fact;
                assertFalse(recorded.get("type").equals("deal_hole") && recorded.get("seat").equals(1));
            }
        }
    }

    @Test
    void resumeRollsBackLaterHandsAndDoesNotSettleTheMatchTwice() {
        command("action", Map.of("type", "FOLD"));
        command("next", Map.of());
        assertEquals(2L, session.state().get("handNumber"));
        int decision = firstHumanDecision(session.replay(1));
        Map<String, Object> restored = command("resume", Map.of("hand", "1", "step", Integer.toString(decision)));
        assertEquals(1L, restored.get("handNumber"));
        assertEquals(false, restored.get("complete"));
        assertEquals(List.of(9950L, 9900L), restored.get("stacks"));
        assertEquals(1, ((List<?>) restored.get("hands")).size());
        command("action", Map.of("type", "FOLD"));
        assertEquals(List.of(9950L, 10050L), session.state().get("stacks"));
        Map<String, Object> next = command("next", Map.of());
        assertEquals(2L, next.get("handNumber"));
        assertEquals(20_000L, totalChips(next));
        assertEquals(1, next.get("buttonSeat"));
    }

    @Test
    void resumeRejectsInvalidOrStaleStepsWithoutMutatingTheMatch() {
        Map<String, Object> before = session.state();
        assertThrows(IllegalStateException.class, () -> command("resume", Map.of("hand", "1", "step", "0")));
        assertThrows(IllegalArgumentException.class, () -> command("resume", Map.of("hand", "1", "step", "999")));
        assertThrows(IllegalStateException.class, () -> command("resume", Map.of("hand", "999", "step", "4")));
        assertThrows(IllegalArgumentException.class, () -> command("resume", Map.of("hand", "x", "step", "4")));
        assertEquals(before, session.state());
        command("action", Map.of("type", "FOLD"));
        assertThrows(IllegalStateException.class, () -> session.command("resume", Map.of("version", "0", "hand", "1", "step", "4")));
    }

    @Test
    void oldHandsAreBoundedAndNewMatchClearsTheArchive() {
        // This opponent always calls and checks against small wagers.
        GameSession longSession = new GameSession(new Random(42), new Random(0) {
            private static final long serialVersionUID = 1L;
            @Override public double nextDouble() { return 0.5; }
        });
        for (int count = 0; count < 25; count++) {
            while (!Boolean.TRUE.equals(longSession.state().get("complete"))) {
                Map<String, Object> state = longSession.state();
                Map<?, ?> legal = (Map<?, ?>) state.get("legal");
                longSession.command("action", Map.of("version", state.get("version").toString(),
                        "type", Boolean.TRUE.equals(legal.get("check")) ? "CHECK" : "CALL"));
            }
            longSession.command("next", Map.of("version", longSession.state().get("version").toString()));
        }
        assertEquals(20, ((List<?>) longSession.state().get("hands")).size());
        assertThrows(IllegalStateException.class, () -> longSession.replay(1));
        longSession.command("new", Map.of("version", longSession.state().get("version").toString()));
        assertEquals(1, ((List<?>) longSession.state().get("hands")).size());
        assertThrows(IllegalStateException.class, () -> longSession.replay(26));
    }

    private void playPassively() {
        while (!Boolean.TRUE.equals(session.state().get("complete"))) {
            Map<?, ?> legal = (Map<?, ?>) session.state().get("legal");
            command("action", Map.of("type", Boolean.TRUE.equals(legal.get("check")) ? "CHECK" : "CALL"));
        }
    }

    private static int firstHumanDecision(Map<String, Object> replay) {
        for (Object item : (List<?>) replay.get("steps")) {
            Map<?, ?> step = (Map<?, ?>) item;
            if (Boolean.TRUE.equals(step.get("canResume"))) return (Integer) step.get("number");
        }
        throw new AssertionError("No human decision found");
    }

    private Map<String, Object> command(String command, Map<String, String> fields) {
        var input = new java.util.HashMap<>(fields);
        input.put("version", session.state().get("version").toString());
        return session.command(command, input);
    }

    private static long totalChips(Map<?, ?> state) {
        List<?> stacks = (List<?>) state.get("stacks");
        return (Long) stacks.get(0) + (Long) stacks.get(1) + (Long) state.get("pot");
    }
}
