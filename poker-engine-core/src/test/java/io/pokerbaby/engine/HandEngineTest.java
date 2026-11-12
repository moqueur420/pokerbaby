package io.pokerbaby.engine;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.pokerbaby.engine.IllegalActionException.Code;
import io.pokerbaby.engine.LegalActions.BetRange;

import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class HandEngineTest {
    static final HandMeta META = new HandMeta("m1-h1", List.of("human", "bot"));

    static Deck acesAgainstKings() {
        // Off-button receives aces; button receives kings, for either button seat.
        return Deck.ordered(cards("As Kd Ah Kh 2c 3d 4h 8s 9c"));
    }

    static SeatChips byPosition(int button, long onButton, long offButton) {
        return button == 0 ? SeatChips.of(onButton, offButton) : SeatChips.of(offButton, onButton);
    }

    static HandState start(int button, long onButton, long offButton) {
        return HandEngine.start(GameConfig.DEFAULT, META, byPosition(button, onButton, offButton),
                button, acesAgainstKings());
    }

    static HandState act(HandState state, Action action) {
        return HandEngine.apply(state, state.actingSeat().orElseThrow(), action);
    }

    static HandState flop(int button, long onButton, long offButton) {
        return act(act(start(button, onButton, offButton), Action.call()), Action.check());
    }

    static HandState checkCallToEnd(HandState state) {
        while (!state.isComplete()) {
            LegalActions legal = HandEngine.legalActions(state);
            state = act(state, legal.canCheck() ? Action.check() : Action.call());
        }
        return state;
    }

    static void rejected(HandState state, int seat, Action action, Code code) {
        HandHistory oldHistory = state.history();
        int oldHash = state.hashCode();
        IllegalActionException failure = assertThrows(IllegalActionException.class,
                () -> HandEngine.apply(state, seat, action));
        assertEquals(code, failure.code());
        assertSame(oldHistory, state.history());
        assertEquals(oldHash, state.hashCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("С-1, С-5, С-7: blinds, alternating deal, first decision and unchanged inputs")
    void createsFirstDecision(int button) {
        Deck deck = acesAgainstKings();
        SeatChips stacks = byPosition(button, 10_000, 10_000);
        HandState state = HandEngine.start(GameConfig.DEFAULT, META, stacks, button, deck);
        assertEquals(Street.PREFLOP, state.street());
        assertEquals(META, state.meta());
        assertEquals(GameConfig.DEFAULT, state.config());
        assertEquals(button, state.buttonSeat());
        assertEquals(OptionalInt.of(button), state.actingSeat());
        assertEquals(100, state.lastRaiseIncrement());
        assertEquals(byPosition(button, 9_950, 9_900), state.stacks());
        assertEquals(byPosition(button, 50, 100), state.streetBets());
        assertEquals(state.streetBets(), state.contributions());
        assertEquals(150, state.pot());
        assertEquals(Set.of(), state.actedSeats());
        assertTrue(state.result().isEmpty());
        assertTrue(state.board().isEmpty());
        assertEquals(cards("Kd Kh"), state.holeCards().get(button));
        assertEquals(cards("As Ah"), state.holeCards().get(1 - button));
        assertEquals(4, state.deck().position());
        assertEquals(5, state.deck().remaining());
        assertEquals(0, deck.position());
        assertEquals(9, deck.remaining());
        assertEquals(20_000, stacks.total());
        assertEquals(List.of(new HandEvent.PostBlind(button, 50), new HandEvent.PostBlind(1 - button, 100),
                new HandEvent.DealHole(0, state.holeCards().get(0)),
                new HandEvent.DealHole(1, state.holeCards().get(1))), state.history().events());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-1: button fold refunds the uncalled half of BB and pays the matched pot")
    void buttonFolds(int button) {
        HandState initial = start(button, 10_000, 10_000);
        HandState end = act(initial, Action.fold());
        HandResult result = end.result().orElseThrow();
        assertTrue(end.isComplete());
        assertEquals(Street.COMPLETE, end.street());
        assertTrue(end.actingSeat().isEmpty());
        assertEquals(0, end.pot());
        assertEquals(SeatChips.of(0, 0), end.contributions());
        assertEquals(SeatChips.of(0, 0), end.streetBets());
        assertEquals(byPosition(button, 9_950, 10_050), end.stacks());
        assertEquals(OptionalInt.of(1 - button), result.winnerSeat());
        assertFalse(result.isTie());
        assertFalse(result.wasShowdown());
        assertTrue(result.ranks().isEmpty());
        assertEquals(SeatChips.of(50, 50), result.contributions());
        assertEquals(byPosition(button, 0, 50), result.returnedChips());
        assertEquals(byPosition(button, 0, 100), result.payouts());
        assertEquals(100, result.pot());
        assertEquals(List.of(new HandEvent.ActionTaken(button, Action.fold()),
                new HandEvent.ReturnUncalled(1 - button, 50), new HandEvent.Payout(1 - button, 100)),
                end.history().events().subList(4, 7));
        assertEquals(initial, start(button, 10_000, 10_000));
        assertEquals(150, initial.pot());
        assertEquals(4, initial.history().events().size());
        assertEquals(LegalActions.none(), HandEngine.legalActions(end));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-2, Т-3, Т-11: a limp retains BB's check/raise option; then off-button opens the flop")
    void limpAndBigBlindOption(int button) {
        HandState initial = start(button, 10_000, 10_000);
        HandState limped = act(initial, Action.call());
        assertEquals(Street.PREFLOP, limped.street());
        assertEquals(OptionalInt.of(1 - button), limped.actingSeat());
        assertEquals(Set.of(button), limped.actedSeats());
        assertEquals(200, limped.pot());
        LegalActions legal = HandEngine.legalActions(limped);
        assertEquals(Set.of(ActionType.CHECK, ActionType.RAISE), legal.types());
        assertEquals(new BetRange(200, 10_000), legal.raise().orElseThrow());
        rejected(limped, 1 - button, Action.fold(), Code.ACTION_UNAVAILABLE);
        rejected(limped, 1 - button, Action.call(), Code.ACTION_UNAVAILABLE);
        rejected(limped, 1 - button, Action.betTo(100), Code.ACTION_UNAVAILABLE);
        HandState flop = act(limped, Action.check());
        assertEquals(Street.FLOP, flop.street());
        assertEquals(OptionalInt.of(1 - button), flop.actingSeat());
        assertEquals(cards("2c 3d 4h"), flop.board());
        assertEquals(200, flop.pot());
        assertEquals(SeatChips.of(100, 100), flop.contributions());
        assertEquals(SeatChips.of(0, 0), flop.streetBets());
        assertEquals(Set.of(), flop.actedSeats());
        assertEquals(new HandEvent.DealBoard(Street.FLOP, cards("2c 3d 4h")),
                flop.history().events().getLast());
        assertEquals(Set.of(ActionType.CHECK, ActionType.BET), HandEngine.legalActions(flop).types());
        assertEquals(new BetRange(100, 9_900), HandEngine.legalActions(flop).bet().orElseThrow());
        rejected(flop, 1 - button, Action.fold(), Code.ACTION_UNAVAILABLE);
        // The BB option is an actual raise even though checking is also free.
        HandState raised = act(limped, Action.raiseTo(300));
        assertEquals(OptionalInt.of(button), raised.actingSeat());
        assertEquals(200, HandEngine.legalActions(raised).callAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-4, Т-15: initial minimum 200 and maximum 10000 include the posted small blind")
    void initialRaiseBounds(int button) {
        HandState state = start(button, 10_000, 10_000);
        LegalActions legal = HandEngine.legalActions(state);
        assertEquals(Set.of(ActionType.FOLD, ActionType.CALL, ActionType.RAISE), legal.types());
        assertEquals(50, legal.callAmount());
        assertEquals(new BetRange(200, 10_000), legal.raise().orElseThrow());
        rejected(state, button, Action.raiseTo(199), Code.BELOW_MINIMUM);
        rejected(state, button, Action.raiseTo(10_001), Code.ABOVE_MAXIMUM);
        rejected(state, button, Action.raiseTo(Long.MAX_VALUE), Code.ABOVE_MAXIMUM);
        rejected(state, button, Action.check(), Code.ACTION_UNAVAILABLE);
        rejected(state, button, Action.betTo(200), Code.ACTION_UNAVAILABLE);
        HandState raised = act(state, Action.raiseTo(200));
        assertEquals(byPosition(button, 200, 100), raised.streetBets());
        assertEquals(byPosition(button, 9_800, 9_900), raised.stacks());
        assertEquals(300, raised.pot());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-5: a raise to 250 makes the next minimum 400, then 550")
    void tracksFullRaiseIncrement(int button) {
        HandState raised = act(start(button, 10_000, 10_000), Action.raiseTo(250));
        assertEquals(150, raised.lastRaiseIncrement());
        assertEquals(new BetRange(400, 10_000), HandEngine.legalActions(raised).raise().orElseThrow());
        rejected(raised, 1 - button, Action.raiseTo(399), Code.BELOW_MINIMUM);
        HandState reraised = act(raised, Action.raiseTo(400));
        assertEquals(150, reraised.lastRaiseIncrement());
        assertEquals(550, HandEngine.legalActions(reraised).raise().orElseThrow().minTo());
        assertEquals(150, HandEngine.legalActions(reraised).callAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-6: a short all-in raise to 300 is legal and leaves only fold or call 50")
    void shortAllInRaise(int button) {
        HandState raised = act(start(button, 10_000, 300), Action.raiseTo(250));
        assertEquals(new BetRange(300, 300), HandEngine.legalActions(raised).raise().orElseThrow());
        HandState allIn = act(raised, Action.raiseTo(300));
        assertEquals(150, allIn.lastRaiseIncrement());
        assertEquals(0, allIn.stacks().get(1 - button));
        assertEquals(Set.of(ActionType.FOLD, ActionType.CALL), HandEngine.legalActions(allIn).types());
        assertEquals(50, HandEngine.legalActions(allIn).callAmount());
        rejected(allIn, button, Action.raiseTo(450), Code.ACTION_UNAVAILABLE);
        HandState end = act(allIn, Action.call());
        assertTrue(end.isComplete());
        assertEquals(600, end.result().orElseThrow().pot());
        assertEquals(SeatChips.of(0, 0), end.result().orElseThrow().returnedChips());
        assertEquals(5, end.board().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-7: a 10000 shove called by 4000 refunds 6000 before running out the board")
    void refundsOversizedAllIn(int button) {
        HandState shoved = act(start(button, 10_000, 4_000), Action.raiseTo(10_000));
        assertEquals(3_900, HandEngine.legalActions(shoved).callAmount());
        assertEquals(Set.of(ActionType.FOLD, ActionType.CALL), HandEngine.legalActions(shoved).types());
        HandState end = act(shoved, Action.call());
        HandResult result = end.result().orElseThrow();
        assertEquals(8_000, result.pot());
        assertEquals(0, end.pot());
        assertEquals(SeatChips.of(4_000, 4_000), result.contributions());
        assertEquals(byPosition(button, 6_000, 0), result.returnedChips());
        assertEquals(byPosition(button, 6_000, 8_000), end.stacks());
        assertEquals(List.of("post_blind", "post_blind", "deal_hole", "deal_hole", "action", "action",
                "return_uncalled", "deal_board", "deal_board", "deal_board", "showdown", "showdown", "payout"),
                end.history().events().stream().map(HandEvent::type).toList());
        assertEquals(new HandEvent.ReturnUncalled(button, 6_000), end.history().events().get(6));
        assertEquals(new HandEvent.Showdown(0, end.holeCards().get(0),
                result.ranks().get(0).category()), end.history().events().get(10));
        assertEquals(new HandEvent.Showdown(1, end.holeCards().get(1),
                result.ranks().get(1).category()), end.history().events().get(11));
        assertEquals(0, end.deck().remaining());
        assertEquals(9, end.deck().position());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-8: a short big blind of 60 leaves a decision to fold or call 10")
    void shortBigBlindNeedsCall(int button) {
        HandState initial = start(button, 10_000, 60);
        assertFalse(initial.isComplete());
        assertEquals(byPosition(button, 50, 60), initial.contributions());
        assertEquals(OptionalInt.of(button), initial.actingSeat());
        assertEquals(Set.of(ActionType.FOLD, ActionType.CALL), HandEngine.legalActions(initial).types());
        assertEquals(10, HandEngine.legalActions(initial).callAmount());
        HandState called = act(initial, Action.call());
        assertTrue(called.isComplete());
        assertEquals(120, called.result().orElseThrow().pot());
        assertEquals(byPosition(button, 9_940, 120), called.stacks());
        HandState folded = act(initial, Action.fold());
        assertFalse(folded.result().orElseThrow().wasShowdown());
        assertEquals(byPosition(button, 9_950, 110), folded.stacks());
        assertEquals(byPosition(button, 0, 10), folded.result().orElseThrow().returnedChips());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-9: a big blind of 30 returns 20 to SB and settles automatically")
    void shortBigBlindAlreadyCovered(int button) {
        HandState end = start(button, 10_000, 30);
        assertTrue(end.isComplete());
        assertTrue(end.result().orElseThrow().wasShowdown());
        assertEquals(60, end.result().orElseThrow().pot());
        assertEquals(byPosition(button, 20, 0), end.result().orElseThrow().returnedChips());
        assertEquals(byPosition(button, 9_970, 60), end.stacks());
        assertEquals(0, end.history().events().stream().filter(HandEvent.ActionTaken.class::isInstance).count());
        assertEquals(new HandEvent.ReturnUncalled(button, 20), end.history().events().get(4));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-10: a small blind of 40 returns 60 to BB and settles automatically")
    void shortSmallBlind(int button) {
        HandState end = start(button, 40, 10_000);
        assertTrue(end.isComplete());
        assertEquals(80, end.result().orElseThrow().pot());
        assertEquals(byPosition(button, 0, 60), end.result().orElseThrow().returnedChips());
        assertEquals(byPosition(button, 0, 10_040), end.stacks());
    }

    @Test
    @DisplayName("Т-12, П-4: wrong turn, completed hand and invalid API arguments cannot change state")
    void rejectsInvalidTurnsAndArguments() {
        HandState initial = start(0, 10_000, 10_000);
        rejected(initial, 1, Action.call(), Code.NOT_YOUR_TURN);
        HandState end = act(initial, Action.fold());
        rejected(end, 0, Action.call(), Code.HAND_COMPLETE);
        rejected(end, 1, Action.check(), Code.HAND_COMPLETE);
        for (int seat : List.of(-1, 2)) {
            assertThrows(IllegalArgumentException.class, () -> HandEngine.apply(initial, seat, Action.call()));
            assertThrows(IllegalArgumentException.class, () -> initial.viewFor(seat));
            assertThrows(IllegalArgumentException.class, () -> initial.history().forPlayer(seat));
            assertThrows(IllegalArgumentException.class, () -> start(seat, 10_000, 10_000));
        }
        assertThrows(NullPointerException.class, () -> HandEngine.apply(null, 0, Action.call()));
        assertThrows(NullPointerException.class, () -> HandEngine.apply(initial, 0, null));
        assertThrows(NullPointerException.class, () -> HandEngine.legalActions(null));
        assertThrows(NullPointerException.class,
                () -> HandEngine.start(GameConfig.DEFAULT, META, SeatChips.of(100, 100), 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> HandEngine.start(GameConfig.DEFAULT, META, SeatChips.of(0, 100), 0, acesAgainstKings()));
        assertEquals(initial, start(0, 10_000, 10_000));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-13: a royal-flush board ties, splitting only matched contributions")
    void tiesReturnBothMatchedContributions(int button) {
        Deck deck = Deck.ordered(cards("2c 3d 4h 5s As Ks Qs Js Ts"));
        HandState initial = HandEngine.start(GameConfig.DEFAULT, META,
                byPosition(button, 10_000, 4_000), button, deck);
        HandState end = act(act(initial, Action.raiseTo(10_000)), Action.call());
        HandResult result = end.result().orElseThrow();
        assertTrue(result.isTie());
        assertTrue(result.wasShowdown());
        assertTrue(result.winnerSeat().isEmpty());
        assertEquals(SeatChips.of(4_000, 4_000), result.payouts());
        assertEquals(byPosition(button, 10_000, 4_000), result.finalStacks());
        assertEquals(result.finalStacks(), end.stacks());
        assertEquals(0, result.ranks().get(0).compareTo(result.ranks().get(1)));
        assertEquals(cards("As Ks Qs Js Ts"), result.ranks().get(0).cards());
        assertEquals(List.of(new HandEvent.Payout(0, 4_000), new HandEvent.Payout(1, 4_000)),
                end.history().events().subList(end.history().events().size() - 2, end.history().events().size()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Д-1, Д-5: postflop bets, raises, reset minima, turn/river and final accounting")
    void playsAllStreets(int button) {
        HandState flop = flop(button, 10_000, 10_000);
        HandState bet = act(flop, Action.betTo(300));
        assertEquals(300, bet.lastRaiseIncrement());
        assertEquals(600, HandEngine.legalActions(bet).raise().orElseThrow().minTo());
        HandState raise = act(bet, Action.raiseTo(900));
        assertEquals(600, raise.lastRaiseIncrement());
        assertEquals(1_500, HandEngine.legalActions(raise).raise().orElseThrow().minTo());
        rejected(raise, 1 - button, Action.raiseTo(1_499), Code.BELOW_MINIMUM);
        HandState turn = act(raise, Action.call());
        assertEquals(Street.TURN, turn.street());
        assertEquals(cards("2c 3d 4h 8s"), turn.board());
        assertEquals(2_000, turn.pot());
        assertEquals(100, turn.lastRaiseIncrement());
        assertEquals(OptionalInt.of(1 - button), turn.actingSeat());
        assertEquals(100, HandEngine.legalActions(turn).bet().orElseThrow().minTo());
        HandState checked = act(turn, Action.check());
        assertEquals(Street.TURN, checked.street());
        HandState turnBet = act(checked, Action.betTo(100));
        HandState river = act(turnBet, Action.call());
        assertEquals(Street.RIVER, river.street());
        assertEquals(cards("2c 3d 4h 8s 9c"), river.board());
        assertEquals(2_200, river.pot());
        HandState riverCheck = act(river, Action.check());
        assertFalse(riverCheck.isComplete());
        HandState end = act(riverCheck, Action.check());
        assertTrue(end.isComplete());
        assertTrue(end.result().orElseThrow().wasShowdown());
        assertEquals(OptionalInt.of(1 - button), end.result().orElseThrow().winnerSeat());
        assertEquals(byPosition(button, 8_900, 11_100), end.stacks());
        assertEquals(SeatChips.of(1_100, 1_100), end.result().orElseThrow().contributions());
        assertEquals(SeatChips.of(0, 0), end.result().orElseThrow().returnedChips());
        assertEquals(2_200, end.result().orElseThrow().payouts().total());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Р-3: postflop fold refunds this street's bet without returning earlier matched chips")
    void postflopFold(int button) {
        HandState bet = act(flop(button, 10_000, 10_000), Action.betTo(300));
        HandState end = act(bet, Action.fold());
        HandResult result = end.result().orElseThrow();
        assertFalse(result.wasShowdown());
        assertEquals(3, end.board().size());
        assertEquals(200, result.pot());
        assertEquals(SeatChips.of(100, 100), result.contributions());
        assertEquals(byPosition(button, 0, 300), result.returnedChips());
        assertEquals(byPosition(button, 9_900, 10_100), end.stacks());
        assertEquals(0, end.history().events().stream().filter(HandEvent.Showdown.class::isInstance).count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Д-1, Д-4: short opening all-in and short call are capped by the actual stack")
    void shortOpeningBetAndCall(int button) {
        HandState checked = act(flop(button, 150, 10_000), Action.check());
        assertEquals(new BetRange(50, 50), HandEngine.legalActions(checked).bet().orElseThrow());
        rejected(checked, button, Action.betTo(49), Code.BELOW_MINIMUM);
        rejected(checked, button, Action.betTo(51), Code.ABOVE_MAXIMUM);
        HandState shortBet = act(checked, Action.betTo(50));
        assertEquals(Set.of(ActionType.FOLD, ActionType.CALL), HandEngine.legalActions(shortBet).types());
        assertEquals(50, HandEngine.legalActions(shortBet).callAmount());
        HandState end = act(shortBet, Action.call());
        assertEquals(300, end.result().orElseThrow().pot());
        HandState shortCaller = start(button, 60, 10_000);
        assertEquals(10, HandEngine.legalActions(shortCaller).callAmount());
        assertFalse(HandEngine.legalActions(shortCaller).canRaise());
        HandState called = act(shortCaller, Action.call());
        assertEquals(120, called.result().orElseThrow().pot());
        assertEquals(byPosition(button, 0, 40), called.result().orElseThrow().returnedChips());
    }

    @Test
    @DisplayName("П-2, П-3: 2e12-chip settlements and hand-start stacks above the initial match limit")
    void largeStacks() {
        long limit = SeatChips.MAX_INITIAL_STACK;
        HandState initial = start(0, limit, limit);
        HandState end = act(act(initial, Action.raiseTo(limit)), Action.call());
        assertEquals(SeatChips.MAX_TOTAL, end.result().orElseThrow().pot());
        assertEquals(SeatChips.of(0, SeatChips.MAX_TOTAL), end.stacks());
        HandState unequal = HandEngine.start(new GameConfig(limit, limit), META,
                SeatChips.of(1, SeatChips.MAX_TOTAL - 1), 1, acesAgainstKings());
        assertTrue(unequal.isComplete());
        assertEquals(2, unequal.result().orElseThrow().pot());
        assertEquals(SeatChips.MAX_TOTAL, unequal.stacks().total());
        assertEquals(SeatChips.of(0, limit - 1), unequal.result().orElseThrow().returnedChips());
    }

    @Test
    @DisplayName("П-6, С-7: start uses the current deck cursor and requires nine remaining cards")
    void validatesRemainingDeck() {
        SeatChips stacks = SeatChips.of(10_000, 10_000);
        Deck shortDeck = Deck.ordered(cards("As Kd Ah Kh 2c 3d 4h 8s"));
        assertThrows(IllegalArgumentException.class,
                () -> HandEngine.start(GameConfig.DEFAULT, META, stacks, 0, shortDeck));
        assertThrows(IllegalArgumentException.class,
                () -> HandEngine.start(GameConfig.DEFAULT, META, stacks, 0, acesAgainstKings().draw().deck()));
        Deck full = Deck.ordered(cards("Tc As Kd Ah Kh 2c 3d 4h 8s 9c"));
        Deck advanced = full.draw().deck();
        HandState state = HandEngine.start(GameConfig.DEFAULT, META, stacks, 0, advanced);
        assertEquals(cards("As Ah"), state.holeCards().get(1));
        assertEquals(5, state.deck().position());
        assertEquals(0, full.position());
        assertEquals(1, advanced.position());
    }
}
