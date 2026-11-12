package io.pokerbaby.engine;

import java.util.Objects;
import java.util.Optional;

/**
 * One observable replay step; step zero has no event and precedes the blinds.
 * @param eventNumber zero for the initial snapshot, otherwise one-based
 * @param event event just applied, empty only at step zero
 * @param snapshot state after this event
 */
public record ReplayStep(int eventNumber, Optional<HandEvent> event, HandSnapshot snapshot) {
    /**
     * Validates the step's position and event presence.
     * @param eventNumber non-negative step number
     * @param event optional event
     * @param snapshot immutable snapshot
     */
    public ReplayStep {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(snapshot, "snapshot");
        if (eventNumber < 0 || (eventNumber == 0) != event.isEmpty()
                || snapshot.history().events().size() != eventNumber) {
            throw new IllegalArgumentException("Inconsistent replay step");
        }
    }
}
