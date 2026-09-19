package net.kfyn.ob.entity;

import java.util.*;

/**
 * Single-threaded. Ticks must originate from this book's instrument
 * (pxTicks/qtyTicks) or the book's ordering is undefined.
 */
public class OrderBook {
    private final Instrument instrument;
    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();

    public OrderBook(Instrument instrument) {
        this.instrument = Objects.requireNonNull(instrument, "instrument");
    }

    public Instrument instrument() {
        return instrument;
    }

    public void add(Order o) {
        side(o.side()).computeIfAbsent(o.pxT(), p -> new ArrayDeque<>()).addLast(o);
    }

    public void requeue(Order o) {
        side(o.side()).computeIfAbsent(o.pxT(), p -> new ArrayDeque<>()).addFirst(o);
    }

    public Map.Entry<Long, ArrayDeque<Order>> bestBid() {
        return best(bids);
    }

    public Map.Entry<Long, ArrayDeque<Order>> bestAsk() {
        return best(asks);
    }

    public NavigableMap<Long, ArrayDeque<Order>> bids() {
        return view(bids);
    }

    public NavigableMap<Long, ArrayDeque<Order>> asks() {
        return view(asks);
    }

    private Map.Entry<Long, ArrayDeque<Order>> best(TreeMap<Long, ArrayDeque<Order>> side) {
        while (!side.isEmpty()) {
            var e = side.firstEntry();
            if (!e.getValue().isEmpty()) return e;
            side.pollFirstEntry();
        }
        return null;
    }

    private NavigableMap<Long, ArrayDeque<Order>> view(TreeMap<Long, ArrayDeque<Order>> side) {
        return Collections.unmodifiableNavigableMap(side);
    }

    private TreeMap<Long, ArrayDeque<Order>> side(Side s) {
        return s == Side.BUY ? bids : asks;
    }
}