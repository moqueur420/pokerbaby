package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;

/**
 * Immutable five-card result. Comparison uses poker strength only; record equality
 * also includes the actual cards. Different physical hands may therefore compare as tied.
 * @param category one of the nine categories
 * @param cards the best five cards in combination order, with stable suit selection
 * @param comparisonKey integer strength, increasing with category and tie breakers
 */
public record HandRank(HandCategory category, List<Card> cards, int comparisonKey)
        implements Comparable<HandRank> {
    /**
     * Validates strength against the supplied five cards and canonicalizes their order.
     * @param category non-null declared category
     * @param cards five distinct non-null cards
     * @param comparisonKey exact strength of these cards
     * @throws IllegalArgumentException if category or key contradict the cards
     */
    public HandRank {
        Objects.requireNonNull(category, "category");
        long mask = HandValues.mask(cards, 5, 5);
        int actual = HandValues.score(mask);
        if (comparisonKey != actual || category != HandValues.category(actual)) {
            throw new IllegalArgumentException("Hand category or comparison key contradicts the five cards");
        }
        cards = HandValues.bestCards(mask, actual);
    }

    /**
     * Compares poker strength, ignoring suits and card identities.
     * @param other non-null result
     * @return a positive number if this hand wins, zero on a tie, otherwise negative
     */
    @Override
    public int compareTo(HandRank other) {
        return Integer.compare(comparisonKey, Objects.requireNonNull(other, "other").comparisonKey);
    }
}
