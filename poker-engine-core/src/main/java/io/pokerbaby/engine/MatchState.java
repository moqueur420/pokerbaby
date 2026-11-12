package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable match state between hands. The counter identifies the next hand;
 * obtaining its metadata or starting it does not advance this state.
 * @param matchId non-blank externally supplied match identifier
 * @param playerIds distinct player identifiers in fixed seat order
 * @param config fixed blind configuration
 * @param stacks current stacks after the preceding hand, or initial stacks
 * @param buttonSeat dealer and small-blind seat for the next hand
 * @param handNumber one-based number of the next hand, including after a match ends
 */
public record MatchState(String matchId, List<String> playerIds, GameConfig config,
                         SeatChips stacks, int buttonSeat, long handNumber) {
    /**
     * Copies player identifiers and validates a match position. At hand number 1,
     * both stacks must be from 1 through 10^12. Later positions allow winnings
     * above that limit and a zero stack for a finished match, up to 2 * 10^12 total.
     * @param matchId non-blank match identifier
     * @param playerIds exactly two distinct non-blank identifiers
     * @param config non-null blind configuration
     * @param stacks non-null current stacks with a positive combined total
     * @param buttonSeat 0 or 1
     * @param handNumber positive next-hand number
     * @throws IllegalArgumentException if identifiers, stacks, seat or counter are invalid
     */
    public MatchState {
        Checks.identifier(matchId, "matchId");
        playerIds = Checks.playerIds(playerIds);
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(stacks, "stacks");
        Checks.seat(buttonSeat);
        if (handNumber < 1 || stacks.total() == 0) {
            throw new IllegalArgumentException("A match requires a positive hand number and chip supply");
        }
        if (handNumber == 1) {
            stacks.validateMatchStart();
        }
    }

    /**
     * Creates a match before its first hand with no clock or random source.
     * @param matchId externally supplied match identifier
     * @param playerIds player identifiers in fixed seat order
     * @param config fixed blind configuration
     * @param stacks initial stacks, each from 1 through 10^12
     * @param buttonSeat first dealer and small-blind seat
     * @return a new match with hand number 1
     */
    public static MatchState start(String matchId, List<String> playerIds, GameConfig config,
                                   SeatChips stacks, int buttonSeat) {
        return new MatchState(matchId, playerIds, config, stacks, buttonSeat, 1);
    }

    /**
     * Builds deterministic metadata without a timestamp or advancing the counter.
     * @return metadata with handId equal to matchId + "-h" + handNumber
     * @throws IllegalStateException if a player has no chips
     */
    public HandMeta nextHandMeta() {
        requireActive();
        return new HandMeta(matchId + "-h" + handNumber, playerIds);
    }

    /**
     * Starts the expected hand with an explicitly supplied immutable deck.
     * The returned hand may already be complete after short all-in blinds.
     * @param deck caller-prepared deck with at least nine remaining cards
     * @return the first decision or settled hand, leaving this match unchanged
     * @throws IllegalStateException if the match is over
     * @throws IllegalArgumentException if too few cards remain
     */
    public HandState nextHand(Deck deck) {
        return HandEngine.start(config, nextHandMeta(), stacks, buttonSeat, deck);
    }

    /**
     * Transfers final stacks, switches the button and advances the counter once.
     * The result header must match the expected hand identity, players, blinds,
     * button and starting stacks. An externally provided timestamp is accepted.
     * @param result a settled result for this match's expected hand
     * @return the next immutable match position, possibly finished
     * @throws IllegalStateException if the match is already over
     * @throws IllegalArgumentException if the result belongs to another hand or context
     * @throws ArithmeticException if the hand counter would overflow
     */
    public MatchState afterHand(HandResult result) {
        Objects.requireNonNull(result, "result");
        HandMeta expected = nextHandMeta();
        HandHistory.Header header = result.header();
        if (!header.meta().handId().equals(expected.handId())
                || !header.meta().playerIds().equals(playerIds)
                || !header.config().equals(config)
                || header.buttonSeat() != buttonSeat
                || !header.startingStacks().equals(stacks)) {
            throw new IllegalArgumentException("Result does not belong to the expected match hand");
        }
        return new MatchState(matchId, playerIds, config, result.finalStacks(),
                1 - buttonSeat, Math.incrementExact(handNumber));
    }

    /**
     * Reports whether either player has lost all chips.
     * @return true if a stack is zero
     */
    public boolean isOver() {
        return stacks.get(0) == 0 || stacks.get(1) == 0;
    }

    /**
     * Counts results already applied to this position.
     * @return the next-hand number minus one
     */
    public long completedHands() {
        return Math.subtractExact(handNumber, 1);
    }

    /**
     * Finds the player with all the chips after the match ends.
     * @return the winner's seat, or empty while the match continues
     */
    public OptionalInt winnerSeat() {
        return isOver() ? OptionalInt.of(stacks.get(0) > 0 ? 0 : 1) : OptionalInt.empty();
    }

    private void requireActive() {
        if (isOver()) {
            throw new IllegalStateException("The match is over");
        }
    }
}
