package io.pokerbaby.engine;

/**
 * Fixed blinds for heads-up no-limit hold'em.
 * @param smallBlind positive small blind
 * @param bigBlind big blind, at least the small blind and at most 10^12
 */
public record GameConfig(long smallBlind, long bigBlind) {
    /** Default 50/100 blinds. */
    public static final GameConfig DEFAULT = new GameConfig(50, 100);

    /**
     * Validates blind amounts.
     * @param smallBlind positive small blind
     * @param bigBlind big blind, at least the small blind and at most 10^12
     * @throws IllegalArgumentException if the blind limits are violated
     */
    public GameConfig {
        if (smallBlind < 1 || bigBlind < smallBlind || bigBlind > SeatChips.MAX_INITIAL_STACK) {
            throw new IllegalArgumentException("Blinds must satisfy 1 <= small <= big <= 10^12");
        }
    }
}
