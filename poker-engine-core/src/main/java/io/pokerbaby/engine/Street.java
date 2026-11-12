package io.pokerbaby.engine;

/** Hand phases, including the two terminal phases. */
public enum Street {
    /** Betting before the board. */ PREFLOP,
    /** Three-card board street. */ FLOP,
    /** Fourth board card street. */ TURN,
    /** Fifth board card street. */ RIVER,
    /** Hand comparison. */ SHOWDOWN,
    /** The hand is settled. */ COMPLETE
}
