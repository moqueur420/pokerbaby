package io.pokerbaby.engine;

/** Card ranks in ascending poker order. */
public enum Rank {
    /** Two. */ TWO('2', 2),
    /** Three. */ THREE('3', 3),
    /** Four. */ FOUR('4', 4),
    /** Five. */ FIVE('5', 5),
    /** Six. */ SIX('6', 6),
    /** Seven. */ SEVEN('7', 7),
    /** Eight. */ EIGHT('8', 8),
    /** Nine. */ NINE('9', 9),
    /** Ten. */ TEN('T', 10),
    /** Jack. */ JACK('J', 11),
    /** Queen. */ QUEEN('Q', 12),
    /** King. */ KING('K', 13),
    /** Ace; the evaluator will also support the five-high straight. */ ACE('A', 14);

    private final char symbol;
    private final int value;

    Rank(char symbol, int value) {
        this.symbol = symbol;
        this.value = value;
    }

    /**
     * Returns the canonical text symbol.
     * @return the single uppercase rank symbol
     */
    public char symbol() {
        return symbol;
    }

    /**
     * Returns the rank's ordinary numeric strength.
     * @return the numeric rank, from 2 to 14
     */
    public int value() {
        return value;
    }

    /**
     * Parses the canonical rank notation.
     * @param symbol one of {@code 23456789TJQKA}
     * @return the rank
     * @throws IllegalArgumentException if the symbol is invalid
     */
    public static Rank fromSymbol(char symbol) {
        for (Rank rank : values()) {
            if (rank.symbol == symbol) {
                return rank;
            }
        }
        throw new IllegalArgumentException("Invalid rank: " + symbol);
    }
}
