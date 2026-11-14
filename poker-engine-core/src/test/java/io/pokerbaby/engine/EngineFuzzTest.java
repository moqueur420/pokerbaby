package io.pokerbaby.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineFuzzTest {
    @Test
    @DisplayName("Optimization stage 0: 1000 seeded hands, replay and every decision resumed to completion")
    void quickCorpus() {
        verifyHands(1_000);
    }

    @Test
    @Tag("slow")
    @DisplayName("Optimization stage 0: 50000 seeded hands, replay and every decision resumed to completion")
    void largeCorpus() {
        verifyHands(50_000);
    }

    private static void verifyHands(int count) {
        long resumes = 0;
        for (int index = 0; index < count; index++) {
            String context = "hand=" + index + ", seed=" + (SeededHands.SEED + index);
            try {
                resumes += verifyHand(SeededHands.play(index), context);
            } catch (RuntimeException | AssertionError failure) {
                throw new AssertionError(context, failure);
            }
        }
        assertTrue(resumes > count, "The corpus must exercise more than one decision per hand on average");
        System.out.println("EngineFuzzTest: hands=" + count + ", resumed decisions=" + resumes
                + ", seed=" + SeededHands.SEED);
    }

    private static int verifyHand(SeededHands.PlayedHand played, String context) {
        HandState end = played.end();
        long supply = end.history().header().startingStacks().total();
        ReplayResult replay = HandReplayer.replay(end.history());
        assertTrue(replay.outcomeVerified(), context);
        assertEquals(end.history().events().size() + 1, replay.steps().size(), context);
        assertEquals(played.actions().size(), replay.decisions().size(), context);
        assertEquals(end.stacks(), replay.lastSnapshot().stacks(), context);
        assertEquals(end.board(), replay.lastSnapshot().board(), context);
        assertTrue(replay.lastSnapshot().isComplete(), context);
        assertEquals(supply, end.stacks().total(), context);
        assertEquals(0, end.pot(), context);
        for (ReplayStep step : replay.steps()) {
            HandSnapshot snapshot = step.snapshot();
            assertEquals(supply, Math.addExact(snapshot.stacks().total(), snapshot.pot()), context);
        }

        for (HandState live : played.states()) {
            assertEquals(supply, Math.addExact(live.stacks().total(), live.pot()), context);
            assertEquals(live.contributions().total(), live.pot(), context);
            for (int seat = 0; seat < 2; seat++) {
                assertTrue(live.stacks().get(seat) >= 0 && live.contributions().get(seat) >= 0, context);
                assertTrue(live.streetBets().get(seat) <= live.contributions().get(seat), context);
            }
        }
        for (int index = 0; index < played.actions().size(); index++) {
            HandState live = played.states().get(index);
            PlayerView view = live.viewFor(live.actingSeat().orElseThrow());
            assertTrue(view.opponentCards().isEmpty(), context);
            for (HandEvent event : view.history().events()) {
                if (event instanceof HandEvent.DealHole hole) {
                    assertEquals(view.seat(), hole.seat(), context);
                }
            }
            Decision decision = replay.decisions().get(index);
            HandSnapshot snapshot = decision.snapshot();
            assertEquals(played.actions().get(index), decision.action(), context);
            assertEquals(live.history(), snapshot.history(), context);
            assertEquals(live.street(), snapshot.street(), context);
            assertEquals(live.board(), snapshot.board(), context);
            assertEquals(live.holeCards(), snapshot.holeCards(), context);
            assertEquals(live.stacks(), snapshot.stacks(), context);
            assertEquals(live.streetBets(), snapshot.streetBets(), context);
            assertEquals(live.contributions(), snapshot.contributions(), context);
            assertEquals(live.actingSeat(), snapshot.actingSeat(), context);
            assertEquals(live.lastRaiseIncrement(), snapshot.lastRaiseIncrement(), context);
            assertEquals(live.actedSeats(), snapshot.actedSeats(), context);
            assertEquals(HandEngine.legalActions(live), snapshot.legalActions(), context);
            HandState resumed = HandEngine.resume(snapshot, live.deck());
            assertEquals(live, resumed, context);
            for (int following = index; following < played.actions().size(); following++) {
                HandEvent.ActionTaken action = played.actions().get(following);
                resumed = HandEngine.apply(resumed, action.seat(), action.action());
            }
            assertEquals(end, resumed, context + ", resumed decision=" + index);
        }
        for (int seat = 0; seat < 2; seat++) {
            HandHistory filtered = end.history().forPlayer(seat);
            for (HandEvent event : filtered.events()) {
                if (event instanceof HandEvent.DealHole hole) {
                    assertEquals(seat, hole.seat(), context);
                }
            }
            ReplayResult visible = HandReplayer.replay(filtered);
            assertTrue(visible.lastSnapshot().isComplete(), context);
            assertTrue(visible.outcomeVerified(), context);
            assertEquals(end.stacks(), visible.lastSnapshot().stacks(), context);
            assertEquals(end.board(), visible.lastSnapshot().board(), context);
        }
        return played.actions().size();
    }
}
