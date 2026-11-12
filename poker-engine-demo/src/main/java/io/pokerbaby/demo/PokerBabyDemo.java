package io.pokerbaby.demo;

import java.util.ArrayList;
import java.util.List;

import io.pokerbaby.engine.Card;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.GameConfig;
import io.pokerbaby.engine.HandMeta;
import io.pokerbaby.engine.SeatChips;

/** Entry point for the poker engine demonstration. */
public final class PokerBabyDemo {
    private PokerBabyDemo() {
    }

    /**
     * Demonstrates deterministic shuffling and immutable hole-card draws.
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

        System.out.println("PokerBaby: этап 2 — карты и колода.");
        System.out.println("Seed: " + seed);
        System.out.println("Конфигурация блайндов: " + config.smallBlind() + "/" + config.bigBlind());
        System.out.println("Стартовые стеки: " + stacks);
        for (int seat = 0; seat < 2; seat++) {
            System.out.println("Карты " + meta.playerId(seat) + ": " + hands.get(seat));
        }
        System.out.println("Остаток новой колоды: " + current.remaining());
        System.out.println("Исходная колода не изменилась: " + original.remaining() + " карты.");
        System.out.println("Оценка комбинаций и торговля будут реализованы на следующих этапах.");
    }
}
