package io.pokerbaby.engine;

import java.util.Objects;

/**
 * An immutable playing card, formatted as two characters such as {@code As}.
 * @param rank card rank
 * @param suit card suit
 */
public record Card(Rank rank, Suit suit) {
    /**
     * Creates a card.
     * @param rank non-null rank
     * @param suit non-null suit
     */
    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    /**
     * Parses exactly two characters; rank is uppercase and suit is lowercase.
     * @param text canonical card notation
     * @return the card
     * @throws IllegalArgumentException if the notation is invalid
     */
    public static Card parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() != 2) {
            throw new IllegalArgumentException("A card must contain exactly two characters: " + text);
        }
        return new Card(Rank.fromSymbol(text.charAt(0)), Suit.fromSymbol(text.charAt(1)));
    }

    /** @return canonical two-character notation */
    @Override
    public String toString() {
        return "" + rank.symbol() + suit.symbol();
    }
}
