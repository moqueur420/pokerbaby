package io.pokerbaby.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.pokerbaby.engine.Card;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.HandEvaluator;
import io.pokerbaby.engine.HandRank;

/** Preliminary warmed-up measurement of the complete public evaluator API. */
public final class HandEvaluatorBenchmark {
    private static final int CORPUS_SIZE = 4_096;
    private static final int WARMUP = 2_000_000;
    private static final int ITERATIONS = 1_000_000;
    private static volatile long sink;
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
        System.out.println("Java: " + System.getProperty("java.runtime.version"));
        System.out.println("VM: " + System.getProperty("java.vm.name"));
        System.out.println("OS: " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        System.out.println("Processor: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("Active processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Corpus: " + CORPUS_SIZE + " seven-card hands; seed=20261113; warmup=" + WARMUP);
        run(corpus, WARMUP);
        long totalNanos = 0;
        for (int pass = 1; pass <= 3; pass++) {
            long start = System.nanoTime();
            run(corpus, ITERATIONS);
            long nanos = System.nanoTime() - start;
            totalNanos += nanos;
            System.out.printf(Locale.ROOT, "Pass %d: %d evaluations, %.3f s, %.0f evaluations/s%n",
                    pass, ITERATIONS, nanos / 1_000_000_000.0, ITERATIONS * 1_000_000_000.0 / nanos);
        }
        System.out.printf(Locale.ROOT, "Aggregate: %.0f evaluations/s; checksum=%d; last=%d%n",
                3.0 * ITERATIONS * 1_000_000_000.0 / totalNanos, sink, lastResult.comparisonKey());
    }

    private static void run(List<List<Card>> corpus, int iterations) {
        long checksum = 0;
        for (int index = 0; index < iterations; index++) {
            HandRank result = HandEvaluator.evaluate(corpus.get(index & (CORPUS_SIZE - 1)));
            checksum += result.comparisonKey() + result.cards().getFirst().rank().value();
            // Escaping results prevents measuring a score-only path after dead-code elimination.
            lastResult = result;
        }
        sink = checksum;
    }
}
