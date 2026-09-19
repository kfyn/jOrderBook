package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderType;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleOrder;
import net.kfyn.ob.entity.Trade;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PriceTimeAuctionEngineTest {

    private final AuctionEngine auction = AuctionEngine.priceTime();

    private static Order buy(long id, long px, long qty) {
        return new SimpleOrder(id, Side.BUY, px, qty, OrderType.LIMIT);
    }

    private static Order sell(long id, long px, long qty) {
        return new SimpleOrder(id, Side.SELL, px, qty, OrderType.LIMIT);
    }

    @Nested
    class NoCross {

        @Test
        void disjointBooksDoNotCross() {
            var r = auction.uncross(List.of(buy(1, 100, 5)), List.of(sell(2, 101, 5)));
            assertTrue(r.priceTicks().isEmpty());
            assertEquals(0, r.volumeTicks());
            assertTrue(r.trades().isEmpty());
            assertEquals(2, r.leftovers().size());
        }

        @Test
        void adjacentNonTouchingPricesDoNotCross() {
            var r = auction.uncross(List.of(buy(1, 99, 5)), List.of(sell(2, 100, 5)));
            assertTrue(r.priceTicks().isEmpty());
            assertEquals(0, r.volumeTicks());
        }

        @Test
        void emptySideLeavesEverythingUncrossed() {
            var r = auction.uncross(List.of(), List.of(sell(2, 100, 5)));
            assertTrue(r.priceTicks().isEmpty());
            assertEquals(0, r.volumeTicks());
            assertEquals(1, r.leftovers().size());
        }
    }

    @Nested
    class PriceDiscovery {

        @Test
        void clearsAtMaxVolumePrice() {
            // p=100: vol 4 imb 4; p=101: vol 4 imb 1; p=102: no bids reach -> 101
            var r = auction.uncross(
                    List.of(buy(1, 101, 5), buy(2, 100, 3)),
                    List.of(sell(3, 100, 4), sell(4, 102, 6)));
            assertEquals(OptionalLong.of(101), r.priceTicks());
            assertEquals(4, r.volumeTicks());
        }

        @Test
        void askOnlyPriceCanWinViaVolume() {
            // union rule (full argmax over bid u ask prices):
            // p=90 (an ask price) trades 8; the best bid-only candidate, 95, trades only 6
            var r = auction.uncross(
                    List.of(buy(1, 100, 5), buy(2, 95, 3)),
                    List.of(sell(3, 98, 6), sell(4, 90, 100)));
            assertEquals(OptionalLong.of(90), r.priceTicks());
            assertEquals(8, r.volumeTicks());
        }

        @Test
        void crossingRequiresBidsAtOrAbovePrice() {
            // ask at 102 is a candidate price but no bid reaches it: 0 volume there
            var r = auction.uncross(
                    List.of(buy(1, 101, 5), buy(2, 100, 3)),
                    List.of(sell(3, 100, 4), sell(4, 102, 6)));
            assertEquals(4, r.volumeTicks());
            assertEquals(OptionalLong.of(101), r.priceTicks());
        }

        @Test
        void negativePricesCrossNormally() {
            // p=-200: vol 3 imb 4; p=-100: vol 3 imb 4 -> tie, buy pressure -> higher (-100)
            var r = auction.uncross(
                    List.of(buy(1, -100, 5), buy(2, -200, 2)),
                    List.of(sell(3, -100, 3)));
            assertEquals(OptionalLong.of(-100), r.priceTicks());
            assertEquals(3, r.volumeTicks());
        }
    }

    @Nested
    class TieBreak {

        @Test
        void buyPressurePicksHigherPrice() {
            // both 100 and 95 trade 8 with imbalance 2 -> residual demand -> higher
            var r = auction.uncross(List.of(buy(1, 100, 10)), List.of(sell(2, 95, 8)));
            assertEquals(OptionalLong.of(100), r.priceTicks());
            assertEquals(8, r.volumeTicks());
        }

        @Test
        void sellPressurePicksLowerPrice() {
            var r = auction.uncross(List.of(buy(1, 100, 8)), List.of(sell(2, 95, 10)));
            assertEquals(OptionalLong.of(95), r.priceTicks());
            assertEquals(8, r.volumeTicks());
        }

        @Test
        void balancedTiePicksLowerPrice() {
            var r = auction.uncross(List.of(buy(1, 100, 10)), List.of(sell(2, 95, 10)));
            assertEquals(OptionalLong.of(95), r.priceTicks());
            assertEquals(10, r.volumeTicks());
        }

        @Test
        void imbalanceBreaksVolumeTie() {
            // p=97/98: vol 6 imb 4; p=99/100: vol 6 imb 5 -> excluded by imbalance:
            // winner 98 (higher of the min-imbalance pair, buy pressure)
            var r = auction.uncross(
                    List.of(buy(1, 100, 6), buy(2, 98, 4)),
                    List.of(sell(3, 99, 5), sell(4, 97, 6)));
            assertEquals(OptionalLong.of(98), r.priceTicks());
            assertEquals(6, r.volumeTicks());
        }
    }

    @Nested
    class Execution {

        @Test
        void allTradesAtClearingPrice() {
            // p=99: d=5 s=6 vol 5 imb 1; p=100: d=5 s=6 vol 5 imb 1 -> tie,
            // sell pressure -> lower price 99
            var r = auction.uncross(
                    List.of(buy(1, 100, 5), buy(2, 98, 3)),
                    List.of(sell(3, 99, 4), sell(4, 97, 2)));
            assertEquals(OptionalLong.of(99), r.priceTicks());
            assertEquals(5, r.volumeTicks());
            assertEquals(2, r.trades().size());
            assertTrue(r.trades().stream().allMatch(t -> t.pxTicks() == 99));
            // bid 100x5 sweeps ask 99x4 then ask 97x2, FIFO
            assertEquals(4, r.trades().get(0).qtyTicks());
            assertEquals(1, r.trades().get(1).qtyTicks());
            assertEquals(1L, r.trades().get(0).bidOrderId());
            assertEquals(3L, r.trades().get(0).askOrderId());
            assertEquals(4L, r.trades().get(1).askOrderId());
        }

        @Test
        void samePriceFillsFifoWithinSide() {
            var r = auction.uncross(
                    List.of(buy(1, 100, 3), buy(2, 100, 2)),
                    List.of(sell(3, 100, 4)));
            assertEquals(OptionalLong.of(100), r.priceTicks());
            assertEquals(2, r.trades().size());
            assertEquals(3, r.trades().get(0).qtyTicks());
            assertEquals(1L, r.trades().get(0).bidOrderId());
            assertEquals(1, r.trades().get(1).qtyTicks());
            assertEquals(2L, r.trades().get(1).bidOrderId());
        }
    }

    @Nested
    class Leftovers {

        @Test
        void partialFillsReturnReducedQtyWithSameId() {
            var r = auction.uncross(
                    List.of(buy(1, 100, 5), buy(2, 95, 3)),
                    List.of(sell(3, 100, 4)));
            // p=95: vol 4 imb 4; p=100: vol 4 imb 1 -> min imbalance -> 100
            assertEquals(OptionalLong.of(100), r.priceTicks());
            assertEquals(2, r.leftovers().size());
            assertEquals(1L, r.leftovers().get(0).id());
            assertEquals(1, r.leftovers().get(0).qtyTicks());
            assertEquals(2L, r.leftovers().get(1).id());
            assertEquals(3, r.leftovers().get(1).qtyTicks());
        }

        @Test
        void nonCrossingOrdersReturnUntouched() {
            var r = auction.uncross(
                    List.of(buy(1, 100, 5)),
                    List.of(sell(2, 99, 4), sell(3, 120, 7)));
            assertEquals(OptionalLong.of(100), r.priceTicks());
            assertEquals(2, r.leftovers().size());
            Order partial = r.leftovers().getFirst();
            assertEquals(1L, partial.id());
            assertEquals(1, partial.qtyTicks());
            Order untouched = r.leftovers().get(1);
            assertEquals(3L, untouched.id());
            assertEquals(120, untouched.pxTicks());
            assertEquals(7, untouched.qtyTicks());
        }
    }

    @Nested
    class Validations {

        @Test
        void rejectsOrderOnWrongSide() {
            assertThrows(IllegalArgumentException.class,
                    () -> auction.uncross(List.of(sell(1, 100, 5)), List.of(sell(2, 100, 5))));
            assertThrows(IllegalArgumentException.class,
                    () -> auction.uncross(List.of(buy(1, 100, 5)), List.of(buy(2, 100, 5))));
        }

        @Test
        void rejectsNullLists() {
            assertThrows(NullPointerException.class, () -> auction.uncross(null, List.of()));
            assertThrows(NullPointerException.class, () -> auction.uncross(List.of(), null));
        }
    }

    @Nested
    class Properties {

        @Test
        void conservationAndInvariantsUnderRandomFlow() {
            Random rand = new Random(42);
            for (int iter = 0; iter < 500; iter++) {
                List<Order> bids = randomOrders(rand, Side.BUY);
                List<Order> asks = randomOrders(rand, Side.SELL);
                var r = auction.uncross(bids, asks);

                long bidInput = qty(bids);
                long askInput = qty(asks);
                if (r.priceTicks().isEmpty()) {
                    assertEquals(0, r.volumeTicks(), "volume on no-cross");
                    assertEquals(bidInput + askInput, qty(r.leftovers()), "all leftovers on no-cross");
                    continue;
                }

                long price = r.priceTicks().getAsLong();
                long traded = r.trades().stream().mapToLong(Trade::qtyTicks).sum();
                assertEquals(r.volumeTicks(), traded, "volume equals trade sum");
                // conservation: every tick is traded once on each side or left over
                assertEquals(bidInput + askInput, qty(r.leftovers()) + 2 * traded,
                        "leftover + 2*traded == submitted");
                assertTrue(r.trades().stream().allMatch(t -> t.pxTicks() == price),
                        "all trades at clearing price");
                assertTrue(r.trades().stream().allMatch(t -> t.qtyTicks() > 0), "positive fills");
                assertTrue(containsPrice(bids, price) || containsPrice(asks, price),
                        "clearing price is a submitted price");
            }
        }
    }

    private static List<Order> randomOrders(Random rand, Side side) {
        int n = rand.nextInt(12);
        List<Order> out = new ArrayList<>(n);
        long id = 1;
        for (int i = 0; i < n; i++) {
            long px = rand.nextInt(81) - 40;             // -40..40, spread-product range
            long qty = 1 + rand.nextInt(12);
            out.add(new SimpleOrder(id++, side, px, qty, OrderType.LIMIT));
        }
        return out;
    }

    private static long qty(List<Order> orders) {
        long total = 0;
        for (Order o : orders) {
            total += o.qtyTicks();
        }
        return total;
    }

    private static boolean containsPrice(List<Order> orders, long price) {
        return orders.stream().anyMatch(o -> o.pxTicks() == price);
    }
}