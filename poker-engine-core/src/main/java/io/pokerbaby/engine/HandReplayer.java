package io.pokerbaby.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Validates recorded events and reconstructs observable states without inventing a deck. */
public final class HandReplayer {
    private HandReplayer() {
    }

    /**
     * Replays a complete history, an empty history, or a prefix ending at a decision.
     * Known hole cards require their showdown events; unknown hands may omit both
     * the private deal and public showdown. No future reveal is backfilled into earlier steps.
     * @param history immutable history in chronological order
     * @return N+1 steps, decisions and the independent-outcome verification flag
     * @throws ReplayException for an illegal, missing or out-of-order event
     */
    public static ReplayResult replay(HandHistory history) {
        Objects.requireNonNull(history, "history");
        Cursor cursor = new Cursor(history.header());
        List<ReplayStep> steps = new ArrayList<>();
        List<Decision> decisions = new ArrayList<>();
        steps.add(new ReplayStep(0, Optional.empty(), cursor.snapshot()));
        for (int index = 0; index < history.events().size(); index++) {
            HandEvent event = history.events().get(index);
            try {
                if (event instanceof HandEvent.ActionTaken taken && cursor.phase == Phase.DECISION) {
                    decisions.add(new Decision(steps.get(steps.size() - 1).snapshot(), taken));
                }
                cursor.apply(event);
                cursor.events.add(event);
                HandEvent following = index + 1 < history.events().size() ? history.events().get(index + 1) : null;
                cursor.skipUnknown(following);
                steps.add(new ReplayStep(index + 1, Optional.of(event), cursor.snapshot()));
            } catch (IllegalArgumentException | IllegalActionException | IllegalStateException exception) {
                throw new ReplayException(index + 1, exception.getMessage());
            }
        }
        if (!history.events().isEmpty() && cursor.phase != Phase.DECISION && cursor.phase != Phase.COMPLETE) {
            throw new ReplayException(history.events().size() + 1, "Missing required event: " + cursor.phase);
        }
        boolean verified = cursor.phase == Phase.COMPLETE
                && (!cursor.showdown || cursor.holes.stream().allMatch(cards -> cards.size() == 2));
        return new ReplayResult(steps, decisions, verified);
    }

    private enum Phase { SMALL_BLIND, BIG_BLIND, HOLES, DECISION, REFUND, BOARD, SHOWDOWN, PAYOUT, COMPLETE }

    private static final class Cursor {
        private final HandHistory.Header header;
        private final List<HandEvent> events = new ArrayList<>();
        private final List<Card> board = new ArrayList<>();
        private final List<List<Card>> holes = new ArrayList<>(List.of(List.of(), List.of()));
        private final List<Optional<HandRank>> ranks = new ArrayList<>(List.of(Optional.empty(), Optional.empty()));
        private final Set<Integer> acted = new HashSet<>();
        private SeatChips stacks;
        private SeatChips bets = SeatChips.of(0, 0);
        private SeatChips contributions = SeatChips.of(0, 0);
        private SeatChips payouts = SeatChips.of(0, 0);
        private SeatChips expectedPayouts;
        private long settledPot;
        private long increment;
        private int actor;
        private int nextHole;
        private int nextShowdown;
        private int nextPayout;
        private int winner = -1;
        private boolean runout;
        private boolean folded;
        private boolean showdown;
        private Street street = Street.PREFLOP;
        private Phase phase = Phase.SMALL_BLIND;

        private Cursor(HandHistory.Header header) {
            this.header = header;
            stacks = header.startingStacks();
            actor = header.buttonSeat();
            increment = header.config().bigBlind();
        }

        private void apply(HandEvent event) {
            switch (phase) {
                case SMALL_BLIND, BIG_BLIND -> {
                    require(event instanceof HandEvent.PostBlind, "Expected blind");
                    HandEvent.PostBlind blind = (HandEvent.PostBlind) event;
                    int seat = phase == Phase.SMALL_BLIND ? header.buttonSeat() : 1 - header.buttonSeat();
                    long requested = phase == Phase.SMALL_BLIND ? header.config().smallBlind() : header.config().bigBlind();
                    require(blind.seat() == seat && blind.amount() == Math.min(requested, stacks.get(seat)), "Incorrect blind");
                    commit(seat, blind.amount());
                    phase = phase == Phase.SMALL_BLIND ? Phase.BIG_BLIND : Phase.HOLES;
                }
                case HOLES -> {
                    require(event instanceof HandEvent.DealHole, "Expected private deal");
                    HandEvent.DealHole hole = (HandEvent.DealHole) event;
                    require(hole.seat() >= nextHole, "Private deals are duplicated or out of order");
                    learn(hole.seat(), hole.cards());
                    nextHole = hole.seat() + 1;
                }
                case DECISION -> action(event);
                case REFUND -> refund(event);
                case BOARD -> dealBoard(event);
                case SHOWDOWN -> reveal(event);
                case PAYOUT -> payout(event);
                case COMPLETE -> throw new IllegalArgumentException("Event after hand completion");
            }
        }

        private void skipUnknown(HandEvent following) {
            if (phase == Phase.HOLES && (!(following instanceof HandEvent.DealHole) || nextHole == 2)) {
                advance();
            }
            if (phase == Phase.SHOWDOWN) {
                while (nextShowdown < 2) {
                    if (following instanceof HandEvent.Showdown shown && shown.seat() == nextShowdown
                            || !holes.get(nextShowdown).isEmpty()) {
                        break;
                    }
                    nextShowdown++;
                }
                if (nextShowdown == 2) {
                    beginPayout();
                }
            }
        }

        private void action(HandEvent event) {
            require(event instanceof HandEvent.ActionTaken, "Expected player action");
            HandEvent.ActionTaken taken = (HandEvent.ActionTaken) event;
            require(taken.seat() == actor, "Action out of turn");
            LegalActions legal = BettingRules.legal(header.config(), stacks, bets, increment, actor);
            BettingRules.validate(taken.action(), legal);
            acted.add(actor);
            switch (taken.action().type()) {
                case FOLD -> {
                    folded = true;
                    winner = 1 - actor;
                }
                case CHECK -> { }
                case CALL -> commit(actor, legal.callAmount());
                case BET, RAISE -> {
                    long to = taken.action().to().orElseThrow();
                    increment = Math.max(increment, Math.subtractExact(to, bets.get(1 - actor)));
                    commit(actor, Math.subtractExact(to, bets.get(actor)));
                }
            }
            actor = 1 - actor;
            if (folded) {
                refundOrContinue();
            } else {
                advance();
            }
        }

        private void advance() {
            if (BettingRules.runout(stacks, bets)) {
                runout = true;
                refundOrContinue();
            } else if (stacks.get(0) == 0 || stacks.get(1) == 0) {
                actor = stacks.get(0) == 0 ? 1 : 0;
                phase = Phase.DECISION;
            } else if (BettingRules.roundClosed(bets, acted)) {
                if (street == Street.RIVER) {
                    beginShowdown();
                } else {
                    phase = Phase.BOARD;
                }
            } else {
                phase = Phase.DECISION;
            }
        }

        private void refundOrContinue() {
            if (contributions.get(0) != contributions.get(1)) {
                phase = Phase.REFUND;
            } else {
                afterRefund();
            }
        }

        private void refund(HandEvent event) {
            require(event instanceof HandEvent.ReturnUncalled, "Expected uncalled-chip refund");
            HandEvent.ReturnUncalled returned = (HandEvent.ReturnUncalled) event;
            int seat = contributions.get(0) > contributions.get(1) ? 0 : 1;
            long amount = Math.subtractExact(contributions.get(seat), contributions.get(1 - seat));
            require(returned.seat() == seat && returned.amount() == amount, "Incorrect uncalled-chip refund");
            stacks = stacks.with(seat, Math.addExact(stacks.get(seat), amount));
            bets = bets.with(seat, Math.subtractExact(bets.get(seat), amount));
            contributions = contributions.with(seat, Math.subtractExact(contributions.get(seat), amount));
            afterRefund();
        }

        private void afterRefund() {
            if (folded) {
                beginPayout();
            } else if (board.size() < 5) {
                phase = Phase.BOARD;
            } else {
                beginShowdown();
            }
        }

        private void dealBoard(HandEvent event) {
            require(event instanceof HandEvent.DealBoard, "Expected community cards");
            HandEvent.DealBoard dealt = (HandEvent.DealBoard) event;
            Street next = BettingRules.nextStreet(street);
            require(dealt.street() == next, "Incorrect community-card street");
            Checks.disjointCards(board, holes.get(0), holes.get(1), dealt.cards());
            board.addAll(dealt.cards());
            street = next;
            bets = SeatChips.of(0, 0);
            increment = header.config().bigBlind();
            acted.clear();
            actor = 1 - header.buttonSeat();
            if (runout) {
                if (board.size() == 5) {
                    beginShowdown();
                }
            } else {
                phase = Phase.DECISION;
            }
        }

        private void beginShowdown() {
            street = Street.SHOWDOWN;
            showdown = true;
            phase = Phase.SHOWDOWN;
        }

        private void reveal(HandEvent event) {
            require(event instanceof HandEvent.Showdown, "Expected showdown reveal");
            HandEvent.Showdown shown = (HandEvent.Showdown) event;
            require(shown.seat() == nextShowdown, "Showdown seats out of order");
            learn(shown.seat(), shown.cards());
            List<Card> available = new ArrayList<>(board);
            available.addAll(shown.cards());
            HandRank rank = HandEvaluator.evaluate(available);
            require(rank.category() == shown.category(), "Incorrect showdown category");
            ranks.set(shown.seat(), Optional.of(rank));
            nextShowdown++;
        }

        private void learn(int seat, List<Card> cards) {
            if (!holes.get(seat).isEmpty()) {
                require(holes.get(seat).equals(cards), "Revealed cards disagree with private deal");
            } else {
                Checks.disjointCards(board, holes.get(1 - seat), cards);
                holes.set(seat, List.copyOf(cards));
            }
        }

        private void beginPayout() {
            phase = Phase.PAYOUT;
            settledPot = contributions.total();
            if (folded) {
                expectedPayouts = HandResult.splitPot(settledPot, OptionalInt.of(winner), header.buttonSeat());
            } else if (ranks.stream().allMatch(Optional::isPresent)) {
                int comparison = ranks.get(0).orElseThrow().compareTo(ranks.get(1).orElseThrow());
                expectedPayouts = HandResult.splitPot(settledPot, comparison == 0 ? OptionalInt.empty()
                        : OptionalInt.of(comparison > 0 ? 0 : 1), header.buttonSeat());
            }
        }

        private void payout(HandEvent event) {
            require(event instanceof HandEvent.Payout, "Expected pot payout");
            HandEvent.Payout paid = (HandEvent.Payout) event;
            if (expectedPayouts == null) {
                require(paid.amount() == settledPot || paid.amount() == settledPot / 2,
                        "Unverified payout must be the full pot or an equal split");
                expectedPayouts = HandResult.splitPot(settledPot,
                        paid.amount() == settledPot ? OptionalInt.of(paid.seat()) : OptionalInt.empty(), header.buttonSeat());
            }
            while (nextPayout < 2 && expectedPayouts.get(nextPayout) == 0) {
                nextPayout++;
            }
            require(paid.seat() == nextPayout && paid.amount() == expectedPayouts.get(nextPayout), "Incorrect payout");
            stacks = stacks.with(paid.seat(), Math.addExact(stacks.get(paid.seat()), paid.amount()));
            payouts = payouts.with(paid.seat(), paid.amount());
            long remaining = paid.amount();
            for (int seat = 0; seat < 2; seat++) {
                long taken = Math.min(remaining, contributions.get(seat));
                contributions = contributions.with(seat, Math.subtractExact(contributions.get(seat), taken));
                bets = bets.with(seat, Math.min(bets.get(seat), contributions.get(seat)));
                remaining = Math.subtractExact(remaining, taken);
            }
            nextPayout++;
            if (payouts.total() == settledPot) {
                street = Street.COMPLETE;
                phase = Phase.COMPLETE;
                acted.clear();
            }
        }

        private void commit(int seat, long amount) {
            stacks = stacks.with(seat, Math.subtractExact(stacks.get(seat), amount));
            bets = bets.with(seat, Math.addExact(bets.get(seat), amount));
            contributions = contributions.with(seat, Math.addExact(contributions.get(seat), amount));
        }

        private HandSnapshot snapshot() {
            boolean decision = phase == Phase.DECISION;
            return new HandSnapshot(new HandHistory(header, events), street, board, holes, stacks, bets, contributions,
                    decision ? OptionalInt.of(actor) : OptionalInt.empty(), increment, acted, decision, payouts, ranks);
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
