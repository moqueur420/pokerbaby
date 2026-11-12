package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Settled hand amounts. Contributions and pot exclude returned uncalled chips;
 * payouts are payments from that contested pot, excluding refunds.
 * @param header the original hand identity and starting parameters
 * @param winnerSeat winner, or empty for a showdown tie
 * @param contributions net matched contributions before payout
 * @param returnedChips uncalled chips returned separately
 * @param payouts contested-pot payments by seat
 * @param finalStacks stacks after refunds and payouts
 * @param ranks two evaluated ranks in seat order at showdown, otherwise empty
 */
public record HandResult(HandHistory.Header header, OptionalInt winnerSeat,
                         SeatChips contributions, SeatChips returnedChips,
                         SeatChips payouts, SeatChips finalStacks, List<HandRank> ranks) {
    /**
     * Copies ranks and validates settlement arithmetic and the declared outcome.
     * @param header non-null hand header
     * @param winnerSeat winner or empty for a tie
     * @param contributions equal matched contributions
     * @param returnedChips non-negative refunds
     * @param payouts payments adding up to the contested pot
     * @param finalStacks resulting stacks
     * @param ranks either zero or two non-null ranks
     */
    public HandResult {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(winnerSeat, "winnerSeat").ifPresent(Checks::seat);
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(returnedChips, "returnedChips");
        Objects.requireNonNull(payouts, "payouts");
        Objects.requireNonNull(finalStacks, "finalStacks");
        ranks = List.copyOf(ranks);
        if (contributions.get(0) != contributions.get(1) || contributions.total() == 0
                || ranks.size() != 0 && ranks.size() != 2
                || ranks.isEmpty() && winnerSeat.isEmpty()) {
            throw new IllegalArgumentException("Inconsistent hand result");
        }
        if (ranks.size() == 2) {
            int comparison = ranks.get(0).compareTo(ranks.get(1));
            OptionalInt evaluatedWinner = comparison == 0 ? OptionalInt.empty()
                    : OptionalInt.of(comparison > 0 ? 0 : 1);
            if (!winnerSeat.equals(evaluatedWinner)) {
                throw new IllegalArgumentException("Winner disagrees with evaluated ranks");
            }
        }
        if (!payouts.equals(splitPot(contributions.total(), winnerSeat, header.buttonSeat()))) {
            throw new IllegalArgumentException("Payouts disagree with the outcome");
        }
        for (int seat = 0; seat < 2; seat++) {
            long start = header.startingStacks().get(seat);
            if (Math.addExact(contributions.get(seat), returnedChips.get(seat)) > start
                    || finalStacks.get(seat) != Math.addExact(
                            Math.subtractExact(start, contributions.get(seat)), payouts.get(seat))) {
                throw new IllegalArgumentException("Settlement does not conserve chips");
            }
        }
    }

    /** @return whether the hand ended in a tie */
    public boolean isTie() {
        return winnerSeat.isEmpty();
    }

    /** @return whether both hands were revealed and evaluated */
    public boolean wasShowdown() {
        return !ranks.isEmpty();
    }

    /** @return the matched pot before payout, excluding refunds */
    public long pot() {
        return contributions.total();
    }

    static SeatChips splitPot(long pot, OptionalInt winner, int buttonSeat) {
        if (winner.isPresent()) {
            return SeatChips.of(0, 0).with(winner.getAsInt(), pot);
        }
        long half = pot / 2;
        SeatChips split = SeatChips.of(half, half);
        int first = 1 - buttonSeat;
        return split.with(first, Math.addExact(half, pot % 2));
    }
}
