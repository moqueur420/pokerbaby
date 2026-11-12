package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayTypesTest {
    @Test
    @DisplayName("П-9, И-7: replay snapshots deeply copy collections and compare by value")
    void immutableReplayValues() {
        ReplayResult replay = sample();
        assertEquals(replay, sample());
        assertEquals(replay.hashCode(), sample().hashCode());
        HandSnapshot source = replay.decisions().get(replay.decisions().size() - 1).snapshot();
        var board = new ArrayList<>(source.board());
        List<List<Card>> hands = new ArrayList<>(source.holeCards().stream().map(ArrayList::new).toList());
        var acted = new HashSet<>(source.actedSeats());
        var ranks = new ArrayList<>(source.ranks());
        HandSnapshot copy = new HandSnapshot(source.history(), source.street(), board, hands, source.stacks(),
                source.streetBets(), source.contributions(), source.actingSeat(), source.lastRaiseIncrement(),
                acted, source.decisionPoint(), source.payouts(), ranks);
        board.clear();
        hands.get(0).clear();
        hands.clear();
        acted.clear();
        ranks.clear();
        assertEquals(source, copy);
        assertThrows(UnsupportedOperationException.class, () -> copy.board().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.holeCards().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.actedSeats().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.ranks().clear());
        assertThrows(UnsupportedOperationException.class, () -> replay.steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> replay.decisions().clear());
        assertThrows(IllegalArgumentException.class, () -> copy.forPlayer(2));
    }

    @Test
    @DisplayName("П-4, П-8: replay value constructors reject inconsistent steps and decisions")
    void invalidReplayValues() {
        ReplayResult replay = sample();
        ReplayStep initial = replay.steps().get(0);
        HandSnapshot decision = replay.decisions().get(0).snapshot();
        assertThrows(IllegalArgumentException.class, () -> new ReplayStep(-1, Optional.empty(), initial.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> new ReplayStep(1, Optional.empty(), initial.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> new Decision(initial.snapshot(), new HandEvent.ActionTaken(0, Action.call())));
        assertThrows(IllegalArgumentException.class, () -> new Decision(decision, new HandEvent.ActionTaken(1, Action.call())));
        assertThrows(IllegalArgumentException.class, () -> new ReplayResult(List.of(), List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new ReplayResult(List.of(initial, replay.steps().get(2)), List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new ReplayResult(List.of(initial), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new ReplayException(0, "invalid"));
        assertEquals(7, new ReplayException(7, "invalid").eventNumber());
        assertThrows(NullPointerException.class, () -> HandReplayer.replay(null));
        assertThrows(IllegalArgumentException.class, () -> new HandSnapshot(decision.history(), Street.FLOP,
                List.of(), decision.holeCards(), decision.stacks(), decision.streetBets(), decision.contributions(),
                decision.actingSeat(), decision.lastRaiseIncrement(), decision.actedSeats(), true, decision.payouts(), decision.ranks()));
        assertThrows(IllegalArgumentException.class, () -> new HandSnapshot(decision.history(), decision.street(),
                decision.board(), List.of(List.of()), decision.stacks(), decision.streetBets(), decision.contributions(),
                decision.actingSeat(), decision.lastRaiseIncrement(), decision.actedSeats(), true, decision.payouts(), decision.ranks()));
        assertThrows(IllegalArgumentException.class, () -> new HandSnapshot(decision.history(), decision.street(),
                decision.board(), decision.holeCards(), decision.stacks(), SeatChips.of(1000, 0), decision.contributions(),
                OptionalInt.empty(), decision.lastRaiseIncrement(), decision.actedSeats(), false, decision.payouts(), decision.ranks()));
    }

    private static ReplayResult sample() {
        HandState state = HandEngine.start(GameConfig.DEFAULT, new HandMeta("immutable", List.of("human", "bot")),
                SeatChips.of(10_000, 10_000), 0, Deck.shuffled(42));
        while (!state.isComplete()) {
            state = HandEngine.apply(state, state.actingSeat().orElseThrow(),
                    HandEngine.legalActions(state).canCheck() ? Action.check() : Action.call());
        }
        return HandReplayer.replay(state.history());
    }
}
