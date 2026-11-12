package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.pokerbaby.engine.LegalActions.BetRange;

import static io.pokerbaby.engine.HandEngineTest.*;
import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class HandStateTest {
    @Test
    @DisplayName("С-2, С-8, П-9: states deeply copy collections and compare by value across branches")
    void immutableStatesAndBranches() {
        HandState initial = start(0, 10_000, 10_000);
        HandState flop = flop(0, 10_000, 10_000);
        StateInput input = new StateInput(flop);
        input.board = new ArrayList<>(flop.board());
        input.hands = new ArrayList<>(List.of(new ArrayList<>(flop.holeCards().get(0)),
                new ArrayList<>(flop.holeCards().get(1))));
        input.acted = new HashSet<>(flop.actedSeats());
        HandState copy = input.make();
        input.board.clear();
        input.hands.get(0).clear();
        input.hands.clear();
        input.acted.add(0);
        assertEquals(flop, copy);
        assertEquals(flop.hashCode(), copy.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> copy.board().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.holeCards().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.holeCards().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.actedSeats().clear());
        HandState folded = act(initial, Action.fold());
        HandState limped = act(initial, Action.call());
        HandState allIn = act(initial, Action.raiseTo(10_000));
        assertEquals(folded, act(start(0, 10_000, 10_000), Action.fold()));
        assertEquals(limped, act(start(0, 10_000, 10_000), Action.call()));
        assertEquals(allIn, act(start(0, 10_000, 10_000), Action.raiseTo(10_000)));
        assertEquals(150, initial.pot());
        assertEquals(4, initial.history().events().size());
        assertEquals(HandHistory.SCHEMA_VERSION, folded.result().orElseThrow().header().schemaVersion());
    }

    @Test
    @DisplayName("С-4, Ф-2: both views hide the deck and opponent's private events through a fold")
    void privateViewsBeforeShowdown() {
        HandState initial = start(0, 10_000, 10_000);
        List<HandState> states = List.of(initial, act(initial, Action.call()), flop(0, 10_000, 10_000),
                act(initial, Action.fold()));
        for (HandState state : states) {
            for (int seat = 0; seat < 2; seat++) {
                PlayerView view = state.viewFor(seat);
                assertEquals(seat, view.seat());
                assertEquals(state.holeCards().get(seat), view.holeCards());
                assertEquals(state.board(), view.board());
                assertTrue(view.opponentCards().isEmpty());
                assertEquals(state.config(), view.config());
                assertEquals(state.meta(), view.meta());
                assertEquals(state.buttonSeat(), view.buttonSeat());
                assertEquals(state.stacks(), view.stacks());
                assertEquals(state.streetBets(), view.streetBets());
                assertEquals(state.contributions(), view.contributions());
                assertEquals(state.pot(), view.pot());
                assertEquals(state.actingSeat(), view.actingSeat());
                assertEquals(state.lastRaiseIncrement(), view.lastRaiseIncrement());
                assertEquals(state.isComplete(), view.isComplete());
                assertEquals(state.result(), view.result());
                assertEquals(state.history().forPlayer(seat), view.history());
                assertEquals(view.history(), view.history().forPlayer(seat));
                assertEquals(view, state.viewFor(seat));
                assertEquals(view.hashCode(), state.viewFor(seat).hashCode());
                for (HandEvent event : view.history().events()) {
                    if (event instanceof HandEvent.DealHole hole) {
                        assertEquals(seat, hole.seat());
                    }
                    assertFalse(event instanceof HandEvent.Showdown);
                }
                if (view.isMyTurn()) {
                    assertEquals(HandEngine.legalActions(state), view.legalActions());
                } else {
                    assertEquals(LegalActions.none(), view.legalActions());
                }
                assertThrows(UnsupportedOperationException.class, () -> view.holeCards().clear());
                assertThrows(UnsupportedOperationException.class, () -> view.board().clear());
                assertThrows(UnsupportedOperationException.class, () -> view.history().events().clear());
            }
        }
        // The public constructor must also redact a full history passed by a caller.
        PlayerView direct = new PlayerView(0, initial.street(), initial.board(), initial.holeCards().get(0),
                initial.stacks(), initial.streetBets(), initial.contributions(), initial.actingSeat(),
                initial.lastRaiseIncrement(), HandEngine.legalActions(initial), initial.history(), initial.result());
        assertEquals(initial.viewFor(0), direct);
        assertTrue(List.of(PlayerView.class.getRecordComponents()).stream()
                .noneMatch(component -> component.getType() == Deck.class || component.getType() == HandState.class));
    }

    @Test
    @DisplayName("С-4, Р-2: showdown explicitly reveals both hands; result ranks are immutable")
    void publicShowdownViewsAndRanks() {
        HandState end = checkCallToEnd(start(0, 10_000, 10_000));
        HandResult result = end.result().orElseThrow();
        List<HandRank> ranks = new ArrayList<>(result.ranks());
        HandResult copy = new HandResult(result.header(), result.winnerSeat(), result.contributions(),
                result.returnedChips(), result.payouts(), result.finalStacks(), ranks);
        ranks.clear();
        assertEquals(result, copy);
        assertEquals(result.hashCode(), copy.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> copy.ranks().clear());
        for (int seat = 0; seat < 2; seat++) {
            PlayerView view = end.viewFor(seat);
            assertEquals(end.holeCards().get(1 - seat), view.opponentCards().orElseThrow());
            assertTrue(view.isComplete());
            assertFalse(view.isMyTurn());
            assertEquals(0, view.pot());
            assertEquals(LegalActions.none(), view.legalActions());
            assertEquals(result, view.result().orElseThrow());
            assertEquals(2, view.history().events().stream().filter(HandEvent.Showdown.class::isInstance).count());
            assertThrows(UnsupportedOperationException.class, () -> view.opponentCards().orElseThrow().clear());
        }
    }

    @Test
    @DisplayName("П-9: public state constructors reject broken card, chip, street and actor invariants")
    void rejectsInconsistentStates() {
        HandState initial = start(0, 10_000, 10_000);
        List<Consumer<StateInput>> invalid = List.of(
                input -> input.street = Street.SHOWDOWN,
                input -> input.street = Street.COMPLETE,
                input -> input.board = cards("2c"),
                input -> input.board = cards("2c 3d 4h"),
                input -> input.hands = List.of(cards("As Ah")),
                input -> input.hands = List.of(cards("As Ah"), cards("As Kh")),
                input -> input.deck = Deck.ordered(cards("As 2c 3d 4h 8s 9c")),
                input -> input.deck = Deck.ordered(cards("2c")),
                input -> input.actor = OptionalInt.empty(),
                input -> input.actor = OptionalInt.of(2),
                input -> input.increment = 0,
                input -> input.acted = Set.of(2),
                input -> input.stacks = SeatChips.of(9_949, 9_900),
                input -> input.bets = SeatChips.of(51, 100),
                input -> {
                    input.contributions = SeatChips.of(10_001, 100);
                    input.stacks = SeatChips.of(0, 9_899);
                },
                input -> {
                    input.stacks = SeatChips.of(0, 9_900);
                    input.contributions = SeatChips.of(10_000, 100);
                },
                input -> {
                    input.stacks = SeatChips.of(9_950, 0);
                    input.contributions = SeatChips.of(50, 10_000);
                    input.actor = OptionalInt.of(1);
                });
        for (Consumer<StateInput> change : invalid) {
            StateInput input = new StateInput(initial);
            change.accept(input);
            assertThrows(IllegalArgumentException.class, input::make);
        }
        StateInput covered = new StateInput(start(0, 10_000, 60));
        covered.stacks = SeatChips.of(9_940, 0);
        covered.bets = SeatChips.of(60, 60);
        covered.contributions = SeatChips.of(60, 60);
        assertThrows(IllegalArgumentException.class, covered::make);
        HandState end = checkCallToEnd(initial);
        StateInput brokenFinal = new StateInput(end);
        brokenFinal.stacks = SeatChips.of(10_000, 10_000);
        assertThrows(IllegalArgumentException.class, brokenFinal::make);
        StateInput missingBoard = new StateInput(end);
        missingBoard.board = end.board().subList(0, 3);
        assertThrows(IllegalArgumentException.class, missingBoard::make);
    }

    @Test
    @DisplayName("Д-1: legal action data rejects contradictory availability and inverted bounds")
    void validatesLegalActionData() {
        BetRange range = new BetRange(100, 200);
        assertEquals(range, new BetRange(100, 200));
        assertEquals(range.hashCode(), new BetRange(100, 200).hashCode());
        assertThrows(IllegalArgumentException.class, () -> new BetRange(0, 200));
        assertThrows(IllegalArgumentException.class, () -> new BetRange(201, 200));
        assertThrows(IllegalArgumentException.class, () -> new BetRange(100, SeatChips.MAX_TOTAL + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new LegalActions(false, false, -1, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new LegalActions(false, false, SeatChips.MAX_TOTAL + 1, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new LegalActions(true, false, 0, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new LegalActions(true, true, 1, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new LegalActions(true, false, 1, Optional.of(range), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new LegalActions(false, true, 0, Optional.of(range), Optional.of(range)));
        assertThrows(NullPointerException.class,
                () -> new LegalActions(false, false, 0, null, Optional.empty()));
        LegalActions legal = HandEngine.legalActions(start(0, 10_000, 10_000));
        assertThrows(UnsupportedOperationException.class, () -> legal.types().clear());
    }

    @Test
    @DisplayName("Р-1, С-6: result constructor checks accounting, rank outcome and allowed rank counts")
    void validatesResults() {
        HandResult result = checkCallToEnd(start(0, 10_000, 10_000)).result().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> new HandResult(result.header(), OptionalInt.of(0), result.contributions(), result.returnedChips(),
                        result.payouts(), result.finalStacks(), result.ranks()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandResult(result.header(), OptionalInt.empty(), result.contributions(), result.returnedChips(),
                        result.payouts(), result.finalStacks(), List.of()));
        for (List<HandRank> ranks : List.of(List.of(result.ranks().get(0)),
                List.of(result.ranks().get(0), result.ranks().get(1), result.ranks().get(0)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HandResult(result.header(), result.winnerSeat(), result.contributions(), result.returnedChips(),
                            result.payouts(), result.finalStacks(), ranks));
        }
        for (SeatChips contribution : List.of(SeatChips.of(0, 0), SeatChips.of(100, 200))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HandResult(result.header(), result.winnerSeat(), contribution, result.returnedChips(),
                            result.payouts(), result.finalStacks(), result.ranks()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new HandResult(result.header(), result.winnerSeat(), result.contributions(), SeatChips.of(10_000, 0),
                        result.payouts(), result.finalStacks(), result.ranks()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandResult(result.header(), result.winnerSeat(), result.contributions(), result.returnedChips(),
                        SeatChips.of(100, 100), result.finalStacks(), result.ranks()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandResult(result.header(), result.winnerSeat(), result.contributions(), result.returnedChips(),
                        result.payouts(), SeatChips.of(10_000, 10_000), result.ranks()));
        // Reserved rule for future odd pots: the off-button seat receives the odd chip.
        assertEquals(SeatChips.of(2, 3), HandResult.splitPot(5, OptionalInt.empty(), 0));
        assertEquals(SeatChips.of(3, 2), HandResult.splitPot(5, OptionalInt.empty(), 1));
    }

    private static final class StateInput {
        private Street street;
        private List<Card> board;
        private List<List<Card>> hands;
        private SeatChips stacks;
        private SeatChips bets;
        private SeatChips contributions;
        private OptionalInt actor;
        private long increment;
        private Set<Integer> acted;
        private Deck deck;
        private final HandHistory history;
        private final Optional<HandResult> result;

        private StateInput(HandState source) {
            street = source.street();
            board = source.board();
            hands = source.holeCards();
            stacks = source.stacks();
            bets = source.streetBets();
            contributions = source.contributions();
            actor = source.actingSeat();
            increment = source.lastRaiseIncrement();
            acted = source.actedSeats();
            deck = source.deck();
            history = source.history();
            result = source.result();
        }

        private HandState make() {
            return new HandState(street, board, hands, stacks, bets, contributions, actor,
                    increment, acted, deck, history, result);
        }
    }
}
