package io.pokerbaby.web;

import io.pokerbaby.engine.Action;
import io.pokerbaby.engine.Card;
import io.pokerbaby.engine.Deck;
import io.pokerbaby.engine.GameConfig;
import io.pokerbaby.engine.HandEngine;
import io.pokerbaby.engine.HandEvent;
import io.pokerbaby.engine.HandReplayer;
import io.pokerbaby.engine.HandSnapshot;
import io.pokerbaby.engine.HandState;
import io.pokerbaby.engine.LegalActions;
import io.pokerbaby.engine.MatchState;
import io.pokerbaby.engine.PlayerView;
import io.pokerbaby.engine.ReplayResult;
import io.pokerbaby.engine.ReplayStep;
import io.pokerbaby.engine.SeatChips;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

final class GameSession {
    private final RandomGenerator decks;
    private final RandomGenerator bot;
    private MatchState match;
    private HandState hand;
    private MatchState matchAtStart;
    private Deck originalDeck;
    private final Map<Long, SavedHand> savedHands = new LinkedHashMap<>();
    private long handNumber;
    private long version;

    GameSession() {
        this(new Random(), new Random());
    }

    GameSession(RandomGenerator decks, RandomGenerator bot) {
        this.decks = decks;
        this.bot = bot;
        reset();
    }

    synchronized Map<String, Object> state() {
        // Project exclusively from PlayerView: never serialize the full HandState.
        PlayerView view = hand.viewFor(0);
        LegalActions legal = view.legalActions();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("version", version);
        state.put("handNumber", handNumber);
        state.put("street", view.street().name());
        state.put("buttonSeat", view.buttonSeat());
        state.put("board", cards(view.board()));
        state.put("holeCards", cards(view.holeCards()));
        state.put("opponentCards", view.opponentCards().map(GameSession::cards).orElse(List.of()));
        state.put("stacks", List.of(view.stacks().get(0), view.stacks().get(1)));
        state.put("streetBets", List.of(view.streetBets().get(0), view.streetBets().get(1)));
        state.put("pot", view.pot());
        state.put("smallBlind", view.config().smallBlind());
        state.put("bigBlind", view.config().bigBlind());
        state.put("complete", view.isComplete());
        state.put("matchOver", match.isOver());
        state.put("hands", savedHands.entrySet().stream().map(entry -> Map.of(
                "handNumber", entry.getKey(), "complete", entry.getValue().hand().isComplete())).toList());
        state.put("legal", Map.of("fold", legal.canFold(), "check", legal.canCheck(),
                "callAmount", legal.callAmount(), "bet", range(legal.bet().orElse(null)),
                "raise", range(legal.raise().orElse(null))));
        state.put("history", view.history().events().stream().map(GameSession::event).toList());
        state.put("result", stateResult(view));
        return state;
    }

    synchronized Map<String, Object> replay(long number) {
        SavedHand saved = savedHand(number);
        ReplayResult replay = HandReplayer.replay(saved.hand().history());
        return Map.of("version", version, "handNumber", number, "outcomeVerified", replay.outcomeVerified(),
                "steps", replay.steps().stream().map(step -> replayStep(saved, number, step)).toList());
    }

    private Map<String, Object> replayStep(SavedHand saved, long number, ReplayStep step) {
        // Redact each historical prefix independently; never backfill future reveals.
        HandSnapshot snapshot = step.snapshot().forPlayer(0);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("handNumber", number);
        view.put("street", snapshot.street().name());
        view.put("buttonSeat", snapshot.history().header().buttonSeat());
        view.put("board", cards(snapshot.board()));
        view.put("holeCards", cards(snapshot.holeCards().get(0)));
        view.put("opponentCards", cards(snapshot.holeCards().get(1)));
        view.put("stacks", snapshot.stacks().values());
        view.put("streetBets", snapshot.streetBets().values());
        view.put("pot", snapshot.pot());
        view.put("smallBlind", snapshot.history().header().config().smallBlind());
        view.put("bigBlind", snapshot.history().header().config().bigBlind());
        view.put("complete", snapshot.isComplete());
        view.put("actingSeat", snapshot.actingSeat().orElse(-1));
        view.put("decisionPoint", snapshot.decisionPoint());
        view.put("legal", Map.of("fold", false, "check", false, "callAmount", 0, "bet", Map.of(), "raise", Map.of()));
        view.put("history", snapshot.history().events().stream().map(GameSession::event).toList());
        view.put("result", snapshot.isComplete() ? stateResult(saved.hand().viewFor(0)) : null);
        Map<String, Object> fact = step.event().map(item -> item instanceof HandEvent.DealHole hole && hole.seat() == 1
                ? Map.<String, Object>of("type", "deal_hidden", "seat", 1) : event(item)).orElse(Map.of());
        return Map.of("number", step.eventNumber(), "event", fact, "state", view,
                "canResume", snapshot.decisionPoint() && snapshot.actingSeat().orElse(-1) == 0
                        && step.snapshot().holeCards().stream().allMatch(handCards -> handCards.size() == 2));
    }

    private static Object stateResult(PlayerView view) {
        return view.result().map(result -> Map.<String, Object>of(
                "winnerSeat", result.winnerSeat().orElse(-1), "pot", result.pot(),
                "showdown", result.wasShowdown(),
                "categories", result.ranks().stream().map(rank -> rank.category().name()).toList(),
                "net", view.stacks().get(0) - view.history().header().startingStacks().get(0))).orElse(null);
    }

    synchronized Map<String, Object> command(String command, Map<String, String> fields) {
        long expected;
        try {
            expected = Long.parseLong(fields.getOrDefault("version", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Обновите страницу перед следующим действием.");
        }
        if (expected != version) {
            throw new IllegalStateException("Состояние стола изменилось. Повторите действие.");
        }
        switch (command) {
            case "new" -> reset();
            case "next" -> {
                if (!hand.isComplete() || match.isOver()) {
                    throw new IllegalStateException("Следующая раздача пока недоступна.");
                }
                startHand();
            }
            case "action" -> {
                if (hand.isComplete()) {
                    throw new IllegalStateException("Раздача уже завершена.");
                }
                Action action = switch (fields.getOrDefault("type", "")) {
                    case "FOLD" -> Action.fold();
                    case "CHECK" -> Action.check();
                    case "CALL" -> Action.call();
                    case "BET" -> Action.betTo(amount(fields));
                    case "RAISE" -> Action.raiseTo(amount(fields));
                    default -> throw new IllegalArgumentException("Неизвестное действие.");
                };
                hand = HandEngine.apply(hand, 0, action);
                playBot();
            }
            case "resume" -> resume(fields);
            default -> throw new IllegalArgumentException("Неизвестная команда.");
        }
        version++;
        remember();
        return state();
    }

    private void resume(Map<String, String> fields) {
        long number = Long.parseLong(fields.getOrDefault("hand", ""));
        int index = Integer.parseInt(fields.getOrDefault("step", ""));
        SavedHand saved = savedHand(number);
        ReplayResult replay = HandReplayer.replay(saved.hand().history());
        if (index < 0 || index >= replay.steps().size()) {
            throw new IllegalArgumentException("Такого шага нет.");
        }
        HandSnapshot snapshot = replay.steps().get(index).snapshot();
        if (!snapshot.decisionPoint() || snapshot.actingSeat().orElse(-1) != 0) {
            throw new IllegalStateException("Продолжить можно только с точки вашего решения.");
        }
        Deck remaining = saved.originalDeck();
        for (int count = 0; count < 4 + snapshot.board().size(); count++) {
            remaining = remaining.draw().deck();
        }
        HandState restored = HandEngine.resume(snapshot, remaining);
        match = saved.matchAtStart();
        matchAtStart = saved.matchAtStart();
        originalDeck = saved.originalDeck();
        handNumber = number;
        hand = restored;
        savedHands.keySet().removeIf(key -> key >= number);
    }

    private SavedHand savedHand(long number) {
        SavedHand saved = savedHands.get(number);
        if (saved == null) {
            throw new IllegalStateException("Раздача больше не доступна. Обновите список.");
        }
        return saved;
    }

    private void remember() {
        savedHands.put(handNumber, new SavedHand(matchAtStart, originalDeck, hand));
        while (savedHands.size() > 20) {
            savedHands.remove(savedHands.keySet().iterator().next());
        }
    }

    private void reset() {
        savedHands.clear();
        match = MatchState.start(UUID.randomUUID().toString(), List.of("human", "bot"),
                GameConfig.DEFAULT, SeatChips.of(10_000, 10_000), 0);
        startHand();
    }

    private void startHand() {
        handNumber = match.handNumber();
        matchAtStart = match;
        originalDeck = Deck.shuffled(decks);
        hand = match.nextHand(originalDeck);
        playBot();
        remember();
    }

    private void playBot() {
        while (!hand.isComplete() && hand.actingSeat().orElseThrow() == 1) {
            hand = HandEngine.apply(hand, 1, chooseBotAction(hand.viewFor(1), bot));
        }
        if (hand.isComplete()) {
            match = match.afterHand(hand.result().orElseThrow());
        }
    }

    // A deliberately modest opponent. Its only game information is its own view.
    private static Action chooseBotAction(PlayerView view, RandomGenerator random) {
        LegalActions legal = view.legalActions();
        double roll = random.nextDouble();
        if (legal.canCall() && legal.canFold()
                && legal.callAmount() > view.config().bigBlind() * 3 && roll < 0.18) {
            return Action.fold();
        }
        if (legal.canRaise() && roll > 0.88) {
            return Action.raiseTo(legal.raise().orElseThrow().minTo());
        }
        if (legal.canBet() && roll > 0.58) {
            LegalActions.BetRange range = legal.bet().orElseThrow();
            return Action.betTo(Math.min(range.maxTo(), Math.max(range.minTo(), view.pot() / 2)));
        }
        return legal.canCheck() ? Action.check() : Action.call();
    }

    private static long amount(Map<String, String> fields) {
        try {
            return Long.parseLong(fields.getOrDefault("to", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Укажите целое число фишек.");
        }
    }

    private static List<String> cards(List<Card> cards) {
        return cards.stream().map(Card::toString).toList();
    }

    private static Map<String, Long> range(LegalActions.BetRange range) {
        return range == null ? Map.of() : Map.of("min", range.minTo(), "max", range.maxTo());
    }

    private static Map<String, Object> event(HandEvent event) {
        return switch (event) {
            case HandEvent.PostBlind blind -> Map.of("type", blind.type(), "seat", blind.seat(), "amount", blind.amount());
            case HandEvent.DealHole hole -> Map.of("type", hole.type(), "seat", hole.seat(), "cards", cards(hole.cards()));
            case HandEvent.ActionTaken taken -> Map.of("type", taken.type(), "seat", taken.seat(),
                    "action", taken.action().type().name(), "to", taken.action().to().orElse(0));
            case HandEvent.DealBoard board -> Map.of("type", board.type(), "street", board.street().name(), "cards", cards(board.cards()));
            case HandEvent.ReturnUncalled returned -> Map.of("type", returned.type(), "seat", returned.seat(), "amount", returned.amount());
            case HandEvent.Showdown shown -> Map.of("type", shown.type(), "seat", shown.seat(),
                    "cards", cards(shown.cards()), "category", shown.category().name());
            case HandEvent.Payout payout -> Map.of("type", payout.type(), "seat", payout.seat(), "amount", payout.amount());
        };
    }

    private record SavedHand(MatchState matchAtStart, Deck originalDeck, HandState hand) {
    }
}
