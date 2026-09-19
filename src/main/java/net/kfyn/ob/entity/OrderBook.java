package net.kfyn.ob.entity;

import net.kfyn.common.number.KNumber;

import java.util.*;

public class OrderBook {
    private final TreeMap<KNumber, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<KNumber, ArrayDeque<Order>> asks = new TreeMap<>();

    public void add(Order o) {
        side(o.side()).computeIfAbsent(o.px(), p -> new ArrayDeque<>()).addLast(o);
    }

    public void requeue(Order o) {
        side(o.side()).computeIfAbsent(o.px(), p -> new ArrayDeque<>()).addFirst(o);
    }

    public Map.Entry<KNumber, ArrayDeque<Order>> bestBid() {
        return bids.firstEntry();
    }

    public Map.Entry<KNumber, ArrayDeque<Order>> bestAsk() {
        return asks.firstEntry();
    }

    public NavigableMap<KNumber, ArrayDeque<Order>> bids() {
        return bids;
    }

    public NavigableMap<KNumber, ArrayDeque<Order>> asks() {
        return asks;
    }

    private TreeMap<KNumber, ArrayDeque<Order>> side(Side s) {
        return s == Side.BUY ? bids : asks;
    }
}
