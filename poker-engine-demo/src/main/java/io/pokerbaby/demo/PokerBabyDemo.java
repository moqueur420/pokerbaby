package io.pokerbaby.demo;

import java.util.ArrayList;
import java.util.List;

import io.pokerbaby.engine.Card;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.GameConfig;
import io.pokerbaby.engine.HandMeta;
import io.pokerbaby.engine.HandCategory;
import io.pokerbaby.engine.HandEvaluator;
import io.pokerbaby.engine.HandRank;
import io.pokerbaby.engine.SeatChips;

/** Entry point for the poker engine demonstration. */
public final class PokerBabyDemo {
    private PokerBabyDemo() {
    }

    /**
     * Deals two hands and a board, then evaluates and compares their best five cards.
     *
     * @param args optional single numeric shuffle seed; defaults to 42
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: PokerBabyDemo [seed]");
        }
        long seed = args.length == 0 ? 42 : Long.parseLong(args[0]);
        GameConfig config = GameConfig.DEFAULT;
        SeatChips stacks = SeatChips.of(SeatChips.DEFAULT_STACK, SeatChips.DEFAULT_STACK);
        HandMeta meta = new HandMeta("demo-h1", List.of("human", "bot"));
        Deck original = Deck.shuffled(seed);
        Deck current = original;
        List<List<Card>> hands = List.of(new ArrayList<>(), new ArrayList<>());
        int buttonSeat = 0;
        for (int round = 0; round < 2; round++) {
            for (int offset = 0; offset < 2; offset++) {
                int seat = (buttonSeat + 1 + offset) % 2;
                Deck.Draw draw = current.draw();
                hands.get(seat).add(draw.card());
                current = draw.deck();
            }
        }

        List<Card> board = new ArrayList<>(5);
        for (int index = 0; index < 5; index++) {
            Deck.Draw draw = current.draw();
            board.add(draw.card());
            current = draw.deck();
        }

        System.out.println("PokerBaby: этап 3 — оценка комбинаций.");
        System.out.println("Seed: " + seed);
        System.out.println("Конфигурация блайндов: " + config.smallBlind() + "/" + config.bigBlind());
        System.out.println("Стартовые стеки: " + stacks);
        for (int seat = 0; seat < 2; seat++) {
            System.out.println("Карты " + meta.playerId(seat) + ": " + hands.get(seat));
        }
        System.out.println("Общие карты: " + board);
        List<HandRank> results = new ArrayList<>(2);
        for (int seat = 0; seat < 2; seat++) {
            List<Card> available = new ArrayList<>(hands.get(seat));
            available.addAll(board);
            HandRank result = HandEvaluator.evaluate(available);
            results.add(result);
            System.out.println("Комбинация " + meta.playerId(seat) + ": "
                    + categoryName(result.category()) + " — " + result.cards());
        }
        int comparison = results.get(0).compareTo(results.get(1));
        System.out.println(comparison == 0 ? "Результат сравнения: ничья."
                : "Сильнее рука " + meta.playerId(comparison > 0 ? 0 : 1) + ".");
        System.out.println("Остаток новой колоды: " + current.remaining());
        System.out.println("Исходная колода не изменилась: " + original.remaining() + " карты.");
        System.out.println("Торговля и выплата банка будут реализованы на следующих этапах.");
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
