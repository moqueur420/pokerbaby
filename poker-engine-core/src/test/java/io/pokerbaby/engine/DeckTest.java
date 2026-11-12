package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.pokerbaby.engine.TestCards.cards;
import static org.junit.jupiter.api.Assertions.*;

class DeckTest {
    // Fixed compatibility vector for canonical order + java.util.Random + Fisher-Yates.
    private static final String SEED_42 = "4d 2s Th Jh 8h 3c Td 6c Ts Ad 5c 6d 5d 3h 2d 4c "
            + "9h Jd 3d Qc 4s Qs Kc 4h 3s Ac 2c 9c 8d Kh Tc 5h 9s Qd Ks Kd Jc As "
            + "8c Qh 6h 5s 7s 6s Ah 7c 9d 7d 7h Js 8s 2h";

    @Test
    @DisplayName("К-1, К-4: canonical deck contains the specified 52-card order")
    void canonicalDeck() {
        Deck deck = Deck.standard();
        assertEquals(52, deck.remaining());
        assertEquals(0, deck.position());
        assertEquals(Card.parse("2c"), deck.cards().getFirst());
        assertEquals(Card.parse("Ac"), deck.cards().get(12));
        assertEquals(Card.parse("2d"), deck.cards().get(13));
        assertEquals(Card.parse("As"), deck.cards().getLast());
    }

    @Test
    @DisplayName("К-3, Н-1: seed 42 has a pinned complete permutation")
    void deterministicShuffle() {
        Deck first = Deck.shuffled(42);
        assertEquals(cards(SEED_42), first.cards());
        assertEquals(first, Deck.shuffled(42));
        assertNotEquals(first, Deck.shuffled(43));
        assertEquals(new HashSet<>(Deck.standard().cards()), new HashSet<>(first.cards()));
        assertEquals(52, first.remaining());
    }

    @Test
    @DisplayName("К-3, К-6, К-7, Н-10: Fisher-Yates consumes the caller's raw random source")
    void externalRandomSource() {
        List<Integer> samples = new ArrayList<>();
        RandomGenerator random = new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new AssertionError("Only raw nextInt() should be used");
            }

            @Override
            public int nextInt(int bound) {
                throw new AssertionError("The deck must perform rejection sampling itself");
            }

            @Override
            public int nextInt() {
                samples.add(0);
                return 0;
            }
        };
        Deck deck = Deck.shuffled(random);
        assertEquals(51, samples.size());
        assertEquals(52, new HashSet<>(deck.cards()).size());
        List<Card> rotated = new ArrayList<>(Deck.standard().cards().subList(1, 52));
        rotated.add(Card.parse("2c"));
        assertEquals(rotated, deck.cards());
        assertThrows(NullPointerException.class, () -> Deck.shuffled(null));
    }

    @Test
    @DisplayName("К-5, С-2: all cards can be drawn exactly once and old decks survive")
    void drawsWithoutMutatingOldDecks() {
        Deck original = Deck.shuffled(42);
        Deck current = original;
        List<Card> drawn = new ArrayList<>();
        while (current.remaining() > 0) {
            int oldPosition = current.position();
            Deck.Draw draw = current.draw();
            assertEquals(oldPosition, current.position());
            assertEquals(current.cards().get(oldPosition), draw.card());
            assertEquals(oldPosition + 1, draw.deck().position());
            drawn.add(draw.card());
            current = draw.deck();
        }
        assertEquals(original.cards(), drawn);
        assertEquals(52, new HashSet<>(drawn).size());
        assertEquals(52, original.remaining());
        assertTrue(current.remainingCards().isEmpty());
        assertThrows(IllegalStateException.class, current::draw);
        assertEquals(original.draw(), original.draw());
    }

    @Test
    @DisplayName("К-4, П-6, П-9: partial decks copy their input and expose immutable lists")
    void immutablePartialDeck() {
        List<Card> input = new ArrayList<>(cards("As Td 2c"));
        Deck deck = Deck.ordered(input);
        input.clear();
        assertEquals(cards("As Td 2c"), deck.cards());
        assertEquals(cards("Td 2c"), deck.draw().deck().remainingCards());
        assertThrows(UnsupportedOperationException.class, () -> deck.cards().clear());
        assertThrows(UnsupportedOperationException.class, () -> deck.draw().deck().remainingCards().clear());
        Deck empty = Deck.ordered(List.of());
        assertEquals(0, empty.remaining());
        assertThrows(IllegalStateException.class, empty::draw);
        assertThrows(IllegalArgumentException.class, () -> Deck.ordered(cards("As As")));
        assertThrows(NullPointerException.class, () -> Deck.ordered(null));
        assertThrows(NullPointerException.class, () -> Deck.ordered(Arrays.asList(Card.parse("As"), null)));
    }

    @Test
    @DisplayName("П-6: start/resume consumers can require the necessary remainder")
    void validatesRemainingCardCount() {
        Deck nine = Deck.ordered(Deck.standard().cards().subList(0, 9));
        assertDoesNotThrow(() -> nine.requireRemaining(9));
        assertThrows(IllegalArgumentException.class, () -> nine.draw().deck().requireRemaining(9));
        assertThrows(IllegalArgumentException.class, () -> nine.requireRemaining(-1));
        assertDoesNotThrow(() -> Deck.ordered(List.of()).requireRemaining(0));
    }

    @Test
    @DisplayName("П-9: deck equality includes both order and cursor")
    void comparesByValue() {
        Deck deck = Deck.ordered(cards("As Td"));
        Deck equal = Deck.ordered(cards("As Td"));
        assertEquals(deck, deck);
        assertEquals(deck, equal);
        assertEquals(deck.hashCode(), equal.hashCode());
        assertNotEquals(deck, equal.draw().deck());
        assertNotEquals(deck, Deck.ordered(cards("Td As")));
        assertNotEquals(deck, null);
        assertNotEquals(deck, "deck");
        assertEquals("Deck[position=0, remaining=2]", deck.toString());
        assertThrows(NullPointerException.class, () -> new Deck.Draw(null, deck));
        assertThrows(NullPointerException.class, () -> new Deck.Draw(Card.parse("As"), null));
    }
}
