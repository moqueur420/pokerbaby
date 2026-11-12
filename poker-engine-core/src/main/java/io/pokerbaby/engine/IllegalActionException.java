package io.pokerbaby.engine;

import java.util.Objects;

/** Illegal player action with a stable, machine-readable reason. */
public final class IllegalActionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * The stable rejection code.
     * @serial Machine-readable rejection reason.
     */
    private final Code code;

    /** Reasons for rejecting an otherwise well-formed action. */
    public enum Code {
        /** The other player must act. */ NOT_YOUR_TURN,
        /** The hand has already finished. */ HAND_COMPLETE,
        /** The action type is unavailable. */ ACTION_UNAVAILABLE,
        /** A wager is below the legal minimum. */ BELOW_MINIMUM,
        /** A wager exceeds the available maximum. */ ABOVE_MAXIMUM
    }

    /**
     * Creates a coded failure.
     * @param code non-null reason
     * @param message non-null diagnostic message
     */
    public IllegalActionException(Code code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Returns the stable rejection code.
     * @return the reason for rejection
     */
    public Code code() {
        return code;
    }
}
