package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Instrument;
import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderBook;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.SimpleOrder;

import java.util.*;

public class PriceTimeOrderBook implements OrderBook {
    private final Instrument instrument;
    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();

    public PriceTimeOrderBook(Instrument instrument) {
        this.instrument = Objects.requireNonNull(instrument, "instrument");
    }

    @Override
    public Instrument instrument() {
        return instrument;
    }

    @Override
    public void add(Order o) {
        Objects.requireNonNull(o, "order");
        side(o.side()).computeIfAbsent(o.pxTicks(), _ -> new ArrayDeque<>()).addLast(o);
    }

    @Override
    public void requeue(Order o) {
        Objects.requireNonNull(o, "order");
        side(o.side()).computeIfAbsent(o.pxTicks(), _ -> new ArrayDeque<>()).addFirst(o);
    }

    @Override
    public boolean remove(Order o) {
        Objects.requireNonNull(o, "order");
        TreeMap<Long, ArrayDeque<Order>> side = side(o.side());
        ArrayDeque<Order> level = side.get(o.pxTicks());
        if (level == null || !level.remove(o)) return false;
        if (level.isEmpty()) side.remove(o.pxTicks());
        return true;
    }

    @Override
    public Order amend(Order o, long newPxTicks, long newQtyTicks) {
        Objects.requireNonNull(o, "order");
        if (newQtyTicks <= 0) throw new IllegalArgumentException("newQtyTicks must be positive: " + newQtyTicks);
        TreeMap<Long, ArrayDeque<Order>> side = side(o.side());
        ArrayDeque<Order> level = side.get(o.pxTicks());
        if (level == null || !level.contains(o)) {
            throw new IllegalStateException("order not resting on this book: " + o.id());
        }
        Order amended = new SimpleOrder(o.id(), o.side(), newPxTicks, newQtyTicks, o.orderType());
        if (newPxTicks == o.pxTicks() && newQtyTicks < o.qtyTicks()) {
            // same level, reduced qty: keep queue position (rebuild in place)
            ArrayDeque<Order> rebuilt = new ArrayDeque<>(level.size());
            for (Order resting : level) {
                // value equality
                rebuilt.addLast(Objects.equals(resting, o) ? amended : resting);
            }
            side.put(newPxTicks, rebuilt);
        } else {
            // price change or qty increase: lose time priority, join back of level
            level.remove(o);
            if (level.isEmpty()) side.remove(o.pxTicks());
            side.computeIfAbsent(newPxTicks, _ -> new ArrayDeque<>()).addLast(amended);
        }
        return amended;
    }

    @Override
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

    @Override
    public Map.Entry<Long, ? extends Collection<Order>> bestBid() {
        return best(bids);
    }

    @Override
    public Map.Entry<Long, ? extends Collection<Order>> bestAsk() {
        return best(asks);
    }

    @Override
    public SortedMap<Long, ? extends Collection<Order>> bids() {
        return Collections.unmodifiableNavigableMap(bids);
    }

    @Override
    public SortedMap<Long, ? extends Collection<Order>> asks() {
        return Collections.unmodifiableNavigableMap(asks);
    }

    /**
     * Runs the auction uncross over the resting book as a pure query: orders
     * are flattened per side in price-time order (price priority, FIFO within
     * a level) and uncrossed by {@link MaxVolAuctionEngine}. The book is
     * not modified.
     */
    public AuctionResult uncross() {
        List<Order> bids = new ArrayList<>();
        this.bids.forEach((_, level) -> bids.addAll(level));
        List<Order> asks = new ArrayList<>();
        this.asks.forEach((_, level) -> asks.addAll(level));
        return new MaxVolAuctionEngine().uncross(bids, asks);
    }

    /**
     * Closes the auction: uncrosses the book and settles it to the outcome.
     * Fully filled orders are removed, partially filled orders reduced to new
     * instances with the same id (queue order preserved), and unfilled orders
     * left resting as-is. Returns the uncrossing result.
     */
    public AuctionResult close() {
        AuctionResult result = uncross();
        bids.clear();
        asks.clear();
        for (Order o : result.leftovers()) {
            side(o.side()).computeIfAbsent(o.pxTicks(), _ -> new ArrayDeque<>()).addLast(o);
        }
        return result;
    }

    private Map.Entry<Long, ? extends Collection<Order>> best(TreeMap<Long, ArrayDeque<Order>> side) {
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