package net.kfyn.ob.entity;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;

public interface OrderBook {
    Instrument instrument();

    void add(Order o);

    boolean remove(Order o);

    Order amend(Order o, long newPxTicks, long newQtyTicks);

    Order pollBest(Side s);

    Map.Entry<Long, ? extends Collection<Order>> bestBid();

    Map.Entry<Long, ? extends Collection<Order>> bestAsk();

    SortedMap<Long, ? extends Collection<Order>> bids();

    SortedMap<Long, ? extends Collection<Order>> asks();
}
