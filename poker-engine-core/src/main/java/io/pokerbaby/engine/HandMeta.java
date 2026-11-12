package io.pokerbaby.engine;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Caller-provided identifiers and optional timestamp; no system time is read.
 * @param handId non-blank hand identifier
 * @param playerIds distinct non-blank player identifiers in seat order
 * @param startedAt optional start timestamp
 */
public record HandMeta(String handId, List<String> playerIds, Optional<Instant> startedAt) {
    /**
     * Copies and validates metadata.
     * @param handId non-blank hand identifier
     * @param playerIds exactly two distinct non-blank player identifiers
     * @param startedAt non-null optional timestamp
     */
    public HandMeta {
        Checks.identifier(handId, "handId");
        playerIds = Checks.playerIds(playerIds);
        Objects.requireNonNull(startedAt, "startedAt");
    }

    /**
     * Creates metadata without a timestamp.
     * @param handId hand identifier
     * @param playerIds player identifiers in seat order
     */
    public HandMeta(String handId, List<String> playerIds) {
        this(handId, playerIds, Optional.empty());
    }

    /**
     * Returns the player identifier at a seat.
     * @param seat 0 or 1
     * @return the player identifier
     */
    public String playerId(int seat) {
        return playerIds.get(Checks.seat(seat));
    }
}
