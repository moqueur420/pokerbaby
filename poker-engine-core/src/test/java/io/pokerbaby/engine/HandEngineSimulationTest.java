package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.pokerbaby.engine.LegalActions.BetRange;

import static io.pokerbaby.engine.HandEngineTest.META;
import static org.junit.jupiter.api.Assertions.*;

class HandEngineSimulationTest {
    @Test
    @DisplayName("С-2—С-8, Д-1—Д-5, Р-1—Р-3: 20000 seeded hands, private decisions and independent chip ledger")
    void diversifiedHandsConserveChipsAndSettle() {
        Random random = new Random(20261113);
        for (int index = 0; index < 20_000; index++) {
            String context = "seed=20261113, hand=" + index;
            GameConfig config = switch (index % 4) {
                case 0 -> GameConfig.DEFAULT;
                case 1 -> new GameConfig(1, 1);
                case 2 -> new GameConfig(1, 100);
                default -> {
                    long small = random.nextLong(1, 1_001);
                    yield new GameConfig(small, random.nextLong(small, 2_001));
                }
            };
            SeatChips starting = switch (index % 6) {
                case 0 -> SeatChips.of(random.nextLong(1, 151), random.nextLong(1, 151));
                case 1 -> SeatChips.of(1, SeatChips.MAX_TOTAL - 1);
                case 2 -> SeatChips.of(SeatChips.MAX_TOTAL - 1, 1);
                case 3 -> SeatChips.of(SeatChips.MAX_INITIAL_STACK, SeatChips.MAX_INITIAL_STACK);
                default -> SeatChips.of(random.nextLong(1, 20_001), random.nextLong(1, 20_001));
            };
            Deck deck = Deck.shuffled(random);
            HandState state = HandEngine.start(config, META, starting, random.nextInt(2), deck);
            int decisions = 0;
            while (!state.isComplete()) {
                assertTrue(decisions++ < 80, context);
                assertEquals(starting.total(), Math.addExact(state.stacks().total(), state.pot()), context);
                assertEquals(state.contributions().total(), state.pot(), context);
                int seat = state.actingSeat().orElseThrow();
                PlayerView view = state.viewFor(seat);
                assertTrue(view.isMyTurn(), context);
                assertTrue(view.opponentCards().isEmpty(), context);
                assertTrue(view.result().isEmpty(), context);
                for (HandEvent event : view.history().events()) {
                    if (event instanceof HandEvent.DealHole hole) {
                        assertEquals(seat, hole.seat(), context);
                    }
                }
                // Deterministic passive runs exercise all streets; random runs include
                // minimum, maximum and intermediate wagers. After 64 decisions, finish passively.
                Action action = choose(view, random, index % 5 == 0 || decisions > 64);
                int oldHash = state.hashCode();
                List<HandEvent> prefix = state.history().events();
                HandState next = HandEngine.apply(state, seat, action);
                assertEquals(oldHash, state.hashCode(), context);
                assertEquals(prefix, next.history().events().subList(0, prefix.size()), context);
                assertNotSame(state, next, context);
                state = next;
            }
            HandResult result = state.result().orElseThrow();
            assertEquals(starting.total(), state.stacks().total(), context);
            assertEquals(0, state.pot(), context);
            assertEquals(0, state.streetBets().total(), context);
            assertEquals(0, state.contributions().total(), context);
            assertTrue(state.actingSeat().isEmpty(), context);
            assertEquals(result.pot(), result.payouts().total(), context);
            assertEquals(result.contributions().get(0), result.contributions().get(1), context);
            assertEquals(0, deck.position(), context);
            assertEquals(52, deck.remaining(), context);
            verifyLedger(state, context);
            if (result.wasShowdown()) {
                assertEquals(5, state.board().size(), context);
                List<Integer> scores = new ArrayList<>(2);
                for (int seat = 0; seat < 2; seat++) {
                    List<Card> available = new ArrayList<>(state.board());
                    available.addAll(state.holeCards().get(seat));
                    int score = FiveCardReference.bestScore(available);
                    scores.add(score);
                    assertEquals(score, FiveCardReference.score(result.ranks().get(seat).cards()), context);
                    assertEquals(state.holeCards().get(1 - seat),
                            state.viewFor(seat).opponentCards().orElseThrow(), context);
                }
                int comparison = Integer.compare(scores.get(0), scores.get(1));
                assertEquals(comparison == 0 ? OptionalInt.empty() : OptionalInt.of(comparison > 0 ? 0 : 1),
                        result.winnerSeat(), context);
            } else {
                assertTrue(state.viewFor(0).opponentCards().isEmpty(), context);
                assertTrue(state.viewFor(1).opponentCards().isEmpty(), context);
            }
        }
    }

    private static Action choose(PlayerView view, Random random, boolean passive) {
        LegalActions legal = view.legalActions();
        if (passive) {
            return legal.canCheck() ? Action.check() : Action.call();
        }
        List<ActionType> types = legal.types().stream().sorted(Comparator.naturalOrder()).toList();
        ActionType type = types.get(random.nextInt(types.size()));
        return switch (type) {
            case FOLD -> Action.fold();
            case CHECK -> Action.check();
            case CALL -> Action.call();
            case BET -> Action.betTo(size(legal.bet().orElseThrow(), random));
            case RAISE -> Action.raiseTo(size(legal.raise().orElseThrow(), random));
        };
    }

    private static long size(BetRange range, Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> range.minTo();
            case 1 -> range.maxTo();
            default -> random.nextLong(range.minTo(), range.maxTo() + 1);
        };
    }

    // A test-only ledger interprets recorded amounts independently of engine transitions.
    private static void verifyLedger(HandState end, String context) {
        long[] stacks = {end.history().header().startingStacks().get(0),
                end.history().header().startingStacks().get(1)};
        long[] bets = {0, 0};
        long[] net = {0, 0};
        long[] refunds = {0, 0};
        long[] payouts = {0, 0};
        long supply = stacks[0] + stacks[1];
        long bank = 0;
        for (HandEvent event : end.history().events()) {
            int seat = -1;
            long paid = 0;
            if (event instanceof HandEvent.PostBlind blind) {
                seat = blind.seat();
                paid = blind.amount();
            } else if (event instanceof HandEvent.ActionTaken taken) {
                seat = taken.seat();
                paid = switch (taken.action().type()) {
                    case BET, RAISE -> taken.action().to().orElseThrow() - bets[seat];
                    case CALL -> Math.min(stacks[seat], bets[1 - seat] - bets[seat]);
                    case FOLD, CHECK -> 0;
                };
            } else if (event instanceof HandEvent.DealBoard) {
                bets[0] = 0;
                bets[1] = 0;
            } else if (event instanceof HandEvent.ReturnUncalled returned) {
                seat = returned.seat();
                paid = -returned.amount();
                refunds[seat] += returned.amount();
            } else if (event instanceof HandEvent.Payout payout) {
                stacks[payout.seat()] += payout.amount();
                payouts[payout.seat()] += payout.amount();
                bank -= payout.amount();
            }
            if (seat >= 0) {
                stacks[seat] -= paid;
                bets[seat] += paid;
                net[seat] += paid;
                bank += paid;
            }
            assertEquals(supply, stacks[0] + stacks[1] + bank, context);
            assertTrue(stacks[0] >= 0 && stacks[1] >= 0 && bank >= 0 && bets[0] >= 0 && bets[1] >= 0, context);
        }
        HandResult result = end.result().orElseThrow();
        assertEquals(0, bank, context);
        assertEquals(SeatChips.of(stacks[0], stacks[1]), end.stacks(), context);
        assertEquals(SeatChips.of(net[0], net[1]), result.contributions(), context);
        assertEquals(SeatChips.of(refunds[0], refunds[1]), result.returnedChips(), context);
        assertEquals(SeatChips.of(payouts[0], payouts[1]), result.payouts(), context);
    }
}
