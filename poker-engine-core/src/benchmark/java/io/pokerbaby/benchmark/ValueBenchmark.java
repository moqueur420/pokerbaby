package io.pokerbaby.benchmark;

import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.SeatChips;

/** Allocation-sensitive baseline cases for chip updates and complete-deck shuffling. */
final class ValueBenchmark {
    private static volatile SeatChips lastChips;
    private static volatile Deck lastDeck;

    private ValueBenchmark() {
    }

    static void measure() {
        SeatChips[] chips = new SeatChips[4_096];
        for (int index = 0; index < chips.length; index++) {
            chips[index] = SeatChips.of(10_000 + index, 20_000 + index);
        }
        BenchmarkSupport.measure("SeatChips.with", 10_000_000, () -> {
            long checksum = 0;
            for (int index = 0; index < 10_000_000; index++) {
                SeatChips updated = chips[index & (chips.length - 1)].with(index & 1, (index & 65_535) + 1L);
                checksum += updated.total();
                lastChips = updated;
            }
            return checksum;
        });
        BenchmarkSupport.measure("Deck.shuffled(seed)", 200_000, () -> {
            long checksum = 0;
            for (int index = 0; index < 200_000; index++) {
                Deck deck = Deck.shuffled(20261114L + index);
                checksum += deck.cards().getFirst().rank().value() + deck.remaining();
                lastDeck = deck;
            }
            return checksum;
        });
    }
}
