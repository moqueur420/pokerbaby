package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class ShuffleTest {
    private static final List<Card> FOUR_CARDS = cards("2c 3c 4c 5c");

    @Test
    @DisplayName("К-4, К-6, П-9: supplied card orders can be shuffled immutably")
    void shufflesPartialDecks() {
        List<Card> input = new ArrayList<>(FOUR_CARDS);
        Deck first = Deck.shuffled(input, 42);
        assertEquals(FOUR_CARDS, input);
        input.clear();
        assertEquals(new HashSet<>(FOUR_CARDS), new HashSet<>(first.cards()));
        assertEquals(4, first.remaining());
        assertEquals(0, first.position());
        assertEquals(first, Deck.shuffled(FOUR_CARDS, new Random(42)));
        assertThrows(UnsupportedOperationException.class, () -> first.cards().clear());
        assertThrows(IllegalArgumentException.class, () -> Deck.shuffled(cards("As As"), 42));
        assertThrows(NullPointerException.class, () -> Deck.shuffled(null, 42));
        assertThrows(NullPointerException.class,
                () -> Deck.shuffled(Arrays.asList(Card.parse("As"), null), 42));
        assertThrows(NullPointerException.class, () -> Deck.shuffled(FOUR_CARDS, null));
    }

    @Test
    @DisplayName("К-6: empty and singleton decks consume no randomness")
    void trivialDecksNeedNoRandomDraws() {
        ScriptedRandom random = new ScriptedRandom();
        assertEquals(List.of(), Deck.shuffled(List.of(), random).cards());
        assertEquals(cards("As"), Deck.shuffled(cards("As"), random).cards());
        assertEquals(0, random.consumed());
    }

    @Test
    @DisplayName("К-6: all 4*3*2 index paths produce exactly the 24 permutations")
    void everyIndexPathIsOnePermutation() {
        Set<List<Card>> permutations = new HashSet<>();
        for (int lastIndex = 0; lastIndex < 4; lastIndex++) {
            for (int middleIndex = 0; middleIndex < 3; middleIndex++) {
                for (int firstIndex = 0; firstIndex < 2; firstIndex++) {
                    ScriptedRandom random = new ScriptedRandom(
                            lastIndex << 30, middleIndex << 1, firstIndex << 31);
                    Deck shuffled = Deck.shuffled(FOUR_CARDS, random);
                    assertEquals(3, random.consumed());
                    assertEquals(new HashSet<>(FOUR_CARDS), new HashSet<>(shuffled.cards()));
                    assertTrue(permutations.add(shuffled.cards()), "Two index paths gave the same permutation");
                }
            }
        }
        assertEquals(24, permutations.size());
    }

    @Test
    @DisplayName("К-7: values at and above the divisible-range limit are rejected")
    void rejectsTheEntireIncompleteBucket() {
        // For bound 3 the accepted 31-bit range is [0, 2147483646).
        // Raw -1 and -3 become 2147483647 and 2147483646; -5 is the last accepted sample.
        ScriptedRandom random = new ScriptedRandom(0, -1, -3, -5, 0);
        Deck shuffled = Deck.shuffled(FOUR_CARDS, random);
        assertEquals(5, random.consumed(), "Both out-of-range samples must request a new draw");
        assertEquals(cards("3c 5c 4c 2c"), shuffled.cards());
    }

    @Test
    @DisplayName("К-7: power-of-two intervals accept the entire range without bias")
    void powerOfTwoIntervalsNeedNoRejection() {
        ScriptedRandom random = new ScriptedRandom(-1, -5, -1);
        assertEquals(FOUR_CARDS, Deck.shuffled(FOUR_CARDS, random).cards());
        assertEquals(3, random.consumed());
    }

    @Test
    @DisplayName("К-6, К-7, §8.4: 2.4 million shuffles meet all 24 frequency bounds")
    void fourCardPermutationFrequencies() {
        int trials = 2_400_000;
        Random random = new Random(42);
        Map<List<Card>, Integer> frequencies = new HashMap<>();
        for (int trial = 0; trial < trials; trial++) {
            List<Card> permutation = Deck.shuffled(FOUR_CARDS, random).cards();
            frequencies.merge(permutation, 1, Integer::sum);
        }
        assertEquals(24, frequencies.size());
        assertEquals(trials, frequencies.values().stream().mapToInt(Integer::intValue).sum());
        for (Map.Entry<List<Card>, Integer> entry : frequencies.entrySet()) {
            int count = entry.getValue();
            assertTrue(count >= 99_000 && count <= 101_000,
                    () -> entry.getKey() + " occurred " + count + " times; expected 99000..101000");
        }
        int minimum = frequencies.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int maximum = frequencies.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        System.out.printf("Four-card shuffle: seed=42, trials=%d, permutations=%d, min=%d, max=%d%n",
                trials, frequencies.size(), minimum, maximum);
    }

    private static final class ScriptedRandom implements RandomGenerator {
        private final int[] values;
        private int position;

        private ScriptedRandom(int... values) {
            this.values = values.clone();
        }

        @Override
        public int nextInt() {
            if (position == values.length) {
                throw new AssertionError("Unexpected extra random draw");
            }
            return values[position++];
        }

        @Override
        public int nextInt(int bound) {
            throw new AssertionError("The deck must not delegate rejection sampling");
        }

        @Override
        public long nextLong() {
            throw new AssertionError("Only raw nextInt() should be requested");
        }

        private int consumed() {
            return position;
        }
    }
}
