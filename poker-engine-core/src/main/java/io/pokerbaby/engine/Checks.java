package io.pokerbaby.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

final class Checks {
    private Checks() {
    }

    static int seat(int seat) {
        if (seat < 0 || seat >= 2) {
            throw new IllegalArgumentException("Seat must be 0 or 1: " + seat);
        }
        return seat;
    }

    static String identifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static long positiveChips(long amount) {
        if (amount <= 0 || amount > SeatChips.MAX_TOTAL) {
            throw new IllegalArgumentException("Chip amount must be between 1 and " + SeatChips.MAX_TOTAL);
        }
        return amount;
    }

    static List<Card> cards(List<Card> cards, int count) {
        List<Card> copy = List.copyOf(cards);
        if (copy.size() != count || new HashSet<>(copy).size() != count) {
            throw new IllegalArgumentException("Expected " + count + " distinct cards");
        }
        return copy;
    }

    static List<List<Card>> holeCards(List<List<Card>> hands) {
        if (hands.size() != 2) {
            throw new IllegalArgumentException("Exactly two hands are required");
        }
        return List.of(cards(hands.get(0), 2), cards(hands.get(1), 2));
    }

    @SafeVarargs
    static void disjointCards(List<Card>... groups) {
        long seen = 0;
        for (List<Card> group : groups) {
            for (Card card : group) {
                long bit = 1L << (card.rank().ordinal() * 4 + card.suit().ordinal());
                if ((seen & bit) != 0) {
                    throw new IllegalArgumentException("Repeated dealt or remaining card: " + card);
                }
                seen |= bit;
            }
        }
    }
}
