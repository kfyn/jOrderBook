package net.kfyn.ob.entity;

import java.util.*;

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
        return bids.firstEntry();
    }

    public Map.Entry<Long, ArrayDeque<Order>> bestAsk() {
        return asks.firstEntry();
    }

    public NavigableMap<Long, ArrayDeque<Order>> bids() {
        return bids;
    }

    public NavigableMap<Long, ArrayDeque<Order>> asks() {
        return asks;
    }

    private TreeMap<Long, ArrayDeque<Order>> side(Side s) {
        return s == Side.BUY ? bids : asks;
    }
}