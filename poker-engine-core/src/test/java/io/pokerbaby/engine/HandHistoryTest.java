package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class HandHistoryTest {
    private static final HandMeta META = new HandMeta("m1-h1", List.of("human", "bot"));
    private static final SeatChips STACKS = SeatChips.of(10_000, 10_000);

    private HandHistory emptyHistory() {
        return HandHistory.empty(GameConfig.DEFAULT, META, STACKS, 0);
    }

    @Test
    @DisplayName("И-1, П-9: append preserves exact event order and old histories")
    void orderedImmutableHistory() {
        HandHistory empty = emptyHistory();
        HandEvent blind = new HandEvent.PostBlind(0, 50);
        HandEvent action = new HandEvent.ActionTaken(0, Action.fold());
        HandHistory one = empty.append(blind);
        HandHistory two = one.append(action);
        assertTrue(empty.events().isEmpty());
        assertEquals(List.of(blind), one.events());
        assertEquals(List.of(blind, action), two.events());
        assertEquals(two, emptyHistory().append(blind).append(action));
        assertEquals(two.hashCode(), emptyHistory().append(blind).append(action).hashCode());
        assertEquals(1, two.header().schemaVersion());
        assertEquals(GameConfig.DEFAULT, two.header().config());
        assertEquals(META, two.header().meta());
        assertEquals(STACKS, two.header().startingStacks());
        assertEquals(0, two.header().buttonSeat());
        assertThrows(UnsupportedOperationException.class, () -> two.events().clear());
        List<HandEvent> source = new ArrayList<>(two.events());
        HandHistory copy = new HandHistory(two.header(), source);
        source.clear();
        assertEquals(two, copy);
        assertThrows(NullPointerException.class, () -> two.append(null));
        assertThrows(NullPointerException.class, () -> new HandHistory(null, List.of()));
        assertThrows(NullPointerException.class, () -> new HandHistory(empty.header(), null));
        assertThrows(NullPointerException.class,
                () -> new HandHistory(empty.header(), Arrays.asList(blind, null)));
    }

    @Test
    @DisplayName("П-2, П-4: a header supports won chips but rejects invalid starts")
    void validatesHeaders() {
        assertDoesNotThrow(() -> HandHistory.empty(GameConfig.DEFAULT, META,
                SeatChips.of(1, 1_999_999_999_999L), 1));
        assertThrows(IllegalArgumentException.class,
                () -> HandHistory.empty(GameConfig.DEFAULT, META, SeatChips.of(0, 100), 0));
        assertThrows(IllegalArgumentException.class,
                () -> HandHistory.empty(GameConfig.DEFAULT, META, STACKS, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new HandHistory.Header(0, GameConfig.DEFAULT, META, STACKS, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new HandHistory.Header(2, GameConfig.DEFAULT, META, STACKS, 0));
        assertThrows(NullPointerException.class, () -> HandHistory.empty(null, META, STACKS, 0));
        assertThrows(NullPointerException.class, () -> HandHistory.empty(GameConfig.DEFAULT, null, STACKS, 0));
        assertThrows(NullPointerException.class, () -> HandHistory.empty(GameConfig.DEFAULT, META, null, 0));
    }

    @Test
    @DisplayName("§6.2: all seven event types have stable names and typed payloads")
    void schemaOneEventTypes() {
        HandEvent.PostBlind blind = new HandEvent.PostBlind(1, 100);
        HandEvent.DealHole hole = new HandEvent.DealHole(0, cards("As Kd"));
        HandEvent.ActionTaken action = new HandEvent.ActionTaken(0, Action.raiseTo(250));
        HandEvent.DealBoard board = new HandEvent.DealBoard(Street.FLOP, cards("Qs 7d 2c"));
        HandEvent.ReturnUncalled refund = new HandEvent.ReturnUncalled(0, 300);
        HandEvent.Showdown showdown = new HandEvent.Showdown(1, cards("9h 9c"), HandCategory.ONE_PAIR);
        HandEvent.Payout payout = new HandEvent.Payout(0, 500);
        List<HandEvent> events = List.of(blind, hole, action, board, refund, showdown, payout);
        assertEquals(List.of("post_blind", "deal_hole", "action", "deal_board", "return_uncalled", "showdown", "payout"),
                events.stream().map(HandEvent::type).toList());
        assertEquals(1, blind.seat());
        assertEquals(100, blind.amount());
        assertEquals(0, hole.seat());
        assertEquals(cards("As Kd"), hole.cards());
        assertEquals(0, action.seat());
        assertEquals(Action.raiseTo(250), action.action());
        assertEquals(Street.FLOP, board.street());
        assertEquals(cards("Qs 7d 2c"), board.cards());
        assertEquals(0, refund.seat());
        assertEquals(300, refund.amount());
        assertEquals(1, showdown.seat());
        assertEquals(cards("9h 9c"), showdown.cards());
        assertEquals(HandCategory.ONE_PAIR, showdown.category());
        assertEquals(0, payout.seat());
        assertEquals(500, payout.amount());
        // The envelope permits incomplete/imported sequences; rule validation belongs to replay.
        assertEquals(events, new HandHistory(emptyHistory().header(), events).events());
    }

    @Test
    @DisplayName("П-6, П-9: card lists in every event are copied and immutable")
    void immutableCardPayloads() {
        List<Card> holes = new ArrayList<>(cards("As Kd"));
        List<Card> flop = new ArrayList<>(cards("Qs 7d 2c"));
        HandEvent.DealHole dealt = new HandEvent.DealHole(0, holes);
        HandEvent.Showdown shown = new HandEvent.Showdown(0, holes, HandCategory.HIGH_CARD);
        HandEvent.DealBoard board = new HandEvent.DealBoard(Street.FLOP, flop);
        holes.clear();
        flop.clear();
        assertEquals(cards("As Kd"), dealt.cards());
        assertEquals(dealt.cards(), shown.cards());
        assertEquals(cards("Qs 7d 2c"), board.cards());
        assertThrows(UnsupportedOperationException.class, () -> dealt.cards().clear());
        assertThrows(UnsupportedOperationException.class, () -> shown.cards().clear());
        assertThrows(UnsupportedOperationException.class, () -> board.cards().clear());
        assertDoesNotThrow(() -> new HandEvent.DealBoard(Street.TURN, cards("As")));
        assertDoesNotThrow(() -> new HandEvent.DealBoard(Street.RIVER, cards("Kd")));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 2000000000001L, Long.MAX_VALUE})
    @DisplayName("Ф-5, П-3: zero, negative, and out-of-range amounts cannot enter events")
    void rejectsInvalidAmounts(long amount) {
        assertThrows(IllegalArgumentException.class, () -> new HandEvent.PostBlind(0, amount));
        assertThrows(IllegalArgumentException.class, () -> new HandEvent.ReturnUncalled(0, amount));
        assertThrows(IllegalArgumentException.class, () -> new HandEvent.Payout(0, amount));
    }

    @Test
    @DisplayName("П-4: seat validation applies to every seated event")
    void rejectsInvalidSeats() {
        List<BiFunction<Integer, Long, HandEvent>> amountEvents = List.of(
                HandEvent.PostBlind::new, HandEvent.ReturnUncalled::new, HandEvent.Payout::new);
        for (int seat : List.of(-1, 2)) {
            for (BiFunction<Integer, Long, HandEvent> factory : amountEvents) {
                assertThrows(IllegalArgumentException.class, () -> factory.apply(seat, 1L));
            }
            assertThrows(IllegalArgumentException.class, () -> new HandEvent.DealHole(seat, cards("As Kd")));
            assertThrows(IllegalArgumentException.class, () -> new HandEvent.ActionTaken(seat, Action.call()));
            assertThrows(IllegalArgumentException.class,
                    () -> new HandEvent.Showdown(seat, cards("As Kd"), HandCategory.HIGH_CARD));
        }
    }

    @Test
    @DisplayName("П-6: malformed card payloads and board streets fail immediately")
    void rejectsMalformedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new HandEvent.DealHole(0, cards("As")));
        assertThrows(IllegalArgumentException.class, () -> new HandEvent.DealHole(0, cards("As As")));
        assertThrows(NullPointerException.class, () -> new HandEvent.DealHole(0, null));
        assertThrows(NullPointerException.class,
                () -> new HandEvent.DealHole(0, Arrays.asList(Card.parse("As"), null)));
        assertThrows(IllegalArgumentException.class,
                () -> new HandEvent.DealBoard(Street.FLOP, cards("As Kd")));
        assertThrows(IllegalArgumentException.class,
                () -> new HandEvent.DealBoard(Street.FLOP, cards("As As Kd")));
        for (Street street : List.of(Street.PREFLOP, Street.SHOWDOWN, Street.COMPLETE)) {
            assertThrows(IllegalArgumentException.class, () -> new HandEvent.DealBoard(street, cards("As")));
        }
        assertThrows(NullPointerException.class, () -> new HandEvent.DealBoard(null, cards("As")));
        assertThrows(NullPointerException.class, () -> new HandEvent.ActionTaken(0, null));
        assertThrows(NullPointerException.class, () -> new HandEvent.Showdown(0, cards("As Kd"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new HandEvent.Showdown(0, cards("As"), HandCategory.HIGH_CARD));
        assertEquals(9, HandCategory.values().length);
        assertEquals(HandCategory.HIGH_CARD, HandCategory.values()[0]);
        assertEquals(HandCategory.STRAIGHT_FLUSH, HandCategory.values()[8]);
    }
}
