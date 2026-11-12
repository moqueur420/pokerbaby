package io.pokerbaby.engine;

import java.util.HashSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CardTest {
    static Stream<Arguments> allCards() {
        return Stream.of(Suit.values()).flatMap(suit -> Stream.of(Rank.values())
                .map(rank -> Arguments.of(rank, suit)));
    }

    @ParameterizedTest
    @MethodSource("allCards")
    @DisplayName("К-1, К-2, П-9: all 52 cards round-trip and compare by value")
    void allCardsRoundTrip(Rank rank, Suit suit) {
        Card card = new Card(rank, suit);
        Card parsed = Card.parse(card.toString());
        assertEquals(2, card.toString().length());
        assertEquals(card, parsed);
        assertEquals(rank, parsed.rank());
        assertEquals(suit, parsed.suit());
        assertEquals(card.hashCode(), parsed.hashCode());
    }

    @Test
    @DisplayName("К-1: exactly 13 ranks and four canonical suits")
    void canonicalRanksAndSuits() {
        assertEquals("23456789TJQKA", Stream.of(Rank.values())
                .map(rank -> String.valueOf(rank.symbol())).reduce("", String::concat));
        assertEquals("cdhs", Stream.of(Suit.values())
                .map(suit -> String.valueOf(suit.symbol())).reduce("", String::concat));
        for (int index = 0; index < Rank.values().length; index++) {
            assertEquals(index + 2, Rank.values()[index].value());
        }
        assertEquals(52, new HashSet<>(Deck.standard().cards()).size());
        assertEquals(new Card(Rank.ACE, Suit.SPADES), Card.parse("As"));
        assertEquals(new Card(Rank.TEN, Suit.DIAMONDS), Card.parse("Td"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "10s", "As ", " As", "AS", "as", "1s", "0c", "Xh", "Ax", "A♠"})
    @DisplayName("К-2: malformed and non-canonical notation is rejected")
    void rejectsMalformedNotation(String notation) {
        assertThrows(IllegalArgumentException.class, () -> Card.parse(notation));
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("К-2: a null string is rejected")
    void rejectsNullNotation(String notation) {
        assertThrows(NullPointerException.class, () -> Card.parse(notation));
    }

    @Test
    @DisplayName("К-1: neither card component may be null")
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new Card(null, Suit.CLUBS));
        assertThrows(NullPointerException.class, () -> new Card(Rank.ACE, null));
    }
}
