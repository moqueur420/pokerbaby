package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Immutable card order and draw position. Drawing leaves the original deck valid.
 * A deck may be partial or empty; starting a hand requires nine remaining cards.
 */
public final class Deck {
    private final List<Card> cards;
    private final int position;

    private Deck(List<Card> cards, int position) {
        this.cards = cards;
        this.position = position;
    }

    /**
     * Creates a deck in the supplied dealing order.
     * @param cards distinct non-null cards, possibly an empty or partial deck
     * @return a deck positioned before its first card
     * @throws IllegalArgumentException if a card is repeated
     */
    public static Deck ordered(List<Card> cards) {
        List<Card> copy = List.copyOf(cards);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("A deck must not contain duplicate cards");
        }
        return new Deck(copy, 0);
    }

    /**
     * Creates all 52 cards: clubs, diamonds, hearts, spades; 2 through ace in each suit.
     * @return the canonical unshuffled deck
     */
    public static Deck standard() {
        List<Card> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        return new Deck(List.copyOf(cards), 0);
    }

    /**
     * Shuffles the canonical deck using reverse Fisher-Yates and {@link Random}.
     * The same seed always produces the same order; use the source overload for live games.
     * @param seed explicit deterministic seed
     * @return the shuffled deck
     */
    public static Deck shuffled(long seed) {
        return shuffled(new Random(seed));
    }

    /**
     * Shuffles the canonical deck, swapping indices 51 down to 1 with
     * {@code random.nextInt(index + 1)}. The source is consumed but never retained.
     * @param random caller-owned source; a secure source can be supplied by a server
     * @return the shuffled deck
     */
    public static Deck shuffled(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        List<Card> cards = new ArrayList<>(standard().cards);
        for (int index = cards.size() - 1; index > 0; index--) {
            Collections.swap(cards, index, random.nextInt(index + 1));
        }
        return new Deck(List.copyOf(cards), 0);
    }

    /**
     * Returns the complete stored order.
     * @return the immutable original card order, including already drawn cards
     */
    public List<Card> cards() {
        return cards;
    }

    /**
     * Returns the current draw cursor.
     * @return the zero-based position of the next card
     */
    public int position() {
        return position;
    }

    /**
     * Counts the cards available for future draws.
     * @return the number of undrawn cards
     */
    public int remaining() {
        return cards.size() - position;
    }

    /**
     * Returns cards that have not been drawn.
     * @return the immutable sequence of undrawn cards
     */
    public List<Card> remainingCards() {
        return cards.subList(position, cards.size());
    }

    /**
     * Checks that a consumer can deal the requested number of cards.
     * @param count required number of remaining cards, zero or greater
     * @throws IllegalArgumentException if count is negative or exceeds the remainder
     */
    public void requireRemaining(int count) {
        if (count < 0 || count > remaining()) {
            throw new IllegalArgumentException("Not enough cards for requested count: " + count);
        }
    }

    /**
     * Deals the next card and returns the advanced deck without modifying this one.
     * @return the card and new deck
     * @throws IllegalStateException if the deck is exhausted
     */
    public Draw draw() {
        if (remaining() == 0) {
            throw new IllegalStateException("The deck is exhausted");
        }
        return new Draw(cards.get(position), new Deck(cards, position + 1));
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Deck deck
                && position == deck.position && cards.equals(deck.cards);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(cards, position);
    }

    /** @return the draw position and remaining card count */
    @Override
    public String toString() {
        return "Deck[position=" + position + ", remaining=" + remaining() + "]";
    }

    /**
     * Result of one immutable draw operation.
     * @param card dealt card
     * @param deck deck positioned after that card
     */
    public record Draw(Card card, Deck deck) {
        /**
         * Creates a draw result.
         * @param card non-null dealt card
         * @param deck non-null advanced deck
         */
        public Draw {
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(deck, "deck");
        }
    }
}
