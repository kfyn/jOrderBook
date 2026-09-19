package net.kfyn.ob;

import net.kfyn.ob.engine.AuctionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-requirement acceptance test (docs/20260919-mq-ob-req.md):
 * the spec's example order book must uncross at the maximum-volume
 * price with the documented volumes.
 */
class ReqExampleTest {

    @Test
    @DisplayName("spec example book: matching auction price 99, total matched volume 700")
    void goldenBookUncrossesAtMaxVolumePrice() {
        AuctionResult r = ReqExample.uncrossGoldenBook();

        // candidate prices in the spec: 98, 99, 100 -> volumes 700@98,
        // 700@99, 500@100; max volume is 700, first achieved at 98, but the
        // spec's own worked answer and the imbalance tie-break both select 99
        assertEquals(OptionalLong.of(99), r.priceTicks());
        assertEquals(700, r.volumeTicks());
        // FIFO uncrossing: 102@50000 fills 102 vs ask 99; 1000@99 fills the
        // ask-99 remainder 98 then sweeps 500 from ask-96 -> 3 trades
        assertEquals(3, r.trades().size());

        // every trade executes at the single clearing price
        assertTrue(r.trades().stream().allMatch(t -> t.pxTicks() == 99));

        // volume conservation: sum of trade quantities == reported total
        assertEquals(r.volumeTicks(),
                r.trades().stream().mapToLong(net.kfyn.ob.entity.Trade::qtyTicks).sum());
    }

    @Test
    @DisplayName("per-candidate volumes match the spec's stated candidates (98, 99, 100)")
    void candidateVolumesMatchSpec() {
        // The spec names 98/99/100 as candidate prices. The algorithm also
        // considers every other union price.
        //   demand(px) = sum of bid qty where bid px >= px; supply(px) = sum of
        //   ask qty where ask px <= px; volume(px) = min(demand, supply).
        //   vol(96)=500, vol(98)=500, vol(99)=700, vol(100)=102, vol(50000)=102.
        // 99 is the unique argmax — no tie-break needed for the spec example.
        assertEquals(500, volumeAt(98));
        assertEquals(700, volumeAt(99));
        assertEquals(102, volumeAt(100));
        assertEquals(500, volumeAt(96));
    }

    /** Volume that would trade if the clearing price were {@code px} (whole dollars). */
    private static long volumeAt(long px) {
        long pxTicks = ReqExample.BHP.pxTicks(java.math.BigDecimal.valueOf(px));
        long demand = ReqExample.bids().stream()
                .filter(o -> o.pxTicks() >= pxTicks)
                .mapToLong(net.kfyn.ob.entity.Order::qtyTicks).sum();
        long supply = ReqExample.asks().stream()
                .filter(o -> o.pxTicks() <= pxTicks)
                .mapToLong(net.kfyn.ob.entity.Order::qtyTicks).sum();
        return Math.min(demand, supply);
    }
}