package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class HandReplayerTest {
    @Test
    @DisplayName("И-2, И-3: an empty history has exactly the initial deckless snapshot")
    void initialStep() {
        HandHistory history = start(42, 10_000, 10_000, 0).history();
        ReplayResult replay = HandReplayer.replay(new HandHistory(history.header(), List.of()));
        assertEquals(1, replay.steps().size());
        assertEquals(Optional.empty(), replay.steps().get(0).event());
        assertEquals(SeatChips.of(10_000, 10_000), replay.lastSnapshot().stacks());
        assertEquals(List.of(List.of(), List.of()), replay.lastSnapshot().holeCards());
        assertFalse(replay.lastSnapshot().decisionPoint());
        assertFalse(replay.outcomeVerified());
        assertEquals(LegalActions.none(), replay.lastSnapshot().legalActions());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("И-3, И-6, И-7: random histories match live decisions and resume with the original deck")
    void agreesWithLivePlayAndResumes(int button) {
        Random random = new Random(420 + button);
        for (int trial = 0; trial < 120; trial++) {
            HandState state = start(random.nextLong(), 1 + random.nextInt(15_000), 1 + random.nextInt(15_000), button);
            List<HandState> states = new ArrayList<>(List.of(state));
            List<HandEvent.ActionTaken> actions = new ArrayList<>();
            while (!state.isComplete()) {
                assertTrue(actions.size() < 500);
                LegalActions legal = HandEngine.legalActions(state);
                List<ActionType> types = new ArrayList<>(legal.types());
                Action action = switch (types.get(random.nextInt(types.size()))) {
                    case FOLD -> Action.fold();
                    case CHECK -> Action.check();
                    case CALL -> Action.call();
                    case BET -> Action.betTo(wager(legal.bet().orElseThrow(), random));
                    case RAISE -> Action.raiseTo(wager(legal.raise().orElseThrow(), random));
                };
                int actor = state.actingSeat().orElseThrow();
                actions.add(new HandEvent.ActionTaken(actor, action));
                state = HandEngine.apply(state, actor, action);
                states.add(state);
            }
            ReplayResult replay = HandReplayer.replay(state.history());
            assertTrue(replay.outcomeVerified());
            assertEquals(state.history().events().size() + 1, replay.steps().size());
            assertEquals(actions.size(), replay.decisions().size());
            for (int index = 0; index < states.size(); index++) {
                HandState live = states.get(index);
                HandSnapshot snapshot = replay.steps().get(live.history().events().size()).snapshot();
                assertSnapshot(live, snapshot);
                if (!live.isComplete()) {
                    assertEquals(live, HandEngine.resume(snapshot, live.deck()));
                    assertEquals(actions.get(index), replay.decisions().get(index).action());
                    assertEquals(snapshot, replay.decisions().get(index).snapshot());
                }
            }
            for (ReplayStep step : replay.steps()) {
                HandSnapshot snapshot = step.snapshot();
                assertEquals(state.history().header().startingStacks().total(), snapshot.stacks().total() + snapshot.pot());
            }
        }
    }

    @Test
    @DisplayName("И-5: a fold needs no private cards to verify its outcome")
    void unknownCardsAtFold() {
        HandState state = HandEngine.apply(start(42, 10_000, 10_000, 0), 0, Action.fold());
        HandHistory redacted = withoutPrivate(state.history());
        ReplayResult replay = HandReplayer.replay(redacted);
        assertTrue(replay.outcomeVerified());
        assertEquals(state.stacks(), replay.lastSnapshot().stacks());
        assertEquals(List.of(List.of(), List.of()), replay.lastSnapshot().holeCards());
        assertEquals(SeatChips.of(0, 100), replay.lastSnapshot().payouts());
    }

    @Test
    @DisplayName("И-5: an unknown showdown validates payouts but does not invent cards or ranks")
    void unknownShowdownAndLaterReveals() {
        HandState finalState = passive(start(42, 10_000, 10_000, 0));
        HandHistory full = finalState.history();
        HandHistory unknown = new HandHistory(full.header(), full.events().stream()
                .filter(event -> !(event instanceof HandEvent.DealHole hole && hole.seat() == 1))
                .filter(event -> !(event instanceof HandEvent.Showdown shown && shown.seat() == 1)).toList());
        ReplayResult replay = HandReplayer.replay(unknown);
        assertFalse(replay.outcomeVerified());
        assertTrue(replay.lastSnapshot().holeCards().get(1).isEmpty());
        assertTrue(replay.lastSnapshot().ranks().get(1).isEmpty());
        assertTrue(replay.lastSnapshot().ranks().get(0).isPresent());
        assertEquals(finalState.stacks(), replay.lastSnapshot().stacks());

        ReplayResult revealed = HandReplayer.replay(withoutPrivate(full));
        assertTrue(revealed.outcomeVerified());
        assertTrue(revealed.decisions().stream().allMatch(decision -> decision.snapshot().holeCards().stream().allMatch(List::isEmpty)));
        assertEquals(finalState.holeCards(), revealed.lastSnapshot().holeCards());
        for (ReplayStep step : HandReplayer.replay(full).steps()) {
            HandSnapshot visible = step.snapshot().forPlayer(0);
            boolean shown = visible.history().events().stream().anyMatch(event -> event instanceof HandEvent.Showdown reveal && reveal.seat() == 1);
            assertEquals(shown, !visible.holeCards().get(1).isEmpty());
            assertFalse(visible.history().events().stream().anyMatch(event -> event instanceof HandEvent.DealHole hole && hole.seat() == 1));
        }
    }

    @Test
    @DisplayName("И-4, И-5: an unknown tie requires equal payouts and conserves chips between them")
    void unknownTiePayouts() {
        Deck deck = Deck.ordered(List.of("2c", "3c", "4d", "5d", "As", "Ks", "Qs", "Js", "Ts")
                .stream().map(Card::parse).toList());
        HandState tied = passive(HandEngine.start(GameConfig.DEFAULT,
                new HandMeta("tie", List.of("human", "bot")), SeatChips.of(10_000, 10_000), 0, deck));
        assertTrue(tied.result().orElseThrow().isTie());
        HandHistory unknown = new HandHistory(tied.history().header(), tied.history().events().stream()
                .filter(event -> !(event instanceof HandEvent.DealHole) && !(event instanceof HandEvent.Showdown)).toList());
        ReplayResult replay = HandReplayer.replay(unknown);
        assertFalse(replay.outcomeVerified());
        assertEquals(tied.stacks(), replay.lastSnapshot().stacks());
        int firstPayout = find(unknown, HandEvent.Payout.class);
        HandSnapshot midway = replay.steps().get(firstPayout + 1).snapshot();
        assertEquals(100, midway.pot());
        assertEquals(20_000, midway.stacks().total() + midway.pot());
        assertFalse(midway.isComplete());
        assertEquals(List.of(Optional.empty(), Optional.empty()), midway.ranks());
        rejectsReplacement(unknown, firstPayout, new HandEvent.Payout(1, 100));
        rejectsReplacement(unknown, firstPayout, new HandEvent.Payout(0, 150));
        rejectsReplacement(unknown, firstPayout + 1, new HandEvent.Payout(1, 99));
        assertThrows(ReplayException.class, () -> HandReplayer.replay(new HandHistory(unknown.header(),
                unknown.events().subList(0, unknown.events().size() - 1))));
        assertTrue(HandReplayer.replay(tied.history()).outcomeVerified());
    }

    @Test
    @DisplayName("И-5, Ф-2: all combinations of omitted private deals and unknown showdown hands")
    void everyPartialInformationCombination() {
        HandState ended = passive(start(42, 10_000, 10_000, 0));
        for (int privateMask = 0; privateMask < 4; privateMask++) {
            for (int revealMask = 0; revealMask < 4; revealMask++) {
                if ((privateMask & ~revealMask) != 0) continue;
                int knownPrivate = privateMask;
                int revealed = revealMask;
                HandHistory history = new HandHistory(ended.history().header(), ended.history().events().stream()
                        .filter(event -> !(event instanceof HandEvent.DealHole hole) || (knownPrivate & 1 << hole.seat()) != 0)
                        .filter(event -> !(event instanceof HandEvent.Showdown shown) || (revealed & 1 << shown.seat()) != 0).toList());
                ReplayResult replay = HandReplayer.replay(history);
                assertEquals(revealMask == 3, replay.outcomeVerified());
                assertEquals(ended.stacks(), replay.lastSnapshot().stacks());
                for (int seat = 0; seat < 2; seat++) {
                    assertEquals((revealMask & 1 << seat) != 0, replay.lastSnapshot().ranks().get(seat).isPresent());
                    for (Decision decision : replay.decisions()) {
                        assertEquals((privateMask & 1 << seat) != 0, !decision.snapshot().holeCards().get(seat).isEmpty());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("И-7, П-2: replay matches live play at extreme chip totals and different blind configurations")
    void unusualBlindsAndMaximumChipSupply() {
        List<GameConfig> configs = List.of(new GameConfig(1, 1), new GameConfig(5, 17),
                new GameConfig(1_000_000_000_000L, 1_000_000_000_000L));
        List<SeatChips> stacks = List.of(SeatChips.of(1, SeatChips.MAX_TOTAL - 1),
                SeatChips.of(1_000_000_000_001L, 999_999_999_999L));
        for (GameConfig config : configs) {
            for (SeatChips starting : stacks) {
                for (int button = 0; button < 2; button++) {
                    HandState live = HandEngine.start(config, new HandMeta("limits", List.of("a", "b")), starting, button, Deck.shuffled(77));
                    HandState ended = passive(live);
                    ReplayResult replay = HandReplayer.replay(ended.history());
                    assertTrue(replay.outcomeVerified());
                    assertSnapshot(ended, replay.lastSnapshot());
                    if (!live.isComplete()) {
                        HandSnapshot first = replay.decisions().getFirst().snapshot();
                        assertEquals(live, HandEngine.resume(first, live.deck()));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {30, 40, 60, 100, 300})
    @DisplayName("И-4: short blinds, all-in runouts and refunds preserve every chip")
    void shortStacks(long shortStack) {
        for (int button = 0; button < 2; button++) {
            HandState state = passive(start(77, shortStack, 10_000, button));
            ReplayResult replay = HandReplayer.replay(state.history());
            assertTrue(replay.outcomeVerified());
            assertSnapshot(state, replay.lastSnapshot());
        }
    }

    @Test
    @DisplayName("И-4: numbered errors reject corrupt betting, dealing, showdown and payouts")
    void rejectsCorruptEvents() {
        HandState finalState = passive(start(42, 10_000, 10_000, 0));
        HandHistory history = finalState.history();
        rejectsReplacement(history, 0, new HandEvent.PostBlind(1, 50));
        rejectsReplacement(history, 1, new HandEvent.PostBlind(1, 99));
        rejectsReplacement(history, 3, new HandEvent.DealHole(1, finalState.holeCards().get(0)));
        int action = find(history, HandEvent.ActionTaken.class);
        rejectsReplacement(history, action, new HandEvent.ActionTaken(1, Action.call()));
        rejectsReplacement(history, action, new HandEvent.ActionTaken(0, Action.check()));
        rejectsReplacement(history, action, new HandEvent.ActionTaken(0, Action.raiseTo(199)));
        int board = find(history, HandEvent.DealBoard.class);
        rejectsReplacement(history, board, new HandEvent.DealBoard(Street.TURN, List.of(Card.parse("As"))));
        rejectsReplacement(history, board, new HandEvent.DealBoard(Street.FLOP,
                List.of(finalState.holeCards().get(0).get(0), Card.parse("As"), Card.parse("Ks"))));
        int shown = find(history, HandEvent.Showdown.class);
        HandEvent.Showdown reveal = (HandEvent.Showdown) history.events().get(shown);
        rejectsReplacement(history, shown, new HandEvent.Showdown(reveal.seat(),
                List.of(Card.parse("As"), Card.parse("Ks")), reveal.category()));
        rejectsReplacement(history, shown, new HandEvent.Showdown(reveal.seat(), reveal.cards(), HandCategory.STRAIGHT_FLUSH));
        rejectsReplacement(history, shown, new HandEvent.Showdown(1, finalState.holeCards().get(1), HandCategory.HIGH_CARD));
        int payout = find(history, HandEvent.Payout.class);
        HandEvent.Payout paid = (HandEvent.Payout) history.events().get(payout);
        rejectsReplacement(history, payout, new HandEvent.Payout(paid.seat(), paid.amount() - 1));
        rejectsReplacement(history, payout, new HandEvent.Payout(1 - paid.seat(), paid.amount()));
        assertThrows(ReplayException.class, () -> HandReplayer.replay(history.append(paid)));
        assertThrows(ReplayException.class, () -> HandReplayer.replay(new HandHistory(history.header(), history.events().subList(0, payout))));
        List<HandEvent> missing = new ArrayList<>(history.events());
        missing.remove(board);
        assertThrows(ReplayException.class, () -> HandReplayer.replay(new HandHistory(history.header(), missing)));
        HandState fold = HandEngine.apply(start(42, 10_000, 10_000, 0), 0, Action.fold());
        int refund = find(fold.history(), HandEvent.ReturnUncalled.class);
        rejectsReplacement(fold.history(), refund, new HandEvent.ReturnUncalled(1, 49));
    }

    @Test
    @DisplayName("И-6: resume rejects intermediate, finished, unknown and forged snapshots or conflicting decks")
    void invalidResume() {
        HandState live = start(42, 10_000, 10_000, 0);
        HandState ended = passive(live);
        ReplayResult replay = HandReplayer.replay(ended.history());
        HandSnapshot first = replay.decisions().get(0).snapshot();
        for (ReplayStep step : replay.steps()) {
            if (!step.snapshot().decisionPoint()) {
                assertThrows(IllegalArgumentException.class, () -> HandEngine.resume(step.snapshot(), live.deck()));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> HandEngine.resume(first.forPlayer(0), live.deck()));
        assertThrows(IllegalArgumentException.class, () -> HandEngine.resume(first, Deck.ordered(first.holeCards().get(0))));
        assertThrows(IllegalArgumentException.class, () -> HandEngine.resume(first, Deck.shuffled(42)));
        assertThrows(IllegalArgumentException.class, () -> HandEngine.resume(first, Deck.ordered(List.of())));
        HandSnapshot forged = new HandSnapshot(first.history(), first.street(), first.board(), first.holeCards(), first.stacks(),
                first.streetBets(), first.contributions(), first.actingSeat(), 101, first.actedSeats(), true, first.payouts(), first.ranks());
        assertThrows(IllegalArgumentException.class, () -> HandEngine.resume(forged, live.deck()));
        assertThrows(NullPointerException.class, () -> HandEngine.resume(null, live.deck()));
        assertThrows(NullPointerException.class, () -> HandEngine.resume(first, null));

        HandState branch = HandEngine.apply(HandEngine.resume(first, live.deck()), 0, Action.fold());
        assertEquals(SeatChips.of(9950, 10050), branch.stacks());
        HandState restored = HandEngine.resume(first, live.deck());
        for (Decision decision : replay.decisions()) {
            restored = HandEngine.apply(restored, decision.action().seat(), decision.action().action());
        }
        assertEquals(ended, restored);
        Deck alternativeDeck = Deck.ordered(live.deck().remainingCards().reversed());
        HandState alternate = passive(HandEngine.resume(first, alternativeDeck));
        assertNotEquals(ended.board(), alternate.board());
        assertTrue(HandReplayer.replay(alternate.history()).outcomeVerified());
    }

    private static HandState start(long seed, long stack0, long stack1, int button) {
        return HandEngine.start(GameConfig.DEFAULT, new HandMeta("replay-test", List.of("human", "bot")),
                SeatChips.of(stack0, stack1), button, Deck.shuffled(seed));
    }

    private static HandState passive(HandState state) {
        while (!state.isComplete()) {
            state = HandEngine.apply(state, state.actingSeat().orElseThrow(),
                    HandEngine.legalActions(state).canCheck() ? Action.check() : Action.call());
        }
        return state;
    }

    private static long wager(LegalActions.BetRange range, Random random) {
        return range.minTo() + random.nextLong(range.maxTo() - range.minTo() + 1);
    }

    private static HandHistory withoutPrivate(HandHistory history) {
        return new HandHistory(history.header(), history.events().stream().filter(event -> !(event instanceof HandEvent.DealHole)).toList());
    }

    private static int find(HandHistory history, Class<? extends HandEvent> type) {
        for (int index = 0; index < history.events().size(); index++) {
            if (type.isInstance(history.events().get(index))) return index;
        }
        throw new AssertionError("Event not found: " + type);
    }

    private static void rejectsReplacement(HandHistory history, int index, HandEvent event) {
        List<HandEvent> events = new ArrayList<>(history.events());
        events.set(index, event);
        ReplayException exception = assertThrows(ReplayException.class, () -> HandReplayer.replay(new HandHistory(history.header(), events)));
        assertEquals(index + 1, exception.eventNumber());
    }

    private static void assertSnapshot(HandState state, HandSnapshot snapshot) {
        assertEquals(state.history(), snapshot.history());
        assertEquals(state.street(), snapshot.street());
        assertEquals(state.board(), snapshot.board());
        assertEquals(state.holeCards(), snapshot.holeCards());
        assertEquals(state.stacks(), snapshot.stacks());
        assertEquals(state.streetBets(), snapshot.streetBets());
        assertEquals(state.contributions(), snapshot.contributions());
        assertEquals(state.actingSeat(), snapshot.actingSeat());
        assertEquals(state.lastRaiseIncrement(), snapshot.lastRaiseIncrement());
        assertEquals(state.actedSeats(), snapshot.actedSeats());
        assertEquals(!state.isComplete(), snapshot.decisionPoint());
        assertEquals(HandEngine.legalActions(state), snapshot.legalActions());
    }
}
