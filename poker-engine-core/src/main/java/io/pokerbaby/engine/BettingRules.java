package io.pokerbaby.engine;

import java.util.Optional;
import java.util.Set;

import io.pokerbaby.engine.IllegalActionException.Code;
import io.pokerbaby.engine.LegalActions.BetRange;

/** Shared betting arithmetic for live hands and histories with unknown cards. */
final class BettingRules {
    private BettingRules() {
    }

    static LegalActions legal(GameConfig config, SeatChips stacks, SeatChips bets, long increment, int seat) {
        int opponent = 1 - seat;
        long ownBet = bets.get(seat);
        long otherBet = bets.get(opponent);
        long call = Math.min(stacks.get(seat), Math.max(0, Math.subtractExact(otherBet, ownBet)));
        long maxTo = Math.addExact(ownBet, stacks.get(seat));
        Optional<BetRange> bet = Optional.empty();
        Optional<BetRange> raise = Optional.empty();
        if (stacks.get(opponent) > 0) {
            if (otherBet == 0 && ownBet == 0) {
                bet = Optional.of(new BetRange(Math.min(config.bigBlind(), maxTo), maxTo));
            } else if (maxTo > otherBet) {
                raise = Optional.of(new BetRange(Math.min(maxTo, Math.addExact(otherBet, increment)), maxTo));
            }
        }
        return new LegalActions(call > 0, call == 0, call, bet, raise);
    }

    static void validate(Action action, LegalActions legal) {
        boolean available = switch (action.type()) {
            case FOLD -> legal.canFold();
            case CHECK -> legal.canCheck();
            case CALL -> legal.canCall();
            case BET -> legal.canBet();
            case RAISE -> legal.canRaise();
        };
        if (!available) {
            throw new IllegalActionException(Code.ACTION_UNAVAILABLE, "Unavailable action: " + action.type());
        }
        if (action.to().isPresent()) {
            BetRange range = (action.type() == ActionType.BET ? legal.bet() : legal.raise()).orElseThrow();
            long to = action.to().orElseThrow();
            if (to < range.minTo()) {
                throw new IllegalActionException(Code.BELOW_MINIMUM, "Wager must be at least " + range.minTo());
            }
            if (to > range.maxTo()) {
                throw new IllegalActionException(Code.ABOVE_MAXIMUM, "Wager must be at most " + range.maxTo());
            }
        }
    }

    static boolean runout(SeatChips stacks, SeatChips bets) {
        if (stacks.get(0) > 0 && stacks.get(1) > 0) {
            return false;
        }
        int live = stacks.get(0) == 0 ? 1 : 0;
        return stacks.get(live) == 0 || bets.get(live) >= bets.get(1 - live);
    }

    static boolean roundClosed(SeatChips bets, Set<Integer> acted) {
        return acted.size() == 2 && bets.get(0) == bets.get(1);
    }

    static Street nextStreet(Street street) {
        return switch (street) {
            case PREFLOP -> Street.FLOP;
            case FLOP -> Street.TURN;
            case TURN -> Street.RIVER;
            default -> throw new IllegalStateException("Cannot deal after " + street);
        };
    }
}
