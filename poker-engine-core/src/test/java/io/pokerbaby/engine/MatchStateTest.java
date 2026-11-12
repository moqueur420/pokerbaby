package io.pokerbaby.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.pokerbaby.engine.HandEngineTest.acesAgainstKings;
import static io.pokerbaby.engine.HandEngineTest.act;
import static io.pokerbaby.engine.HandEngineTest.byPosition;
import static io.pokerbaby.engine.HandEngineTest.checkCallToEnd;
import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class MatchStateTest {
    private static final List<String> PLAYERS = List.of("human", "bot");
    private static final SeatChips STACKS = SeatChips.of(10_000, 10_000);

    private static MatchState start(int button) {
        return MatchState.start("m1", PLAYERS, GameConfig.DEFAULT, STACKS, button);
    }

    private static HandResult folded(GameConfig config, HandMeta meta, SeatChips stacks, int button) {
        HandState state = HandEngine.start(config, meta, stacks, button, acesAgainstKings());
        return act(state, Action.fold()).result().orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("М-1, Н-1, П-9: a match starts at hand 1 with copied players and deterministic metadata")
    void createsImmutableMatch(int button) {
        List<String> players = new ArrayList<>(PLAYERS);
        MatchState match = MatchState.start("m1", players, GameConfig.DEFAULT, STACKS, button);
        players.clear();
        assertEquals("m1", match.matchId());
        assertEquals(PLAYERS, match.playerIds());
        assertEquals(GameConfig.DEFAULT, match.config());
        assertEquals(STACKS, match.stacks());
        assertEquals(button, match.buttonSeat());
        assertEquals(1, match.handNumber());
        assertEquals(0, match.completedHands());
        assertFalse(match.isOver());
        assertTrue(match.winnerSeat().isEmpty());
        assertEquals(new HandMeta("m1-h1", PLAYERS), match.nextHandMeta());
        assertEquals(match.nextHandMeta(), match.nextHandMeta());
        assertTrue(match.nextHandMeta().startedAt().isEmpty());
        assertEquals(start(button), match);
        assertEquals(start(button).hashCode(), match.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> match.playerIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> match.nextHandMeta().playerIds().clear());
        Deck deck = acesAgainstKings();
        HandState hand = match.nextHand(deck);
        assertEquals(match.nextHandMeta(), hand.meta());
        assertEquals(STACKS, hand.history().header().startingStacks());
        assertEquals(button, hand.buttonSeat());
        assertEquals(0, deck.position());
        assertEquals(hand, match.nextHand(deck));
        assertEquals(start(button), match);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-14, М-2: two folds transfer exact stacks, alternate the button and advance hand IDs")
    void playsTwoHands(int button) {
        MatchState initial = start(button);
        HandState first = initial.nextHand(acesAgainstKings());
        HandState firstEnd = act(first, Action.fold());
        HandResult firstResult = firstEnd.result().orElseThrow();
        MatchState afterFirst = initial.afterHand(firstResult);
        assertEquals(byPosition(button, 9_950, 10_050), afterFirst.stacks());
        assertEquals(1 - button, afterFirst.buttonSeat());
        assertEquals(2, afterFirst.handNumber());
        assertEquals(1, afterFirst.completedHands());
        assertEquals(new HandMeta("m1-h2", PLAYERS), afterFirst.nextHandMeta());
        assertFalse(afterFirst.isOver());
        assertEquals(initial.config(), afterFirst.config());
        assertEquals(initial.matchId(), afterFirst.matchId());
        assertEquals(initial.playerIds(), afterFirst.playerIds());
        HandState second = afterFirst.nextHand(acesAgainstKings());
        assertEquals(firstResult.finalStacks(), second.history().header().startingStacks());
        assertEquals(byPosition(button, 9_850, 10_000), second.stacks());
        assertEquals(OptionalInt.of(1 - button), second.actingSeat());
        assertEquals("m1-h2", second.meta().handId());
        HandState secondEnd = act(second, Action.fold());
        MatchState afterSecond = afterFirst.afterHand(secondEnd.result().orElseThrow());
        assertEquals(STACKS, afterSecond.stacks());
        assertEquals(button, afterSecond.buttonSeat());
        assertEquals(3, afterSecond.handNumber());
        assertEquals(2, afterSecond.completedHands());
        assertEquals("m1-h3", afterSecond.nextHandMeta().handId());
        assertFalse(afterSecond.isOver());
        assertEquals(initial, start(button));
        assertEquals(first, initial.nextHand(acesAgainstKings()));
        assertEquals(afterFirst, initial.afterHand(firstResult));
        assertEquals(4, first.history().events().size());
        assertEquals(firstEnd, act(first, Action.fold()));
    }

    @ParameterizedTest
    @CsvSource({"0,40", "1,40", "0,100", "1,100"})
    @DisplayName("М-3: zero stack ends the match, including hands settled immediately after blinds")
    void stopsAfterBust(int button, long stack) {
        MatchState initial = MatchState.start("short", PLAYERS, GameConfig.DEFAULT,
                SeatChips.of(stack, stack), button);
        HandState first = initial.nextHand(acesAgainstKings());
        assertEquals(stack == 40, first.isComplete());
        HandResult result = checkCallToEnd(first).result().orElseThrow();
        MatchState end = initial.afterHand(result);
        assertTrue(end.isOver());
        assertEquals(OptionalInt.of(1 - button), end.winnerSeat());
        assertEquals(byPosition(button, 0, 2 * stack), end.stacks());
        assertEquals(2 * stack, end.stacks().total());
        assertEquals(1 - button, end.buttonSeat());
        assertEquals(2, end.handNumber());
        assertEquals(1, end.completedHands());
        assertThrows(IllegalStateException.class, end::nextHandMeta);
        assertThrows(IllegalStateException.class, () -> end.nextHand(acesAgainstKings()));
        assertThrows(IllegalStateException.class, () -> end.afterHand(result));
        assertFalse(initial.isOver());
        assertEquals(1, initial.handNumber());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Т-13, М-2: a tied hand still advances the button and number without ending the match")
    void continuesAfterTie(int button) {
        MatchState initial = start(button);
        Deck deck = Deck.ordered(cards("2c 3d 4h 5s As Ks Qs Js Ts"));
        HandResult result = checkCallToEnd(initial.nextHand(deck)).result().orElseThrow();
        assertTrue(result.isTie());
        MatchState next = initial.afterHand(result);
        assertEquals(STACKS, next.stacks());
        assertEquals(1 - button, next.buttonSeat());
        assertEquals(2, next.handNumber());
        assertFalse(next.isOver());
        assertTrue(next.winnerSeat().isEmpty());
        assertEquals("m1-h2", next.nextHandMeta().handId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("М-2, П-2: winnings may exceed 1e12 and start the following hand up to the total limit")
    void carriesWinningsAboveInitialLimit(int button) {
        long limit = SeatChips.MAX_INITIAL_STACK;
        MatchState initial = MatchState.start("large", PLAYERS, GameConfig.DEFAULT,
                SeatChips.of(limit, limit), button);
        HandResult result = checkCallToEnd(initial.nextHand(acesAgainstKings())).result().orElseThrow();
        MatchState next = initial.afterHand(result);
        assertEquals(byPosition(button, limit - 100, limit + 100), next.stacks());
        assertFalse(next.isOver());
        HandState hand = next.nextHand(acesAgainstKings());
        assertEquals(next.stacks(), hand.history().header().startingStacks());
        assertEquals(SeatChips.MAX_TOTAL, hand.stacks().total() + hand.pot());
        MatchState afterSecond = next.afterHand(act(hand, Action.fold()).result().orElseThrow());
        assertEquals(byPosition(button, limit - 50, limit + 50), afterSecond.stacks());
        assertThrows(IllegalArgumentException.class,
                () -> MatchState.start("reset", PLAYERS, GameConfig.DEFAULT, next.stacks(), button));
        MatchState maximumBlinds = MatchState.start("max", PLAYERS, new GameConfig(limit, limit),
                SeatChips.of(limit, limit), button);
        HandState allIn = maximumBlinds.nextHand(acesAgainstKings());
        assertTrue(allIn.isComplete());
        MatchState end = maximumBlinds.afterHand(allIn.result().orElseThrow());
        assertTrue(end.isOver());
        assertEquals(byPosition(button, 0, SeatChips.MAX_TOTAL), end.stacks());
    }

    @Test
    @DisplayName("М-2: reject foreign, stale and context-mismatched results without advancing the match")
    void rejectsWrongResults() {
        MatchState match = start(0);
        HandMeta meta = match.nextHandMeta();
        List<HandResult> foreign = List.of(
                folded(GameConfig.DEFAULT, new HandMeta("another-h1", PLAYERS), STACKS, 0),
                folded(GameConfig.DEFAULT, new HandMeta("m1-h2", PLAYERS), STACKS, 0),
                folded(GameConfig.DEFAULT, new HandMeta("m1-h01", PLAYERS), STACKS, 0),
                folded(GameConfig.DEFAULT, new HandMeta("m1-h1", List.of("bot", "human")), STACKS, 0),
                folded(GameConfig.DEFAULT, new HandMeta("m1-h1", List.of("other", "bot")), STACKS, 0),
                folded(new GameConfig(25, 50), meta, STACKS, 0),
                folded(GameConfig.DEFAULT, meta, SeatChips.of(9_999, 10_001), 0),
                folded(GameConfig.DEFAULT, meta, SeatChips.of(10_000, 10_001), 0),
                folded(GameConfig.DEFAULT, meta, STACKS, 1));
        for (HandResult result : foreign) {
            assertThrows(IllegalArgumentException.class, () -> match.afterHand(result));
            assertEquals(start(0), match);
            assertEquals(start(0).hashCode(), match.hashCode());
            assertEquals(meta, match.nextHandMeta());
        }
        HandResult valid = folded(match.config(), meta, match.stacks(), match.buttonSeat());
        MatchState next = match.afterHand(valid);
        assertThrows(IllegalArgumentException.class, () -> next.afterHand(valid));
        assertEquals(2, next.handNumber());
        HandResult equalCopy = new HandResult(valid.header(), valid.winnerSeat(), valid.contributions(),
                valid.returnedChips(), valid.payouts(), valid.finalStacks(), valid.ranks());
        assertEquals(next, match.afterHand(equalCopy));
    }

    @Test
    @DisplayName("М-2, Н-10: a caller-provided timestamp does not change match identity")
    void acceptsExternalTimestamp() {
        MatchState match = start(0);
        HandMeta timed = new HandMeta(match.nextHandMeta().handId(), PLAYERS,
                Optional.of(Instant.parse("2026-11-13T12:00:00Z")));
        HandResult result = folded(match.config(), timed, match.stacks(), match.buttonSeat());
        MatchState next = match.afterHand(result);
        assertEquals(2, next.handNumber());
        assertEquals(result.finalStacks(), next.stacks());
        assertTrue(next.nextHandMeta().startedAt().isEmpty());
    }

    @Test
    @DisplayName("П-2, П-4, П-8: invalid initial stacks, identifiers, seats and current positions are rejected")
    void validatesInputs() {
        for (SeatChips stacks : List.of(SeatChips.of(0, 100), SeatChips.of(100, 0), SeatChips.of(0, 0),
                SeatChips.of(1, SeatChips.MAX_TOTAL - 1), SeatChips.of(SeatChips.MAX_TOTAL - 1, 1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> MatchState.start("m1", PLAYERS, GameConfig.DEFAULT, stacks, 0));
        }
        for (String id : List.of("", " ", "\t")) {
            assertThrows(IllegalArgumentException.class,
                    () -> MatchState.start(id, PLAYERS, GameConfig.DEFAULT, STACKS, 0));
        }
        for (List<String> players : List.of(List.<String>of(), List.of("human"),
                List.of("human", "bot", "third"), List.of("same", "same"), List.of("human", " "))) {
            assertThrows(IllegalArgumentException.class,
                    () -> MatchState.start("m1", players, GameConfig.DEFAULT, STACKS, 0));
        }
        for (int seat : List.of(-1, 2)) {
            assertThrows(IllegalArgumentException.class, () -> start(seat));
        }
        for (long number : List.of(0L, -1L, Long.MIN_VALUE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new MatchState("m1", PLAYERS, GameConfig.DEFAULT, STACKS, 0, number));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new MatchState("m1", PLAYERS, GameConfig.DEFAULT, SeatChips.of(0, 0), 0, 2));
        assertThrows(NullPointerException.class,
                () -> MatchState.start(null, PLAYERS, GameConfig.DEFAULT, STACKS, 0));
        assertThrows(NullPointerException.class,
                () -> MatchState.start("m1", null, GameConfig.DEFAULT, STACKS, 0));
        assertThrows(NullPointerException.class,
                () -> MatchState.start("m1", Arrays.asList("human", null), GameConfig.DEFAULT, STACKS, 0));
        assertThrows(NullPointerException.class,
                () -> MatchState.start("m1", PLAYERS, null, STACKS, 0));
        assertThrows(NullPointerException.class,
                () -> MatchState.start("m1", PLAYERS, GameConfig.DEFAULT, null, 0));
        MatchState match = start(0);
        assertThrows(NullPointerException.class, () -> match.afterHand(null));
        assertThrows(NullPointerException.class, () -> match.nextHand(null));
        assertThrows(IllegalArgumentException.class, () -> match.nextHand(Deck.ordered(cards("As Kd"))));
        assertEquals(start(0), match);
    }

    @Test
    @DisplayName("Н-1, П-3: the hand counter never wraps and metadata uses the exact positive number")
    void counterOverflowIsRejected() {
        MatchState last = new MatchState("m1", PLAYERS, GameConfig.DEFAULT, STACKS, 0, Long.MAX_VALUE);
        assertEquals("m1-h9223372036854775807", last.nextHandMeta().handId());
        assertEquals(Long.MAX_VALUE - 1, last.completedHands());
        HandResult result = act(last.nextHand(acesAgainstKings()), Action.fold()).result().orElseThrow();
        int oldHash = last.hashCode();
        assertThrows(ArithmeticException.class, () -> last.afterHand(result));
        assertEquals(Long.MAX_VALUE, last.handNumber());
        assertEquals(oldHash, last.hashCode());
    }

    @Test
    @DisplayName("М-1—М-3, Н-1: 100 seeded matches finish, conserve chips and repeat with identical histories")
    void playsReproducibleMatchesToCompletion() {
        assertEquals(playMatches(20261113), playMatches(20261113));
    }

    private static List<PlayedMatch> playMatches(long seed) {
        Random random = new Random(seed);
        List<PlayedMatch> played = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            String context = "seed=" + seed + ", match=" + index;
            MatchState match = MatchState.start("simulation-" + index, PLAYERS, GameConfig.DEFAULT,
                    SeatChips.of(random.nextLong(1, 10_001), random.nextLong(1, 10_001)), index & 1);
            long supply = match.stacks().total();
            List<HandHistory> histories = new ArrayList<>();
            while (!match.isOver()) {
                assertTrue(match.completedHands() < 1_000, context);
                HandState hand = match.nextHand(Deck.shuffled(random));
                while (!hand.isComplete()) {
                    PlayerView view = hand.viewFor(hand.actingSeat().orElseThrow());
                    LegalActions legal = view.legalActions();
                    Action action;
                    if (legal.canRaise()) {
                        action = Action.raiseTo(legal.raise().orElseThrow().maxTo());
                    } else if (legal.canBet()) {
                        action = Action.betTo(legal.bet().orElseThrow().maxTo());
                    } else {
                        action = legal.canCheck() ? Action.check() : Action.call();
                    }
                    hand = HandEngine.apply(hand, view.seat(), action);
                }
                MatchState next = match.afterHand(hand.result().orElseThrow());
                assertEquals(match.handNumber() + 1, next.handNumber(), context);
                assertEquals(1 - match.buttonSeat(), next.buttonSeat(), context);
                assertEquals(hand.stacks(), next.stacks(), context);
                assertEquals(supply, next.stacks().total(), context);
                assertEquals(match.completedHands(), histories.size(), context);
                histories.add(hand.history());
                match = next;
            }
            int winner = match.winnerSeat().orElseThrow();
            assertEquals(supply, match.stacks().get(winner), context);
            assertEquals(0, match.stacks().get(1 - winner), context);
            assertEquals(histories.size(), match.completedHands(), context);
            played.add(new PlayedMatch(match, List.copyOf(histories)));
        }
        return List.copyOf(played);
    }

    private record PlayedMatch(MatchState end, List<HandHistory> histories) {
    }
}
