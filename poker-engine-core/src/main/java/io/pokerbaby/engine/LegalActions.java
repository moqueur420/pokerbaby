package io.pokerbaby.engine;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Available actions for one decision; all wager sizes are street totals, not increments.
 * An empty set describes a completed hand or a player who is not acting.
 * @param canFold whether a wager may be surrendered to
 * @param canCheck whether checking is free
 * @param callAmount chips to pay now, capped by the remaining stack; zero means unavailable
 * @param bet available opening-bet range
 * @param raise available raise range, including the big blind's option after a limp
 */
public record LegalActions(boolean canFold, boolean canCheck, long callAmount,
                           Optional<BetRange> bet, Optional<BetRange> raise) {
    /**
     * Validates action availability and immutable wager ranges.
     * @param canFold fold availability
     * @param canCheck check availability
     * @param callAmount non-negative call cost
     * @param bet non-null optional opening-bet range
     * @param raise non-null optional raise range
     */
    public LegalActions {
        Objects.requireNonNull(bet, "bet");
        Objects.requireNonNull(raise, "raise");
        if (callAmount < 0 || callAmount > SeatChips.MAX_TOTAL
                || canFold != (callAmount > 0) || canFold && canCheck
                || bet.isPresent() && (!canCheck || raise.isPresent())) {
            throw new IllegalArgumentException("Inconsistent legal actions");
        }
    }

    /** @return no available actions */
    public static LegalActions none() {
        return new LegalActions(false, false, 0, Optional.empty(), Optional.empty());
    }

    /** @return whether calling is available */
    public boolean canCall() {
        return callAmount > 0;
    }

    /** @return whether an opening bet is available */
    public boolean canBet() {
        return bet.isPresent();
    }

    /** @return whether raising is available */
    public boolean canRaise() {
        return raise.isPresent();
    }

    /** @return the immutable set of available action types */
    public Set<ActionType> types() {
        EnumSet<ActionType> types = EnumSet.noneOf(ActionType.class);
        if (canFold) {
            types.add(ActionType.FOLD);
        }
        if (canCheck) {
            types.add(ActionType.CHECK);
        }
        if (canCall()) {
            types.add(ActionType.CALL);
        }
        if (canBet()) {
            types.add(ActionType.BET);
        }
        if (canRaise()) {
            types.add(ActionType.RAISE);
        }
        return Set.copyOf(types);
    }

    /**
     * Inclusive wager bounds; a short all-in has equal minimum and maximum.
     * @param minTo minimum street total
     * @param maxTo maximum street total, including chips already posted
     */
    public record BetRange(long minTo, long maxTo) {
        /**
         * Validates inclusive bounds.
         * @param minTo positive minimum
         * @param maxTo maximum not below the minimum
         */
        public BetRange {
            Checks.positiveChips(minTo);
            Checks.positiveChips(maxTo);
            if (minTo > maxTo) {
                throw new IllegalArgumentException("Minimum wager exceeds maximum");
            }
        }
    }
}
