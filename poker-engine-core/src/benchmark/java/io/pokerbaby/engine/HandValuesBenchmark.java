package io.pokerbaby.engine;

import java.util.List;

import io.pokerbaby.benchmark.BenchmarkSupport;

/** Measures package-private evaluator arithmetic without adding API to the production JAR. */
public final class HandValuesBenchmark {
    private static final int ITERATIONS = 20_000_000;

    private HandValuesBenchmark() {
    }

    /**
     * Measures validation plus scoring and scoring of already prepared masks.
     * @param corpus the same 4096 seven-card hands used by the public evaluator benchmark
     */
    public static void measure(List<List<Card>> corpus) {
        long[] masks = new long[corpus.size()];
        for (int index = 0; index < masks.length; index++) {
            masks[index] = HandValues.mask(corpus.get(index), 5, 7);
        }
        BenchmarkSupport.measure("HandValues.mask+score", ITERATIONS, () -> {
            long checksum = 0;
            for (int index = 0; index < ITERATIONS; index++) {
                checksum += HandValues.score(HandValues.mask(corpus.get(index & (masks.length - 1)), 5, 7));
            }
            return checksum;
        });
        BenchmarkSupport.measure("HandValues.score(mask)", ITERATIONS, () -> {
            long checksum = 0;
            for (int index = 0; index < ITERATIONS; index++) {
                checksum += HandValues.score(masks[index & (masks.length - 1)]);
            }
            return checksum;
        });
    }
}
