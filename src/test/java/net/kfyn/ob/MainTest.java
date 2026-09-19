package net.kfyn.ob;

import net.kfyn.ob.engine.AuctionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    @DisplayName("spec example book: matching auction price 99, total matched volume 700")
    void bookUncrossesAtMaxVolumePrice() {
        AuctionResult r = Main.uncrossBook();

        // Candidate prices named by the spec (98, 99, 100); volumes:
        //   vol(px) = min(sum of bid qty with bid px >= px,
        //                 sum of ask qty with ask px <= px)
        //   vol(98)=500, vol(99)=700, vol(100)=102.
        // 99 is the unique argmax — no tie-break needed for the spec example.
        assertEquals(OptionalLong.of(99), r.priceTicks());
        assertEquals(700, r.volumeTicks());

        // FIFO uncrossing at the clearing price: 102@50000 takes 102 of the
        // 200@99 ask; 1000@99 takes the remaining 98 of that ask and sweeps
        // all 500 of the 500@96 ask -> 3 trades
        assertEquals(3, r.trades().size());
        assertTrue(r.trades().stream().allMatch(t -> t.pxTicks() == 99));
        assertEquals(r.volumeTicks(),
                r.trades().stream().mapToLong(net.kfyn.ob.entity.Trade::qtyTicks).sum());
    }

    @Test
    @DisplayName("per-candidate volumes match the spec's stated candidates (98, 99, 100)")
    void candidateVolumesMatchSpec() {
        // The algorithm considers the union of all bid and ask prices, not
        // just the spec-named candidates: vol(96)=500, vol(50000)=0.
        assertEquals(500, volumeAt(96));
        assertEquals(500, volumeAt(98));
        assertEquals(700, volumeAt(99));
        assertEquals(102, volumeAt(100));
    }

    @Test
    @DisplayName("demo run: price 99, volume 700, trades and leftovers printed")
    void demoRunPrintsAuctionOutcome() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("matching auction price : 99"), out);
        assertTrue(out.contains("total matched volume   : 700 share(s)"), out);
        assertTrue(out.contains("trades                 : 3"), out);
        assertTrue(out.contains("unfilled (leftover) orders : 3"), out);
    }

    /** Volume that would trade if the clearing price were {@code px} (whole dollars). */
    private static long volumeAt(long px) {
        long pxTicks = Main.BHP.pxTicks(java.math.BigDecimal.valueOf(px));
        long demand = Main.bids().stream()
                .filter(o -> o.pxTicks() >= pxTicks)
                .mapToLong(net.kfyn.ob.entity.Order::qtyTicks).sum();
        long supply = Main.asks().stream()
                .filter(o -> o.pxTicks() <= pxTicks)
                .mapToLong(net.kfyn.ob.entity.Order::qtyTicks).sum();
        return Math.min(demand, supply);
    }
}