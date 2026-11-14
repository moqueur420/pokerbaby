package io.pokerbaby.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.pokerbaby.engine.Card;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.HandEvaluator;
import io.pokerbaby.engine.HandRank;
import io.pokerbaby.engine.HandValuesBenchmark;

/** Preliminary warmed-up measurement of the complete public evaluator API. */
public final class HandEvaluatorBenchmark {
    private static final int CORPUS_SIZE = 4_096;
    private static final int ITERATIONS = 1_000_000;
    private static volatile HandRank lastResult;

    private HandEvaluatorBenchmark() {
    }

    /**
     * Runs a deterministic corpus on one application thread without the coverage agent.
     * @param args unused
     */
    public static void main(String[] args) {
        List<List<Card>> corpus = new ArrayList<>(CORPUS_SIZE);
        Random random = new Random(20261113);
        for (int index = 0; index < CORPUS_SIZE; index++) {
            corpus.add(List.copyOf(Deck.shuffled(random).cards().subList(0, 7)));
        }
        BenchmarkSupport.environment();
        System.out.println("Corpus: " + CORPUS_SIZE + " seven-card hands; seed=20261113; shuffle excluded");
        BenchmarkSupport.measure("HandEvaluator.evaluate(7 cards)", ITERATIONS, () -> run(corpus));
        HandValuesBenchmark.measure(corpus);
    }

    private static long run(List<List<Card>> corpus) {
        long checksum = 0;
        for (int index = 0; index < ITERATIONS; index++) {
            HandRank result = HandEvaluator.evaluate(corpus.get(index & (CORPUS_SIZE - 1)));
            checksum += result.comparisonKey() + result.cards().getFirst().rank().value();
            // Escaping results prevents measuring a score-only path after dead-code elimination.
            lastResult = result;
        }
        return checksum;
    }
}
