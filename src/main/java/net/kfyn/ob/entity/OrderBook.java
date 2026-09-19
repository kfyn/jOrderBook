package net.kfyn.ob.entity;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;

/**
 * Price-time priority order book. Levels are keyed by pxTicks (bids
 * descending, asks ascending); each level holds its orders in FIFO
 * priority order. The returned maps are read-only views, but the level
 * collections are live: the engine drains them in place and emptied
 * levels are pruned eagerly.
 */
public interface OrderBook {
    Instrument instrument();

    void add(Order o);

    void requeue(Order o);

    boolean remove(Order o);

    Order pollBest(Side s);

    Map.Entry<Long, ? extends Collection<Order>> bestBid();

    Map.Entry<Long, ? extends Collection<Order>> bestAsk();

    SortedMap<Long, ? extends Collection<Order>> bids();

    SortedMap<Long, ? extends Collection<Order>> asks();
}