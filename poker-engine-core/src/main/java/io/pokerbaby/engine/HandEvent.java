package io.pokerbaby.engine;

import java.util.List;
import java.util.Objects;

/**
 * Immutable facts in chronological hand-history order. Constructors check data shape;
 * the replayer will validate the sequence against the rules of a hand.
 */
public sealed interface HandEvent permits HandEvent.PostBlind, HandEvent.DealHole,
        HandEvent.ActionTaken, HandEvent.DealBoard, HandEvent.ReturnUncalled,
        HandEvent.Showdown, HandEvent.Payout {

    /**
     * Identifies the event in the history schema.
     * @return the stable schema-version-1 event name
     */
    String type();

    /**
     * A posted blind, possibly short because of an all-in.
     * @param seat player seat
     * @param amount chips posted
     */
    record PostBlind(int seat, long amount) implements HandEvent {
        /**
         * Validates a blind event.
         * @param seat 0 or 1
         * @param amount positive chip amount
         */
        public PostBlind {
            Checks.seat(seat);
            Checks.positiveChips(amount);
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "post_blind";
        }
    }

    /**
     * Known hole cards; this event may be omitted in imported or redacted histories.
     * @param seat player seat
     * @param cards two hole cards in received order
     */
    record DealHole(int seat, List<Card> cards) implements HandEvent {
        /**
         * Copies and validates hole cards.
         * @param seat 0 or 1
         * @param cards two distinct non-null cards
         */
        public DealHole {
            Checks.seat(seat);
            cards = Checks.cards(cards, 2);
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "deal_hole";
        }
    }

    /**
     * A player action.
     * @param seat acting player
     * @param action requested action
     */
    record ActionTaken(int seat, Action action) implements HandEvent {
        /**
         * Validates action event structure; game legality is checked separately.
         * @param seat 0 or 1
         * @param action non-null action
         */
        public ActionTaken {
            Checks.seat(seat);
            Objects.requireNonNull(action, "action");
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "action";
        }
    }

    /**
     * Newly dealt board cards.
     * @param street FLOP, TURN, or RIVER
     * @param cards new cards for that street, not the entire board
     */
    record DealBoard(Street street, List<Card> cards) implements HandEvent {
        /**
         * Copies and validates the new board cards.
         * @param street FLOP, TURN, or RIVER
         * @param cards three cards for the flop, one for the turn or river
         */
        public DealBoard {
            Objects.requireNonNull(street, "street");
            int count = switch (street) {
                case FLOP -> 3;
                case TURN, RIVER -> 1;
                default -> throw new IllegalArgumentException("Not a board-dealing street: " + street);
            };
            cards = Checks.cards(cards, count);
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "deal_board";
        }
    }

    /**
     * Positive uncalled chips returned before payout.
     * @param seat receiving player
     * @param amount amount returned
     */
    record ReturnUncalled(int seat, long amount) implements HandEvent {
        /**
         * Validates a refund event.
         * @param seat 0 or 1
         * @param amount positive amount returned
         */
        public ReturnUncalled {
            Checks.seat(seat);
            Checks.positiveChips(amount);
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "return_uncalled";
        }
    }

    /**
     * Revealed hole cards and declared combination category.
     * @param seat player seat
     * @param cards the player's two hole cards
     * @param category declared category, to be verified by replay when possible
     */
    record Showdown(int seat, List<Card> cards, HandCategory category) implements HandEvent {
        /**
         * Copies and validates a showdown event.
         * @param seat 0 or 1
         * @param cards two distinct hole cards
         * @param category non-null declared category
         */
        public Showdown {
            Checks.seat(seat);
            cards = Checks.cards(cards, 2);
            Objects.requireNonNull(category, "category");
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "showdown";
        }
    }

    /**
     * Positive payment from the contested pot.
     * @param seat receiving player
     * @param amount payment amount
     */
    record Payout(int seat, long amount) implements HandEvent {
        /**
         * Validates a payout event.
         * @param seat 0 or 1
         * @param amount positive payment amount
         */
        public Payout {
            Checks.seat(seat);
            Checks.positiveChips(amount);
        }

        /** {@inheritDoc} */
        @Override
        public String type() {
            return "payout";
        }
    }
}
