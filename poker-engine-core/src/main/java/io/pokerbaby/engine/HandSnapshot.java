package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Immutable observable state after an individual history event, without a deck.
 * Empty hole-card lists mean unknown cards, never invented replacement cards.
 * @param history history prefix ending at this snapshot
 * @param street current street, including automatic SHOWDOWN and COMPLETE
 * @param board cards dealt so far
 * @param holeCards two lists, each empty or containing two known cards
 * @param stacks uncommitted chips
 * @param streetBets current-street commitments, included in contributions
 * @param contributions chips still in the pot, including during partial payouts
 * @param actingSeat next actor only at a decision point
 * @param lastRaiseIncrement last full raise increment
 * @param actedSeats players who have acted on this street
 * @param decisionPoint whether live play can be resumed here
 * @param payouts payments already made from the pot
 * @param ranks two optional ranks revealed at showdown
 */
public record HandSnapshot(HandHistory history, Street street, List<Card> board, List<List<Card>> holeCards,
                           SeatChips stacks, SeatChips streetBets, SeatChips contributions,
                           OptionalInt actingSeat, long lastRaiseIncrement, Set<Integer> actedSeats,
                           boolean decisionPoint, SeatChips payouts, List<Optional<HandRank>> ranks) {
    /**
     * Copies collections and validates card, chip and decision invariants.
     * @param history immutable prefix
     * @param street current street
     * @param board known shared cards
     * @param holeCards two optional known hands
     * @param stacks current stacks
     * @param streetBets current street totals
     * @param contributions remaining pot contributions
     * @param actingSeat actor at a decision, otherwise empty
     * @param lastRaiseIncrement positive last full increment
     * @param actedSeats seats that acted this street
     * @param decisionPoint whether a player decision is pending
     * @param payouts payments already made
     * @param ranks two optional showdown ranks
     */
    public HandSnapshot {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(street, "street");
        board = Checks.cards(board, board.size());
        if (!Set.of(0, 3, 4, 5).contains(board.size()) || holeCards.size() != 2) {
            throw new IllegalArgumentException("Invalid snapshot card counts");
        }
        holeCards = holeCards.stream().map(cards -> Checks.cards(cards, cards.isEmpty() ? 0 : 2)).toList();
        Checks.disjointCards(board, holeCards.get(0), holeCards.get(1));
        Objects.requireNonNull(stacks, "stacks").validateResume(contributions);
        Objects.requireNonNull(streetBets, "streetBets");
        Objects.requireNonNull(actingSeat, "actingSeat").ifPresent(Checks::seat);
        Checks.positiveChips(lastRaiseIncrement);
        actedSeats = Set.copyOf(actedSeats);
        actedSeats.forEach(Checks::seat);
        Objects.requireNonNull(payouts, "payouts");
        ranks = List.copyOf(ranks);
        int expectedBoardSize = switch (street) {
            case PREFLOP -> 0;
            case FLOP -> 3;
            case TURN -> 4;
            case RIVER, SHOWDOWN -> 5;
            case COMPLETE -> board.size();
        };
        if (ranks.size() != 2 || decisionPoint != actingSeat.isPresent()
                || board.size() != expectedBoardSize
                || decisionPoint && (street == Street.COMPLETE || street == Street.SHOWDOWN)
                || street == Street.COMPLETE && contributions.total() != 0
                || Math.addExact(stacks.total(), contributions.total()) != history.header().startingStacks().total()) {
            throw new IllegalArgumentException("Inconsistent snapshot state");
        }
        for (int seat = 0; seat < 2; seat++) {
            if (streetBets.get(seat) > contributions.get(seat)) {
                throw new IllegalArgumentException("Street bets exceed contributions");
            }
        }
    }

    /**
     * Computes the remaining pot.
     * @return contributions without double-counting street bets
     */
    public long pot() {
        return contributions.total();
    }

    /**
     * Reports completion.
     * @return whether all automatic events and payouts have completed
     */
    public boolean isComplete() {
        return street == Street.COMPLETE;
    }

    /**
     * Computes available actions.
     * @return actor's actions, or none between events
     */
    public LegalActions legalActions() {
        return decisionPoint ? BettingRules.legal(history.header().config(), stacks, streetBets,
                lastRaiseIncrement, actingSeat.orElseThrow()) : LegalActions.none();
    }

    /**
     * Hides the opponent's private cards and ranks until a preceding showdown event.
     * @param seat viewer's seat
     * @return a snapshot containing only information available at this step
     */
    public HandSnapshot forPlayer(int seat) {
        Checks.seat(seat);
        int opponent = 1 - seat;
        boolean revealed = history.events().stream()
                .anyMatch(event -> event instanceof HandEvent.Showdown shown && shown.seat() == opponent);
        List<List<Card>> visible = List.of(seat == 0 || revealed ? holeCards.get(0) : List.of(),
                seat == 1 || revealed ? holeCards.get(1) : List.of());
        List<Optional<HandRank>> visibleRanks = List.of(seat == 0 || revealed ? ranks.get(0) : Optional.empty(),
                seat == 1 || revealed ? ranks.get(1) : Optional.empty());
        return new HandSnapshot(history.forPlayer(seat), street, board, visible, stacks, streetBets,
                contributions, actingSeat, lastRaiseIncrement, actedSeats, decisionPoint, payouts, visibleRanks);
    }
}
