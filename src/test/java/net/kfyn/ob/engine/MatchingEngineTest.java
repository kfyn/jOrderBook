package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderBook;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private static final Instrument BTC = Instrument.of("BTCUSDT", "0.10", "0.001");

    private static Order buy(long id, long pxTicks, long qtyTicks) {
        return new Order(id, Side.BUY, pxTicks, qtyTicks, OrderType.LIMIT);
    }

    private static Order sell(long id, long pxTicks, long qtyTicks) {
        return new Order(id, Side.SELL, pxTicks, qtyTicks, OrderType.LIMIT);
    }

    @Nested
    @DisplayName("no cross")
    class NoCross {

        @Test
        void restsWhenNoLiquidity() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            var r = engine.submit(buy(1, 100, 5));
            assertEquals(0, r.trades().size());
            assertEquals(5, r.remainingQtyTicks());
            assertTrue(engine.isOpen(1));
        }

        @Test
        void restsWhenNotCrossing() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            engine.submit(sell(1, 102, 5));
            var r = engine.submit(buy(2, 101, 5));   // bid below best ask
            assertEquals(0, r.trades().size());
            assertEquals(5, r.remainingQtyTicks());
            assertEquals(102L, book.bestAsk().getKey());
        }

        @Test
        void nullOrderRejected() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            assertThrows(NullPointerException.class, () -> engine.submit(null));
        }
    }

    @Nested
    @DisplayName("crossing")
    class Crossing {

        @Test
        void fullFillAtRestingPrice() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, 100, 5));
            var r = engine.submit(buy(2, 105, 5));   // crosses; fills at 100, not 105
            assertEquals(1, r.trades().size());
            var t = r.trades().get(0);
            assertEquals(100L, t.pxTicks());
            assertEquals(5L, t.qtyTicks());
            assertEquals(2L, t.bidOrderId());
            assertEquals(1L, t.askOrderId());
            assertEquals(0, r.remainingQtyTicks());
            assertFalse(engine.isOpen(1));
            assertFalse(engine.isOpen(2));
        }

        @Test
        void incomingPartialFillRemainderRests() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            engine.submit(sell(1, 100, 3));
            var r = engine.submit(buy(2, 100, 5));
            assertEquals(1, r.trades().size());
            assertEquals(3L, r.trades().get(0).qtyTicks());
            assertEquals(2, r.remainingQtyTicks());
            assertTrue(engine.isOpen(2));
            assertEquals(100L, book.bestBid().getKey());
        }

        @Test
        void restingPartialFillKeepsFifoPosition() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, 100, 2));
            engine.submit(sell(2, 100, 3));
            engine.submit(buy(3, 100, 3));            // fills order 1 (2) + 1 of order 2
            assertFalse(engine.isOpen(1));
            assertTrue(engine.isOpen(2));
            engine.submit(sell(4, 100, 1));           // queues behind order 2
            var r = engine.submit(buy(5, 100, 1));     // must hit order 2 first, not order 4
            assertEquals(1, r.trades().size());
            assertEquals(2L, r.trades().get(0).askOrderId());
        }

        @Test
        void sweepsMultipleLevelsInPriceOrder() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, 101, 2));
            engine.submit(sell(2, 100, 3));
            engine.submit(sell(3, 102, 4));
            var r = engine.submit(buy(4, 102, 9));     // 3@100 + 2@101 + 4@102
            assertEquals(3, r.trades().size());
            assertEquals(100L, r.trades().get(0).pxTicks());
            assertEquals(101L, r.trades().get(1).pxTicks());
            assertEquals(102L, r.trades().get(2).pxTicks());
            assertEquals(9L, r.trades().stream().mapToLong(Trade::qtyTicks).sum());
            assertEquals(0, r.remainingQtyTicks());
            assertEquals(0, engine.openCount());
        }

        @Test
        void duplicateIdRejected() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(buy(1, 100, 5));
            assertThrows(IllegalArgumentException.class, () -> engine.submit(buy(1, 99, 5)));
        }

        @Test
        void filledIdCannotBeResubmitted() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, 100, 5));
            engine.submit(buy(2, 100, 5));           // both fully filled
            assertThrows(IllegalArgumentException.class, () -> engine.submit(buy(1, 100, 5)));
            assertThrows(IllegalArgumentException.class, () -> engine.submit(sell(2, 100, 5)));
        }

        @Test
        void canceledIdCannotBeResubmitted() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(buy(1, 100, 5));
            engine.cancel(1);
            assertThrows(IllegalArgumentException.class, () -> engine.submit(buy(1, 100, 5)));
        }

        @Test
        void cancelThrowsWhenOpenOrderMissingFromBook() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            engine.submit(buy(1, 100, 5));
            book.bids().get(100L).clear();            // corrupt: open says 1, book lost it
            assertThrows(IllegalStateException.class, () -> engine.cancel(1));
        }

        @Test
        void incomingPartiallyFillsMultipleLevelsAndRestsRemainder() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            engine.submit(sell(1, 100, 3));
            engine.submit(sell(2, 101, 4));
            engine.submit(sell(3, 102, 5));
            var r = engine.submit(buy(4, 103, 14));   // 3@100 + 4@101 + 5@102 = 12 filled
            assertEquals(3, r.trades().size());
            assertEquals(100L, r.trades().get(0).pxTicks());
            assertEquals(101L, r.trades().get(1).pxTicks());
            assertEquals(102L, r.trades().get(2).pxTicks());
            assertEquals(12, r.trades().stream().mapToLong(Trade::qtyTicks).sum());
            assertEquals(2, r.remainingQtyTicks());          // remainder rests at 103
            assertTrue(engine.isOpen(4));
            assertEquals(103L, book.bestBid().getKey());
            assertTrue(book.asks().isEmpty());               // all asks consumed
        }

        @Test
        void nullOrderRejectedByBook() {
            var book = new OrderBook(BTC);
            assertThrows(NullPointerException.class, () -> book.add(null));
            assertThrows(NullPointerException.class, () -> book.requeue(null));
            assertThrows(NullPointerException.class, () -> book.remove(null));
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        void cancelRemovesOpenOrder() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(buy(1, 100, 5));
            assertTrue(engine.cancel(1));
            assertFalse(engine.isOpen(1));
            assertFalse(engine.cancel(1));
        }

        @Test
        void cancelUnknownIdReturnsFalse() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            assertFalse(engine.cancel(42));
        }

        @Test
        void cancelledLevelIsPrunedFromBest() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            engine.submit(buy(1, 101, 5));
            engine.submit(buy(2, 99, 5));
            engine.cancel(1);
            assertEquals(99L, book.bestBid().getKey());
        }
    }

    @Nested
    @DisplayName("negative prices (spread products)")
    class Negatives {

        @Test
        void negativePricesMatchCorrectly() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, -100, 5));
            var r = engine.submit(buy(2, -100, 5));
            assertEquals(1, r.trades().size());
            assertEquals(-100L, r.trades().get(0).pxTicks());
            assertFalse(engine.isOpen(1));
            assertFalse(engine.isOpen(2));
        }

        @Test
        void crossingBelowZeroAsk() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, -100, 5));
            var r = engine.submit(buy(2, -99, 5));    // -99 >= -100 crosses
            assertEquals(1, r.trades().size());
            assertEquals(-100L, r.trades().get(0).pxTicks());
        }
    }

    @Nested
    @DisplayName("invariants (property-style)")
    class Invariants {

        @Test
        void quantityConservationUnderRandomFlow() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            var rnd = new Random(20260919L);
            long totalSubmitted = 0, totalTraded = 0;
            for (int i = 1; i <= 2_000; i++) {
                Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
                long px = rnd.nextLong(95, 106);
                long qty = rnd.nextLong(1, 20);
                totalSubmitted += qty;
                var r = engine.submit(new Order(i, side, px, qty, OrderType.LIMIT));
                for (Trade t : r.trades()) totalTraded += t.qtyTicks();
            }
            long openOnBook = 0;
            for (var level : book.bids().values()) for (var o : level) openOnBook += o.qtyTicks();
            for (var level : book.asks().values()) for (var o : level) openOnBook += o.qtyTicks();
            // a trade extinguishes qty on both sides: submitted = open + 2 x traded
            assertEquals(totalSubmitted, totalTraded * 2 + openOnBook);
        }

        @Test
        void remainingPlusFilledEqualsSubmitted() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, 100, 5));
            engine.submit(sell(2, 101, 5));
            var r = engine.submit(buy(3, 101, 7));    // fills 5@100 + 2@101
            long filled = r.trades().stream().mapToLong(Trade::qtyTicks).sum();
            assertEquals(7, filled + r.remainingQtyTicks());
        }

        @Test
        void totalTradedNeverExceedsResting() {
            var engine = new MatchingEngine(new OrderBook(BTC));
            engine.submit(sell(1, 100, 5));
            var r = engine.submit(buy(2, 100, 100));  // can only fill 5
            assertEquals(5L, r.trades().stream().mapToLong(Trade::qtyTicks).sum());
            assertEquals(95, r.remainingQtyTicks());
        }

        @Test
        void openOrdersBookAndEngineAgree() {
            var book = new OrderBook(BTC);
            var engine = new MatchingEngine(book);
            engine.submit(buy(1, 100, 5));
            engine.submit(buy(2, 101, 5));
            engine.submit(sell(3, 110, 5));
            assertEquals(3, engine.openCount());
            assertEquals(3, book.bids().values().stream().mapToInt(q -> q.size()).sum()
                    + book.asks().values().stream().mapToInt(q -> q.size()).sum());
            engine.cancel(2);
            assertEquals(2, engine.openCount());
        }
    }
}