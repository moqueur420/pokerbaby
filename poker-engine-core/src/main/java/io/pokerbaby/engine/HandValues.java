package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Internal rank-major bit representation: four suit bits for each of thirteen ranks. */
final class HandValues {
    private static final long RANK_LANE = 0x1111111111111L;
    private static final long WHEEL = (1L << 48) | 0x1111L;
    private static final List<HandCategory> CATEGORIES = List.of(HandCategory.values());
    private static final List<Card> CARDS = createCards();

    private HandValues() {
    }

    static long mask(Collection<Card> cards, int minimum, int maximum) {
        Objects.requireNonNull(cards, "cards");
        if (cards.size() < minimum || cards.size() > maximum) {
            throw new IllegalArgumentException("Expected " + minimum + " to " + maximum + " cards");
        }
        long mask = 0;
        for (Card card : cards) {
            Objects.requireNonNull(card, "card");
            long bit = 1L << (card.rank().ordinal() * 4 + card.suit().ordinal());
            if ((mask & bit) != 0) {
                throw new IllegalArgumentException("Duplicate card: " + card);
            }
            mask |= bit;
        }
        return mask;
    }

    static int score(long mask) {
        long clubs = mask & RANK_LANE;
        long diamonds = (mask >>> 1) & RANK_LANE;
        long hearts = (mask >>> 2) & RANK_LANE;
        long spades = (mask >>> 3) & RANK_LANE;
        long present = clubs | diamonds | hearts | spades;
        long pairs = (clubs & diamonds) | (clubs & hearts) | (clubs & spades)
                | (diamonds & hearts) | (diamonds & spades) | (hearts & spades);
        long trips = (clubs & diamonds & hearts) | (clubs & diamonds & spades)
                | (clubs & hearts & spades) | (diamonds & hearts & spades);
        long quads = clubs & diamonds & hearts & spades;

        long flush = flushRanks(mask);
        if (flush != 0) {
            int high = straightHigh(flush);
            if (high != 0) {
                return straightScore(HandCategory.STRAIGHT_FLUSH, high);
            }
        }
        if (quads != 0) {
            int four = highestRank(quads);
            return pack(HandCategory.FOUR_OF_A_KIND, four, four, four, four,
                    highestRank(present & ~rankBit(four)));
        }
        int three = trips == 0 ? 0 : highestRank(trips);
        if (three != 0) {
            long otherPairs = pairs & ~rankBit(three);
            if (otherPairs != 0) {
                int pair = highestRank(otherPairs);
                return pack(HandCategory.FULL_HOUSE, three, three, three, pair, pair);
            }
        }
        if (flush != 0) {
            return topFive(HandCategory.FLUSH, flush);
        }
        int straight = straightHigh(present);
        if (straight != 0) {
            return straightScore(HandCategory.STRAIGHT, straight);
        }
        if (three != 0) {
            long kickers = present & ~rankBit(three);
            int first = highestRank(kickers);
            int second = highestRank(kickers & ~rankBit(first));
            return pack(HandCategory.THREE_OF_A_KIND, three, three, three, first, second);
        }
        if (pairs != 0) {
            int first = highestRank(pairs);
            long otherPairs = pairs & ~rankBit(first);
            if (otherPairs != 0) {
                int second = highestRank(otherPairs);
                int kicker = highestRank(present & ~rankBit(first) & ~rankBit(second));
                return pack(HandCategory.TWO_PAIR, first, first, second, second, kicker);
            }
            long kickers = present & ~rankBit(first);
            int high = highestRank(kickers);
            kickers &= ~rankBit(high);
            int middle = highestRank(kickers);
            int low = highestRank(kickers & ~rankBit(middle));
            return pack(HandCategory.ONE_PAIR, first, first, high, middle, low);
        }
        return topFive(HandCategory.HIGH_CARD, present);
    }

    static HandCategory category(int score) {
        return CATEGORIES.get(score >>> 20);
    }

    static List<Card> bestCards(long mask, int score) {
        HandCategory category = category(score);
        if (category == HandCategory.FLUSH || category == HandCategory.STRAIGHT_FLUSH) {
            for (int suit = 0; suit < 4; suit++) {
                long suited = mask & (RANK_LANE << suit);
                if (Long.bitCount(suited) >= 5) {
                    mask = suited;
                    break;
                }
            }
        }
        // A list is used throughout the public API. Suit order breaks ties only
        // when choosing a physical representative, never when comparing strength.
        List<Card> selected = new ArrayList<>(5);
        for (int shift = 16; shift >= 0; shift -= 4) {
            int rank = (score >>> shift) & 15;
            long candidates = mask & (15L << ((rank - 2) * 4));
            long chosen = Long.lowestOneBit(candidates);
            selected.add(CARDS.get(Long.numberOfTrailingZeros(chosen)));
            mask &= ~chosen;
        }
        return List.copyOf(selected);
    }

    private static long flushRanks(long mask) {
        for (int suit = 0; suit < 4; suit++) {
            long ranks = (mask >>> suit) & RANK_LANE;
            if (Long.bitCount(ranks) >= 5) {
                return ranks;
            }
        }
        return 0;
    }

    private static int straightHigh(long ranks) {
        long ends = ranks & (ranks << 4) & (ranks << 8) & (ranks << 12) & (ranks << 16);
        if (ends != 0) {
            return highestRank(ends);
        }
        return (ranks & WHEEL) == WHEEL ? 5 : 0;
    }

    private static int straightScore(HandCategory category, int high) {
        return pack(category, high, high - 1, high - 2, high - 3, high == 5 ? 14 : high - 4);
    }

    private static int topFive(HandCategory category, long ranks) {
        int score = category.ordinal() << 20;
        for (int shift = 16; shift >= 0; shift -= 4) {
            int rank = highestRank(ranks);
            score |= rank << shift;
            ranks &= ~rankBit(rank);
        }
        return score;
    }

    private static int pack(HandCategory category, int a, int b, int c, int d, int e) {
        return category.ordinal() << 20 | a << 16 | b << 12 | c << 8 | d << 4 | e;
    }

    private static int highestRank(long ranks) {
        return 2 + ((63 - Long.numberOfLeadingZeros(ranks)) >>> 2);
    }

    private static long rankBit(int rank) {
        return 1L << ((rank - 2) * 4);
    }

    private static List<Card> createCards() {
        List<Card> cards = new ArrayList<>(52);
        for (Rank rank : Rank.values()) {
            for (Suit suit : Suit.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        return List.copyOf(cards);
    }
}
