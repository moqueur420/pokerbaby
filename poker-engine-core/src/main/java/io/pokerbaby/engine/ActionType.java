package io.pokerbaby.engine;

/** Player action types. */
public enum ActionType {
    /** Give up against a wager. */ FOLD,
    /** Pass when nothing is owed. */ CHECK,
    /** Match the wager, capped by available chips. */ CALL,
    /** Open betting on a street. */ BET,
    /** Increase an existing wager. */ RAISE
}
