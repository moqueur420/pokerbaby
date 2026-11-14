package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Frozen input generation shared by the version-1 golden corpus and fuzz checks. */
final class SeededHands {
    static final long SEED = 20261114L;
    static final int GOLDEN_HANDS = 10_000;
    private static final int PASSIVE_AFTER = 64;
    private static final int MAX_DECISIONS = 128;

    private SeededHands() {
    }

    static PlayedHand play(int index) {
        long seed = SEED + index;
        Random inputs = new Random(seed);
        long small = inputs.nextLong(1, 1_001);
        GameConfig config = switch (index % 12) {
            case 7 -> new GameConfig(1, SeatChips.MAX_INITIAL_STACK);
            case 8, 10 -> new GameConfig(small, small);
            case 9, 11 -> GameConfig.DEFAULT;
            default -> new GameConfig(small, inputs.nextLong(small, 2_001));
        };
        SeatChips stacks = switch (index % 12) {
            case 1 -> SeatChips.of(inputs.nextLong(1, config.smallBlind() + 1),
                    inputs.nextLong(1, config.bigBlind() + 1));
            case 2 -> SeatChips.of(1, inputs.nextLong(2_001, 20_001));
            case 3 -> SeatChips.of(inputs.nextLong(2_001, 20_001), 1);
            case 4, 7 -> SeatChips.of(SeatChips.MAX_INITIAL_STACK, SeatChips.MAX_INITIAL_STACK);
            case 5 -> SeatChips.of(SeatChips.MAX_TOTAL - 1, 1);
            case 6 -> SeatChips.of(1, SeatChips.MAX_TOTAL - 1);
            case 11 -> SeatChips.of(config.smallBlind(), config.bigBlind());
            default -> SeatChips.of(inputs.nextLong(1, 20_001), inputs.nextLong(1, 20_001));
        };
        int button = inputs.nextInt(2);
        long deckSeed = inputs.nextLong();
        long actionSeed = inputs.nextLong();
        HandMeta meta = new HandMeta("golden-v1-h" + index, List.of("p0", "p1"));
        HandState state = HandEngine.start(config, meta, stacks, button, Deck.shuffled(deckSeed));
        List<HandState> states = new ArrayList<>(List.of(state));
        List<HandEvent.ActionTaken> actions = new ArrayList<>();
        Random decisions = new Random(actionSeed);
        while (!state.isComplete()) {
            if (actions.size() >= MAX_DECISIONS) {
                throw new AssertionError("Non-terminating hand: index=" + index + ", seed=" + seed);
            }
            int seat = state.actingSeat().orElseThrow();
            Action action = choose(state.viewFor(seat).legalActions(), decisions,
                    index % 5 == 0 || actions.size() >= PASSIVE_AFTER);
            actions.add(new HandEvent.ActionTaken(seat, action));
            state = HandEngine.apply(state, seat, action);
            states.add(state);
        }
        return new PlayedHand(index, seed, deckSeed, actionSeed, List.copyOf(states), List.copyOf(actions));
    }

    private static Action choose(LegalActions legal, Random random, boolean passive) {
        if (passive) {
            return legal.canCheck() ? Action.check() : Action.call();
        }
        // Set.copyOf deliberately has no iteration-order guarantee across JVM processes.
        List<ActionType> types = new ArrayList<>(legal.types());
        types.sort(Comparator.naturalOrder());
        return switch (types.get(random.nextInt(types.size()))) {
            case FOLD -> Action.fold();
            case CHECK -> Action.check();
            case CALL -> Action.call();
            case BET -> Action.betTo(wager(legal.bet().orElseThrow(), random));
            case RAISE -> Action.raiseTo(wager(legal.raise().orElseThrow(), random));
        };
    }

    private static long wager(LegalActions.BetRange range, Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> range.minTo();
            case 1 -> range.maxTo();
            default -> random.nextLong(range.minTo(), range.maxTo() + 1);
        };
    }

    record PlayedHand(int index, long seed, long deckSeed, long actionSeed,
                      List<HandState> states, List<HandEvent.ActionTaken> actions) {
        HandState end() {
            return states.getLast();
        }

        String goldenLine() {
            HandState first = states.getFirst();
            return "hand=" + index + ";seed=" + seed + ";deckSeed=" + deckSeed + ";actionSeed=" + actionSeed
                    + ";config=" + first.config() + ";starting=" + first.history().header().startingStacks()
                    + ";button=" + first.buttonSeat() + ";events=" + end().history().events()
                    + ";final=" + end().stacks();
        }
    }
}
