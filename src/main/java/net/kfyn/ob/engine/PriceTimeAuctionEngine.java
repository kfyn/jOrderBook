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
 */
public class PriceTimeAuctionEngine implements AuctionEngine {

    @Override public AuctionResult uncross(List<Order> bids, List<Order> asks) {
        Objects.requireNonNull(bids, "bids");
        Objects.requireNonNull(asks, "asks");
        for (Order b : bids) {
            if (b.side() != Side.BUY) throw new IllegalArgumentException("bid side: " + b.side());
        }
        for (Order a : asks) {
            if (a.side() != Side.SELL) throw new IllegalArgumentException("ask side: " + a.side());
        }

        TreeMap<Long, Long> bidQty = aggregate(bids);
        TreeMap<Long, Long> askQty = aggregate(asks);
        if (bidQty.isEmpty() || askQty.isEmpty()) {
            return uncrossed(bids, asks);
        }

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
        if (bestVol == 0) {
            return uncrossed(bids, asks);
        }

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
        List<Order> leftovers = new ArrayList<>(bids);
        leftovers.addAll(asks);
        return new AuctionResult(OptionalLong.empty(), 0, List.of(), leftovers);
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