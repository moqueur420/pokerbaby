package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable client-facing state with the viewer's own cards and a redacted history.
 * This type contains no deck or full engine state.
 * @param seat viewer's seat
 * @param street current street
 * @param board shared cards
 * @param holeCards viewer's two cards
 * @param stacks remaining stacks
 * @param streetBets street totals
 * @param contributions net committed chips
 * @param actingSeat next actor, or empty at completion
 * @param lastRaiseIncrement last full raise increment
 * @param legalActions viewer's actions, empty when not acting
 * @param history history with the opponent's deal-hole event removed
 * @param result optional public settlement; ranks appear only at showdown
 */
public record PlayerView(int seat, Street street, List<Card> board, List<Card> holeCards,
                         SeatChips stacks, SeatChips streetBets, SeatChips contributions,
                         OptionalInt actingSeat, long lastRaiseIncrement, LegalActions legalActions,
                         HandHistory history, Optional<HandResult> result) {
    /**
     * Copies cards and always projects the supplied history for the viewer.
     * @param seat 0 or 1
     * @param street non-null street
     * @param board shared cards
     * @param holeCards exactly two distinct own cards
     * @param stacks non-null remaining amounts
     * @param streetBets non-null street totals
     * @param contributions non-null contributions
     * @param actingSeat non-null optional actor
     * @param lastRaiseIncrement positive last full increment
     * @param legalActions non-null viewer's available actions
     * @param history full or already redacted history
     * @param result non-null optional settlement
     */
    public PlayerView {
        Checks.seat(seat);
        Objects.requireNonNull(street, "street");
        board = List.copyOf(board);
        holeCards = Checks.cards(holeCards, 2);
        Objects.requireNonNull(stacks, "stacks");
        Objects.requireNonNull(streetBets, "streetBets");
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(actingSeat, "actingSeat").ifPresent(Checks::seat);
        Checks.positiveChips(lastRaiseIncrement);
        Objects.requireNonNull(legalActions, "legalActions");
        history = Objects.requireNonNull(history, "history").forPlayer(seat);
        Objects.requireNonNull(result, "result");
    }

    /** @return original hand metadata */
    public HandMeta meta() {
        return history.header().meta();
    }

    /** @return configured blinds */
    public GameConfig config() {
        return history.header().config();
    }

    /** @return dealer and small-blind seat */
    public int buttonSeat() {
        return history.header().buttonSeat();
    }

    /** @return currently committed pot, zero after settlement */
    public long pot() {
        return contributions.total();
    }

    /** @return whether the viewer must act */
    public boolean isMyTurn() {
        return actingSeat.equals(OptionalInt.of(seat));
    }

    /** @return whether payouts are complete */
    public boolean isComplete() {
        return result.isPresent();
    }

    /** @return opponent's cards only if a showdown event has revealed them */
    public Optional<List<Card>> opponentCards() {
        return history.events().stream()
                .filter(event -> event instanceof HandEvent.Showdown showdown && showdown.seat() != seat)
                .map(event -> ((HandEvent.Showdown) event).cards())
                .findFirst();
    }
}
