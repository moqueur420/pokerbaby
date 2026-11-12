package io.pokerbaby.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.pokerbaby.engine.Action;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.GameConfig;
import io.pokerbaby.engine.HandEngine;
import io.pokerbaby.engine.HandMeta;
import io.pokerbaby.engine.HandState;
import io.pokerbaby.engine.PlayerView;
import io.pokerbaby.engine.SeatChips;

/** Preliminary throughput of eight decisions through showdown, including views and history. */
public final class HandEngineBenchmark {
    private static final int CORPUS_SIZE = 4_096;
    private static final int ITERATIONS = 100_000;
    private static final HandMeta META = new HandMeta("benchmark-h1", List.of("p0", "p1"));
    private static final SeatChips STACKS = SeatChips.of(10_000, 10_000);
    private static final List<Action> ACTIONS = List.of(Action.raiseTo(250), Action.call(),
            Action.betTo(300), Action.call(), Action.check(), Action.check(), Action.betTo(500), Action.call());
    private static volatile HandState lastState;
    private static volatile long sink;

    private HandEngineBenchmark() {
    }

    /**
     * Runs one warmup and three measured passes on a single application thread.
     * @param args unused
     */
    public static void main(String[] args) {
        List<Deck> corpus = new ArrayList<>(CORPUS_SIZE);
        Random random = new Random(20261113);
        for (int index = 0; index < CORPUS_SIZE; index++) {
            corpus.add(Deck.shuffled(random));
        }
        System.out.println("Java: " + System.getProperty("java.runtime.version"));
        System.out.println("VM: " + System.getProperty("java.vm.name"));
        System.out.println("OS: " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        System.out.println("Processor: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("Active processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Corpus: " + CORPUS_SIZE + " decks; seed=20261113; warmup=" + ITERATIONS);
        System.out.println("Each hand: 8 actions, 8 player views, full history, showdown and payouts; shuffle excluded");
        run(corpus);
        long totalNanos = 0;
        for (int pass = 1; pass <= 3; pass++) {
            long start = System.nanoTime();
            run(corpus);
            long nanos = System.nanoTime() - start;
            totalNanos += nanos;
            System.out.printf(Locale.ROOT, "Pass %d: %d hands, %.3f s, %.0f hands/s%n",
                    pass, ITERATIONS, nanos / 1_000_000_000.0, ITERATIONS * 1_000_000_000.0 / nanos);
        }
        System.out.printf(Locale.ROOT, "Aggregate: %.0f hands/s; checksum=%d; last-pot=%d%n",
                3.0 * ITERATIONS * 1_000_000_000.0 / totalNanos, sink, lastState.result().orElseThrow().pot());
    }

    private static void run(List<Deck> corpus) {
        long checksum = 0;
        for (int index = 0; index < ITERATIONS; index++) {
            HandState state = HandEngine.start(GameConfig.DEFAULT, META, STACKS,
                    index & 1, corpus.get(index & (CORPUS_SIZE - 1)));
            for (Action action : ACTIONS) {
                PlayerView view = state.viewFor(state.actingSeat().orElseThrow());
                // Consume the view so that the measurement includes client projection.
                checksum += view.pot() + view.holeCards().getFirst().rank().value()
                        + view.history().events().size() + view.legalActions().callAmount();
                state = HandEngine.apply(state, view.seat(), action);
            }
            checksum += state.stacks().get(0) + state.history().events().size();
            lastState = state;
        }
        sink = checksum;
    }
}
