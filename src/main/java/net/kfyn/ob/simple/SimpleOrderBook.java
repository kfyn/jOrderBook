package net.kfyn.ob.simple;

import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderBook;
import net.kfyn.ob.entity.Side;

import java.util.*;

public class SimpleOrderBook implements OrderBook {
    private final Instrument instrument;
    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();

    public SimpleOrderBook(Instrument instrument) {
        this.instrument = Objects.requireNonNull(instrument, "instrument");
    }

    @Override public Instrument instrument() {
        return instrument;
    }

    @Override public void add(Order o) {
        Objects.requireNonNull(o, "order");
        side(o.side()).computeIfAbsent(o.pxTicks(), p -> new ArrayDeque<>()).addLast(o);
    }

    @Override public void requeue(Order o) {
        Objects.requireNonNull(o, "order");
        side(o.side()).computeIfAbsent(o.pxTicks(), p -> new ArrayDeque<>()).addFirst(o);
    }

    @Override public boolean remove(Order o) {
        Objects.requireNonNull(o, "order");
        TreeMap<Long, ArrayDeque<Order>> side = side(o.side());
        ArrayDeque<Order> level = side.get(o.pxTicks());
        if (level == null || !level.remove(o)) return false;
        if (level.isEmpty()) side.remove(o.pxTicks());
        return true;
    }

    @Override public Order pollBest(Side s) {
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

    @Override public Map.Entry<Long, ArrayDeque<Order>> bestBid() {
        return best(bids);
    }

    @Override public Map.Entry<Long, ArrayDeque<Order>> bestAsk() {
        return best(asks);
    }

    @Override public NavigableMap<Long, ArrayDeque<Order>> bids() {
        return Collections.unmodifiableNavigableMap(bids);
    }

    @Override public NavigableMap<Long, ArrayDeque<Order>> asks() {
        return Collections.unmodifiableNavigableMap(asks);
    }

    private Map.Entry<Long, ArrayDeque<Order>> best(TreeMap<Long, ArrayDeque<Order>> side) {
        while (!side.isEmpty()) {
            var e = side.firstEntry();
            if (!e.getValue().isEmpty()) return e;
            side.pollFirstEntry();
        }
        return null;
    }

    private TreeMap<Long, ArrayDeque<Order>> side(Side s) {
        return s == Side.BUY ? bids : asks;
    }
}