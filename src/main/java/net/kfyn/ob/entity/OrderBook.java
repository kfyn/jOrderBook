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

    /**
     * Amends a resting order's price and/or quantity with exchange
     * semantics: same price with a quantity reduction keeps the order's
     * queue position; any price change or quantity increase loses time
     * priority (joins the back of the destination level). The resting
     * instance is replaced by the returned instance — use the returned
     * order for subsequent cancels/amends. {@code newQtyTicks <= 0} is
     * rejected; cancelling is {@link #remove}, not a zero-qty amend.
     *
     * @throws NullPointerException     if {@code o} is null
     * @throws IllegalArgumentException if {@code newQtyTicks <= 0}
     * @throws IllegalStateException    if the order is not resting on this book
     */
    Order amend(Order o, long newPxTicks, long newQtyTicks);

    Order pollBest(Side s);

    Map.Entry<Long, ? extends Collection<Order>> bestBid();

    Map.Entry<Long, ? extends Collection<Order>> bestAsk();

    SortedMap<Long, ? extends Collection<Order>> bids();

    SortedMap<Long, ? extends Collection<Order>> asks();
}