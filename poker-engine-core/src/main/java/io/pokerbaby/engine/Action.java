package io.pokerbaby.engine;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * A requested action; betting legality is checked by the future hand engine.
 * @param type action type
 * @param to total committed on the street after a bet or raise; absent for other types
 */
public record Action(ActionType type, OptionalLong to) {
    /**
     * Validates the action's shape without judging current game legality.
     * @param type non-null action type
     * @param to positive amount for BET/RAISE, empty for FOLD/CHECK/CALL
     * @throws IllegalArgumentException if amount presence or sign is invalid
     */
    public Action {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(to, "to");
        boolean sized = type == ActionType.BET || type == ActionType.RAISE;
        if (sized != to.isPresent() || to.isPresent() && to.getAsLong() <= 0) {
            throw new IllegalArgumentException("Only BET and RAISE require a positive amount 'to'");
        }
    }

    /**
     * Creates a fold request.
     * @return a fold request
     */
    public static Action fold() {
        return new Action(ActionType.FOLD, OptionalLong.empty());
    }

    /**
     * Creates a check request.
     * @return a check request
     */
    public static Action check() {
        return new Action(ActionType.CHECK, OptionalLong.empty());
    }

    /**
     * Creates a call request whose amount is determined by the hand engine.
     * @return a call request
     */
    public static Action call() {
        return new Action(ActionType.CALL, OptionalLong.empty());
    }

    /**
     * Creates a bet request.
     * @param to total street commitment after betting
     * @return the request
     */
    public static Action betTo(long to) {
        return new Action(ActionType.BET, OptionalLong.of(to));
    }

    /**
     * Creates a raise request.
     * @param to total street commitment after raising
     * @return the request
     */
    public static Action raiseTo(long to) {
        return new Action(ActionType.RAISE, OptionalLong.of(to));
    }
}
