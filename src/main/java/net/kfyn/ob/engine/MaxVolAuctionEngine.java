package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleOrder;
import net.kfyn.ob.entity.SimpleTrade;
import net.kfyn.ob.entity.Trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stateless, single-threaded.
 *
 * <p>Tie-break, used when several candidate prices share the maximum
 * volume: prefer the price with the smallest demand/supply imbalance;
 * among equal-imbalance ties pick the highest price under pure buy
 * pressure, otherwise the lowest.
 */
public class MaxVolAuctionEngine implements AuctionEngine {

    @Override
    public AuctionResult uncross(List<Order> bids, List<Order> asks) {
        Objects.requireNonNull(bids, "bids");
        Objects.requireNonNull(asks, "asks");
        // Validate sides and quantities at this boundary, and find the extreme
        // prices, in the same pass. Rejected outright when violated.
        long bestBid = Long.MIN_VALUE;
        for (Order b : bids) {
            Objects.requireNonNull(b, "bid");
            if (b.side() != Side.BUY) throw new IllegalArgumentException("bid side: " + b.side());
            if (b.qtyTicks() <= 0) throw new IllegalArgumentException("bid qty must be positive: " + b.qtyTicks());
            if (b.pxTicks() > bestBid) bestBid = b.pxTicks();
        }
        long bestAsk = Long.MAX_VALUE;
        for (Order a : asks) {
            Objects.requireNonNull(a, "ask");
            if (a.side() != Side.SELL) throw new IllegalArgumentException("ask side: " + a.side());
            if (a.qtyTicks() <= 0) throw new IllegalArgumentException("ask qty must be positive: " + a.qtyTicks());
            if (a.pxTicks() < bestAsk) bestAsk = a.pxTicks();
        }
        if (bids.isEmpty() || asks.isEmpty()) {
            return uncrossed(bids, asks);
        }
        // Early exit: a clearing price needs bids >= p and asks <= p
        if (bestBid < bestAsk) {
            return uncrossed(bids, asks);
        }

        TreeMap<Long, Long> bidQty = aggregate(bids);
        TreeMap<Long, Long> askQty = aggregate(asks);

        TreeSet<Long> prices = new TreeSet<>(bidQty.keySet());
        prices.addAll(askQty.keySet());

        // Ascending sweep: supply accumulates asks <= price, demand drops bids < price.
        long demand = sum(bidQty.values());
        long supply = 0;
        long bestVol = 0;
        long bestImb = Long.MAX_VALUE;
        List<long[]> ties = new ArrayList<>();   // {price, demand, supply}
        for (long price : prices) {
            supply = Math.addExact(supply, askQty.getOrDefault(price, 0L));
            long volume = Math.min(demand, supply);
            // demand and supply are non-negative and bounded by sums already
            // checked with addExact, so their difference cannot overflow.
            long imb = Math.abs(demand - supply);
            if (volume > bestVol || (volume == bestVol && imb < bestImb)) {
                bestVol = volume;
                bestImb = imb;
                ties.clear();
                ties.add(new long[]{price, demand, supply});
            } else if (volume == bestVol && imb == bestImb) {
                ties.add(new long[]{price, demand, supply});
            }
            demand = Math.subtractExact(demand, bidQty.getOrDefault(price, 0L));
        }
        // bestVol > 0 is guaranteed here:  V(bestBid) >= min(bidQty(bestBid), askQty(bestAsk)) > 0.
        return execute(selectPrice(ties), bids, asks);
    }

    /** Ties are ascending by price. Pressure rule; lower price as final fallback. */
    private static long selectPrice(List<long[]> ties) {
        boolean allBuyPressure = ties.stream().allMatch(t -> t[1] > t[2]);
        if (allBuyPressure) {
            return ties.getLast()[0];
        }
        return ties.getFirst()[0];
    }

    private static AuctionResult execute(long price, List<Order> bids, List<Order> asks) {
        List<Order> execBids = new ArrayList<>();
        List<Order> execAsks = new ArrayList<>();
        for (Order b : bids) {
            if (b.pxTicks() >= price) execBids.add(b);
        }
        for (Order a : asks) {
            if (a.pxTicks() <= price) execAsks.add(a);
        }

        long[] remB = new long[execBids.size()];
        long[] remA = new long[execAsks.size()];
        for (int i = 0; i < remB.length; i++) {
            remB[i] = execBids.get(i).qtyTicks();
        }
        for (int j = 0; j < remA.length; j++) {
            remA[j] = execAsks.get(j).qtyTicks();
        }

        List<Trade> trades = new ArrayList<>();
        long executed = 0;
        int i = 0;
        int j = 0;
        while (i < execBids.size() && j < execAsks.size()) {
            long fill = Math.min(remB[i], remA[j]);
            trades.add(new SimpleTrade(execBids.get(i).id(), execAsks.get(j).id(), price, fill));
            executed = Math.addExact(executed, fill);
            remB[i] -= fill;
            remA[j] -= fill;
            if (remB[i] == 0) i++;
            if (remA[j] == 0) j++;
        }

        // leftovers in input order per side: untouched as-is, partials reduced
        List<Order> leftovers = new ArrayList<>();
        int bi = 0;
        for (Order b : bids) {
            if (b.pxTicks() >= price) {
                if (remB[bi] > 0) leftovers.add(reduced(b, remB[bi]));
                bi++;
            } else {
                leftovers.add(b);
            }
        }
        int ai = 0;
        for (Order a : asks) {
            if (a.pxTicks() <= price) {
                if (remA[ai] > 0) leftovers.add(reduced(a, remA[ai]));
                ai++;
            } else {
                leftovers.add(a);
            }
        }

        return new AuctionResult(OptionalLong.of(price), executed, trades, leftovers);
    }

    private static Order reduced(Order o, long qtyTicks) {
        return new SimpleOrder(o.id(), o.side(), o.pxTicks(), qtyTicks, o.orderType());
    }

    private static AuctionResult uncrossed(List<Order> bids, List<Order> asks) {
        // A true single copy would require AuctionResult to
        // trust a caller-supplied immutable list; safety was kept instead.
        List<Order> leftovers = new ArrayList<>(bids.size() + asks.size());
        leftovers.addAll(bids);
        leftovers.addAll(asks);
        return new AuctionResult(OptionalLong.empty(), 0, List.of(), List.copyOf(leftovers));
    }

    private static TreeMap<Long, Long> aggregate(List<Order> orders) {
        TreeMap<Long, Long> byPrice = new TreeMap<>();
        for (Order o : orders) {
            byPrice.merge(o.pxTicks(), o.qtyTicks(), Math::addExact);
        }
        return byPrice;
    }

    private static long sum(Collection<Long> values) {
        long total = 0;
        for (long v : values) {
            total = Math.addExact(total, v);
        }
        return total;
    }
}