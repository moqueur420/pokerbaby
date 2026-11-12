package io.pokerbaby.demo;

import java.util.List;
import java.util.Random;

import io.pokerbaby.engine.Action;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.GameConfig;
import io.pokerbaby.engine.HandCategory;
import io.pokerbaby.engine.HandEngine;
import io.pokerbaby.engine.HandEvent;
import io.pokerbaby.engine.HandMeta;
import io.pokerbaby.engine.HandRank;
import io.pokerbaby.engine.HandResult;
import io.pokerbaby.engine.HandState;
import io.pokerbaby.engine.LegalActions;
import io.pokerbaby.engine.LegalActions.BetRange;
import io.pokerbaby.engine.MatchState;
import io.pokerbaby.engine.PlayerView;
import io.pokerbaby.engine.SeatChips;
import io.pokerbaby.engine.Street;

/** Entry point for the poker engine demonstration. */
public final class PokerBabyDemo {
    private PokerBabyDemo() {
    }

    /**
     * Plays consecutive hands with a fixed illustrative betting policy.
     *
     * @param args optional numeric seed and positive hand limit; defaults to 42 and 2
     */
    public static void main(String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: PokerBabyDemo [seed] [maxHands]");
        }
        long seed = args.length == 0 ? 42 : Long.parseLong(args[0]);
        int maxHands = args.length < 2 ? 2 : Integer.parseInt(args[1]);
        if (maxHands < 1) {
            throw new IllegalArgumentException("The hand limit must be positive");
        }
        GameConfig config = GameConfig.DEFAULT;
        SeatChips stacks = SeatChips.of(SeatChips.DEFAULT_STACK, SeatChips.DEFAULT_STACK);
        MatchState match = MatchState.start("demo", List.of("human", "bot"), config, stacks, 0);
        Random random = new Random(seed);
        System.out.println("PokerBaby: этап 5 — матч.");
        System.out.println("Seed: " + seed);
        System.out.println("Блайнды: " + config.smallBlind() + "/" + config.bigBlind());
        System.out.println("Стартовые стеки матча: " + stacks);
        System.out.println("Лимит раздач: " + maxHands);
        while (!match.isOver() && match.completedHands() < maxHands) {
            Deck original = Deck.shuffled(random);
            HandState hand = match.nextHand(original);
            System.out.println();
            System.out.println("Раздача " + match.handNumber() + ": " + hand.meta().handId());
            match = match.afterHand(playHand(hand, original));
        }
        System.out.println();
        System.out.println("Сыграно раздач: " + match.completedHands());
        System.out.println("Итоговые стеки матча: " + match.stacks());
        if (match.isOver()) {
            System.out.println("Матч завершён. Победитель: "
                    + match.playerIds().get(match.winnerSeat().orElseThrow()) + ".");
        } else {
            System.out.println("Достигнут лимит показа; матч продолжается.");
            System.out.println("Следующая раздача: " + match.nextHandMeta().handId()
                    + ", баттон: " + match.playerIds().get(match.buttonSeat()));
        }
    }

    private static HandResult playHand(HandState state, Deck original) {
        HandMeta meta = state.meta();
        System.out.println("Стартовые стеки раздачи: " + state.history().header().startingStacks());
        System.out.println("Баттон: " + meta.playerId(state.buttonSeat()));
        printEvents(meta, Street.PREFLOP, state.history().events());
        while (!state.isComplete()) {
            PlayerView view = state.viewFor(state.actingSeat().orElseThrow());
            Action action = choose(view);
            HandState next = HandEngine.apply(state, view.seat(), action);
            printEvents(meta, state.street(),
                    next.history().events().subList(state.history().events().size(), next.history().events().size()));
            state = next;
        }
        HandResult result = state.result().orElseThrow();
        System.out.println("Общие карты: " + state.board());
        for (int seat = 0; seat < result.ranks().size(); seat++) {
            HandRank rank = result.ranks().get(seat);
            System.out.println("Комбинация " + meta.playerId(seat) + ": "
                    + categoryName(rank.category()) + " — " + rank.cards());
        }
        System.out.println(result.isTie() ? "Результат: ничья."
                : "Победитель: " + meta.playerId(result.winnerSeat().orElseThrow()) + ".");
        System.out.println("Сыгранный банк: " + result.pot());
        System.out.println("Выплаты: " + result.payouts());
        System.out.println("Итоговые стеки: " + result.finalStacks());
        System.out.println("Банк после выплаты: " + state.pot());
        System.out.println("Остаток колоды: " + state.deck().remaining());
        System.out.println("Исходная колода не изменилась: " + original.remaining() + " карты.");
        return result;
    }

    // The decision function has access only to one player's view.
    // This fixed policy illustrates each street; random bot policies are a later stage.
    private static Action choose(PlayerView view) {
        LegalActions legal = view.legalActions();
        if (view.street() == Street.PREFLOP && view.seat() == view.buttonSeat() && legal.canRaise()) {
            return Action.raiseTo(clamp(250, legal.raise().orElseThrow()));
        }
        if (view.seat() != view.buttonSeat() && legal.canBet()) {
            if (view.street() == Street.FLOP) {
                return Action.betTo(clamp(300, legal.bet().orElseThrow()));
            }
            if (view.street() == Street.RIVER) {
                return Action.betTo(clamp(500, legal.bet().orElseThrow()));
            }
        }
        return legal.canCheck() ? Action.check() : Action.call();
    }

    private static long clamp(long amount, BetRange range) {
        return Math.max(range.minTo(), Math.min(amount, range.maxTo()));
    }

    private static void printEvents(HandMeta meta, Street actingStreet, List<HandEvent> events) {
        for (HandEvent event : events) {
            switch (event) {
                case HandEvent.PostBlind blind -> System.out.println(
                        "Блайнд " + meta.playerId(blind.seat()) + ": " + blind.amount());
                case HandEvent.DealHole hole -> System.out.println("Закрытые карты сданы " + meta.playerId(hole.seat()));
                case HandEvent.ActionTaken taken -> System.out.println(actingStreet + " — "
                        + meta.playerId(taken.seat()) + ": " + taken.action().type()
                        + (taken.action().to().isPresent() ? " до " + taken.action().to().orElseThrow() : ""));
                case HandEvent.DealBoard board -> System.out.println(board.street() + ": " + board.cards());
                case HandEvent.ReturnUncalled returned -> System.out.println("Возврат "
                        + meta.playerId(returned.seat()) + ": " + returned.amount());
                case HandEvent.Showdown shown -> System.out.println("Вскрытие "
                        + meta.playerId(shown.seat()) + ": " + shown.cards());
                case HandEvent.Payout payout -> System.out.println("Выплата "
                        + meta.playerId(payout.seat()) + ": " + payout.amount());
            }
        }
    }

    private static String categoryName(HandCategory category) {
        return switch (category) {
            case HIGH_CARD -> "Старшая карта";
            case ONE_PAIR -> "Пара";
            case TWO_PAIR -> "Две пары";
            case THREE_OF_A_KIND -> "Тройка";
            case STRAIGHT -> "Стрит";
            case FLUSH -> "Флеш";
            case FULL_HOUSE -> "Фулл-хаус";
            case FOUR_OF_A_KIND -> "Каре";
            case STRAIGHT_FLUSH -> "Стрит-флеш";
        };
    }
}
