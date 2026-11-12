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
}
