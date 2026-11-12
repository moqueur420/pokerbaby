package io.pokerbaby.engine;

import java.util.Objects;

/**
 * A training example pairing a decision state with the action actually taken.
 * @param snapshot observable state immediately before the action
 * @param action recorded player action
 */
public record Decision(HandSnapshot snapshot, HandEvent.ActionTaken action) {
    /**
     * Checks that the actor matches the decision snapshot.
     * @param snapshot a decision snapshot
     * @param action action taken by its actor
     */
    public Decision {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(action, "action");
        if (!snapshot.decisionPoint() || snapshot.actingSeat().orElseThrow() != action.seat()) {
            throw new IllegalArgumentException("Action does not belong to this decision");
        }
    }
}
