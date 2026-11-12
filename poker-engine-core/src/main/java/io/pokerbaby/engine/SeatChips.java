package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.List;

/** Immutable non-negative chip amounts indexed by seats 0 and 1. */
public final class SeatChips {
    /** Maximum stack of one player when a match is created. */
    public static final long MAX_INITIAL_STACK = 1_000_000_000_000L;
    /** Maximum total chip supply in a hand or match. */
    public static final long MAX_TOTAL = 2 * MAX_INITIAL_STACK;
    /** Default initial stack per player. */
    public static final long DEFAULT_STACK = 10_000L;

    private final List<Long> values;
    private final long total;

    private SeatChips(List<Long> values) {
        this.values = List.copyOf(values);
        if (this.values.size() != 2) {
            throw new IllegalArgumentException("Exactly two chip amounts are required");
        }
        long sum = 0;
        for (long value : this.values) {
            if (value < 0 || value > MAX_TOTAL) {
                throw new IllegalArgumentException("Chip amount is outside the supported range: " + value);
            }
            sum = Math.addExact(sum, value);
        }
        if (sum > MAX_TOTAL) {
            throw new IllegalArgumentException("Total chip amount exceeds " + MAX_TOTAL);
        }
        this.total = sum;
    }

    /**
     * Creates amounts for the two seats; zero is allowed for current balances.
     * @param seat0 amount at seat 0
     * @param seat1 amount at seat 1
     * @return the amounts
     * @throws IllegalArgumentException if a value or total is out of range
     */
    public static SeatChips of(long seat0, long seat1) {
        return copyOf(List.of(seat0, seat1));
    }

    /**
     * Copies chip amounts in seat order.
     * @param values exactly two non-null, non-negative amounts
     * @return the amounts
     * @throws IllegalArgumentException if the size, a value, or total is invalid
     */
    public static SeatChips copyOf(List<Long> values) {
        return new SeatChips(values);
    }

    /**
     * Returns the amounts as an immutable list.
     * @return immutable amounts in seat order
     */
    public List<Long> values() {
        return values;
    }

    /**
     * Reads one amount.
     * @param seat 0 or 1
     * @return the chip amount
     * @throws IllegalArgumentException if the seat is invalid
     */
    public long get(int seat) {
        return values.get(Checks.seat(seat));
    }

    /**
     * Returns the combined chip amount.
     * @return the sum, calculated with overflow checking
     */
    public long total() {
        return total;
    }

    /**
     * Replaces one amount without changing this instance.
     * @param seat 0 or 1
     * @param amount replacement amount
     * @return the new amounts
     * @throws IllegalArgumentException if the seat, amount, or total is invalid
     */
    public SeatChips with(int seat, long amount) {
        List<Long> updated = new ArrayList<>(values);
        updated.set(Checks.seat(seat), amount);
        return copyOf(updated);
    }

    /**
     * Validates initial match stacks: each from 1 through 10^12.
     * @throws IllegalArgumentException if a stack is zero or above the initial limit
     */
    public void validateMatchStart() {
        for (long value : values) {
            if (value < 1 || value > MAX_INITIAL_STACK) {
                throw new IllegalArgumentException("Initial match stacks must be between 1 and " + MAX_INITIAL_STACK);
            }
        }
    }

    /**
     * Validates hand-start stacks; prior winnings may exceed the initial match limit.
     * @throws IllegalArgumentException if either player has no chips
     */
    public void validateHandStart() {
        if (values.contains(0L)) {
            throw new IllegalArgumentException("A hand requires two positive stacks");
        }
    }

    /**
     * Validates current stacks together with already committed chips for resumption.
     * @param contributions non-negative committed chips by seat
     * @throws IllegalArgumentException if combined supply exceeds the supported limit
     */
    public void validateResume(SeatChips contributions) {
        if (Math.addExact(total, contributions.total()) > MAX_TOTAL) {
            throw new IllegalArgumentException("Stacks and contributions exceed " + MAX_TOTAL);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SeatChips chips && values.equals(chips.values);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /** @return chip amounts in seat order */
    @Override
    public String toString() {
        return values.toString();
    }
}
