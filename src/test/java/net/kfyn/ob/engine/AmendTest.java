package net.kfyn.ob.engine;

import net.kfyn.ob.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmendTest {

    private static Order order(long id, Side side, long pxTicks, long qtyTicks) {
        return new SimpleOrder(id, side, pxTicks, qtyTicks, OrderType.LIMIT);
    }

    private static Order buy(long id, long pxTicks, long qtyTicks) {
        return order(id, Side.BUY, pxTicks, qtyTicks);
    }

    private static Order sell(long id, long pxTicks, long qtyTicks) {
        return order(id, Side.SELL, pxTicks, qtyTicks);
    }

    private PriceTimeOrderBook book() {
        return new PriceTimeOrderBook(Instrument.of("BTCUSDT", "0.10", "0.001"));
    }

    @Nested
    @DisplayName("queue position")
    class QueuePosition {

        @Test
        void samePxQtyReductionKeepsQueuePosition() {
            var book = book();
            var a = buy(1, 100, 10);
            var b = buy(2, 100, 10);
            book.add(a);
            book.add(b);
            var amended = book.amend(a, 100, 4);
            var it = book.bids().get(100L).iterator();
            assertSame(amended, it.next(), "reduced order keeps front position");
            assertSame(b, it.next());
            assertEquals(4, amended.qtyTicks());
            assertEquals(1, amended.id(), "same id");
        }

        @Test
        void qtyIncreaseLosesPriority() {
            var book = book();
            var a = buy(1, 100, 10);
            var b = buy(2, 100, 10);
            book.add(a);
            book.add(b);
            var amended = book.amend(a, 100, 20);
            var it = book.bids().get(100L).iterator();
            assertSame(b, it.next(), "increased order goes to back");
            assertSame(amended, it.next());
        }

        @Test
        void pxChangeLosesPriorityEvenWithQtyReduction() {
            var book = book();
            var a = buy(1, 100, 10);
            var b = buy(2, 99, 10);
            book.add(a);
            book.add(b);
            var amended = book.amend(a, 99, 5);
            // a joins behind b at the 99 level
            var it = book.bids().get(99L).iterator();
            assertSame(b, it.next());
            assertSame(amended, it.next());
            assertNull(book.bids().get(100L), "old level pruned when emptied");
            assertEquals(1, book.bids().size());
        }

        @Test
        void amendToFreshLevelIsAloneThere() {
            var book = book();
            var a = buy(1, 100, 10);
            var b = buy(2, 100, 10);
            book.add(a);
            book.add(b);
            var amended = book.amend(a, 101, 10);
            assertEquals(2, book.bids().size());   // levels 101 and 100
            assertSame(amended, book.bids().get(101L).iterator().next());
            assertEquals(101L, book.bestBid().getKey());
        }
    }

    @Nested
    @DisplayName("returned instance")
    class ReturnedInstance {

        @Test
        void oldInstanceIsNoLongerCancellableNewOneIs() {
            var book = book();
            var a = buy(1, 100, 10);
            book.add(a);
            var amended = book.amend(a, 100, 5);
            // remove() is boolean-by-contract: the old qty-10 instance no
            // longer matches anything on the book
            assertFalse(book.remove(a), "old instance no longer rests on the book");
            assertTrue(book.remove(amended), "returned instance is the resting one");
            assertTrue(book.bids().isEmpty());
        }

        @Test
        void amendOfReturnedInstanceChains() {
            var book = book();
            var a = buy(1, 100, 10);
            book.add(a);
            var first = book.amend(a, 100, 8);
            var second = book.amend(first, 99, 6);
            assertEquals(99L, second.pxTicks());
            assertEquals(6, second.qtyTicks());
            assertSame(second, book.bids().get(99L).iterator().next());
        }

        @Test
        void samePxSameQtyAmendIsValueEqualToRemoveStillWorks() {
            var book = book();
            var a = buy(1, 100, 10);
            book.add(a);
            var amended = book.amend(a, 100, 10);
            assertEquals(a, amended, "value-equal, but a fresh instance");
            assertNotSame(a, amended);
            // records: value-equality + lifetime-unique ids means the old
            // instance still matches the resting amended instance
            assertTrue(book.remove(a));
            assertTrue(book.bids().isEmpty());
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        @Test
        void rejectsNonPositiveQty() {
            var book = book();
            var a = buy(1, 100, 10);
            book.add(a);
            assertThrows(IllegalArgumentException.class, () -> book.amend(a, 100, 0));
            assertThrows(IllegalArgumentException.class, () -> book.amend(a, 100, -3));
        }

        @Test
        void rejectsNullOrder() {
            var book = book();
            assertThrows(NullPointerException.class, () -> book.amend(null, 100, 5));
        }

        @Test
        void rejectsOrderNotOnBook() {
            var book = book();
            assertThrows(IllegalStateException.class, () -> book.amend(buy(1, 100, 10), 100, 5));
            var a = buy(1, 100, 10);
            book.add(a);
            book.remove(a);
            assertThrows(IllegalStateException.class, () -> book.amend(a, 100, 5));
        }

        @Test
        void rejectsStaleInstanceNotRestingAtItsOwnPrice() {
            var book = book();
            var a = buy(1, 100, 10);
            book.add(a);
            // SimpleOrder is a record: amend() finds the resting order by
            // value-equality at the instance's OWN price level. A stale clone
            // claiming a price where it is not resting must be rejected.
            assertThrows(IllegalStateException.class, () -> book.amend(buy(1, 101, 10), 100, 5));
            // distinct-qty clone at the same px IS value-different from the
            // resting instance and must also be rejected
            assertThrows(IllegalStateException.class, () -> book.amend(buy(1, 100, 7), 100, 5));
            // value-equal clone is accepted (record equality) — documents
            // the identity contract: ids are lifetime-unique, so value-equal
            // implies same order
            var clone = buy(1, 100, 10);
            var amended = book.amend(clone, 100, 5);
            assertEquals(5, amended.qtyTicks());
            // ...and the substitution actually reached the book: the returned
            // instance rests with the reduced quantity (the clone's old qty
            // must NOT still be resting)
            assertSame(amended, book.bids().get(100L).iterator().next());
            assertEquals(5, book.bids().get(100L).iterator().next().qtyTicks());
            assertFalse(book.remove(clone), "the qty-10 clone no longer matches anything resting");
        }

        @Test
        void failedAmendLeavesBookUnchanged() {
            var book = book();
            var a = buy(1, 100, 10);
            book.add(a);
            assertThrows(IllegalArgumentException.class, () -> book.amend(a, 100, 0));
            assertEquals(1, book.bids().get(100L).size());
            assertSame(a, book.bids().get(100L).iterator().next());
        }
    }

    @Nested
    @DisplayName("auction phase workflow (submit/amend/cancel -> uncross)")
    class AuctionWorkflow {

        @Test
        void submitAmendCancelThenUncrossMatchesSemantics() {
            var book = book();
            // build the sample req shape, then exercise amend + cancel first
            var b1 = buy(1, 99, 1000);
            var b2 = buy(2, 98, 700);
            var b3 = buy(3, 100, 102);    // will be amended up to 50000-tick equivalent... keep in tick units
            var s1 = sell(4, 100, 100);
            var s2 = sell(5, 99, 200);
            var s3 = sell(6, 96, 500);
            var s4 = sell(7, 105, 50);    // will be cancelled

            book.add(b1); book.add(b2); book.add(b3);
            book.add(s1); book.add(s2); book.add(s3); book.add(s4);

            // amend: lift the far buy's price (loses priority at old level, alone at new)
            book.amend(b3, 104, 102);
            // cancel the ask that would never execute
            assertTrue(book.remove(s4));

            // uncross the resting book directly; uncross() is a pure query
            var r = book.uncross();
            assertEquals(3, book.bids().size(), "book untouched by uncross()");
            assertEquals(3, book.asks().size(), "book untouched by uncross()");
            // 99 ticks is the max-volume price: demand>=99 = 1102, supply<=99 = 700 -> vol 700
            assertEquals(java.util.OptionalLong.of(99), r.priceTicks());
            assertEquals(700, r.volumeTicks());
        }
    }
}