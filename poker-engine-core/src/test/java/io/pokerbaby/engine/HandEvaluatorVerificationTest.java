package io.pokerbaby.engine;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorVerificationTest {
    @Test
    @Tag("slow")
    @DisplayName("О-2, §8.1: enumerate all 2598960 five-card hands and match exact category totals")
    void allFiveCardHands() {
        List<Card> deck = Deck.standard().cards();
        long[] frequencies = new long[9];
        for (int a = 0; a < 48; a++) {
            for (int b = a + 1; b < 49; b++) {
                for (int c = b + 1; c < 50; c++) {
                    for (int d = c + 1; d < 51; d++) {
                        for (int e = d + 1; e < 52; e++) {
                            HandRank rank = HandEvaluator.evaluate(List.of(
                                    deck.get(a), deck.get(b), deck.get(c), deck.get(d), deck.get(e)));
                            frequencies[rank.category().ordinal()]++;
                        }
                    }
                }
            }
        }
        assertArrayEquals(new long[] {1_302_540, 1_098_240, 123_552, 54_912, 10_200, 5_108, 3_744, 624, 40},
                frequencies);
        long total = 0;
        for (long frequency : frequencies) {
            total += frequency;
        }
        assertEquals(2_598_960, total);
        System.out.println("Five-card exhaustive frequencies: " + java.util.Arrays.toString(frequencies));
    }

    @Test
    @Tag("slow")
    @DisplayName("О-1, О-4, §8.1: 50000 seven-card hands match an independent 21-subset oracle")
    void randomSevenCardHands() {
        verifySamples(7, 50_000, 20261113);
    }

    @Test
    @Tag("slow")
    @DisplayName("О-1, О-4: 10000 six-card hands match an independent six-subset oracle")
    void randomSixCardHands() {
        verifySamples(6, 10_000, 20261114);
    }

    @Test
    @DisplayName("О-2: 1000 five-card hands match an independent oracle in the fast suite")
    void quickFiveCardHands() {
        verifySamples(5, 1_000, 20261115);
    }

    @Test
    @DisplayName("О-1, О-4: 1000 seven-card hands match the 21-subset oracle in the fast suite")
    void quickSevenCardHands() {
        verifySamples(7, 1_000, 20261113);
    }

    @Test
    @DisplayName("О-1, О-4: 1000 six-card hands match the six-subset oracle in the fast suite")
    void quickSixCardHands() {
        verifySamples(6, 1_000, 20261114);
    }

    private static void verifySamples(int cardCount, int samples, long seed) {
        Random random = new Random(seed);
        for (int sample = 0; sample < samples; sample++) {
            List<Card> input = Deck.shuffled(random).cards().subList(0, cardCount);
            HandRank actual = HandEvaluator.evaluate(input);
            int expected = FiveCardReference.bestScore(input);
            int actualReference = FiveCardReference.score(actual.cards());
            assertEquals(expected, actualReference, () -> "Input " + input + ", result " + actual);
            assertEquals(expected >>> 20, actual.category().ordinal());
            assertEquals(5, actual.cards().size());
            assertTrue(input.containsAll(actual.cards()));
        }
    }
}
