package io.pokerbaby.engine;

import java.util.Arrays;
import java.util.List;

final class TestCards {
    private TestCards() {
    }

    static List<Card> cards(String text) {
        return Arrays.stream(text.split(" ")).map(Card::parse).toList();
    }
}
