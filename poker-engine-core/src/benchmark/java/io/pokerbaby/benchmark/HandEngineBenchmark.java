package io.pokerbaby.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.pokerbaby.engine.Action;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.GameConfig;
import io.pokerbaby.engine.HandEngine;
import io.pokerbaby.engine.HandMeta;
import io.pokerbaby.engine.HandState;
import io.pokerbaby.engine.LegalActions;
import io.pokerbaby.engine.PlayerView;
import io.pokerbaby.engine.SeatChips;

/** Single-thread baseline cases for complete fixed and random hands, chip updates and shuffling. */
public final class HandEngineBenchmark {
    private static final int CORPUS_SIZE = 4_096;
    private static final int ITERATIONS = 100_000;
    private static final HandMeta META = new HandMeta("benchmark-h1", List.of("p0", "p1"));
    private static final SeatChips STACKS = SeatChips.of(10_000, 10_000);
    private static final List<Action> ACTIONS = List.of(Action.raiseTo(250), Action.call(),
            Action.betTo(300), Action.call(), Action.check(), Action.check(), Action.betTo(500), Action.call());
    private static volatile HandState lastState;

    private HandEngineBenchmark() {
    }

    /**
     * Runs timed warmup and three measured passes per case on one application thread.
     * @param args unused
     */
    public static void main(String[] args) {
        List<Deck> corpus = new ArrayList<>(CORPUS_SIZE);
        Random random = new Random(20261113);
        for (int index = 0; index < CORPUS_SIZE; index++) {
            corpus.add(Deck.shuffled(random));
        }
        BenchmarkSupport.environment();
        System.out.println("Corpus: " + CORPUS_SIZE + " decks; seed=20261113; shuffle excluded from hand cases");
        System.out.println("Each hand: 8 actions, 8 player views, full history, showdown and payouts; shuffle excluded");
        BenchmarkSupport.measure("Fixed hand + PlayerView", ITERATIONS, () -> run(corpus));
        System.out.println("Random hands: uniform legal action types, min/max/random wager sizes; action seed=20261114");
        System.out.println("Blinds=50/100, stacks=10000/10000, button alternates; passive after 64 decisions");
        BenchmarkSupport.measure("Random hand (engine)", ITERATIONS, () -> randomHands(corpus, false, false));
        BenchmarkSupport.measure("Random hand + PlayerView", ITERATIONS, () -> randomHands(corpus, true, false));
        System.out.println("End-to-end case includes fresh Deck.shuffled(Random), seed=20261113, in the timed loop");
        BenchmarkSupport.measure("Random hand + shuffle + PlayerView", ITERATIONS,
                () -> randomHands(corpus, true, true));
        ValueBenchmark.measure();
    }

    private static long run(List<Deck> corpus) {
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
        return checksum;
    }

    private static long randomHands(List<Deck> corpus, boolean playerViews, boolean shuffle) {
        Random random = new Random(20261114);
        Random decks = new Random(20261113);
        long checksum = 0;
        for (int index = 0; index < ITERATIONS; index++) {
            HandState state = HandEngine.start(GameConfig.DEFAULT, META, STACKS,
                    index & 1, shuffle ? Deck.shuffled(decks) : corpus.get(index & (CORPUS_SIZE - 1)));
            int decisions = 0;
            while (!state.isComplete()) {
                int seat = state.actingSeat().orElseThrow();
                LegalActions legal;
                if (playerViews) {
                    PlayerView view = state.viewFor(seat);
                    legal = view.legalActions();
                    checksum += view.pot() + view.holeCards().getFirst().rank().value()
                            + view.history().events().size();
                } else {
                    legal = HandEngine.legalActions(state);
                }
                Action action = decisions++ < 64 ? choose(legal, random)
                        : legal.canCheck() ? Action.check() : Action.call();
                if (decisions > 128) {
                    throw new IllegalStateException("Benchmark policy did not finish a hand");
                }
                state = HandEngine.apply(state, seat, action);
            }
            checksum += state.stacks().get(0) + state.history().events().size();
            lastState = state;
        }
        return checksum;
    }

    private static Action choose(LegalActions legal, Random random) {
        int count = (legal.canFold() ? 1 : 0) + (legal.canCheck() ? 1 : 0) + (legal.canCall() ? 1 : 0)
                + (legal.canBet() ? 1 : 0) + (legal.canRaise() ? 1 : 0);
        int choice = random.nextInt(count);
        if (legal.canFold() && choice-- == 0) {
            return Action.fold();
        }
        if (legal.canCheck() && choice-- == 0) {
            return Action.check();
        }
        if (legal.canCall() && choice-- == 0) {
            return Action.call();
        }
        if (legal.canBet() && choice-- == 0) {
            return Action.betTo(wager(legal.bet().orElseThrow(), random));
        }
        return Action.raiseTo(wager(legal.raise().orElseThrow(), random));
    }

    private static long wager(LegalActions.BetRange range, Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> range.minTo();
            case 1 -> range.maxTo();
            default -> random.nextLong(range.minTo(), range.maxTo() + 1);
        };
    }
}
