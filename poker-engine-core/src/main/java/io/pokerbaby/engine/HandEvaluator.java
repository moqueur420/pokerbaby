package io.pokerbaby.engine;

import java.util.Collection;

/** Stateless evaluator for five to seven cards; all public results are immutable. */
public final class HandEvaluator {
    private HandEvaluator() {
    }

    /**
     * Selects the strongest five-card combination from the supplied cards.
     * Selection is independent of input order; equal-strength alternatives use
     * clubs, diamonds, hearts, then spades to choose physical cards of equal rank.
     * This display convention does not affect poker comparison.
     * @param cards five, six, or seven distinct non-null cards
     * @return category, canonical best five cards, and their comparison key
     * @throws IllegalArgumentException if the size is invalid or a card is duplicated
     * @throws NullPointerException if the collection or a card is null
     */
    public static HandRank evaluate(Collection<Card> cards) {
        long mask = HandValues.mask(cards, 5, 7);
        int score = HandValues.score(mask);
        return new HandRank(HandValues.category(score), HandValues.bestCards(mask, score), score);
    }
}
