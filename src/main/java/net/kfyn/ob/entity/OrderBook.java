package net.kfyn.ob.entity;

import java.util.*;

/**
 * Single-threaded. Ticks must originate from this book's instrument
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
        side(o.side()).computeIfAbsent(o.pxTicks(), p -> new ArrayDeque<>()).addLast(o);
    }

    public void requeue(Order o) {
        side(o.side()).computeIfAbsent(o.pxTicks(), p -> new ArrayDeque<>()).addFirst(o);
    }

    public boolean remove(Order o) {
        TreeMap<Long, ArrayDeque<Order>> side = side(o.side());
        ArrayDeque<Order> level = side.get(o.pxTicks());
        if (level == null || !level.remove(o)) return false;
        if (level.isEmpty()) side.remove(o.pxTicks());
        return true;
    }

    public Order pollBest(Side s) {
        TreeMap<Long, ArrayDeque<Order>> side = side(s);
        while (!side.isEmpty()) {
            var e = side.firstEntry();
            ArrayDeque<Order> level = e.getValue();
            if (level.isEmpty()) {
                side.pollFirstEntry();
                continue;
            }
            Order o = level.pollFirst();
            if (level.isEmpty()) side.remove(e.getKey());
            return o;
        }
        return null;
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