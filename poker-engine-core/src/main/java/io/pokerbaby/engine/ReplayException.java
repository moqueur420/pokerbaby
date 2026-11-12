package io.pokerbaby.engine;

/** Invalid hand history, with a one-based event number (N+1 for a missing event). */
public final class ReplayException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /**
     * One-based position of the violation.
     * @serial Event number in the supplied history.
     */
    private final int eventNumber;

    /**
     * Describes a history violation.
     * @param eventNumber positive one-based event number
     * @param message reason the history is invalid
     */
    public ReplayException(int eventNumber, String message) {
        super("Event " + eventNumber + ": " + message);
        if (eventNumber < 1) {
            throw new IllegalArgumentException("Event numbers start at one");
        }
        this.eventNumber = eventNumber;
    }

    /**
     * Locates the violation.
     * @return the one-based offending or missing event number
     */
    public int eventNumber() {
        return eventNumber;
    }
}
