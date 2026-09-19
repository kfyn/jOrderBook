package net.kfyn.ob.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private static Order buy(long id, long pxT, long qtyT) {
        return new Order(id, Side.BUY, pxT, qtyT, OrderType.LIMIT);
    }

    private static Order sell(long id, long pxT, long qtyT) {
        return new Order(id, Side.SELL, pxT, qtyT, OrderType.LIMIT);
    }

    @Nested
    @DisplayName("order validation")
    class OrderValidation {

        @Test
        void rejectsNegativeTicksAndNulls() {
            assertThrows(IllegalArgumentException.class, () -> buy(1, -1, 1));
            assertThrows(IllegalArgumentException.class, () -> buy(1, 1, -1));
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(1, null, 1, 1, OrderType.LIMIT));
            assertThrows(IllegalArgumentException.class,
                    () -> new Order(1, Side.BUY, 1, 1, null));
        }
    }

    @Nested
    @DisplayName("book operations")
    class BookOps {

        @Test
        void addGroupsByPriceLevelAndKeepsFifoOrder() {
            var book = new OrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(buy(1, 100, 5));
            book.add(buy(2, 100, 7));
            book.add(buy(3, 99, 1));
            var level = book.bids().get(100L);
            assertEquals(2, level.size());
            assertEquals(1, level.peekFirst().id());
            assertEquals(2, level.peekLast().id());
            assertEquals(2, book.bids().size());
        }

        @Test
        void requeueMovesOrderToFrontOfLevel() {
            var book = new OrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(buy(1, 100, 5));
            book.add(buy(2, 100, 7));
            book.requeue(buy(1, 100, 5));
            assertEquals(1, book.bids().get(100L).peekFirst().id());
        }

        @Test
        void bestBidIsHighestAskIsLowest() {
            var book = new OrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            book.add(buy(1, 98, 5));
            book.add(buy(2, 101, 5));
            book.add(sell(3, 103, 5));
            book.add(sell(4, 102, 5));
            assertEquals(101L, book.bestBid().getKey());
            assertEquals(102L, book.bestAsk().getKey());
        }

        @Test
        void emptyBookBestIsNull() {
            var book = new OrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            assertNull(book.bestBid());
            assertNull(book.bestAsk());
        }

        @Test
        void nullInstrumentRejected() {
            assertThrows(NullPointerException.class, () -> new OrderBook(null));
        }
    }

    @Nested
    @DisplayName("cross-instrument safety")
    class Units {

        @Test
        void instrumentIsCarriedAndIdentifiesUnits() {
            var i = Instrument.of("BTCUSDT", "0.10", "0.001");
            var book = new OrderBook(i);
            assertSame(i, book.instrument());
            assertEquals("BTCUSDT", book.instrument().symbol());
        }

        @Test
        void boundaryConversionThroughBook() {
            var book = new OrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            long pxT = book.instrument().pxTicks(new BigDecimal("5000.00"));
            book.add(buy(1, pxT, book.instrument().qtyTicks(new BigDecimal("2.000"))));
            assertEquals(0, new BigDecimal("5000.00").compareTo(book.instrument().pxValue(pxT)));
        }

        @Test
        void longCompareTotalOrderMatchesValueOrder() {
            var book = new OrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
            var rnd = new Random(20260919L);
            for (int i = 0; i < 10_000; i++) {
                long t1 = rnd.nextLong(0, Long.MAX_VALUE);
                long t2 = rnd.nextLong(0, Long.MAX_VALUE);
                var v1 = book.instrument().pxValue(t1);
                var v2 = book.instrument().pxValue(t2);
                assertEquals(Integer.signum(v1.compareTo(v2)), Integer.signum(Long.compare(t1, t2)));
            }
        }
    }
}