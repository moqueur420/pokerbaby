package io.pokerbaby.engine;

/** Card suits in the canonical unshuffled deck order. */
public enum Suit {
    /** Clubs. */ CLUBS('c'),
    /** Diamonds. */ DIAMONDS('d'),
    /** Hearts. */ HEARTS('h'),
    /** Spades. */ SPADES('s');

    private final char symbol;

    Suit(char symbol) {
        this.symbol = symbol;
    }

    /**
     * Returns the canonical text symbol.
     * @return the lowercase suit symbol
     */
    public char symbol() {
        return symbol;
    }

    /**
     * Parses the canonical suit notation.
     * @param symbol one of {@code cdhs}
     * @return the suit
     * @throws IllegalArgumentException if the symbol is invalid
     */
    public static Suit fromSymbol(char symbol) {
        for (Suit suit : values()) {
            if (suit.symbol == symbol) {
                return suit;
            }
        }
        throw new IllegalArgumentException("Invalid suit: " + symbol);
    }
}
