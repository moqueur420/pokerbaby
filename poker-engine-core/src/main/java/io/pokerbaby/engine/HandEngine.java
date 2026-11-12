package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import io.pokerbaby.engine.IllegalActionException.Code;

/** Pure heads-up no-limit hold'em operations; randomness and metadata are caller-owned. */
public final class HandEngine {
    private HandEngine() {
    }

    /**
     * Posts blinds, deals alternating hole cards starting off the button, then
     * returns the first decision or automatically settles an unavoidable all-in.
     * @param config configured blinds
     * @param meta externally supplied identifiers and optional timestamp
     * @param stacks positive hand-start stacks, total at most 2 * 10^12
     * @param buttonSeat small-blind seat, 0 or 1
     * @param deck immutable deck with at least nine undrawn cards
     * @return a new immutable hand state; no input is modified
     * @throws IllegalArgumentException if stacks, seat or remaining deck are invalid
     */
    public static HandState start(GameConfig config, HandMeta meta, SeatChips stacks,
                                  int buttonSeat, Deck deck) {
        HandHistory history = HandHistory.empty(config, meta, stacks, buttonSeat);
        Objects.requireNonNull(deck, "deck").requireRemaining(9);
        Transition next = new Transition(history, deck);
        next.postBlind(buttonSeat, config.smallBlind());
        next.postBlind(1 - buttonSeat, config.bigBlind());
        for (int round = 0; round < 2; round++) {
            next.holeCards.get(1 - buttonSeat).add(next.draw());
            next.holeCards.get(buttonSeat).add(next.draw());
        }
        for (int seat = 0; seat < 2; seat++) {
            next.events.add(new HandEvent.DealHole(seat, next.holeCards.get(seat)));
        }
        next.advance();
        return next.state();
    }

    /**
     * Computes actions for the next actor, including capped calls and short all-ins.
     * @param state full non-null engine state
     * @return immutable available actions; empty for a completed hand
     */
    public static LegalActions legalActions(HandState state) {
        Objects.requireNonNull(state, "state");
        if (state.isComplete()) {
            return LegalActions.none();
        }
        int seat = state.actingSeat().orElseThrow();
        return BettingRules.legal(state.config(), state.stacks(), state.streetBets(), state.lastRaiseIncrement(), seat);
    }

    /**
     * Restores a validated decision snapshot using an explicitly supplied remaining deck.
     * Both players' hole cards must be known. The history prefix is replayed to reject
     * forged betting context and intermediate automatic-transition snapshots.
     * @param snapshot a non-null decision snapshot with its history prefix
     * @param remainingDeck undrawn cards, disjoint from all dealt cards
     * @return a full state ready for {@link #apply}
     * @throws IllegalArgumentException if the snapshot or deck cannot be resumed
     */
    public static HandState resume(HandSnapshot snapshot, Deck remainingDeck) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(remainingDeck, "remainingDeck");
        if (!snapshot.decisionPoint() || snapshot.isComplete()
                || snapshot.holeCards().stream().anyMatch(cards -> cards.size() != 2)) {
            throw new IllegalArgumentException("Resume requires a decision and both known hands");
        }
        HandSnapshot verified = HandReplayer.replay(snapshot.history()).lastSnapshot();
        if (!verified.equals(snapshot)) {
            throw new IllegalArgumentException("Snapshot disagrees with its history prefix");
        }
        remainingDeck.requireRemaining(5 - snapshot.board().size());
        return new HandState(snapshot.street(), snapshot.board(), snapshot.holeCards(), snapshot.stacks(),
                snapshot.streetBets(), snapshot.contributions(), snapshot.actingSeat(), snapshot.lastRaiseIncrement(),
                snapshot.actedSeats(), remainingDeck, snapshot.history(), Optional.empty());
    }

    /**
     * Applies one legal action, then performs all automatic dealing and payouts.
     * Rejected actions leave the original state and history intact.
     * @param state immutable full engine state
     * @param seat acting seat, 0 or 1
     * @param action structurally valid requested action
     * @return a new decision or completed state
     * @throws IllegalArgumentException if seat is not 0 or 1
     * @throws IllegalActionException if the turn, action type or amount is illegal
     */
    public static HandState apply(HandState state, int seat, Action action) {
        Objects.requireNonNull(state, "state");
        Checks.seat(seat);
        Objects.requireNonNull(action, "action");
        if (state.isComplete()) {
            throw rejected(Code.HAND_COMPLETE, "The hand is complete");
        }
        if (state.actingSeat().orElseThrow() != seat) {
            throw rejected(Code.NOT_YOUR_TURN, "The other player must act");
        }
        LegalActions legal = legalActions(state);
        BettingRules.validate(action, legal);
        Transition next = new Transition(state);
        next.events.add(new HandEvent.ActionTaken(seat, action));
        next.actedSeats.add(seat);
        switch (action.type()) {
            case FOLD -> next.finish(OptionalInt.of(1 - seat), List.of());
            case CHECK -> { }
            case CALL -> next.commit(seat, legal.callAmount());
            case BET, RAISE -> {
                long to = action.to().orElseThrow();
                long increment = Math.subtractExact(to, next.streetBets.get(1 - seat));
                if (increment >= next.lastRaiseIncrement) {
                    next.lastRaiseIncrement = increment;
                }
                next.commit(seat, Math.subtractExact(to, next.streetBets.get(seat)));
            }
        }
        if (next.result.isEmpty()) {
            next.actor = 1 - seat;
            next.advance();
        }
        return next.state();
    }

    private static IllegalActionException rejected(Code code, String message) {
        return new IllegalActionException(code, message);
    }

    // Mutable work is local to one invocation and never escapes. Only state() publishes it.
    private static final class Transition {
        private final HandHistory.Header header;
        private final List<HandEvent> events;
        private final List<Card> board;
        private final List<List<Card>> holeCards;
        private final Set<Integer> actedSeats;
        private Deck deck;
        private Street street;
        private SeatChips stacks;
        private SeatChips streetBets;
        private SeatChips contributions;
        private SeatChips returnedChips = SeatChips.of(0, 0);
        private int actor;
        private long lastRaiseIncrement;
        private Optional<HandResult> result = Optional.empty();

        private Transition(HandHistory history, Deck deck) {
            this.header = history.header();
            this.events = new ArrayList<>(history.events());
            this.board = new ArrayList<>(5);
            this.holeCards = List.of(new ArrayList<>(2), new ArrayList<>(2));
            this.actedSeats = new HashSet<>();
            this.deck = deck;
            this.street = Street.PREFLOP;
            this.stacks = header.startingStacks();
            this.streetBets = SeatChips.of(0, 0);
            this.contributions = SeatChips.of(0, 0);
            this.actor = header.buttonSeat();
            this.lastRaiseIncrement = header.config().bigBlind();
        }

        private Transition(HandState state) {
            this.header = state.history().header();
            this.events = new ArrayList<>(state.history().events());
            this.board = new ArrayList<>(state.board());
            this.holeCards = state.holeCards();
            this.actedSeats = new HashSet<>(state.actedSeats());
            this.deck = state.deck();
            this.street = state.street();
            this.stacks = state.stacks();
            this.streetBets = state.streetBets();
            this.contributions = state.contributions();
            this.actor = state.actingSeat().orElseThrow();
            this.lastRaiseIncrement = state.lastRaiseIncrement();
        }

        private void postBlind(int seat, long requested) {
            long amount = Math.min(requested, stacks.get(seat));
            commit(seat, amount);
            events.add(new HandEvent.PostBlind(seat, amount));
        }

        private void commit(int seat, long amount) {
            stacks = stacks.with(seat, Math.subtractExact(stacks.get(seat), amount));
            streetBets = streetBets.with(seat, Math.addExact(streetBets.get(seat), amount));
            contributions = contributions.with(seat, Math.addExact(contributions.get(seat), amount));
        }

        private Card draw() {
            Deck.Draw draw = deck.draw();
            deck = draw.deck();
            return draw.card();
        }

        private void advance() {
            if (stacks.get(0) == 0 || stacks.get(1) == 0) {
                int live = stacks.get(0) == 0 ? 1 : 0;
                if (BettingRules.runout(stacks, streetBets)) {
                    returnUncalled();
                    while (board.size() < 5) {
                        dealNextStreet();
                    }
                    showdown();
                } else {
                    actor = live;
                }
            } else if (BettingRules.roundClosed(streetBets, actedSeats)) {
                if (street == Street.RIVER) {
                    showdown();
                } else {
                    dealNextStreet();
                }
            }
        }

        private void dealNextStreet() {
            street = BettingRules.nextStreet(street);
            int count = street == Street.FLOP ? 3 : 1;
            List<Card> dealt = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                dealt.add(draw());
            }
            board.addAll(dealt);
            events.add(new HandEvent.DealBoard(street, dealt));
            streetBets = SeatChips.of(0, 0);
            lastRaiseIncrement = header.config().bigBlind();
            actedSeats.clear();
            actor = 1 - header.buttonSeat();
        }

        private void returnUncalled() {
            int seat = contributions.get(0) > contributions.get(1) ? 0 : 1;
            long amount = Math.subtractExact(contributions.get(seat), contributions.get(1 - seat));
            if (amount > 0) {
                stacks = stacks.with(seat, Math.addExact(stacks.get(seat), amount));
                streetBets = streetBets.with(seat, Math.subtractExact(streetBets.get(seat), amount));
                contributions = contributions.with(seat, Math.subtractExact(contributions.get(seat), amount));
                returnedChips = returnedChips.with(seat, Math.addExact(returnedChips.get(seat), amount));
                events.add(new HandEvent.ReturnUncalled(seat, amount));
            }
        }

        private void showdown() {
            List<HandRank> ranks = new ArrayList<>(2);
            for (int seat = 0; seat < 2; seat++) {
                List<Card> available = new ArrayList<>(holeCards.get(seat));
                available.addAll(board);
                HandRank rank = HandEvaluator.evaluate(available);
                ranks.add(rank);
                events.add(new HandEvent.Showdown(seat, holeCards.get(seat), rank.category()));
            }
            int comparison = ranks.get(0).compareTo(ranks.get(1));
            finish(comparison == 0 ? OptionalInt.empty() : OptionalInt.of(comparison > 0 ? 0 : 1), ranks);
        }

        private void finish(OptionalInt winner, List<HandRank> ranks) {
            returnUncalled();
            SeatChips payouts = HandResult.splitPot(contributions.total(), winner, header.buttonSeat());
            for (int seat = 0; seat < 2; seat++) {
                long payout = payouts.get(seat);
                if (payout > 0) {
                    stacks = stacks.with(seat, Math.addExact(stacks.get(seat), payout));
                    events.add(new HandEvent.Payout(seat, payout));
                }
            }
            result = Optional.of(new HandResult(header, winner, contributions, returnedChips, payouts, stacks, ranks));
            contributions = SeatChips.of(0, 0);
            streetBets = SeatChips.of(0, 0);
            street = Street.COMPLETE;
            actor = -1;
            actedSeats.clear();
        }

        private HandState state() {
            return new HandState(street, board, holeCards, stacks, streetBets, contributions,
                    actor < 0 ? OptionalInt.empty() : OptionalInt.of(actor), lastRaiseIncrement,
                    actedSeats, deck, new HandHistory(header, events), result);
        }
    }
}
