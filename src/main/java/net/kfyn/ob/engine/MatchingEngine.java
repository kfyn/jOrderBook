package net.kfyn.ob.engine;

import net.kfyn.ob.entity.Order;
import net.kfyn.ob.entity.OrderBook;
import net.kfyn.ob.entity.Side;
import net.kfyn.ob.entity.Trade;

import java.util.*;

/**
 * Price-time priority matching over an OrderBook. Incoming executes against
 * resting orders at the resting price; unfilled remainder rests on the book.
 * Partially filled resting orders keep their FIFO position. No self-trade
 * prevention. Single-threaded.
 */
public class MatchingEngine {
    private final OrderBook book;
    private final Map<Long, Order> open = new HashMap<>();

    public MatchingEngine(OrderBook book) {
        this.book = Objects.requireNonNull(book, "book");
    }

    public MatchResult submit(Order incoming) {
        Objects.requireNonNull(incoming, "order");
        if (open.containsKey(incoming.id()))
            throw new IllegalArgumentException("duplicate open order id: " + incoming.id());

        List<Trade> trades = new ArrayList<>();
        long remaining = incoming.qtyTicks();

        while (remaining > 0) {
            var best = incoming.side() == Side.BUY ? book.bestAsk() : book.bestBid();
            if (best == null || !crosses(incoming, best.getKey())) break;

            Order resting = best.getValue().pollFirst();
            long fill = Math.min(remaining, resting.qtyTicks());
            if (resting.qtyTicks() > fill) {
                Order reduced = withQty(resting, resting.qtyTicks() - fill);
                best.getValue().addFirst(reduced);        // keeps FIFO position
                open.put(reduced.id(), reduced);
            } else {
                open.remove(resting.id());
            }
            trades.add(new Trade(
                    incoming.side() == Side.BUY ? incoming.id() : resting.id(),
                    incoming.side() == Side.BUY ? resting.id() : incoming.id(),
                    best.getKey(), fill));                 // fill at resting price
            remaining -= fill;
        }

        if (remaining > 0) {
            Order remainder = remaining == incoming.qtyTicks() ? incoming : withQty(incoming, remaining);
            book.add(remainder);
            open.put(remainder.id(), remainder);
        }
        return new MatchResult(trades, remaining);
    }

    public boolean cancel(long orderId) {
        Order o = open.remove(orderId);
        if (o == null) return false;
        var side = o.side() == Side.BUY ? book.bids() : book.asks();
        ArrayDeque<Order> level = side.get(o.pxTicks());
        return level != null && level.remove(o);
    }

    public boolean isOpen(long orderId) {
        return open.containsKey(orderId);
    }

    public int openCount() {
        return open.size();
    }

    private boolean crosses(Order incoming, long bestOppositePx) {
        return incoming.side() == Side.BUY
                ? incoming.pxTicks() >= bestOppositePx
                : incoming.pxTicks() <= bestOppositePx;
    }

    private static Order withQty(Order o, long qtyTicks) {
        return new Order(o.id(), o.side(), o.pxTicks(), qtyTicks, o.orderType());
    }
}