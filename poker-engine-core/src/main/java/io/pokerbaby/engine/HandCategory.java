package io.pokerbaby.engine;

/** The nine categories in ascending strength; suits never break ties. */
public enum HandCategory {
    /** No made hand. */ HIGH_CARD,
    /** One pair. */ ONE_PAIR,
    /** Two pairs. */ TWO_PAIR,
    /** Three of a kind. */ THREE_OF_A_KIND,
    /** Five consecutive ranks. */ STRAIGHT,
    /** Five cards of one suit. */ FLUSH,
    /** Three of a kind and a pair. */ FULL_HOUSE,
    /** Four of a kind. */ FOUR_OF_A_KIND,
    /** A straight of one suit; includes the royal flush. */ STRAIGHT_FLUSH
}
