package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Independent test oracle using sorted ranks and frequency counters, not bit masks. */
final class FiveCardReference {
    private FiveCardReference() {
    }

    static int score(List<Card> cards) {
        int[] counts = new int[15];
        int[] ranks = new int[5];
        boolean flush = true;
        for (int index = 0; index < 5; index++) {
            Card card = cards.get(index);
            ranks[index] = card.rank().value();
            counts[ranks[index]]++;
            flush &= card.suit() == cards.getFirst().suit();
        }
        Arrays.sort(ranks);
        int straight = 0;
        if (ranks[0] == 2 && ranks[1] == 3 && ranks[2] == 4 && ranks[3] == 5 && ranks[4] == 14) {
            straight = 5;
        } else if (ranks[1] == ranks[0] + 1 && ranks[2] == ranks[0] + 2
                && ranks[3] == ranks[0] + 3 && ranks[4] == ranks[0] + 4) {
            straight = ranks[4];
        }
        int four = 0;
        int three = 0;
        List<Integer> pairs = new ArrayList<>();
        List<Integer> singles = new ArrayList<>();
        for (int rank = 14; rank >= 2; rank--) {
            switch (counts[rank]) {
                case 4 -> four = rank;
                case 3 -> three = rank;
                case 2 -> pairs.add(rank);
                case 1 -> singles.add(rank);
                default -> { }
            }
        }
        if (flush && straight != 0) {
            return key(8, List.of(straight));
        }
        if (four != 0) {
            return key(7, List.of(four, singles.getFirst()));
        }
        if (three != 0 && !pairs.isEmpty()) {
            return key(6, List.of(three, pairs.getFirst()));
        }
        if (flush) {
            return key(5, List.of(ranks[4], ranks[3], ranks[2], ranks[1], ranks[0]));
        }
        if (straight != 0) {
            return key(4, List.of(straight));
        }
        if (three != 0) {
            return key(3, List.of(three, singles.get(0), singles.get(1)));
        }
        if (pairs.size() == 2) {
            return key(2, List.of(pairs.get(0), pairs.get(1), singles.getFirst()));
        }
        if (pairs.size() == 1) {
            return key(1, List.of(pairs.getFirst(), singles.get(0), singles.get(1), singles.get(2)));
        }
        return key(0, singles);
    }

    static int bestScore(List<Card> cards) {
        int best = -1;
        int size = cards.size();
        for (int a = 0; a < size - 4; a++) {
            for (int b = a + 1; b < size - 3; b++) {
                for (int c = b + 1; c < size - 2; c++) {
                    for (int d = c + 1; d < size - 1; d++) {
                        for (int e = d + 1; e < size; e++) {
                            best = Math.max(best, score(List.of(
                                    cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e))));
                        }
                    }
                }
            }
        }
        return best;
    }

    private static int key(int category, List<Integer> priorities) {
        int key = category << 20;
        for (int index = 0; index < priorities.size(); index++) {
            key |= priorities.get(index) << (16 - index * 4);
        }
        return key;
    }
}
