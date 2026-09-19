package net.kfyn.ob.engine;

import net.kfyn.ob.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PriceTimeOrderBookTest {

    private static Order order(long id, Side side, long pxTicks, long qtyTicks) {
        return new SimpleOrder(id, side, pxTicks, qtyTicks, OrderType.LIMIT);
    }

    private static Order buy(long id, long pxTicks, long qtyTicks) {
        return order(id, Side.BUY, pxTicks, qtyTicks);
    }

    @Nested
    @DisplayName("order validation")
    class OrderValidation {

        @Test
        void rejectsNonPositiveQty() {
            assertThrows(IllegalArgumentException.class, () -> order(1, Side.BUY, 100, 0));
            assertThrows(IllegalArgumentException.class, () -> order(1, Side.BUY, 100, -5));
        }

        @Test
        void acceptsNegativePx() {
            var o = order(1, Side.BUY, -100, 5);
            assertEquals(-100L, o.pxTicks());
        }

        @Test
        void rejectsNullEnums() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SimpleOrder(1, null, 1, 1, OrderType.LIMIT));
            assertThrows(IllegalArgumentException.class,
                    () -> new SimpleOrder(1, Side.BUY, 1, 1, null));
        }
    }

    @Nested
    @DisplayName("trade validation")
    class TradeValidation {

        @Test
        void rejectsNonPositiveQty() {
            assertThrows(IllegalArgumentException.class, () -> new SimpleTrade(1, 2, 100, 0));
            assertThrows(IllegalArgumentException.class, () -> new SimpleTrade(1, 2, 100, -5));
        }

        @Test
        void acceptsNegativePx() {
            var t = new SimpleTrade(1, 2, -100, 5);
            assertEquals(-100L, t.pxTicks());
        }
    }

    @Nested
    @DisplayName("book operations")
    class BookOps {

        OrderBook book() {
            return new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
        }

        @Test
        void addGroupsByPriceLevelAndKeepsFifoOrder() {
            var book = book();
            book.add(order(1, Side.BUY, 100, 5));
            book.add(order(2, Side.BUY, 100, 7));
            book.add(order(3, Side.BUY, 99, 1));
            var level = book.bids().get(100L);
            assertEquals(2, level.size());
            var it = level.iterator();
            assertEquals(1, it.next().id());   // FIFO: first-in first-out
            assertEquals(2, it.next().id());
            assertEquals(2, book.bids().size());
        }

        @Test
        void requeueMovesOrderToFrontOfLevel() {
            var book = book();
            book.add(order(1, Side.BUY, 100, 5));
            book.add(order(2, Side.BUY, 100, 7));
            book.requeue(order(1, Side.BUY, 100, 5));
            assertEquals(1, Objects.requireNonNull(book.bids().get(100L).iterator().next()).id());
        }

        @Test
        void bestBidIsHighestAskIsLowest() {
            var book = book();
            book.add(order(1, Side.BUY, 98, 5));
            book.add(order(2, Side.BUY, 101, 5));
            book.add(order(3, Side.SELL, 103, 5));
            book.add(order(4, Side.SELL, 102, 5));
            assertEquals(101L, book.bestBid().getKey());
            assertEquals(102L, book.bestAsk().getKey());
        }

        @Test
        void emptyBookBestIsNull() {
            var book = book();
            assertNull(book.bestBid());
            assertNull(book.bestAsk());
        }

        @Test
        void bestSkipsDrainedLevelsAndPrunesThem() {
            var book = book();
            book.add(order(1, Side.BUY, 101, 5));
            book.add(order(2, Side.BUY, 99, 5));
            book.bids().get(101L).clear();     // drain top level via read view
            var best = book.bestBid();
            assertNotNull(best);
            assertEquals(99L, best.getKey());  // drained level skipped
            assertFalse(book.bids().containsKey(101L));  // ...and pruned
        }

        @Test
        void negativePriceLevels() {
            var book = new PriceTimeOrderBook(Instrument.of("SPREAD", "0.10", "1"));
            book.add(order(1, Side.SELL, -100, 5));
            book.add(order(2, Side.SELL, -200, 5));
            assertEquals(-200L, book.bestAsk().getKey());   // best ask = lowest price
        }

        @Test
        void nullInstrumentRejected() {
            assertThrows(NullPointerException.class, () -> new PriceTimeOrderBook(null));
        }

        @Test
        void removeDeletesOrderAndPrunesLevel() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(buy(1, 100, 5));
            book.add(buy(2, 100, 7));
            assertTrue(book.remove(buy(1, 100, 5)));   // record equality: same id+px+qty+side
            assertEquals(1, book.bids().get(100L).size());
            assertTrue(book.remove(buy(2, 100, 7)));
            assertFalse(book.bids().containsKey(100L));  // level pruned eagerly
            assertNull(book.bestBid());
        }

        @Test
        void removeUnknownReturnsFalse() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(buy(1, 100, 5));
            assertFalse(book.remove(buy(9, 100, 5)));   // no such order at level
            assertFalse(book.remove(buy(1, 101, 5)));   // no such level
        }

        @Test
        void pollBestReturnsFifoHeadAndPrunes() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(buy(1, 100, 5));
            book.add(buy(2, 100, 7));
            book.add(buy(3, 99, 5));
            var head = book.pollBest(Side.BUY);
            assertEquals(1L, head.id());
            assertEquals(1, book.bids().get(100L).size());   // only order 2 remains
            book.pollBest(Side.BUY);                       // order 2 -> level 100 pruned
            assertEquals(99L, book.bestBid().getKey());     // order 3 still resting
            assertFalse(book.bids().containsKey(100L));
            assertNull(book.pollBest(Side.SELL));
        }
    }

    @Nested
    @DisplayName("view immutability")
    class Views {

        @Test
        void bidsAndAsksAreUnmodifiable() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(order(1, Side.BUY, 100, 5));
            assertThrows(UnsupportedOperationException.class,
                    () -> book.bids().clear());           // map read-only: no new levels via view
            assertThrows(UnsupportedOperationException.class,
                    () -> book.asks().remove(100L));
        }

        @Test
        void levelDequesRemainLiveThroughView() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(order(1, Side.BUY, 100, 5));
            book.bids().get(100L).clear();   // level collections deliberately live: engine drains levels through best
            assertTrue(book.bids().get(100L).isEmpty());
        }
    }

    @Nested
    @DisplayName("units")
    class Units {

        @Test
        void instrumentIsCarriedAndIdentifiesUnits() {
            var i = Instrument.of("BTCUSDT", "0.10", "0.001");
            var book = new PriceTimeOrderBook(i);
            assertSame(i, book.instrument());
            assertEquals("BTCUSDT", book.instrument().symbol());
        }

        @Test
        void boundaryConversionThroughBook() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            long pxTicks = book.instrument().pxTicks(new BigDecimal("5000.00"));
            book.add(order(1, Side.BUY, pxTicks, book.instrument().qtyTicks(new BigDecimal("2.000"))));
            assertEquals(0, new BigDecimal("5000.00").compareTo(book.instrument().pxValue(pxTicks)));
        }

        @Test
        void longCompareTotalOrderMatchesValueOrder() {
            var book = new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            var rnd = new Random(20260919L);
            for (int i = 0; i < 10_000; i++) {
                long t1 = rnd.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
                long t2 = rnd.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
                var v1 = book.instrument().pxValue(t1);
                var v2 = book.instrument().pxValue(t2);
                assertEquals(Integer.signum(v1.compareTo(v2)), Integer.signum(Long.compare(t1, t2)));
            }
        }
    }
}