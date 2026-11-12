package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Full immutable engine state, published only at a decision or after settlement.
 * It contains private information; give clients {@link #viewFor(int)} instead.
 * @param street current betting street or COMPLETE
 * @param board shared cards in dealing order
 * @param holeCards two hands in seat order, each in received order
 * @param stacks uncommitted chips
 * @param streetBets current-street parts of the contributions
 * @param contributions committed chips; zero after payout
 * @param actingSeat next actor, or empty after completion
 * @param lastRaiseIncrement last full increment; initially the big blind on each street
 * @param actedSeats players who have acted on this street; blinds do not count
 * @param deck immutable deck and next-card cursor
 * @param history complete chronological history, including private hole cards
 * @param result settlement, present only after completion
 */
public record HandState(Street street, List<Card> board, List<List<Card>> holeCards,
                        SeatChips stacks, SeatChips streetBets, SeatChips contributions,
                        OptionalInt actingSeat, long lastRaiseIncrement, Set<Integer> actedSeats,
                        Deck deck, HandHistory history, Optional<HandResult> result) {
    /**
     * Copies collections and validates card, turn and chip invariants. Use
     * {@link HandEngine#start} to create a hand with a rule-consistent history.
     * @param street betting street or COMPLETE
     * @param board zero, three, four or five distinct cards
     * @param holeCards exactly two distinct two-card hands
     * @param stacks non-negative stacks
     * @param streetBets non-negative street bets, included in contributions
     * @param contributions non-negative committed amounts
     * @param actingSeat live actor or empty at completion
     * @param lastRaiseIncrement positive full-raise increment
     * @param actedSeats immutable copied set of seats that have acted
     * @param deck remaining deck, disjoint from dealt cards
     * @param history non-null complete history
     * @param result non-null optional settlement
     */
    public HandState {
        Objects.requireNonNull(street, "street");
        board = List.copyOf(board);
        holeCards = Checks.holeCards(holeCards);
        Objects.requireNonNull(stacks, "stacks");
        Objects.requireNonNull(streetBets, "streetBets");
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(actingSeat, "actingSeat").ifPresent(Checks::seat);
        Checks.positiveChips(lastRaiseIncrement);
        actedSeats = Set.copyOf(actedSeats);
        actedSeats.forEach(Checks::seat);
        Objects.requireNonNull(deck, "deck");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(result, "result");
        int expectedBoardSize = switch (street) {
            case PREFLOP -> 0;
            case FLOP -> 3;
            case TURN -> 4;
            case RIVER -> 5;
            case COMPLETE -> board.size();
            case SHOWDOWN -> throw new IllegalArgumentException("Showdown is an automatic transition");
        };
        if (board.size() != expectedBoardSize || !Set.of(0, 3, 4, 5).contains(board.size())
                || (street == Street.COMPLETE) != result.isPresent()
                || actingSeat.isEmpty() != result.isPresent()) {
            throw new IllegalArgumentException("Inconsistent street, board, actor or result");
        }
        Checks.disjointCards(holeCards.get(0), holeCards.get(1), board, deck.remainingCards());
        if (Math.addExact(stacks.total(), contributions.total()) != history.header().startingStacks().total()) {
            throw new IllegalArgumentException("Hand state does not conserve chips");
        }
        for (int seat = 0; seat < 2; seat++) {
            if (streetBets.get(seat) > contributions.get(seat)
                    || contributions.get(seat) > history.header().startingStacks().get(seat)) {
                throw new IllegalArgumentException("Inconsistent contributions or street bets");
            }
        }
        if (result.isPresent()) {
            HandResult settled = result.orElseThrow();
            if (contributions.total() != 0 || streetBets.total() != 0
                    || !settled.finalStacks().equals(stacks) || !settled.header().equals(history.header())
                    || settled.wasShowdown() && board.size() != 5) {
                throw new IllegalArgumentException("State disagrees with settlement");
            }
        } else {
            deck.requireRemaining(5 - board.size());
            int actor = actingSeat.orElseThrow();
            int opponent = 1 - actor;
            if (stacks.get(actor) == 0 || stacks.get(opponent) == 0
                    && streetBets.get(actor) >= streetBets.get(opponent)) {
                throw new IllegalArgumentException("State has no pending decision");
            }
        }
    }

    /** @return blind configuration from the original header */
    public GameConfig config() {
        return history.header().config();
    }

    /** @return externally supplied hand metadata */
    public HandMeta meta() {
        return history.header().meta();
    }

    /** @return dealer and small-blind seat */
    public int buttonSeat() {
        return history.header().buttonSeat();
    }

    /** @return sum of contributions, without double-counting street bets */
    public long pot() {
        return contributions.total();
    }

    /** @return whether all payouts have been applied */
    public boolean isComplete() {
        return result.isPresent();
    }

    /**
     * Hides the deck and the opponent's unrevealed cards, including history events.
     * @param seat viewer's seat, 0 or 1
     * @return the immutable view; legal actions are empty outside this player's turn
     */
    public PlayerView viewFor(int seat) {
        Checks.seat(seat);
        LegalActions legal = actingSeat.equals(OptionalInt.of(seat))
                ? HandEngine.legalActions(this) : LegalActions.none();
        return new PlayerView(seat, street, board, holeCards.get(seat), stacks, streetBets,
                contributions, actingSeat, lastRaiseIncrement, legal, history, result);
    }
}
