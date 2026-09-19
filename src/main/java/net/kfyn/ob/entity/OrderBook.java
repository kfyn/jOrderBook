package net.kfyn.ob.entity;

import java.util.Map;
import java.util.NavigableMap;
import java.util.ArrayDeque;

public interface OrderBook {
    Instrument instrument();

    void add(Order o);

    void requeue(Order o);

    boolean remove(Order o);

    Order pollBest(Side s);

    Map.Entry<Long, ArrayDeque<Order>> bestBid();

    Map.Entry<Long, ArrayDeque<Order>> bestAsk();

    NavigableMap<Long, ArrayDeque<Order>> bids();

    NavigableMap<Long, ArrayDeque<Order>> asks();
}