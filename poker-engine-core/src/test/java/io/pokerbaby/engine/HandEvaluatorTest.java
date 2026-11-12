package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {
    @ParameterizedTest
    @CsvSource({
        "As Kd 9h 7c 3s,HIGH_CARD,As Kd 9h 7c 3s",
        "As Ad Qh 9c 3s,ONE_PAIR,Ad As Qh 9c 3s",
        "As Ad Kh Kc 2s,TWO_PAIR,Ad As Kc Kh 2s",
        "As Ad Ac Kd 2s,THREE_OF_A_KIND,Ac Ad As Kd 2s",
        "9s Th Jd Qc Kh,STRAIGHT,Kh Qc Jd Th 9s",
        "As Js 9s 4s 2s,FLUSH,As Js 9s 4s 2s",
        "As Ad Ac Kd Ks,FULL_HOUSE,Ac Ad As Kd Ks",
        "As Ad Ac Ah Ks,FOUR_OF_A_KIND,Ac Ad Ah As Ks",
        "9s Ts Js Qs Ks,STRAIGHT_FLUSH,Ks Qs Js Ts 9s",
        "As Ks Qs Js Ts,STRAIGHT_FLUSH,As Ks Qs Js Ts",
        "As 2d 3h 4c 5s,STRAIGHT,5s 4c 3h 2d As",
        "As 2s 3s 4s 5s,STRAIGHT_FLUSH,5s 4s 3s 2s As",
        "Ac Ad Ah Kc Kd Kh 2s,FULL_HOUSE,Ac Ad Ah Kc Kd",
        "Qc Qd Qh Ac Ad Kc Kd,FULL_HOUSE,Qc Qd Qh Ac Ad",
        "Ac Ad Kc Kd Qc Qd 2s,TWO_PAIR,Ac Ad Kc Kd Qc",
        "9c 9d 9h 9s Ac Kd 2s,FOUR_OF_A_KIND,9c 9d 9h 9s Ac",
        "Qc Qd Qh As Kd 9s 2c,THREE_OF_A_KIND,Qc Qd Qh As Kd",
        "Ac Ad Ks Qh Jd 9c 2s,ONE_PAIR,Ac Ad Ks Qh Jd",
        "Ac 2d 3h 4c 5s 6d Kc,STRAIGHT,6d 5s 4c 3h 2d",
        "2h 6h 7h 8h 9h Tc Js,FLUSH,9h 8h 7h 6h 2h",
        "7s 8s 9s Ts Js Qs Ks,STRAIGHT_FLUSH,Ks Qs Js Ts 9s",
        "2c 3c 4d 5h 6s 6c As,STRAIGHT,6c 5h 4d 3c 2c",
        "2c 2d Ac Kd Qh Js,ONE_PAIR,2c 2d Ac Kd Qh"
    })
    @DisplayName("О-1—О-5: categories, wheels, kickers, and canonical best five cards")
    void evaluatesKnownHands(String input, HandCategory category, String best) {
        HandRank result = HandEvaluator.evaluate(cards(input));
        assertEquals(category, result.category());
        assertEquals(cards(best), result.cards());
        assertEquals(5, new HashSet<>(result.cards()).size());
        assertEquals(FiveCardReference.bestScore(cards(input)), FiveCardReference.score(result.cards()));
    }

    @ParameterizedTest
    @CsvSource({
        "Ac Ad 4h 3s 2c,Kc Kd As Qh Js",
        "Ac Ad Kc Qd 2h,As Ah Qc Jd 9h",
        "Ac Ad Kc Qd 2h,As Ah Kd Jc 9h",
        "Ac Ad Kc Qd 9h,As Ah Kd Qc 8h",
        "Ac Ad Kc Kd 2h,Ks Kh Qc Qd As",
        "Ac Ad Kc Kd 2h,As Ah Qc Qd Kh",
        "Ac Ad Kc Kd Qh,As Ah Ks Kh Jd",
        "Ac Ad Ah Kc Qd,Ac Ad Ah Ks Jd",
        "2c 3d 4h 5s 6c,Ac 2d 3h 4s 5c",
        "Ac Kc 9c 4c 2c,As Qs Js 9s 7s",
        "Ac Kc 9c 4c 3c,As Ks 9s 4s 2s",
        "Ac Ad Ah 2c 2d,Kc Kd Kh As Ad",
        "Ac Ad Ah Kc Kd,Ac Ad Ah Qc Qd",
        "Ac Ad Ah As Kc,Kc Kd Kh Ks Ac",
        "Ac Ad Ah As Kc,Ac Ad Ah As Qc",
        "9s Ts Js Qs Ks,8h 9h Th Jh Qh",
        "Ac Kd Qh 9s 3c,As Kh Qd 9c 2s"
    })
    @DisplayName("О-4: each category-specific tie breaker is respected")
    void comparesWithinCategories(String stronger, String weaker) {
        HandRank winner = HandEvaluator.evaluate(cards(stronger));
        HandRank loser = HandEvaluator.evaluate(cards(weaker));
        assertTrue(winner.compareTo(loser) > 0);
        assertTrue(loser.compareTo(winner) < 0);
    }

    @Test
    @DisplayName("О-2, О-4: all nine categories are strictly ordered")
    void categoryOrder() {
        List<String> examples = List.of("As Kd 9h 7c 3s", "2c 2d As Kh Qs", "2c 2d 3h 3s Ac",
                "2c 2d 2h As Kh", "Ac 2d 3h 4s 5c", "2c 4c 7c 9c Jc",
                "2c 2d 2h 3s 3c", "2c 2d 2h 2s 3c", "2c 3c 4c 5c 6c");
        for (int index = 1; index < examples.size(); index++) {
            assertTrue(HandEvaluator.evaluate(cards(examples.get(index)))
                    .compareTo(HandEvaluator.evaluate(cards(examples.get(index - 1)))) > 0);
        }
    }

    @Test
    @DisplayName("О-4: suits do not break ties, while physical-card identity remains in equality")
    void equalStrengthAcrossSuits() {
        HandRank clubs = HandEvaluator.evaluate(cards("Ac Jc 9c 4c 2c"));
        HandRank spades = HandEvaluator.evaluate(cards("As Js 9s 4s 2s"));
        assertEquals(0, clubs.compareTo(spades));
        assertEquals(clubs.comparisonKey(), spades.comparisonKey());
        assertNotEquals(clubs, spades);
        assertEquals(0, clubs.compareTo(clubs));
        assertThrows(NullPointerException.class, () -> clubs.compareTo(null));
    }

    @Test
    @DisplayName("О-1, О-5: a royal flush on the board gives both players exactly the same result")
    void playingTheBoard() {
        HandRank first = HandEvaluator.evaluate(cards("As Ks Qs Js Ts 2c 3d"));
        HandRank second = HandEvaluator.evaluate(cards("As Ks Qs Js Ts 9h 9c"));
        assertEquals(first, second);
        assertEquals(cards("As Ks Qs Js Ts"), first.cards());
        assertEquals(0, first.compareTo(second));
    }

    @Test
    @DisplayName("Н-1, П-9: input permutations and collection types do not change the result")
    void stableSelectionForEquivalentFives() {
        List<Card> input = new ArrayList<>(cards("Ac Ad Ah Kc Kd Kh 2s"));
        HandRank expected = HandEvaluator.evaluate(input);
        Random random = new Random(20261113);
        for (int attempt = 0; attempt < 100; attempt++) {
            Collections.shuffle(input, random);
            HandRank actual = HandEvaluator.evaluate(input);
            assertEquals(expected, actual);
            assertEquals(expected.hashCode(), actual.hashCode());
        }
        assertEquals(expected, HandEvaluator.evaluate(new HashSet<>(input)));
        input.clear();
        assertEquals(cards("Ac Ad Ah Kc Kd"), expected.cards());
        assertThrows(UnsupportedOperationException.class, () -> expected.cards().clear());
        List<Card> straight = new ArrayList<>(cards("2c 3c 4d 5h 6s 6c As"));
        HandRank canonical = HandEvaluator.evaluate(straight);
        Collections.reverse(straight);
        assertEquals(canonical, HandEvaluator.evaluate(straight));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 8, 9, 52})
    @DisplayName("П-7: only five through seven cards are accepted")
    void rejectsWrongCounts(int count) {
        assertThrows(IllegalArgumentException.class,
                () -> HandEvaluator.evaluate(Deck.standard().cards().subList(0, count)));
    }

    @Test
    @DisplayName("П-7: null collections, null cards, and repeated cards fail immediately")
    void rejectsMalformedInput() {
        assertThrows(NullPointerException.class, () -> HandEvaluator.evaluate(null));
        assertThrows(NullPointerException.class,
                () -> HandEvaluator.evaluate(Arrays.asList(null, Card.parse("As"), Card.parse("Kd"),
                        Card.parse("Qh"), Card.parse("Js"))));
        assertThrows(IllegalArgumentException.class, () -> HandEvaluator.evaluate(cards("As As Kd Qh Js")));
        assertThrows(IllegalArgumentException.class,
                () -> HandEvaluator.evaluate(cards("As Kd Qh Js Tc 9c As")));
    }

    @Test
    @DisplayName("П-9: result construction validates its category/key and copies its cards")
    void validatesResultConstruction() {
        HandRank evaluated = HandEvaluator.evaluate(cards("As Kd Qh Js Tc"));
        List<Card> input = new ArrayList<>(evaluated.cards());
        Collections.reverse(input);
        HandRank copy = new HandRank(evaluated.category(), input, evaluated.comparisonKey());
        input.clear();
        assertEquals(evaluated, copy);
        assertThrows(IllegalArgumentException.class,
                () -> new HandRank(HandCategory.FLUSH, evaluated.cards(), evaluated.comparisonKey()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandRank(evaluated.category(), evaluated.cards(), evaluated.comparisonKey() + 1));
        assertThrows(NullPointerException.class,
                () -> new HandRank(null, evaluated.cards(), evaluated.comparisonKey()));
        assertThrows(NullPointerException.class,
                () -> new HandRank(evaluated.category(), null, evaluated.comparisonKey()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandRank(evaluated.category(), cards("As Kd Qh Js"), evaluated.comparisonKey()));
    }
}
