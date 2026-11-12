package io.pokerbaby.engine;

import java.util.List;

/**
 * Immutable replay output, including the initial snapshot and training decisions.
 * @param steps initial snapshot plus one snapshot per event
 * @param decisions states immediately before each recorded action
 * @param outcomeVerified whether the completed outcome was independently checked
 */
public record ReplayResult(List<ReplayStep> steps, List<Decision> decisions, boolean outcomeVerified) {
    /**
     * Copies replay output and checks its step numbering.
     * @param steps contiguous steps beginning at zero
     * @param decisions immutable training examples
     * @param outcomeVerified false for unfinished or unverifiable outcomes
     */
    public ReplayResult {
        steps = List.copyOf(steps);
        decisions = List.copyOf(decisions);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("The initial replay step is required");
        }
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).eventNumber() != index) {
                throw new IllegalArgumentException("Replay steps must be contiguous");
            }
        }
        if (outcomeVerified && !steps.get(steps.size() - 1).snapshot().isComplete()) {
            throw new IllegalArgumentException("An unfinished outcome cannot be verified");
        }
    }

    /**
     * Reads the last step.
     * @return the final state, possibly an unfinished decision
     */
    public HandSnapshot lastSnapshot() {
        return steps.get(steps.size() - 1).snapshot();
    }
}
