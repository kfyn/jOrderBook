package net.kfyn.ob;

import net.kfyn.ob.entity.Trade;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void simRunsAuctionThenContinuousMatching() {
        Simulation.Outcome out = Simulation.run();

        // Phase B: opening auction — max volume 23 at 99 (tie with 101 broken
        // by sell pressure -> lower price); all 3 trades at the clearing price
        assertEquals(OptionalLong.of(99), out.auction().priceTicks());
        assertEquals(23, out.auction().volumeTicks());
        assertEquals(3, out.auction().trades().size());
        assertTrue(out.auction().trades().stream().allMatch(t -> t.pxTicks() == 99));

        // Phase C: rested leftover (ask 2@99) drains against resting bid 99x10
        assertEquals(1, out.engineTrades().size());
        Trade t = out.engineTrades().getFirst();
        assertEquals(1L, t.bidOrderId());
        assertEquals(14L, t.askOrderId());
        assertEquals(99, t.pxTicks());
        assertEquals(2, t.qtyTicks());

        // open state: 10 seeds + 1 rested-buy remainder (leftover ask fully drained)
        assertEquals(11, out.openOrders());
        assertEquals(OptionalLong.of(99), out.bestBidTicks());
        assertEquals(OptionalLong.of(101), out.bestAskTicks());

        // global conservation invariant (also enforced fail-loud inside run())
        assertEquals(out.submittedQtyTicks(), out.openQtyTicks() + 2 * out.tradedQtyTicks());
        assertEquals(25, out.tradedQtyTicks());   // 23 auction + 2 engine
    }

    @Test
    void simIsDeterministic() {
        Simulation.Outcome a = Simulation.run();
        Simulation.Outcome b = Simulation.run();
        assertEquals(a, b);
    }
}