package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable history envelope; event order is preserved exactly as supplied.
 * The replayer, not this data type, will check rule consistency and completion.
 * @param header hand parameters and metadata
 * @param events events in chronological order
 */
public record HandHistory(Header header, List<HandEvent> events) {
    /** Current wire-format version. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Copies a sequence of events.
     * @param header non-null history header
     * @param events non-null events, possibly empty or incomplete
     */
    public HandHistory {
        Objects.requireNonNull(header, "header");
        events = List.copyOf(events);
    }

    /**
     * Creates an empty version-1 history.
     * @param config blind configuration
     * @param meta externally supplied metadata
     * @param startingStacks positive initial hand stacks
     * @param buttonSeat 0 or 1
     * @return the empty history
     */
    public static HandHistory empty(GameConfig config, HandMeta meta, SeatChips startingStacks, int buttonSeat) {
        return new HandHistory(new Header(SCHEMA_VERSION, config, meta, startingStacks, buttonSeat), List.of());
    }

    /**
     * Appends a fact, leaving the original history unchanged.
     * @param event non-null next event
     * @return the extended history
     */
    public HandHistory append(HandEvent event) {
        Objects.requireNonNull(event, "event");
        List<HandEvent> updated = new ArrayList<>(events);
        updated.add(event);
        return new HandHistory(header, updated);
    }

    /**
     * Immutable header; the JSON module will map these values to schema-1 fields.
     * @param schemaVersion supported schema version, currently 1
     * @param config blinds
     * @param meta hand identifier, player identifiers, and optional timestamp
     * @param startingStacks positive stacks at hand start
     * @param buttonSeat dealer/small-blind seat
     */
    public record Header(int schemaVersion, GameConfig config, HandMeta meta,
                         SeatChips startingStacks, int buttonSeat) {
        /**
         * Validates a history header.
         * @param schemaVersion 1
         * @param config non-null blind configuration
         * @param meta non-null metadata
         * @param startingStacks non-null positive hand stacks
         * @param buttonSeat 0 or 1
         */
        public Header {
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported history schema: " + schemaVersion);
            }
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(meta, "meta");
            Objects.requireNonNull(startingStacks, "startingStacks").validateHandStart();
            Checks.seat(buttonSeat);
        }
    }
}
